package com.guidedtour.app.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** One entry in the reading queue: a scan's text, or its translation. */
data class QueueItem(
    val id: Long,
    val translated: Boolean,
    val title: String,
    val text: String,
    val languageTag: String?,
) {
    val key: Long get() = id * 2 + if (translated) 1 else 0
}

data class PlayerState(
    val queue: List<QueueItem> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    /** Range of the current item's text being spoken right now. */
    val highlight: IntRange? = null,
) {
    val current: QueueItem? get() = queue.getOrNull(index)
    val active: Boolean get() = current != null
    val upcoming: List<QueueItem> get() = if (index < 0) emptyList() else queue.drop(index + 1)
}

/**
 * Process-wide reading queue on top of TextToSpeech. Texts are read sentence by sentence so long
 * plaques fit the engine's input limit and can be paused, resumed and skipped. Call from the main thread.
 */
class TextReader private constructor(context: Context) {
    companion object {
        /** Speaking pace at normal speed until one has been measured, to turn seconds into text. */
        private const val DEFAULT_CHARS_PER_SECOND = 16f

        @Volatile private var instance: TextReader? = null
        fun get(context: Context): TextReader =
            instance ?: synchronized(this) { instance ?: TextReader(context.applicationContext).also { instance = it } }
    }

    private val appContext = context
    private val main = Handler(Looper.getMainLooper())
    private val settings = Settings.get(context)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var focusRequest: AudioFocusRequest? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state
    private val _speakingId = MutableStateFlow<Long?>(null)
    val speakingId: StateFlow<Long?> = _speakingId

    private val queue = mutableListOf<QueueItem>()
    private var index = -1
    private var chunks: List<Chunk> = emptyList()
    private var chunkIndex = 0
    /** Where speaking starts inside the current sentence: 0, or a word after a seek. */
    private var startInChunk = 0
    /** Measured speaking pace in characters per second (0 until measured), from word timings. */
    private var pace = 0f
    private var lastWordAt = 0L
    private var lastWordOffset = -1
    private var playing = false
    private var highlight: IntRange? = null
    private var generation = 0
    private var resumeOnFocusGain = false

    private var ready = false
    private val _speechReady = MutableStateFlow(false)
    /** True once the speech engine has started and voices can be listed. */
    val speechReady: StateFlow<Boolean> = _speechReady
    private var pending: (() -> Unit)? = null
    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.setAudioAttributes(speechAttributes)
            _speechReady.value = true
            pending?.invoke()
        }
        pending = null
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { main.post { onChunkFinished(utteranceId) } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { onChunkFinished(utteranceId) } }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                main.post { onRange(utteranceId, start, end) }
            }
        })
    }

    // ---- Queue control ----

    /** Replaces the queue with [item] and starts reading it. */
    fun play(item: QueueItem) = playAll(listOf(item))

    /** Replaces the queue with [items] and starts reading from the first. */
    fun playAll(items: List<QueueItem>) {
        if (items.isEmpty()) return
        queue.clear()
        queue += items.distinctBy { it.key }
        startItem(0)
    }

    /** Adds [item] after the current one ([next] = true) or at the end, without interrupting. */
    fun enqueue(item: QueueItem, next: Boolean = false) {
        if (index < 0) { play(item); return }
        if (queue[index].key == item.key) return
        val existing = queue.indexOfFirst { it.key == item.key }
        if (existing > index) {
            queue.removeAt(existing)
        } else if (existing in 0 until index) {
            queue.removeAt(existing)
            index--
        }
        queue.add(if (next) index + 1 else queue.size, item)
        publish()
    }

    fun removeAt(position: Int) {
        if (position !in queue.indices) return
        when {
            position == index -> {
                queue.removeAt(position)
                if (position < queue.size) startItem(position) else finish()
            }
            else -> {
                queue.removeAt(position)
                if (position < index) index--
                publish()
            }
        }
    }

    /** Moves an upcoming item by [delta] places (negative = earlier). */
    fun move(position: Int, delta: Int) {
        val target = position + delta
        if (position <= index || target <= index || position !in queue.indices || target !in queue.indices) return
        queue.add(target, queue.removeAt(position))
        publish()
    }

    fun pause() {
        if (!playing) return
        resumeOnFocusGain = false
        interrupt()
        abandonFocus()
        publish()
    }

    fun resume() {
        if (playing || index < 0) return
        playing = true
        requestFocus()
        speakChunk()
        publish()
    }

    fun toggle() = if (playing) pause() else resume()

    fun next() {
        if (index < 0) return
        if (index + 1 < queue.size) startItem(index + 1) else finish()
    }

    /** Restarts the current item if it has progressed past its first sentence, else goes back one. */
    fun previous() {
        if (index < 0) return
        startItem(if (chunkIndex > 0 || index == 0) index else index - 1)
    }

    fun stop() = finish()

    /**
     * Jumps about [seconds] back (negative) or forward in the current text. Speech has no clock, so
     * the distance is estimated from the text length and speed; reading restarts at a word.
     * Forward past the end goes to the next item; back past the start restarts this one.
     */
    fun seekBy(seconds: Int) {
        if (index < 0 || chunks.isEmpty()) return
        val here = highlight?.first ?: (chunks[chunkIndex].start + startInChunk)
        val charsPerSecond = if (pace > 0f) pace else DEFAULT_CHARS_PER_SECOND * settings.rate.value
        val target = here + (seconds * charsPerSecond).toInt()
        if (target >= queue[index].text.length) { next(); return }
        seekTo(target.coerceAtLeast(0))
    }

    private fun seekTo(offset: Int) {
        val wasPlaying = playing
        if (wasPlaying) interrupt()
        chunkIndex = chunks.indexOfLast { it.start <= offset }.coerceAtLeast(0)
        val chunk = chunks[chunkIndex]
        var local = (offset - chunk.start).coerceIn(0, chunk.text.length)
        while (local > 0 && !chunk.text[local - 1].isWhitespace()) local--
        startInChunk = local
        highlight = null
        if (wasPlaying) {
            playing = true
            speakChunk()
        }
        publish()
    }

    // ---- Playback ----

    private fun startItem(position: Int) {
        index = position
        val item = queue[position]
        val maxLen = minOf(TextToSpeech.getMaxSpeechInputLength() - 1, 1500)
        chunks = splitForSpeech(item.text, maxLen, localeFor(item.languageTag))
        chunkIndex = 0
        startInChunk = 0
        lastWordOffset = -1
        highlight = null
        if (chunks.isEmpty()) { next(); return }
        playing = true
        requestFocus()
        speakChunk()
        publish()
    }

    private fun speakChunk() {
        val run = run@{
            val item = queue.getOrNull(index) ?: return@run
            val chunk = chunks.getOrNull(chunkIndex) ?: return@run
            val locale = localeFor(item.languageTag)
            configureVoice(locale, settings.voices.value[locale.language])
            val id = ++generation
            val text = chunk.text.substring(startInChunk.coerceAtMost(chunk.text.length))
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id.toString()) == TextToSpeech.ERROR) {
                onChunkFinished(id.toString())
            }
        }
        if (ready) run() else pending = run
    }

    /** Picks the language, the user's chosen voice for it (if still installed), speed and pitch. */
    private fun configureVoice(locale: Locale, chosenName: String?) {
        val result = tts.setLanguage(locale)
        var effective = locale
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            effective = Locale.getDefault()
            tts.setLanguage(effective)
        }
        val chosen = chosenName?.let { name -> tts.voices.orEmpty().firstOrNull { it.name == name } }
        (chosen ?: bestVoice(effective))?.let { tts.voice = it }
        tts.setSpeechRate(settings.rate.value)
        tts.setPitch(settings.pitch.value)
    }

    /** Installed voices for [language], highest quality first (offline before online at equal quality). */
    private fun sortedVoices(language: String): List<Voice> = runCatching {
        tts.voices.orEmpty()
            .filter { it.locale.language == language && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.isNetworkConnectionRequired }.thenBy { it.name })
    }.getOrDefault(emptyList())

    /** Default voice per language, as its position in the settings picker ("Voice 5" = 5). */
    private val preferredVoiceNumber = mapOf("en" to 5)

    /**
     * The voice used when the user hasn't picked one: the best-quality installed voice, preferring the
     * phone's own region (US vs UK English) and skipping online-only voices while offline.
     */
    private fun bestVoice(locale: Locale): Voice? {
        preferredVoiceNumber[locale.language]
            ?.let { sortedVoices(locale.language).getOrNull(it - 1) }
            ?.takeIf { !it.isNetworkConnectionRequired || isOnline() }
            ?.let { return it }
        val usable = sortedVoices(locale.language).filter { !it.isNetworkConnectionRequired || isOnline() }
        val region = locale.country.ifBlank { Locale.getDefault().country }
        return usable.firstOrNull { it.locale.country == region } ?: usable.firstOrNull()
    }

    private fun isOnline(): Boolean = runCatching {
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
        manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(false)

    /** Installed voices for [language] (e.g. "en"), best first. Empty until [speechReady]. */
    fun voicesFor(language: String): List<Voice> = if (ready) sortedVoices(language) else emptyList()

    /** Speaks [sample] with [voiceName] (or the default voice if null) so the user can compare. Pauses reading. */
    fun preview(language: String, voiceName: String?, sample: String) {
        if (!ready) return
        pause()
        configureVoice(Locale.forLanguageTag(language), voiceName)
        tts.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "preview")
    }

    fun stopPreview() {
        if (!playing) tts.stop()
    }

    private fun onChunkFinished(utteranceId: String?) {
        if (!playing || utteranceId != generation.toString()) return
        chunkIndex++
        startInChunk = 0
        highlight = null
        if (chunkIndex < chunks.size) {
            speakChunk()
            publish()
        } else {
            next()
        }
    }

    private fun onRange(utteranceId: String?, start: Int, end: Int) {
        if (!playing || utteranceId != generation.toString()) return
        val offset = (chunks.getOrNull(chunkIndex)?.start ?: return) + startInChunk
        highlight = (offset + start) until (offset + end)
        measurePace(offset + start)
        publish()
    }

    /** Updates [pace] from the time between spoken words; pauses between sentences count too. */
    private fun measurePace(position: Int) {
        val now = SystemClock.elapsedRealtime()
        if (lastWordOffset in 0 until position) {
            val seconds = (now - lastWordAt) / 1000f
            if (seconds in 0.05f..3f) {
                val sample = (position - lastWordOffset) / seconds
                pace = if (pace == 0f) sample else pace * 0.9f + sample * 0.1f
            }
        }
        lastWordAt = now
        lastWordOffset = position
    }

    /** Stops speaking but keeps the queue and position. */
    private fun interrupt() {
        lastWordOffset = -1
        playing = false
        generation++
        pending = null
        tts.stop()
    }

    private fun finish() {
        interrupt()
        queue.clear()
        index = -1
        chunks = emptyList()
        chunkIndex = 0
        startInChunk = 0
        highlight = null
        resumeOnFocusGain = false
        abandonFocus()
        publish()
    }

    private fun publish() {
        _state.value = PlayerState(queue.toList(), index, playing, highlight)
        _speakingId.value = queue.getOrNull(index)?.id
    }

    private fun localeFor(tag: String?): Locale = tag?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()

    // ---- Audio focus: duck music while reading, pause for calls and other speech ----

    /** Takes audio focus: other apps pause (or just lower their volume if the user chose that) and resume afterwards. */
    private fun requestFocus() {
        if (focusRequest != null) return
        val gain = if (settings.duckMusic.value) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        val request = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(speechAttributes)
            .setOnAudioFocusChangeListener({ change -> onFocusChange(change) }, main)
            .build()
        audio.requestAudioFocus(request)
        focusRequest = request
    }

    private fun abandonFocus() {
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> if (playing) { pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (playing) {
                interrupt()
                resumeOnFocusGain = true
                publish()
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocusGain) {
                resumeOnFocusGain = false
                resume()
            }
        }
    }
}
