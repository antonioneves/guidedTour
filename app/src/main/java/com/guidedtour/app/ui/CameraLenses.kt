package com.guidedtour.app.ui

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One way to frame the shot, as a chip under the preview: a camera ([selector]) and a zoom on it.
 * [label] is the field of view next to the main camera's, as camera apps show it ("0.5×", "2×").
 * [night] says whether the phone's night mode works with it, and [nightLive] whether live text
 * detection can run in night mode too.
 */
data class Lens(
    val selector: CameraSelector,
    val zoom: Float,
    val label: String,
    val front: Boolean,
    val night: Boolean,
    val nightLive: Boolean,
)

/** The lenses to offer, and the phone's camera extensions to turn night mode on with. */
class CameraChoices(val lenses: List<Lens>, val extensions: ExtensionsManager?) {
    /** The main camera unzoomed, where the camera starts. */
    val main: Lens get() = lenses.firstOrNull { !it.front && it.zoom == 1f && it.label == "1×" } ?: lenses.first()

    /** [lens]'s camera with night mode on, if it has it. */
    fun nightSelector(lens: Lens): CameraSelector? =
        extensions?.takeIf { lens.night }?.getExtensionEnabledCameraSelector(lens.selector, ExtensionMode.NIGHT)
}

/**
 * Every lens the phone offers: on the main back camera, its own wide end (on phones like Pixels the
 * ultra-wide sits behind the main camera as a zoom below 1×), 1× and 2×; any other back camera at
 * its own field of view; and the front camera.
 */
suspend fun cameraChoices(context: Context): CameraChoices? = runCatching {
    val provider = ProcessCameraProvider.getInstance(context).await(context)
    val extensions = runCatching { ExtensionsManager.getInstanceAsync(context, provider).await(context) }.getOrNull()
    val infos = provider.availableCameraInfos
    val backs = infos.filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }.sortedBy { abs(it.intrinsicZoomRatio - 1f) }
    val main = backs.firstOrNull()
    val lenses = mutableListOf<Lens>()
    fun add(info: CameraInfo, zoom: Float, ratio: Float) {
        val label = label(ratio)
        val front = info.lensFacing == CameraSelector.LENS_FACING_FRONT
        if (lenses.any { it.front == front && it.label == label }) return
        // Not below 1×: on the Pixel the wide end comes out dark in night mode.
        val night = zoom >= 1f && extensions?.isExtensionAvailable(info.cameraSelector, ExtensionMode.NIGHT) == true
        val nightLive = night && extensions?.let {
            runCatching { it.isImageAnalysisSupported(info.cameraSelector, ExtensionMode.NIGHT) }.getOrDefault(false)
        } == true
        lenses += Lens(info.cameraSelector, zoom, label, front, night, nightLive)
    }
    if (main != null) {
        val (min, max) = zoomRange(main)
        if (min < 0.95f) add(main, min, min)
        add(main, 1f, 1f)
        // Other back cameras, at their own field of view (an ultra-wide or a telephoto the main one doesn't cover).
        backs.drop(1).forEach { add(it, 1f, it.intrinsicZoomRatio) }
        if (max >= 2f) add(main, 2f, 2f)
    }
    infos.firstOrNull { it.lensFacing == CameraSelector.LENS_FACING_FRONT }?.let { add(it, 1f, 1f) }
    val sorted = lenses.sortedWith(compareBy<Lens> { it.front }.thenBy { ratioOf(it.label) })
    android.util.Log.i("GuidedTour", "camera: lenses ${sorted.map { (if (it.front) "front " else "") + it.label + (if (it.night) " night" + (if (it.nightLive) "+live" else "") else "") }}")
    if (sorted.isEmpty()) null else CameraChoices(sorted, extensions)
}.onFailure { android.util.Log.w("GuidedTour", "camera: can't list the lenses", it) }.getOrNull()

/** The zoom [info]'s camera allows, from its characteristics: its zoom state is only known once it's open. */
@androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
private fun zoomRange(info: CameraInfo): Pair<Float, Float> {
    info.zoomState.value?.let { return it.minZoomRatio to it.maxZoomRatio }
    val camera = Camera2CameraInfo.from(info)
    val range = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) camera.getCameraCharacteristic(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
    val digital = camera.getCameraCharacteristic(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
    return (range?.lower ?: 1f) to (range?.upper ?: digital ?: 1f)
}

/** "0.5×", "1×", "2×", "2.5×": like camera apps write zoom. */
private fun label(ratio: Float): String {
    val rounded = (ratio * 10).roundToInt() / 10f
    return if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()}×" else String.format(Locale.US, "%.1f×", rounded)
}

private fun ratioOf(label: String) = label.removeSuffix("×").toFloatOrNull() ?: 1f

private suspend fun <T> ListenableFuture<T>.await(context: Context): T = suspendCancellableCoroutine { cont ->
    addListener({
        runCatching { get() }.onSuccess { cont.resume(it) }.onFailure { cont.resumeWithException(it) }
    }, ContextCompat.getMainExecutor(context))
    cont.invokeOnCancellation { cancel(false) }
}
