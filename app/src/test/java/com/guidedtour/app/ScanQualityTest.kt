package com.guidedtour.app

import com.guidedtour.app.ui.ANALYSIS_PAUSED
import com.guidedtour.app.ui.LineBox
import com.guidedtour.app.ui.LiveFrame
import com.guidedtour.app.ui.Tip
import com.guidedtour.app.ui.analysisIntervalMs
import com.guidedtour.app.ui.evaluate
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanQualityTest {
    private val w = 1000
    private val h = 2000

    private fun line(top: Int = 500, height: Int = 60, left: Int = 100, right: Int = 900, confidence: Float = 0.9f) =
        LineBox(left, top, right, top + height, confidence)

    private fun frame(vararg lines: LineBox, brightness: Int = 150) = LiveFrame("text", lines.toList(), brightness)

    @Test fun darkWinsOverEverything() = assertEquals(Tip.Dark, evaluate(frame(line(), brightness = 20), w, h))
    @Test fun noLinesMeansNoText() = assertEquals(Tip.NoText, evaluate(frame(), w, h))
    @Test fun unknownViewSizeMeansNoText() = assertEquals(Tip.NoText, evaluate(frame(line()), 0, 0))
    @Test fun tinyTextIsTooSmall() = assertEquals(Tip.TooSmall, evaluate(frame(line(height = 20)), w, h))
    @Test fun textTouchingEdgeIsCutOff() = assertEquals(Tip.CutOff, evaluate(frame(line(left = 2)), w, h))
    @Test fun lowConfidenceIsUnclear() = assertEquals(Tip.Unclear, evaluate(frame(line(confidence = 0.4f)), w, h))
    @Test fun movingPhoneIsShakyEvenIfTextLooksGood() =
        assertEquals(Tip.Shaky, evaluate(frame(line()), w, h, steady = false))

    @Test fun glareIsAWarningButStillOk() {
        val tip = evaluate(LiveFrame("text", listOf(line()), 150, glare = 0.2f), w, h)
        assertEquals(Tip.Glare, tip)
        assertEquals(true, tip.ok)
    }

    @Test fun goodFrameIsOk() {
        val tip = evaluate(frame(line(), line(top = 700)), w, h)
        assertEquals(Tip.Good, tip)
        assertEquals(true, tip.ok)
    }

    @Test fun analysisRunsFasterWithTextAndPausesWhileReading() {
        val searching = analysisIntervalMs(processing = false, showingResult = false, textInView = false)
        val withText = analysisIntervalMs(processing = false, showingResult = false, textInView = true)
        assertEquals(true, withText < searching)
        assertEquals(ANALYSIS_PAUSED, analysisIntervalMs(processing = true, showingResult = false, textInView = true))
        assertEquals(true, analysisIntervalMs(processing = false, showingResult = true, textInView = true) > searching)
    }
}
