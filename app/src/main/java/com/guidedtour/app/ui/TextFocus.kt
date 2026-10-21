package com.guidedtour.app.ui

import android.os.SystemClock
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Keeps the camera focused, and its exposure set, on the text in view rather than wherever its
 * autofocus settles (a bright wall, the frame of a display case). Focuses again when the text moves
 * or changes size, and stays out of the way for a while after the user taps to focus.
 */
class TextFocus(private val controller: LifecycleCameraController) {
    private class Area(val x: Float, val y: Float, val width: Float, val height: Float)

    private var focused: Area? = null
    private var focusedAt = 0L
    private var tappedAt = 0L

    /** The user tapped to focus: theirs wins for [USER_FOCUS_MS]. */
    fun userTapped() {
        tappedAt = SystemClock.elapsedRealtime()
    }

    /** Focuses on [lines] (in [view]'s coordinates) if they're new, have moved or changed size. */
    fun follow(view: PreviewView?, lines: List<LineBox>) {
        if (view == null || lines.isEmpty()) {
            if (lines.isEmpty()) focused = null
            return
        }
        if (SystemClock.elapsedRealtime() - tappedAt < USER_FOCUS_MS) return
        val area = areaOf(lines)
        if (!changed(area, view)) return
        start(view, area)
    }

    /** Focuses on [lines] unless just done, and waits until focus has settled (at most [WAIT_MS]). */
    suspend fun focusNow(view: PreviewView?, lines: List<LineBox>) {
        if (view == null || lines.isEmpty() || SystemClock.elapsedRealtime() - tappedAt < USER_FOCUS_MS) return
        val area = areaOf(lines)
        if (!changed(area, view)) {
            // Already on this text: just let a focus that's still moving finish.
            delay((SETTLE_MS - (SystemClock.elapsedRealtime() - focusedAt)).coerceAtLeast(0))
            return
        }
        val future = start(view, area) ?: return
        withTimeoutOrNull(WAIT_MS) {
            suspendCancellableCoroutine { cont ->
                future.addListener({ if (cont.isActive) cont.resume(Unit) }, ContextCompat.getMainExecutor(view.context))
            }
        }
    }

    private fun start(view: PreviewView, area: Area) = runCatching {
        val size = (max(area.width / view.width, area.height / view.height)).coerceIn(0.1f, 0.5f)
        val point = view.meteringPointFactory.createPoint(area.x, area.y, size)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .disableAutoCancel()
            .build()
        focused = area
        focusedAt = SystemClock.elapsedRealtime()
        controller.cameraControl?.startFocusAndMetering(action)
    }.getOrNull()

    private fun areaOf(lines: List<LineBox>): Area {
        val left = lines.minOf { it.left }
        val top = lines.minOf { it.top }
        val right = lines.maxOf { it.right }
        val bottom = lines.maxOf { it.bottom }
        return Area((left + right) / 2f, (top + bottom) / 2f, (right - left).toFloat(), (bottom - top).toFloat())
    }

    /** No text focused yet, or this text has moved a tenth of the screen or grown or shrunk by a third. */
    private fun changed(area: Area, view: PreviewView): Boolean {
        val last = focused ?: return true
        val moved = hypot(area.x - last.x, area.y - last.y) > 0.1f * hypot(view.width.toFloat(), view.height.toFloat())
        val resized = abs(area.width - last.width) > last.width / 3 || abs(area.height - last.height) > last.height / 3
        return moved || resized
    }

    private companion object {
        const val USER_FOCUS_MS = 5_000L
        const val SETTLE_MS = 1_500L
        const val WAIT_MS = 1_000L
    }
}
