package com.guidedtour.app

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guidedtour.app.data.AppDatabase
import com.guidedtour.app.data.Accuracy
import com.guidedtour.app.data.AiProofreader
import com.guidedtour.app.data.Backup
import com.guidedtour.app.data.CloudOcr
import com.guidedtour.app.data.PlaceAbout
import com.guidedtour.app.data.Headliner
import com.guidedtour.app.data.printedTitle
import com.guidedtour.app.data.WordLists
import com.guidedtour.app.data.PhotoRegion
import com.guidedtour.app.data.ViewArea
import com.guidedtour.app.data.photoRegion
import com.guidedtour.app.data.readPickedPhoto
import com.guidedtour.app.data.agreed
import com.guidedtour.app.data.Locator
import com.guidedtour.app.data.PersonalWords
import com.guidedtour.app.data.PhotoSpot
import com.guidedtour.app.data.PlacePhotos
import com.guidedtour.app.data.groupPlaces
import com.guidedtour.app.data.photoSpot
import com.guidedtour.app.data.PlayerState
import com.guidedtour.app.data.QueueItem
import com.guidedtour.app.data.Recognizer
import com.guidedtour.app.data.Scan
import com.guidedtour.app.data.Settings
import com.guidedtour.app.data.TextReader
import com.guidedtour.app.data.Translator
import com.guidedtour.app.ui.placeLabel
import com.guidedtour.app.data.mainLanguage
import com.guidedtour.app.data.encodeSpans
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

sealed interface ScanState {
    data object Idle : ScanState
    data object Processing : ScanState
    data object NoText : ScanState
    data class Done(val scan: Scan) : ScanState
}

/** Longest side of the photo saved with a scan. */
private const val SAVED_PHOTO_SIDE = 2400

/** The burst's other photos are kept this big for voting (see [vote]). */
private const val VOTE_SIDE = 2600

/** What a reading's vote counts: the sharpest photo's, another photo's, one of the live preview's. */
private const val SHARPEST_VOTE = 1.2f
private const val PHOTO_VOTE = 1f
private const val LIVE_VOTE = 0.4f

/** How long a scan waits for the place's words from Wikipedia (only the first scan there asks). */
private const val PLACE_WORDS_WAIT_MS = 3_000L

/** A picked photo taken this recently is placed where the phone is now. */
private const val RECENT_PHOTO_MS = 2 * 60 * 60 * 1000L

class TourViewModel(private val app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.build(app).scanDao()
    private val locator = Locator(app)
    private val reader = TextReader.get(app)
    val settings = Settings.get(app)
    val placePhotos = PlacePhotos.get(app)
    private val wordLists = WordLists.get(app)
    private val personalWords = PersonalWords.get(app)
    val aiProofreader = AiProofreader.get(app)

    // Loaded from the start, so Guides rarely opens before the database has been read; null until then.
    private val loadedScans: StateFlow<List<Scan>?> = dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val scans: StateFlow<List<Scan>> = loadedScans.map { it.orEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** False until the database has been read once (slow the first time after an app update). */
    val scansLoaded: StateFlow<Boolean> = loadedScans.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val player: StateFlow<PlayerState> = reader.state

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state

    /** One-off messages for a snackbar. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<String> = _messages

    /** Ids of scans being translated right now. */
    private val _translating = MutableStateFlow<Set<Long>>(emptySet())
    val translating: StateFlow<Set<Long>> = _translating

    /** Hardware-button shutter presses (see MainActivity). */
    private val _shutter = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val shutter: SharedFlow<Unit> = _shutter

    init {
        // A word list takes about a second to load: have the likely languages ready before the first scan.
        viewModelScope.launch(Dispatchers.Default) {
            delay(3_000)
            android.util.Log.i("GuidedTour", "ai proofreading: ${aiProofreader.status()}")
            if (!settings.correctWords.value) return@launch
            val recent = loadedScans.filterNotNull().first().take(20).mapNotNull { it.languageTag }
            (listOf(Locale.getDefault().language) + recent).distinct().take(2).forEach { wordLists.of(it) }
        }
        // Off the main thread: reaching AICore can block, above all on phones without it.
        viewModelScope.launch(Dispatchers.Default) { writeHeadlines() }
        viewModelScope.launch { keepBackedUp() }
        viewModelScope.launch(Dispatchers.Default) { translateHeadlines() }
    }

    /**
     * Gives each guide without one a headline for its card, one at a time, newest first: new scans,
     * edited texts, and ones saved before there were headlines. Each text is tried once a session.
     * Guides with neither an AI headline nor a printed title keep showing their first lines.
     */
    private suspend fun writeHeadlines() {
        delay(3_000)
        val tried = mutableSetOf<Pair<Long, String>>()
        var scans = loadedScans.filterNotNull().first()
        while (true) {
            val next = scans.firstOrNull { it.headline == null && it.text.isNotBlank() && (it.id to it.text) !in tried }
            if (next != null) {
                tried += next.id to next.text
                // Written by the phone's AI where it has one, or else the title printed on the plaque.
                val headline = Headliner.headline(next.text, next.languageTag) ?: printedTitle(next.text)
                if (headline != null) dao.setHeadline(next.id, headline)
                android.util.Log.i("GuidedTour", "headline: scan ${next.id} " + if (headline != null) "written" else "not written")
            }
            val seen = scans
            scans = if (next != null) loadedScans.value ?: seen else loadedScans.filterNotNull().first { it != seen }
        }
    }
    val scanTabVisible = MutableStateFlow(true)

    val volumeShutterActive: Boolean get() = settings.volumeShutter.value && scanTabVisible.value
    fun fireShutter() { _shutter.tryEmit(Unit) }

    // ---- Scanning ----

    // Careful capture takes several photos; we keep only the sharpest to save memory.
    private var bestFrame: Bitmap? = null
    private var bestSharpness = -1.0
    /** Where a scan is: position, place name and country, and the place's words (see [PlacePhotos.words]). */
    private class Whereabouts(val position: Pair<Double, Double>?, val spot: Locator.Spot?, val words: Set<String>)

    /** Where the phone is, looked up from the shutter press on, while the photo is read. */
    private var whereabouts: Deferred<Whereabouts>? = null

    /** Finds where the phone is now (a fix up to a minute old will do), its place's name and words. */
    private fun lookUpWhereabouts(): Deferred<Whereabouts> = viewModelScope.async {
        val position = locator.current()?.let { it.latitude to it.longitude }
        whereaboutsAt(position)
    }

    private suspend fun whereaboutsAt(position: Pair<Double, Double>?): Whereabouts {
        val spot = position?.let { locator.spot(it.first, it.second) }
        // The place's own words (from Wikipedia, if place photos are allowed to ask it), so names on its
        // plaques are recognised and mended.
        val words = position?.takeIf { settings.correctWords.value && settings.placePhotos.value }?.let { (lat, lng) ->
            withTimeoutOrNull(PLACE_WORDS_WAIT_MS) { placePhotos.words(PhotoSpot(lat, lng, spot?.name)) }
        }.orEmpty()
        return Whereabouts(position, spot, words)
    }

    /** The burst's other photos, smaller, to vote on the sharpest one's words. */
    private val otherFrames = mutableListOf<Bitmap>()

    /** Starts a capture. Returns false if one is already being processed. */
    fun beginCapture(): Boolean {
        if (_state.value == ScanState.Processing) return false
        _state.value = ScanState.Processing
        whereabouts?.cancel()
        whereabouts = lookUpWhereabouts()
        bestFrame?.recycle()
        bestFrame = null
        bestSharpness = -1.0
        otherFrames.forEach { it.recycle() }
        otherFrames.clear()
        return true
    }

    /** Offers one photo of the current capture; closes [image]. */
    suspend fun addFrame(image: ImageProxy) {
        val bitmap = runCatching { Recognizer.toUprightBitmap(image) }.getOrNull() ?: return
        val sharpness = withContext(Dispatchers.Default) { Recognizer.sharpness(bitmap) }
        val other = if (sharpness > bestSharpness) bestFrame.also { bestFrame = bitmap; bestSharpness = sharpness } else bitmap
        if (other != null) {
            otherFrames += withContext(Dispatchers.Default) { Recognizer.shrunk(other, VOTE_SIDE) }.also { if (it !== other) other.recycle() }
        }
    }

    /**
     * Reads the sharpest photo: only what the preview showed ([shown], the whole preview), and within
     * it only the [aimed] text frames if there are any, with the [skipped] ones blanked out; if that
     * finds nothing, falls back to [liveText] seen in the preview. Where it isn't sure of words, the
     * burst's other photos and the preview's [liveReadings] vote on them (see [vote]). The photo is
     * saved as the preview showed it: the camera takes in more at the sides (or top and bottom) than a
     * phone screen shows.
     */
    fun finishCapture(
        liveText: String,
        aimed: List<ViewArea> = emptyList(),
        skipped: List<ViewArea> = emptyList(),
        shown: ViewArea? = null,
        liveReadings: List<String> = emptyList(),
    ) {
        val taken = bestFrame
        bestFrame = null
        bestSharpness = -1.0
        val burst = otherFrames.toList()
        otherFrames.clear()
        viewModelScope.launch {
            if (taken == null) {
                persist(liveText, null, emptySet())
            } else {
                val visible = shown?.let { photoRegion(it, taken.width, taken.height, margin = 0f) }
                val frame = if (visible == null) taken else withContext(Dispatchers.Default) { Recognizer.cropped(taken, visible) }
                // Only the frames to read (cropped like the preview, the photo now matches it exactly);
                // skipped frames between them are blanked out, unless they overlap one to read.
                val read = aimed.mapNotNull { photoRegion(it, frame.width, frame.height) }
                val exact = aimed.mapNotNull { photoRegion(it, frame.width, frame.height, margin = 0f) }
                val skip = skipped.mapNotNull { photoRegion(it, frame.width, frame.height, margin = 0.05f) }
                    .filter { s -> exact.none { it.overlaps(s) } }
                val region = read.takeIf { it.isNotEmpty() }?.let { r ->
                    PhotoRegion(r.minOf { it.left }, r.minOf { it.top }, r.maxOf { it.right }, r.maxOf { it.bottom })
                }
                val chosen = aimed.isNotEmpty() || skipped.isNotEmpty()
                val reading = Recognizer.read(frame, settings.script.value, region = region, skipped = skip, chosen = chosen)
                // Words OCR wasn't sure of: the burst's other photos (a quick reading of the same text) and
                // the preview's last readings vote on them.
                val others = if (reading.unsure.isEmpty()) emptyList() else {
                    burst.map { other ->
                        val seen = visible?.let { Recognizer.cropped(other, it) } ?: other
                        Recognizer.read(seen, settings.script.value, enhance = false, region = region, skipped = skip, chosen = chosen).text
                            .also { if (seen !== other) seen.recycle() } to PHOTO_VOTE
                    } + liveReadings.map { it to LIVE_VOTE }
                }
                val text = agreed(reading, others, wordLists, SHARPEST_VOTE)
                burst.forEach { it.recycle() }
                // Save the photo the way the text reads, if it had to be turned.
                val photo = if (reading.turn == 0) frame else withContext(Dispatchers.Default) {
                    Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, android.graphics.Matrix().apply { postRotate(reading.turn.toFloat()) }, true)
                }
                if (text.isBlank()) persist(liveText, photo, emptySet()) else persist(text, photo, reading.unsure)
                if (frame !== taken) taken.recycle()
            }
        }
    }

    /**
     * Reads a photo picked from the phone's gallery and saves it like a scan, dated when it was taken.
     * It's placed where the photo says it was taken, or else here if it was taken in the last couple
     * of hours (an older photo was likely taken somewhere else).
     */
    fun importPhoto(uri: Uri) {
        if (!beginCapture()) return
        viewModelScope.launch {
            val picked = withContext(Dispatchers.IO) { readPickedPhoto(app, uri) }
            if (picked == null) {
                _state.value = ScanState.Idle
                _messages.tryEmit("Couldn't open that photo")
                return@launch
            }
            val reading = Recognizer.read(picked.bitmap, settings.script.value)
            val photo = if (reading.turn == 0) picked.bitmap else withContext(Dispatchers.Default) {
                Bitmap.createBitmap(picked.bitmap, 0, 0, picked.bitmap.width, picked.bitmap.height, android.graphics.Matrix().apply { postRotate(reading.turn.toFloat()) }, true)
            }
            val recent = picked.taken == null || System.currentTimeMillis() - picked.taken < RECENT_PHOTO_MS
            val position = if (picked.latitude != null && picked.longitude != null) picked.latitude to picked.longitude else null
            persist(agreed(reading, emptyList(), wordLists), photo, reading.unsure, taken = picked.taken, position = position, here = position == null && recent)
        }
    }

    /**
     * Saves and plays a scan of [read]; words in [unsure] that aren't real words in its language are
     * corrected first. It's dated [taken] (now if null) and placed at [position], or else where the
     * phone is now if [here].
     */
    private suspend fun persist(
        read: String,
        photo: Bitmap?,
        unsure: Set<String>,
        taken: Long? = null,
        position: Pair<Double, Double>? = null,
        here: Boolean = true,
    ) {
        android.util.Log.i("GuidedTour", "capture: read ${read.length} characters")
        if (read.isBlank()) {
            whereabouts?.cancel()
            whereabouts = null
            _state.value = ScanState.NoText
            return
        }
        // Looked up since the shutter press, if it's to be where the phone is; else where the photo says.
        val found = whereabouts.also { whereabouts = null }
        val where = when {
            position != null -> { found?.cancel(); whereaboutsAt(position) }
            here -> (found ?: lookUpWhereabouts()).await()
            else -> { found?.cancel(); Whereabouts(null, null, emptySet()) }
        }
        val location = where.position
        val spot = where.spot
        var spans = Recognizer.detectLanguages(read, spot?.countryCode)
        val text = if (settings.correctWords.value) {
            // The place's own words and the user's, so names on its plaques are recognised and mended.
            val placeWords = where.words
            // On-device AI proofreading first, where the phone has it, then the word lists.
            val list = mainLanguage(read, spans)?.let(wordLists::of)
            val proofread = if (!settings.aiProofreading.value) read else {
                aiProofreader.proofread(read, mainLanguage(read, spans), unsure) { word -> list != null && list.frequency(word) >= 0 }
            }
            withContext(Dispatchers.Default) { wordLists.correct(proofread, spans, unsure, personalWords.words + placeWords) }
        } else {
            read
        }
        // Corrected words change where each language starts, and may make it clearer.
        if (text != read) spans = Recognizer.detectLanguages(text, spot?.countryCode)
        val now = System.currentTimeMillis()
        val scan = Scan(
            text = text,
            timestamp = taken ?: now,
            latitude = location?.first,
            longitude = location?.second,
            placeName = spot?.name,
            languageTag = mainLanguage(text, spans),
            languages = encodeSpans(spans),
            thumbnail = photo?.let { saveThumbnail(it, now) },
        )
        val saved = scan.copy(id = dao.insert(scan))
        _state.value = ScanState.Done(saved)
        // While something is playing, new scans wait their turn (unless the user prefers interrupting).
        if (settings.queueNewScans.value && player.value.active) addToQueue(saved) else playNow(saved)
    }

    private suspend fun saveThumbnail(photo: Bitmap, timestamp: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            // Shrunk only now the text has been read: sharp enough to zoom into and read again in the cloud.
            val scale = SAVED_PHOTO_SIDE.toFloat() / maxOf(photo.width, photo.height)
            val small = if (scale < 1f) photo.scale((photo.width * scale).toInt(), (photo.height * scale).toInt()) else photo
            val dir = File(app.filesDir, "thumbs").apply { mkdirs() }
            File(dir, "$timestamp.jpg").also { file -> file.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 90, it) } }.absolutePath
        }.getOrNull()
    }

    fun dismissResult() {
        _state.value = ScanState.Idle
    }

    // ---- Reading ----

    /** Each located scan's photo spot, by scan id, from the same places as the map. */
    private fun spots(): Map<Long, PhotoSpot> =
        groupPlaces(scans.value).flatMap { place -> place.photoSpot().let { spot -> place.scans.map { it.id to spot } } }.toMap()

    private fun Scan.item(translated: Boolean, spots: Map<Long, PhotoSpot> = spots()) = QueueItem(
        id = id,
        translated = translated,
        title = placeLabel(),
        text = if (translated) translation.orEmpty() else text,
        languageTag = if (translated) translationTag else languageTag,
        // A translation is all in one language; the original may switch language between paragraphs.
        spans = if (translated) emptyList() else spans(),
        spot = spots[id],
    )

    private fun start() = ReaderService.start(app)

    fun playNow(scan: Scan, translated: Boolean = false) { reader.play(scan.item(translated)); start() }
    fun playNext(scan: Scan, translated: Boolean = false) { reader.enqueue(scan.item(translated), next = true); start() }
    fun addToQueue(scan: Scan, translated: Boolean = false) { reader.enqueue(scan.item(translated), next = false); start() }
    /** Reads a place's Wikipedia introduction ([about], of the place at [spot]) now. */
    fun playAbout(about: PlaceAbout, spot: PhotoSpot) {
        reader.play(QueueItem(aboutId(about), false, about.title, about.text, about.language, spot = spot))
        start()
    }

    fun playAll(scans: List<Scan>) { val spots = spots(); reader.playAll(scans.map { it.item(false, spots) }); start() }

    val speechReady: StateFlow<Boolean> = reader.speechReady
    fun voicesFor(language: String) = reader.voicesFor(language)
    fun previewVoice(language: String, voiceName: String?, sample: String) = reader.preview(language, voiceName, sample)
    fun stopPreview() = reader.stopPreview()

    fun togglePlayback() = reader.toggle()
    fun pause() = reader.pause()
    fun next() = reader.next()
    fun previous() = reader.previous()
    fun seekBack() = reader.seekBy(-SEEK_SECONDS)
    fun seekForward() = reader.seekBy(SEEK_SECONDS)
    fun stopSpeaking() = reader.stop()
    fun removeFromQueue(position: Int) = reader.removeAt(position)
    fun moveInQueue(position: Int, delta: Int) = reader.move(position, delta)

    // ---- Saved texts ----

    fun restore(scan: Scan) {
        viewModelScope.launch { dao.insert(scan) }
    }

    fun delete(scan: Scan) {
        if (player.value.queue.any { it.id == scan.id }) {
            val position = player.value.queue.indexOfFirst { it.id == scan.id }
            reader.removeAt(position)
        }
        viewModelScope.launch { dao.delete(scan.id) }
    }

    fun toggleFavorite(scan: Scan) {
        viewModelScope.launch { dao.setFavorite(scan.id, !scan.favorite) }
    }

    /**
     * Fixes OCR mistakes. Clears any stale translation and detects the languages again, unless the
     * user chose the language by hand.
     */
    /** Saves [newText] as [scan]'s text; if the user typed it ([byUser]), names in it are learned for later scans. */
    fun editText(scan: Scan, newText: String, byUser: Boolean = true) {
        val text = newText.trim()
        if (text.isEmpty() || text == scan.text) return
        viewModelScope.launch {
            if (byUser) withContext(Dispatchers.Default) {
                val list = scan.languageTag?.let(wordLists::of)
                personalWords.learn(scan.text, text) { word -> list != null && list.frequency(word) >= 0 }
            }
            val spans = if (scan.languageFixed) emptyList() else Recognizer.detectLanguages(text, scan.country())
            val language = mainLanguage(text, spans) ?: scan.languageTag
            val languages = encodeSpans(spans)
            dao.updateText(scan.id, text, language, languages)
            (_state.value as? ScanState.Done)?.takeIf { it.scan.id == scan.id }?.let {
                _state.value = ScanState.Done(it.scan.copy(text = text, languageTag = language, languages = languages, translation = null, translationTag = null))
            }
        }
    }

    /** The country the scan was taken in, if it has a location, to tell close languages apart. */
    private suspend fun Scan.country(): String? {
        val lat = latitude ?: return null
        val lng = longitude ?: return null
        return locator.spot(lat, lng).countryCode
    }

    /** Sets the whole text's language by hand, or with null detects it again paragraph by paragraph. */
    fun setLanguage(scan: Scan, language: String?) {
        viewModelScope.launch {
            if (language != null) {
                dao.setLanguage(scan.id, language, null, fixed = true)
            } else {
                val spans = Recognizer.detectLanguages(scan.text, scan.country())
                dao.setLanguage(scan.id, mainLanguage(scan.text, spans) ?: scan.languageTag, encodeSpans(spans), fixed = false)
            }
        }
    }

    // ---- Cloud reading and accuracy testing ----

    val cloudAvailable: Boolean get() = CloudOcr.available

    /** Ids of scans being re-read in the cloud. */
    private val _cloudReading = MutableStateFlow<Set<Long>>(emptySet())
    val cloudReading: StateFlow<Set<Long>> = _cloudReading

    /** Uploads the saved photo to Google Cloud Vision and replaces the text with its result. Ask the user first. */
    fun improveWithCloud(scan: Scan) {
        val photo = scan.thumbnail?.let(::File)?.takeIf { it.exists() }
        if (photo == null) { _messages.tryEmit("There's no saved photo for this text."); return }
        if (scan.id in _cloudReading.value) return
        _cloudReading.value += scan.id
        viewModelScope.launch {
            when (val result = CloudOcr.read(photo)) {
                is CloudOcr.Result.Text -> editText(scan, result.text, byUser = false)
                is CloudOcr.Result.Failure -> _messages.tryEmit(result.message)
            }
            _cloudReading.value -= scan.id
        }
    }

    private val _accuracyReport = MutableStateFlow<String?>(null)
    val accuracyReport: StateFlow<String?> = _accuracyReport
    val accuracyFolder: String get() = Accuracy.folder(app).path

    fun runAccuracyTest() {
        _accuracyReport.value = "Running…"
        viewModelScope.launch { _accuracyReport.value = Accuracy.run(app, settings.script.value) }
    }

    /** Translates into the chosen language (the phone's by default), stores the result and queues it for reading. */
    /** Translates [scan] into [into] (a language code), or the language chosen in Settings. */
    fun translate(scan: Scan, into: String? = null) {
        if (scan.id in _translating.value) return
        _translating.value += scan.id
        viewModelScope.launch {
            val target = into ?: Translator.target(settings.translateTo.value)
            when (val result = Translator.translate(scan.text, scan.spans(), target)) {
                is Translator.Result.Success -> {
                    val headline = scan.headline?.let { translatedHeadline(it, scan, target) }
                    dao.setTranslation(scan.id, result.text, target, headline)
                    val withTranslation = scan.copy(translation = result.text, translationTag = target, translatedHeadline = headline)
                    if (player.value.active) addToQueue(withTranslation, translated = true) else playNow(withTranslation, translated = true)
                }
                is Translator.Result.Failure -> _messages.tryEmit(result.message)
            }
            _translating.value -= scan.id
        }
    }

    /** [headline] of [scan] in [target], or null if it can't be translated now. */
    private suspend fun translatedHeadline(headline: String, scan: Scan, target: String): String? =
        (Translator.translate(headline, scan.languageTag, target) as? Translator.Result.Success)?.text?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Translates the headlines of translated guides that don't have theirs in the translation's
     * language yet: written after the translation, changed, or translated before headlines were.
     * Each is tried once a session.
     */
    private suspend fun translateHeadlines() {
        delay(5_000)
        val tried = mutableSetOf<Pair<Long, String>>()
        // Not collectLatest: a translation under way finishes before the next list is looked at.
        loadedScans.filterNotNull().collect { scans ->
            for (scan in scans) {
                val headline = scan.headline ?: continue
                val tag = scan.translationTag ?: continue
                if (scan.translation == null || scan.translatedHeadline != null || !tried.add(scan.id to headline)) continue
                val translated = translatedHeadline(headline, scan, tag)
                if (translated != null) dao.setTranslatedHeadline(scan.id, translated)
                android.util.Log.i("GuidedTour", "headline: scan ${scan.id} " + if (translated != null) "translated" else "not translated")
            }
        }
    }

    // ---- Backup ----

    /** True while a backup is being written or read. */
    private val _backingUp = MutableStateFlow(false)
    val backingUp: StateFlow<Boolean> = _backingUp
    private val backupLock = Mutex()

    /** Writes every guide, its photo and the personal words to [uri], a file the user chose. */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val ok = writeBackup(uri)
            _messages.tryEmit(if (ok) "Guides exported" else "Couldn't export the guides")
        }
    }

    /** Adds the guides of the backup at [uri] that aren't in the app yet; nothing is removed or replaced. */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val contents = backupLock.withLock {
                _backingUp.value = true
                try {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val have = dao.all().map { it.timestamp }.toSet()
                            val photos = File(app.filesDir, "thumbs").apply { mkdirs() }
                            val contents = app.contentResolver.openInputStream(uri)!!.use { Backup.read(it, photos) { time -> time in have } }
                            dao.insertAll(contents.fresh)
                            personalWords.addAll(contents.words)
                            contents
                        }.onFailure { android.util.Log.w("GuidedTour", "backup: can't import", it) }.getOrNull()
                    }
                } finally {
                    _backingUp.value = false
                }
            }
            _messages.tryEmit(
                when {
                    contents == null -> "That file isn't a Guided Tour backup"
                    contents.fresh.isEmpty() -> "Every guide in that backup is already here"
                    else -> "Added ${contents.fresh.size} " + (if (contents.fresh.size == 1) "guide" else "guides") +
                        (if (contents.skipped > 0) " (${contents.skipped} already here)" else "")
                },
            )
        }
    }

    /**
     * Keeps [uri] (a file the user chose, e.g. in Google Drive) up to date as a backup, written now and
     * after each change; null stops it. Restoring stays the user's choice (see [importFrom]).
     */
    fun setAutoBackup(uri: Uri?) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        settings.backupFile.value?.let { old -> runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(old), flags) } }
        if (uri != null && runCatching { app.contentResolver.takePersistableUriPermission(uri, flags) }.isFailure) {
            _messages.tryEmit("Can't keep a backup in that place: choose another")
            settings.setBackupFile(null)
            return
        }
        settings.setBackupFile(uri?.toString())
    }

    /** Writes the automatic backup a little after the guides change, when they differ from the last one. */
    private suspend fun keepBackedUp() {
        combine(loadedScans.filterNotNull(), settings.backupFile) { scans, file -> scans to file }.collectLatest { (scans, file) ->
            if (file == null) return@collectLatest
            val signature = scans.hashCode()
            if (signature == settings.backedUp) return@collectLatest
            // A burst of changes (a scan, its headline, its translation) is written once.
            if (settings.lastBackup.value != 0L) delay(BACKUP_DELAY_MS)
            // Once begun, a write finishes, so a change meanwhile can't leave half a file.
            withContext(NonCancellable) {
                if (writeBackup(Uri.parse(file))) settings.setBackedUp(signature, System.currentTimeMillis())
                else _messages.tryEmit("Automatic backup failed: choose the backup file again in Settings")
            }
        }
    }

    private suspend fun writeBackup(uri: Uri): Boolean = backupLock.withLock {
        _backingUp.value = true
        try {
            withContext(Dispatchers.IO) {
                runCatching {
                    val scans = dao.all()
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { Backup.write(scans, personalWords.words, it) }
                }.onFailure { android.util.Log.w("GuidedTour", "backup: can't write", it) }.isSuccess
            }
        } finally {
            _backingUp.value = false
        }
    }
}

/** How long after a change the automatic backup waits, so a burst of changes is written once. */
private const val BACKUP_DELAY_MS = 30_000L

/** How far the player's back and forward buttons jump. */
const val SEEK_SECONDS = 10

/** The player's id for a place's Wikipedia introduction: negative, so it's never a guide's. */
fun aboutId(about: PlaceAbout): Long = -1L - (about.page.hashCode().toLong() and 0xffff_ffffL)
