package com.guidedtour.app.data

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sqrt

/**
 * Text photographed at an angle (a plaque seen from the side, or high on a wall) looks like a
 * trapezoid: its lines converge, or its margins do. This finds that trapezoid from the lines
 * themselves, so the photo can be stretched back to a rectangle, as a document scanner does with a
 * page's corners, without needing the plaque's edges to show.
 */

/** Converging by less than this many degrees is just a tilt, which straightening handles. */
const val MIN_CONVERGENCE_DEGREES = 2.5

/**
 * The four corners of the text area (x, y of top-left, top-right, bottom-right, bottom-left) as seen
 * at an angle, or null if the text isn't seen at an angle or the lines don't tell.
 *
 * The top and bottom edges follow the first and last lines, with the slant of every line fitted
 * against its height in the photo (lines seen at an angle all point to one vanishing point, so their
 * slant changes steadily down the text). The left and right edges follow the line ends where they
 * line up (a left-aligned margin), mirror the other side when only one does, and otherwise stand
 * square to the lines.
 */
fun textQuad(lines: List<OcrLine>): FloatArray? {
    val usable = lines.filter { it.corners.size == 8 && it.length > 3 * it.size && it.corners[2] > it.corners[0] }
    if (usable.size < 2) return null
    val sizes = usable.map { it.size.toDouble() }.sorted()
    val size = sizes[sizes.size / 2]

    // Each line's slant (along its top) against the height of its top, weighted by length.
    val ys = usable.map { l -> (l.corners[1] + l.corners[3]) / 2.0 }
    val slopes = usable.map { l -> (l.corners[3] - l.corners[1]).toDouble() / (l.corners[2] - l.corners[0]) }
    if (slopes.any { abs(it) > 1 }) return null
    val slant = fit(ys, slopes, usable.map { it.length.toDouble() }) ?: return null

    val topLine = usable[ys.indexOf(ys.min())]
    val bottomLine = usable[ys.indexOf(ys.max())]
    // Through the middle of the first line's top and of the last line's bottom.
    val topX = (topLine.corners[0] + topLine.corners[2]) / 2.0
    val topY = (topLine.corners[1] + topLine.corners[3]) / 2.0
    val bottomX = (bottomLine.corners[4] + bottomLine.corners[6]) / 2.0
    val bottomY = (bottomLine.corners[5] + bottomLine.corners[7]) / 2.0
    val topSlope = slant.at(topY)
    val bottomSlope = slant.at(bottomY)

    // Line ends: the middle of each line's left side and right side.
    val lefts = usable.map { l -> (l.corners[0] + l.corners[6]) / 2.0 to (l.corners[1] + l.corners[7]) / 2.0 }
    val rights = usable.map { l -> (l.corners[2] + l.corners[4]) / 2.0 to (l.corners[3] + l.corners[5]) / 2.0 }
    // A margin as x = c + d·y, if the ends line up well enough to trust.
    fun margin(ends: List<Pair<Double, Double>>): Line? {
        if (ends.size < 3) return null
        val line = fit(ends.map { it.second }, ends.map { it.first }, List(ends.size) { 1.0 }) ?: return null
        val spread = sqrt(ends.sumOf { (x, y) -> (x - line.at(y)).let { it * it } } / ends.size)
        return line.takeIf { spread < 0.35 * size }
    }
    // Square to the lines: a margin's slope when the text is only tilted.
    val square = -(topSlope + bottomSlope) / 2
    val alignedLeft = margin(lefts)
    val alignedRight = margin(rights)
    val leftSlope = alignedLeft?.slope ?: alignedRight?.let { 2 * square - it.slope } ?: square
    val rightSlope = alignedRight?.slope ?: alignedLeft?.let { 2 * square - it.slope } ?: square
    // An edge that isn't fitted goes through the outermost line end.
    val left = alignedLeft ?: Line(lefts.minOf { (x, y) -> x - leftSlope * y }, leftSlope)
    val right = alignedRight ?: Line(rights.maxOf { (x, y) -> x - rightSlope * y }, rightSlope)

    val converging = degrees(atan(topSlope) - atan(bottomSlope))
    val spreading = degrees(atan(left.slope) - atan(right.slope))
    if (abs(converging) < MIN_CONVERGENCE_DEGREES && abs(spreading) < MIN_CONVERGENCE_DEGREES) return null

    val corners = listOf(
        meet(topX, topY, topSlope, left), meet(topX, topY, topSlope, right),
        meet(bottomX, bottomY, bottomSlope, right), meet(bottomX, bottomY, bottomSlope, left),
    )
    if (corners.any { it == null }) return null
    val quad = FloatArray(8) { i -> corners[i / 2]!!.let { if (i % 2 == 0) it.first else it.second }.toFloat() }
    return quad.takeIf { isConvex(it) }
}

/** y = intercept + slope·x (or x = … · y, for margins). */
private class Line(val intercept: Double, val slope: Double) {
    fun at(v: Double) = intercept + slope * v
}

/** Weighted least-squares line through ([xs], [ys]), or null if the xs don't vary. */
private fun fit(xs: List<Double>, ys: List<Double>, weights: List<Double>): Line? {
    val total = weights.sum()
    val mx = xs.indices.sumOf { weights[it] * xs[it] } / total
    val my = xs.indices.sumOf { weights[it] * ys[it] } / total
    val sxx = xs.indices.sumOf { weights[it] * (xs[it] - mx) * (xs[it] - mx) }
    val sxy = xs.indices.sumOf { weights[it] * (xs[it] - mx) * (ys[it] - my) }
    if (sxx < 1e-6) return if (xs.size >= 2) Line(my, 0.0) else null
    val slope = sxy / sxx
    return Line(my - slope * mx, slope)
}

/** Where the edge through ([x], [y]) with [slope] meets the margin x = c + d·y. */
private fun meet(x: Double, y: Double, slope: Double, margin: Line): Pair<Double, Double>? {
    val d = margin.slope
    val denominator = 1 - d * slope
    if (abs(denominator) < 1e-6) return null
    val mx = (margin.intercept + d * (y - slope * x)) / denominator
    return mx to y + slope * (mx - x)
}

private fun isConvex(q: FloatArray): Boolean {
    val signs = (0 until 4).map { i ->
        val (ax, ay) = q[2 * i] to q[2 * i + 1]
        val (bx, by) = q[2 * ((i + 1) % 4)] to q[2 * ((i + 1) % 4) + 1]
        val (cx, cy) = q[2 * ((i + 2) % 4)] to q[2 * ((i + 2) % 4) + 1]
        (bx - ax) * (cy - by) - (by - ay) * (cx - bx)
    }
    return signs.all { it > 0 } || signs.all { it < 0 }
}

private fun degrees(radians: Double) = Math.toDegrees(radians)
