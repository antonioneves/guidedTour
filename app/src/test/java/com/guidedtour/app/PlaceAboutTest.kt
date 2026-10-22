package com.guidedtour.app

import com.guidedtour.app.data.introOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceAboutTest {
    @Test fun asidesInBracketsAreLeftOut() {
        assertEquals(
            "The Parthenon is a former temple on the Athenian Acropolis, Greece, that was dedicated to the goddess Athena.",
            introOf("The Parthenon (/ˈpɑːrθəˌnɒn/; Ancient Greek: Παρθενών) is a former temple on the Athenian Acropolis, Greece, that was dedicated to the goddess Athena."),
        )
    }

    @Test fun aLongIntroductionKeepsItsFirstParagraphs() {
        val paragraph = "A paragraph about the history of the place, long enough to count as real text. ".repeat(5).trim()
        val intro = introOf(List(6) { paragraph }.joinToString("\n"))!!
        assertTrue(intro.length <= 1_200)
        assertTrue(intro.split("\n\n").size in 2..5)
    }

    @Test fun noTextIsNoIntroduction() {
        assertNull(introOf(""))
        assertNull(introOf("Short stub."))
    }
}
