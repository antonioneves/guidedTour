package com.guidedtour.app

import com.guidedtour.app.data.OcrBlock
import com.guidedtour.app.data.OcrLine
import com.guidedtour.app.data.characterErrorRate
import com.guidedtour.app.data.cleanOcr
import com.guidedtour.app.data.dropIntruders
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

    @Test fun aBigTitleDoesNotMakeTheInstructionsCaptions() {
        val title = block("Low Row", 0, 0, height = 175)
        val steps = listOf(
            "1 Select a resistance level that enables you to perform the movement correctly.",
            "2 Sit down in an upright position with the legs slightly bent at the knees.",
            "3 Pull the handle to the upper abdominals keeping the elbows close to your sides.",
        ).mapIndexed { i, text -> block(text, 0, 200 + 60 * i, height = 47) }
        val caution = block("CAUTION", 0, 400, height = 34)
        val credit = block("www.example.com", 0, 600, height = 20)
        val all = listOf(title) + steps + caution
        assertEquals(all, dropNoise(all + credit))
    }

    // A 2000 x 1500 photo with the plaque in the middle.
    private val body = listOf(
        block("The Parthenon was built between 447 and 432 BC", 500, 500, width = 1000),
        block("as a temple dedicated to the goddess Athena.", 500, 560, width = 1000),
        block("Its marble sculptures are among the finest works.", 500, 620, width = 1000),
    )

    @Test fun textFromANeighbouringPlaqueIsDropped() {
        val side = block("of the Roman", 1850, 540, width = 150)
        assertEquals(body, dropIntruders(body + side, 2000, 1500))
        assertTrue("Roman" !in layoutText(body + side, 2000, 1500))
    }

    @Test fun columnsAndHeadingOfOnePlaqueAreKept() {
        val heading = block("THE ACROPOLIS", 500, 380, width = 700, height = 80)
        val left = block("The citadel rose above the city", 300, 500, width = 600)
        val right = block("Pericles led the great building", 1000, 500, width = 600)
        val all = listOf(heading, left, right)
        assertEquals(all, dropIntruders(all, 2000, 1500))
    }

    @Test fun withoutThePhotoSizeNothingIsDropped() {
        val side = block("of the Roman", 1850, 540, width = 150)
        assertEquals(body + side, dropIntruders(body + side, 0, 0))
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
