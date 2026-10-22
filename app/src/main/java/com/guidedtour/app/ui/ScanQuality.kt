package com.guidedtour.app.ui

/** One recognized line of text, in camera-view pixel coordinates. */
class LineBox(val left: Int, val top: Int, val right: Int, val bottom: Int, val confidence: Float) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** What the live camera analysis saw in the latest frame. */
class LiveFrame(val text: String, val lines: List<LineBox>, val brightness: Int, val glare: Float = 0f)

enum class Tip(val message: String, val ok: Boolean = false) {
    NoText("Point the camera at the text"),
    Dark("Too dark – find more light (flash is usually not allowed)"),
    TooSmall("Text is too small – move closer"),
    CutOff("Text is cut off – center it in the frame"),
    Unclear("Hard to read – hold steady and avoid glare"),
    Shaky("Hold the phone steady"),
    Glare("Readable, but glare may hurt – tilt the phone", ok = true),
    Good("Looks readable – tap to read aloud", ok = true),
}

/** Share of near-white pixels in the frame above which we warn about glare. */
const val GLARE_SHARE = 0.06f

/**
 * Thresholds are tunable heuristics: line height is relative to the view height. [steady] is false
 * while the phone is being moved (from the gyroscope).
 */
fun evaluate(frame: LiveFrame, viewWidth: Int, viewHeight: Int, steady: Boolean = true): Tip {
    if (frame.brightness < 40) return Tip.Dark
    if (frame.lines.isEmpty() || viewWidth == 0 || viewHeight == 0) return Tip.NoText

    val medianHeight = frame.lines.map { it.height }.sorted()[frame.lines.size / 2]
    if (medianHeight < viewHeight * 0.015f) return Tip.TooSmall

    val margin = viewWidth * 0.01f
    val touchesEdge = frame.lines.any {
        it.left < margin || it.right > viewWidth - margin ||
            it.top < margin || it.bottom > viewHeight - margin
    }
    if (touchesEdge) return Tip.CutOff

    if (frame.lines.map { it.confidence }.average() < 0.7) return Tip.Unclear
    if (!steady) return Tip.Shaky
    if (frame.glare > GLARE_SHARE) return Tip.Glare
    return Tip.Good
}

/** Live analysis is paused while a photo is being read. */
const val ANALYSIS_PAUSED = -1L

/**
 * How often the live preview should run text recognition, to save battery: often while text is in
 * view, a few times a second while searching for text, and slowly while a result is on screen.
 */
fun analysisIntervalMs(processing: Boolean, showingResult: Boolean, textInView: Boolean): Long = when {
    processing -> ANALYSIS_PAUSED
    showingResult -> 1000L
    textInView -> 150L
    else -> 300L
}
