package com.guidedtour.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guidedtour.app.data.AppDatabase
import com.guidedtour.app.data.Accuracy
import com.guidedtour.app.data.CloudOcr
import com.guidedtour.app.data.WordLists
import com.guidedtour.app.data.PhotoRegion
import com.guidedtour.app.data.ViewArea
import com.guidedtour.app.data.photoRegion
import com.guidedtour.app.data.readPickedPhoto
import com.guidedtour.app.data.Locator
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
import kotlinx.coroutines.Dispatchers
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

/** A picked photo taken this recently is placed where the phone is now. */
private const val RECENT_PHOTO_MS = 2 * 60 * 60 * 1000L

class TourViewModel(private val app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.build(app).scanDao()
    private val locator = Locator(app)
    private val reader = TextReader.get(app)
    val settings = Settings.get(app)
    val placePhotos = PlacePhotos.get(app)
    private val wordLists = WordLists.get(app)

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
            if (!settings.correctWords.value) return@launch
            val recent = loadedScans.filterNotNull().first().take(20).mapNotNull { it.languageTag }
            (listOf(Locale.getDefault().language) + recent).distinct().take(2).forEach { wordLists.of(it) }
        }
    }
    val scanTabVisible = MutableStateFlow(true)

    val volumeShutterActive: Boolean get() = settings.volumeShutter.value && scanTabVisible.value
    fun fireShutter() { _shutter.tryEmit(Unit) }

    // ---- Scanning ----

    // Careful capture takes several photos; we keep only the sharpest to save memory.
    private var bestFrame: Bitmap? = null
    private var bestSharpness = -1.0

    /** Starts a capture. Returns false if one is already being processed. */
    fun beginCapture(): Boolean {
        if (_state.value == ScanState.Processing) return false
        _state.value = ScanState.Processing
        bestFrame?.recycle()
        bestFrame = null
        bestSharpness = -1.0
        return true
    }

    /** Offers one photo of the current capture; closes [image]. */
    suspend fun addFrame(image: ImageProxy) {
        val bitmap = runCatching { Recognizer.toUprightBitmap(image) }.getOrNull() ?: return
        val sharpness = withContext(Dispatchers.Default) { Recognizer.sharpness(bitmap) }
        if (sharpness > bestSharpness) {
            bestFrame?.recycle()
            bestFrame = bitmap
            bestSharpness = sharpness
        } else {
            bitmap.recycle()
        }
    }

    /**
     * Reads the sharpest photo: only what the preview showed ([shown], the whole preview), and within
     * it only the [aimed] text frames if there are any, with the [skipped] ones blanked out; if that
     * finds nothing, falls back to [liveText] seen in the preview. The photo is saved as the preview
     * showed it: the camera takes in more at the sides (or top and bottom) than a phone screen shows.
     */
    fun finishCapture(liveText: String, aimed: List<ViewArea> = emptyList(), skipped: List<ViewArea> = emptyList(), shown: ViewArea? = null) {
        val taken = bestFrame
        bestFrame = null
        bestSharpness = -1.0
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
                // Save the photo the way the text reads, if it had to be turned.
                val photo = if (reading.turn == 0) frame else withContext(Dispatchers.Default) {
                    Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, android.graphics.Matrix().apply { postRotate(reading.turn.toFloat()) }, true)
                }
                if (reading.text.isBlank()) persist(liveText, photo, emptySet()) else persist(reading.text, photo, reading.unsure)
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
            persist(reading.text, photo, reading.unsure, taken = picked.taken, position = position, here = position == null && recent)
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
            _state.value = ScanState.NoText
            return
        }
        val location = position ?: if (here) locator.current()?.let { it.latitude to it.longitude } else null
        val spot = location?.let { locator.spot(it.first, it.second) }
        var spans = Recognizer.detectLanguages(read, spot?.countryCode)
        val text = if (settings.correctWords.value) withContext(Dispatchers.Default) { wordLists.correct(read, spans, unsure) } else read
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
    fun editText(scan: Scan, newText: String) {
        val text = newText.trim()
        if (text.isEmpty() || text == scan.text) return
        viewModelScope.launch {
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
                is CloudOcr.Result.Text -> editText(scan, result.text)
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
    fun translate(scan: Scan) {
        if (scan.id in _translating.value) return
        _translating.value += scan.id
        viewModelScope.launch {
            val target = Translator.target(settings.translateTo.value)
            when (val result = Translator.translate(scan.text, scan.spans(), target)) {
                is Translator.Result.Success -> {
                    dao.setTranslation(scan.id, result.text, target)
                    val withTranslation = scan.copy(translation = result.text, translationTag = target)
                    if (player.value.active) addToQueue(withTranslation, translated = true) else playNow(withTranslation, translated = true)
                }
                is Translator.Result.Failure -> _messages.tryEmit(result.message)
            }
            _translating.value -= scan.id
        }
    }
}

/** How far the player's back and forward buttons jump. */
const val SEEK_SECONDS = 10
