package com.guidedtour.app

import com.guidedtour.app.data.LanguageSpan
import com.guidedtour.app.data.at
import com.guidedtour.app.data.decodeSpans
import com.guidedtour.app.data.detectSpans
import com.guidedtour.app.data.encodeSpans
import com.guidedtour.app.data.mainLanguage
import com.guidedtour.app.data.paragraphRanges
import com.guidedtour.app.data.pickLanguage
import com.guidedtour.app.data.pieces
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguagesTest {
    private val portuguese = "O Partenon foi construído entre 447 e 432 a.C. como templo dedicado à deusa Atena."
    private val english = "The Parthenon was built between 447 and 432 BC as a temple dedicated to the goddess Athena."

    /** Pretends to be the language identifier: Portuguese if it sees "foi", English if "was". */
    private val fake: suspend (String) -> String? = { p ->
        when {
            " foi " in p -> "pt"
            " was " in p -> "en"
            else -> null
        }
    }

    @Test fun findsEachParagraph() {
        val text = "  First\n\nSecond one\n \n\nThird  "
        assertEquals(listOf("First", "Second one", "Third"), paragraphRanges(text).map { text.substring(it.first, it.last + 1) })
    }

    @Test fun bilingualPlaqueGetsTwoSpans() = runBlocking {
        val text = "$portuguese\n\n$english"
        val spans = detectSpans(text, fake)
        assertEquals(listOf(LanguageSpan(0, "pt"), LanguageSpan(text.indexOf("The Parthenon"), "en")), spans)
        assertEquals(listOf("pt" to "$portuguese\n\n", "en" to english), pieces(text, spans))
    }

    @Test fun shortTitleBelongsWithTheTextBelowIt() = runBlocking {
        val text = "PARTENON\n\n$portuguese\n\nTHE PARTHENON\n\n$english\n\n447–432 BC"
        val spans = detectSpans(text, fake)
        assertEquals(listOf("pt", "en"), spans.map { it.tag })
        assertEquals(text.indexOf("THE PARTHENON"), spans[1].start)
    }

    @Test fun sameLanguageParagraphsMerge() = runBlocking {
        assertEquals(listOf(LanguageSpan(0, "en")), detectSpans("$english\n\n$english", fake))
    }

    @Test fun nothingToldGivesNoSpans() = runBlocking {
        assertTrue(detectSpans("VENUS DE MILO\n\nc. 150 BC", fake).isEmpty())
    }

    @Test fun mainLanguageHasTheMostText() {
        val text = "Short pt foi.\n\n$english"
        assertEquals("en", mainLanguage(text, listOf(LanguageSpan(0, "pt"), LanguageSpan(text.indexOf("The"), "en"))))
    }

    @Test fun closeLanguagesShareTheVote() {
        // What ML Kit says for a Portuguese paragraph, and for a short Portuguese sentence.
        assertEquals("pt", pickLanguage(listOf("gl" to 0.66f, "pt" to 0.34f)))
        assertEquals("pt", pickLanguage(listOf("pt" to 0.40f, "gl" to 0.19f, "ca" to 0.13f)))
        assertEquals("pt", pickLanguage(listOf("gl" to 0.86f, "pt" to 0.14f)))
        // Galician only where it's written.
        assertEquals("gl", pickLanguage(listOf("gl" to 0.86f, "pt" to 0.14f), country = "ES"))
        assertEquals("pt", pickLanguage(listOf("gl" to 0.86f, "pt" to 0.14f), country = "PT"))
        assertEquals("pt", pickLanguage(listOf("pt" to 0.70f, "gl" to 0.30f), country = "ES"))
        assertEquals("ca", pickLanguage(listOf("ca" to 1.0f)))
        assertNull(pickLanguage(listOf("es" to 0.42f, "pt" to 0.36f, "nl" to 0.12f)))
        assertNull(pickLanguage(emptyList()))
    }

    @Test fun spanAtAnOffset() {
        val spans = listOf(LanguageSpan(0, "pt"), LanguageSpan(50, "en"))
        assertEquals("pt", spans.at(10)?.tag)
        assertEquals("en", spans.at(50)?.tag)
        assertNull(emptyList<LanguageSpan>().at(3))
    }

    @Test fun encodesOnlyMixedLanguages() {
        val spans = listOf(LanguageSpan(0, "pt"), LanguageSpan(812, "en"))
        assertEquals("0:pt;812:en", encodeSpans(spans))
        assertEquals(spans, decodeSpans("0:pt;812:en"))
        assertNull(encodeSpans(listOf(LanguageSpan(0, "en"))))
        assertTrue(decodeSpans(null).isEmpty())
    }
}
