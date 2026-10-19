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
    private val _volumeShutter = MutableStateFlow(prefs.getBoolean("volumeShutter", false))
    val volumeShutter: StateFlow<Boolean> = _volumeShutter
    private val _script = MutableStateFlow(
        OcrScript.entries.firstOrNull { it.name == prefs.getString("script", null) } ?: OcrScript.Auto
    )
    val script: StateFlow<OcrScript> = _script

    fun setRate(value: Float) { _rate.value = value; prefs.edit().putFloat("rate", value).apply() }
    fun setPitch(value: Float) { _pitch.value = value; prefs.edit().putFloat("pitch", value).apply() }
    fun setQueueNewScans(value: Boolean) { _queueNewScans.value = value; prefs.edit().putBoolean("queueNewScans", value).apply() }
    fun setVolumeShutter(value: Boolean) { _volumeShutter.value = value; prefs.edit().putBoolean("volumeShutter", value).apply() }
    fun setScript(value: OcrScript) { _script.value = value; prefs.edit().putString("script", value.name).apply() }
}
