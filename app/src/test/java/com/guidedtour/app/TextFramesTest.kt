package com.guidedtour.app

import com.guidedtour.app.data.FrameTracker
import com.guidedtour.app.data.OcrBlock
import com.guidedtour.app.data.OcrLine
import com.guidedtour.app.data.TextFrame
import com.guidedtour.app.data.mergeOverlapping
import com.guidedtour.app.data.splitMergedColumns
import com.guidedtour.app.data.textFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFramesTest {
    /** A line of [words], each [wordWidth] wide with [space] between, starting at [left]; [gutter] adds a wide gap before word [at]. */
    private fun line(words: List<String>, left: Int, top: Int, height: Int = 40, wordWidth: Int = 100, space: Int = 15, gutter: Int = 0, at: Int = -1): OcrLine {
        var x = left
        val boxes = words.indices.map { i ->
            if (i == at) x += gutter
            (x to x + wordWidth).also { x += wordWidth + space }
        }
        return OcrLine(words.joinToString(" "), boxes.first().first, top, boxes.last().second, top + height, words = words.map { it to 0.9f }, wordBoxes = boxes)
    }

    private fun block(text: String, left: Int, top: Int, width: Int = 400, height: Int = 40, lines: Int = 3) =
        OcrBlock(List(lines) { OcrLine(text, left, top + it * (height + 10), left + width, top + it * (height + 10) + height) })

    @Test fun twoColumnsReadAsOneBlockAreSplitAtTheGutter() {
        val merged = OcrBlock(listOf(
            line(listOf("The", "citadel", "rose", "Pericles", "led", "the"), 100, 100, gutter = 120, at = 3),
            line(listOf("above", "the", "city", "great", "building", "work"), 100, 150, gutter = 120, at = 3),
            line(listOf("and", "was", "a", "that", "created", "the"), 100, 200, gutter = 120, at = 3),
        ))
        val columns = splitMergedColumns(merged)
        assertEquals(2, columns.size)
        assertEquals(listOf("The citadel rose", "above the city", "and was a"), columns[0].lines.map { it.text })
        assertEquals(listOf("Pericles led the", "great building work", "that created the"), columns[1].lines.map { it.text })
    }

    @Test fun aParagraphWithOneWideSpaceIsNotSplit() {
        val paragraph = OcrBlock(listOf(
            line(listOf("Room", "12", "EXIT"), 100, 100, gutter = 120, at = 2),
            line(listOf("Opening", "hours", "daily"), 100, 150),
        ))
        assertEquals(1, splitMergedColumns(paragraph).size)
    }

    @Test fun columnsOfOnePlaqueAreSeparateSuggestedFrames() {
        // A heading over two columns, in the middle of a 1600 × 1200 view.
        val heading = block("THE ACROPOLIS OF ATHENS", 300, 200, width = 1000, height = 60, lines = 1)
        val left = block("The citadel rose above the city", 300, 320, width = 450)
        val right = block("Pericles led the great building", 850, 320, width = 450)
        val frames = textFrames(listOf(heading, left, right), 1600, 1200)
        assertEquals(3, frames.size)
        assertTrue(frames.all { it.suggested })
    }

    @Test fun aSmallNoteAsideIsAFrameButNotSuggested() {
        val main = block("The Parthenon was built between 447 and 432 BC as a temple", 300, 300, width = 900, lines = 5)
        val note = block("Room 12", 1450, 1000, width = 120, height = 18, lines = 1)
        val frames = textFrames(listOf(main, note), 1600, 1200)
        assertEquals(2, frames.size)
        assertTrue(frames.single { it.blocks.contains(main) }.suggested)
        assertFalse(frames.single { it.blocks.contains(note) }.suggested)
    }

    private fun frame(left: Float, top: Float, suggested: Boolean = true) =
        TextFrame(left, top, left + 400, top + 200, listOf(block("text", left.toInt(), top.toInt())), suggested)

    @Test fun trackerKeepsAFrameAcrossSmallMovesAndShortGaps() {
        val tracker = FrameTracker()
        val id = tracker.update(listOf(frame(100f, 100f))).single().id
        assertEquals(id, tracker.update(listOf(frame(120f, 110f))).single().id)
        // Missed for one frame: still there, then found again with the same id.
        assertEquals(id, tracker.update(emptyList()).single().id)
        assertEquals(id, tracker.update(listOf(frame(125f, 112f))).single().id)
        repeat(7) { tracker.update(emptyList()) }
        assertTrue(tracker.frames.isEmpty())
    }

    @Test fun suggestionChangesOnlyOnceItHolds() {
        val tracker = FrameTracker()
        tracker.update(listOf(frame(100f, 100f, suggested = true)))
        tracker.update(listOf(frame(100f, 100f, suggested = false)))
        assertTrue(tracker.frames.single().included)
        repeat(6) { tracker.update(listOf(frame(100f, 100f, suggested = false))) }
        assertFalse(tracker.frames.single().included)
    }

    @Test fun tapTogglesTheFrameUnderIt() {
        val tracker = FrameTracker()
        repeat(3) { tracker.update(listOf(frame(100f, 100f), frame(700f, 100f, suggested = false))) }
        assertTrue(tracker.toggleAt(800f, 150f))
        assertTrue(tracker.frames.all { it.included })
        assertTrue(tracker.toggleAt(150f, 150f))
        assertFalse(tracker.frames.single { it.left < 500 }.included)
        assertFalse(tracker.toggleAt(1400f, 900f))
        // The choice outlasts the suggestion changing.
        repeat(7) { tracker.update(listOf(frame(100f, 100f), frame(700f, 100f, suggested = false))) }
        assertFalse(tracker.frames.single { it.left < 500 }.included)
    }

    @Test fun overlappingFramesAreOneFrame() {
        val inner = frame(150f, 120f)
        val outer = TextFrame(100f, 100f, 520f, 330f, listOf(block("more text", 100, 100)), true)
        val merged = mergeOverlapping(listOf(inner, outer))
        assertEquals(1, merged.size)
        assertEquals(2, merged.single().blocks.size)
    }

    @Test fun trackerKeepsOneFrameWhenItGrowsAroundTheOldOne() {
        val tracker = FrameTracker()
        val id = tracker.update(listOf(frame(100f, 100f))).single().id
        // The same text read as a larger block, with the old one lying inside it.
        val grown = TextFrame(60f, 60f, 560f, 400f, listOf(block("text", 60, 60)), true)
        val tracked = tracker.update(listOf(grown))
        assertEquals(1, tracked.size)
        assertEquals(id, tracked.single().id)
    }

    @Test fun trackerDropsAFrameInsideAnother() {
        val tracker = FrameTracker()
        tracker.update(listOf(frame(100f, 100f)))
        tracker.update(listOf(frame(100f, 100f)))
        // A new, small frame appears inside the settled one: not tracked separately.
        val tracked = tracker.update(listOf(frame(100f, 100f), TextFrame(150f, 130f, 300f, 200f, listOf(block("x", 150, 130)), true)))
        assertEquals(1, tracked.size)
    }

    @Test fun framesThatOverlapAtTheCornersAreJoined() {
        val a = frame(100f, 100f)
        val b = frame(250f, 130f)
        assertEquals(1, mergeOverlapping(listOf(a, b)).size)
        // Skipped next to suggested ones stay apart unless one is mostly inside the other.
        assertEquals(2, mergeOverlapping(listOf(a, frame(250f, 130f, suggested = false))).size)
    }

    @Test fun anUnseenFrameGoesAtOnceWhenAnotherCoversItsPlace() {
        val tracker = FrameTracker()
        tracker.update(listOf(frame(100f, 100f)))
        // The text now reads as a frame elsewhere on top of the old place, too far to be matched to it.
        val tracked = tracker.update(listOf(TextFrame(150f, 90f, 1100f, 500f, listOf(block("far", 150, 90)), true)))
        assertTrue(tracked.none { it.left < 140 })
    }

    @Test fun aSteadyFrameHoldsStillThroughJitter() {
        val tracker = FrameTracker()
        repeat(8) { tracker.update(listOf(frame(100f, 100f))) }
        val still = tracker.frames.single().let { it.left to it.top }
        // A few pixels either way, as ML Kit's readings wobble.
        repeat(10) { i -> tracker.update(listOf(frame(100f + (i % 3) * 8f, 100f + (i % 2) * 8f))) }
        assertEquals(still, tracker.frames.single().let { it.left to it.top })
    }

    @Test fun aSteadyFrameFollowsTheTextOnceItClearlyMovedAway() {
        val tracker = FrameTracker()
        repeat(8) { tracker.update(listOf(frame(100f, 100f))) }
        val id = tracker.frames.single().id
        repeat(5) { tracker.update(listOf(frame(260f, 100f))) }
        repeat(10) { tracker.update(listOf(frame(260f, 100f))) }
        val moved = tracker.frames.single()
        assertEquals(id, moved.id)
        assertTrue(moved.left > 240f)
    }

    @Test fun framesAtTheEdgeAreIgnoredUnlessTheyAreAllThereIs() {
        val main = block("The Parthenon was built between 447 and 432 BC as a temple", 300, 300, width = 900, lines = 5)
        val cut = block("half a line of another plaque", 0, 600, width = 500, lines = 2)
        val both = textFrames(listOf(main, cut), 1600, 1200)
        assertEquals(1, both.size)
        assertTrue(both.single().blocks.contains(main))
        // Alone, the text at the edge is all there is to read.
        assertEquals(1, textFrames(listOf(cut), 1600, 1200).size)
    }

    @Test fun aBigFrameStaysWhenOnlyASmallPieceOfItIsSeen() {
        val tracker = FrameTracker()
        repeat(3) { tracker.update(listOf(frame(100f, 100f))) }
        // This time only a few words inside it are read.
        val tracked = tracker.update(listOf(TextFrame(150f, 130f, 300f, 200f, listOf(block("x", 150, 130)), true)))
        assertTrue(tracked.any { it.left < 110 && it.right > 450 })
    }
}
