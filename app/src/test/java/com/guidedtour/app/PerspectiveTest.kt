package com.guidedtour.app

import com.guidedtour.app.data.OcrLine
import com.guidedtour.app.data.textQuad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

class PerspectiveTest {
    private val width = 1600.0
    private val height = 1200.0

    /** A point on a plaque [width] × [height] as a camera sees it turned [yaw]° to the side and [pitch]° up. */
    private fun camera(yaw: Double, pitch: Double): (Double, Double) -> Pair<Double, Double> {
        val f = 1.1 * width
        val ry = Math.toRadians(yaw)
        val rp = Math.toRadians(pitch)
        return { x, y ->
            val px = x - width / 2
            val py = y - height / 2
            // Turn about the vertical axis, then the horizontal one, then push away from the camera.
            val x1 = cos(ry) * px
            val z1 = -sin(ry) * px
            val y2 = cos(rp) * py - sin(rp) * z1
            val z2 = sin(rp) * py + cos(rp) * z1 + 1.25 * f
            (f * x1 / z2 + width / 2) to (f * y2 / z2 + height / 2)
        }
    }

    private fun rotation(degrees: Double): (Double, Double) -> Pair<Double, Double> {
        val r = Math.toRadians(degrees)
        return { x, y ->
            val px = x - width / 2
            val py = y - height / 2
            (cos(r) * px - sin(r) * py + width / 2) to (sin(r) * px + cos(r) * py + height / 2)
        }
    }

    /** Five left-aligned lines of a paragraph, ragged on the right, as [view] shows them. */
    private fun paragraph(view: (Double, Double) -> Pair<Double, Double>): List<OcrLine> =
        listOf(1100, 1050, 1150, 1000, 900).mapIndexed { i, length ->
            val top = 260.0 + i * 81
            val corners = listOf(120.0 to top, 120.0 + length to top, 120.0 + length to top + 54, 120.0 to top + 54).map { view(it.first, it.second) }
            val xs = corners.map { it.first }
            val ys = corners.map { it.second }
            OcrLine(
                "line $i", xs.min().roundToInt(), ys.min().roundToInt(), xs.max().roundToInt(), ys.max().roundToInt(),
                corners = corners.flatMap { listOf(it.first.toFloat(), it.second.toFloat()) },
            )
        }

    private fun angle(ax: Float, ay: Float, bx: Float, by: Float) = Math.toDegrees(atan2((by - ay).toDouble(), (bx - ax).toDouble()))

    @Test fun squareOnOrTiltedTextNeedsNoStretching() {
        assertNull(textQuad(paragraph { x, y -> x to y }))
        assertNull(textQuad(paragraph(rotation(10.0))))
    }

    @Test fun oneLineDoesNotTell() {
        assertNull(textQuad(paragraph(camera(40.0, 0.0)).take(1)))
    }

    @Test fun findsTextSeenFromTheSide() = checkFound(camera(40.0, 0.0))

    @Test fun findsTextSeenFromBelow() = checkFound(camera(0.0, -40.0))

    @Test fun findsTextSeenFromTheSideAndBelow() = checkFound(camera(35.0, -25.0))

    /** The found corners sit on the text's left margin, and its top and bottom edges run along the first and last lines. */
    private fun checkFound(view: (Double, Double) -> Pair<Double, Double>) {
        val lines = paragraph(view)
        val quad = requireNotNull(textQuad(lines)) { "not seen at an angle" }
        val (topLeftX, topLeftY) = view(120.0, 260.0)
        val (bottomLeftX, bottomLeftY) = view(120.0, 260.0 + 4 * 81 + 54)
        val size = 30.0
        assertTrue("top-left off by ${hypot(quad[0] - topLeftX, quad[1] - topLeftY)}", hypot(quad[0] - topLeftX, quad[1] - topLeftY) < size)
        assertTrue("bottom-left off by ${hypot(quad[6] - bottomLeftX, quad[7] - bottomLeftY)}", hypot(quad[6] - bottomLeftX, quad[7] - bottomLeftY) < size)
        val first = lines.first().corners
        val last = lines.last().corners
        assertEquals(angle(first[0], first[1], first[2], first[3]), angle(quad[0], quad[1], quad[2], quad[3]), 1.0)
        assertEquals(angle(last[6], last[7], last[4], last[5]), angle(quad[6], quad[7], quad[4], quad[5]), 1.0)
        // The left margin runs down the line starts.
        assertEquals(angle(first[0], first[1], last[6], last[7]), angle(quad[0], quad[1], quad[6], quad[7]), 1.0)
    }
}
