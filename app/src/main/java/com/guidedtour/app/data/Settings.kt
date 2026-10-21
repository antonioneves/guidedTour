package com.guidedtour.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Which writing system the OCR looks for. Auto tries Latin first, then the others. */
enum class OcrScript(val label: String) {
    Auto("Automatic"), Latin("Latin"), Chinese("Chinese"), Japanese("Japanese"),
    Korean("Korean"), Devanagari("Devanagari"),
}

/** User preferences, persisted in SharedPreferences and observable as flows. */
class Settings private constructor(context: Context) {
    companion object {
        @Volatile private var instance: Settings? = null
        fun get(context: Context): Settings =
            instance ?: synchronized(this) { instance ?: Settings(context.applicationContext).also { instance = it } }
    }

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _rate = MutableStateFlow(prefs.getFloat("rate", 1f))
    val rate: StateFlow<Float> = _rate
    private val _pitch = MutableStateFlow(prefs.getFloat("pitch", 1f))
    val pitch: StateFlow<Float> = _pitch
    private val _queueNewScans = MutableStateFlow(prefs.getBoolean("queueNewScans", true))
    val queueNewScans: StateFlow<Boolean> = _queueNewScans
    private val _duckMusic = MutableStateFlow(prefs.getBoolean("duckMusic", false))
    /** False (default): other apps' audio is paused while reading. True: it is only turned down. */
    val duckMusic: StateFlow<Boolean> = _duckMusic
    private val _carefulCapture = MutableStateFlow(prefs.getBoolean("carefulCapture", true))
    /** Take three photos and keep the sharpest. */
    val carefulCapture: StateFlow<Boolean> = _carefulCapture
    private val _volumeShutter = MutableStateFlow(prefs.getBoolean("volumeShutter", false))
    val volumeShutter: StateFlow<Boolean> = _volumeShutter
    private val _placePhotos = MutableStateFlow(prefs.getBoolean("placePhotos", true))
    /** Show Wikipedia photos of the places guides were scanned at (asks Wikipedia about each place). */
    val placePhotos: StateFlow<Boolean> = _placePhotos
    private val _script = MutableStateFlow(
        OcrScript.entries.firstOrNull { it.name == prefs.getString("script", null) } ?: OcrScript.Auto
    )
    val script: StateFlow<OcrScript> = _script

    private val _translateTo = MutableStateFlow(prefs.getString("translateTo", "").orEmpty())
    /** Language code to translate into; empty (the default) means the phone's language. */
    val translateTo: StateFlow<String> = _translateTo

    /** Chosen TextToSpeech voice name per language code (e.g. "es"); no entry means the default voice. */
    private val _voices = MutableStateFlow(
        prefs.all.filterKeys { it.startsWith("voice.") }.entries
            .associate { it.key.removePrefix("voice.") to it.value.toString() }
    )
    val voices: StateFlow<Map<String, String>> = _voices

    fun setVoice(language: String, voiceName: String?) {
        _voices.value = if (voiceName == null) _voices.value - language else _voices.value + (language to voiceName)
        prefs.edit().apply { if (voiceName == null) remove("voice.$language") else putString("voice.$language", voiceName) }.apply()
    }

    fun setRate(value: Float) { _rate.value = value; prefs.edit().putFloat("rate", value).apply() }
    fun setPitch(value: Float) { _pitch.value = value; prefs.edit().putFloat("pitch", value).apply() }
    fun setQueueNewScans(value: Boolean) { _queueNewScans.value = value; prefs.edit().putBoolean("queueNewScans", value).apply() }
    fun setDuckMusic(value: Boolean) { _duckMusic.value = value; prefs.edit().putBoolean("duckMusic", value).apply() }
    fun setCarefulCapture(value: Boolean) { _carefulCapture.value = value; prefs.edit().putBoolean("carefulCapture", value).apply() }
    fun setVolumeShutter(value: Boolean) { _volumeShutter.value = value; prefs.edit().putBoolean("volumeShutter", value).apply() }
    fun setPlacePhotos(value: Boolean) { _placePhotos.value = value; prefs.edit().putBoolean("placePhotos", value).apply() }
    fun setTranslateTo(code: String) { _translateTo.value = code; prefs.edit().putString("translateTo", code).apply() }
    fun setScript(value: OcrScript) { _script.value = value; prefs.edit().putString("script", value.name).apply() }
}
