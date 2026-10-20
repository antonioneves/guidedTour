package com.guidedtour.app

import com.guidedtour.app.data.OcrBlock
import com.guidedtour.app.data.OcrLine
import com.guidedtour.app.data.characterErrorRate
import com.guidedtour.app.data.cleanOcr
import com.guidedtour.app.data.dropNoise
import com.guidedtour.app.data.fixConfusables
import com.guidedtour.app.data.joinLines
import com.guidedtour.app.data.laplacianVariance
import com.guidedtour.app.data.layoutText
import com.guidedtour.app.data.meanConfidence
import com.guidedtour.app.data.orderBlocks
import com.guidedtour.app.data.paragraphsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrLayoutTest {
    private fun block(text: String, left: Int, top: Int, width: Int = 400, height: Int = 40, conf: Float = 0.9f) =
        OcrBlock(listOf(OcrLine(text, left, top, left + width, top + height, conf)))

    @Test fun hyphenIsRemovedOnlyBeforeLowercaseContinuation() {
        assertEquals("The temple", joinLines(listOf("The tem-", "ple")))
        assertEquals("Greek- Roman art", joinLines(listOf("Greek-", "Roman art")))
    }

    @Test fun noSpaceBetweenCjkLines() {
        assertEquals("你好世界", joinLines(listOf("你好", "世界")))
    }

    @Test fun cleanOcrJoinsLines() = assertEquals("one two three", cleanOcr("one\ntwo\nthree\n"))

    @Test fun columnsAreReadOneAfterTheOther() {
        val leftTop = block("L1", 0, 100)
        val rightTop = block("R1", 500, 100)
        val leftBottom = block("L2", 0, 200)
        val rightBottom = block("R2", 500, 200)
        val order = orderBlocks(listOf(rightBottom, leftTop, rightTop, leftBottom)).map { it.text }
        assertEquals(listOf("L1", "L2", "R1", "R2"), order)
    }

    @Test fun fullWidthHeadingSplitsTheBands() {
        val heading = block("TITLE", 0, 0, width = 900)
        val order = orderBlocks(
            listOf(block("R1", 500, 100), heading, block("L1", 0, 100), block("L2", 0, 200), block("R2", 500, 200))
        ).map { it.text }
        assertEquals(listOf("TITLE", "L1", "L2", "R1", "R2"), order)
    }

    @Test fun smallCaptionsAndPageNumbersAreDropped() {
        val main = block("The main plaque text which is long enough to matter.", 0, 100, height = 60)
        val credit = block("Photo: someone", 0, 400, height = 20)
        val page = block("12", 800, 900, height = 60)
        assertEquals(listOf(main), dropNoise(listOf(main, credit, page)))
    }

    @Test fun lonelyBlockIsNeverDropped() {
        val only = block("12", 0, 0)
        assertEquals(listOf(only), dropNoise(listOf(only)))
    }

    @Test fun confusablesAreFixedInsideWords() {
        assertEquals("a love of table and 2024 and A0", fixConfusables("a l0ve of tab1e and 2024 and A0"))
    }

    @Test fun confusablesLeftAloneForNonLatinText() {
        assertEquals("你好0好", fixConfusables("你好0好"))
    }

    @Test fun layoutSeparatesParagraphsWithBlankLine() {
        val text = layoutText(listOf(block("Second", 0, 200), block("First", 0, 100)))
        assertEquals("First\n\nSecond", text)
        assertEquals(listOf("First", "Second"), paragraphsOf(text))
    }

    @Test fun meanConfidenceIsWeightedByLength() {
        val blocks = listOf(block("aaaa", 0, 0, conf = 1f), block("bbbbbbbbbbbb", 0, 100, conf = 0.5f))
        assertEquals(0.625f, meanConfidence(blocks), 0.001f)
    }

    @Test fun errorRate() {
        assertEquals(0.0, characterErrorRate("hello  world", "hello world"), 0.0)
        assertEquals(0.2, characterErrorRate("hello", "hallo"), 0.0001)
        assertEquals(1.0, characterErrorRate("abc", ""), 0.0)
    }

    @Test fun sharpImageScoresHigherThanBlurred() {
        val w = 40
        val h = 40
        val sharp = IntArray(w * h) { if ((it % w / 4 + it / w / 4) % 2 == 0) 0 else 255 }
        val blurred = IntArray(w * h) { 128 }
        assertTrue(laplacianVariance(sharp, w, h) > laplacianVariance(blurred, w, h) + 1000)
    }
}
