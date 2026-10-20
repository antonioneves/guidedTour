package com.guidedtour.app

import android.app.Application
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guidedtour.app.data.AppDatabase
import com.guidedtour.app.data.Accuracy
import com.guidedtour.app.data.CloudOcr
import com.guidedtour.app.data.Locator
import com.guidedtour.app.data.PlayerState
import com.guidedtour.app.data.QueueItem
import com.guidedtour.app.data.Recognizer
import com.guidedtour.app.data.Scan
import com.guidedtour.app.data.Settings
import com.guidedtour.app.data.TextReader
import com.guidedtour.app.data.Translator
import com.guidedtour.app.ui.placeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

class TourViewModel(private val app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.build(app).scanDao()
    private val locator = Locator(app)
    private val reader = TextReader.get(app)
    val settings = Settings.get(app)

    val scans: StateFlow<List<Scan>> =
        // Loaded from the start, so Guides never opens on an empty list while the database is read.
        dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
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

    /** Reads the sharpest photo; if that finds nothing, falls back to [liveText] seen in the preview. */
    fun finishCapture(liveText: String) {
        val frame = bestFrame
        bestFrame = null
        bestSharpness = -1.0
        viewModelScope.launch {
            if (frame == null) {
                persist(liveText, null)
            } else {
                val reading = Recognizer.read(frame, settings.script.value)
                // Save the photo the way the text reads, if it had to be turned.
                val photo = if (reading.turn == 0) frame else withContext(Dispatchers.Default) {
                    Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, android.graphics.Matrix().apply { postRotate(reading.turn.toFloat()) }, true)
                }
                persist(reading.text.ifBlank { liveText }, photo)
            }
        }
    }

    private suspend fun persist(text: String, photo: Bitmap?) {
        android.util.Log.i("GuidedTour", "capture: read ${text.length} characters")
        if (text.isBlank()) {
            _state.value = ScanState.NoText
            return
        }
        val language = Recognizer.detectLanguage(text)
        val location = locator.current()
        val place = location?.let { locator.placeName(it.latitude, it.longitude) }
        val now = System.currentTimeMillis()
        val scan = Scan(
            text = text,
            timestamp = now,
            latitude = location?.latitude,
            longitude = location?.longitude,
            placeName = place,
            languageTag = language,
            thumbnail = photo?.let { saveThumbnail(it, now) },
        )
        val saved = scan.copy(id = dao.insert(scan))
        _state.value = ScanState.Done(saved)
        // While something is playing, new scans wait their turn (unless the user prefers interrupting).
        if (settings.queueNewScans.value && player.value.active) addToQueue(saved) else playNow(saved)
    }

    private suspend fun saveThumbnail(photo: Bitmap, timestamp: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            val scale = 1280f / maxOf(photo.width, photo.height)
            val small = if (scale < 1f) Bitmap.createScaledBitmap(photo, (photo.width * scale).toInt(), (photo.height * scale).toInt(), true) else photo
            val dir = File(app.filesDir, "thumbs").apply { mkdirs() }
            File(dir, "$timestamp.jpg").also { file -> file.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 85, it) } }.absolutePath
        }.getOrNull()
    }

    fun dismissResult() {
        _state.value = ScanState.Idle
    }

    // ---- Reading ----

    private fun Scan.item(translated: Boolean) = QueueItem(
        id = id,
        translated = translated,
        title = placeLabel(),
        text = if (translated) translation.orEmpty() else text,
        languageTag = if (translated) translationTag else languageTag,
    )

    private fun start() = ReaderService.start(app)

    fun playNow(scan: Scan, translated: Boolean = false) { reader.play(scan.item(translated)); start() }
    fun playNext(scan: Scan, translated: Boolean = false) { reader.enqueue(scan.item(translated), next = true); start() }
    fun addToQueue(scan: Scan, translated: Boolean = false) { reader.enqueue(scan.item(translated), next = false); start() }
    fun playAll(scans: List<Scan>) { reader.playAll(scans.map { it.item(false) }); start() }

    val speechReady: StateFlow<Boolean> = reader.speechReady
    fun voicesFor(language: String) = reader.voicesFor(language)
    fun previewVoice(language: String, voiceName: String?, sample: String) = reader.preview(language, voiceName, sample)
    fun stopPreview() = reader.stopPreview()

    fun togglePlayback() = reader.toggle()
    fun pause() = reader.pause()
    fun next() = reader.next()
    fun previous() = reader.previous()
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

    /** Fixes OCR mistakes. Clears any stale translation and re-detects the language. */
    fun editText(scan: Scan, newText: String) {
        val text = newText.trim()
        if (text.isEmpty() || text == scan.text) return
        viewModelScope.launch {
            val language = Recognizer.detectLanguage(text) ?: scan.languageTag
            dao.updateText(scan.id, text, language)
            (_state.value as? ScanState.Done)?.takeIf { it.scan.id == scan.id }?.let {
                _state.value = ScanState.Done(it.scan.copy(text = text, languageTag = language, translation = null, translationTag = null))
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

    /** Translates into the device language, stores the result and queues it for reading. */
    fun translate(scan: Scan) {
        if (scan.id in _translating.value) return
        _translating.value += scan.id
        viewModelScope.launch {
            val target = Locale.getDefault().language
            when (val result = Translator.translate(scan.text, scan.languageTag, target)) {
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
