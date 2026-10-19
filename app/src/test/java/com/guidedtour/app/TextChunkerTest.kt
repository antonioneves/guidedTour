package com.guidedtour.app

import com.guidedtour.app.data.cleanOcr
import com.guidedtour.app.data.splitForSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TextChunkerTest {
    @Test fun cleanJoinsLinesAndHyphenation() {
        assertEquals("The temple was built in 432 BC.", cleanOcr("The tem-\nple was built\nin 432 BC.\n"))
    }

    @Test fun splitsIntoSentencesWithOffsets() {
        val text = "First sentence. Second one here. Third."
        val chunks = splitForSpeech(text, 100, Locale.ENGLISH)
        assertEquals(listOf("First sentence.", "Second one here.", "Third."), chunks.map { it.text })
        chunks.forEach { assertEquals(it.text, text.substring(it.start, it.start + it.text.length)) }
    }

    @Test fun longSentencesAreCutUnderTheLimit() {
        val text = (1..400).joinToString(" ") { "word$it" } + "."
        val chunks = splitForSpeech(text, 200, Locale.ENGLISH)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.text.length <= 200 })
        // Nothing is lost.
        assertEquals(text.split(" ").size, chunks.sumOf { it.text.split(" ").size })
    }

    @Test fun textWithoutSpacesStillSplits() {
        val chunks = splitForSpeech("a".repeat(450), 200, Locale.ENGLISH)
        assertEquals(listOf(200, 200, 50), chunks.map { it.text.length })
    }

    @Test fun blankTextHasNoChunks() {
        assertTrue(splitForSpeech("   \n ", 100, Locale.ENGLISH).isEmpty())
    }
}
