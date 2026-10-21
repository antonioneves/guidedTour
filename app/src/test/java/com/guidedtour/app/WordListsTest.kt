package com.guidedtour.app

import com.guidedtour.app.data.LanguageSpan
import com.guidedtour.app.data.WordList
import com.guidedtour.app.data.correctWords
import com.guidedtour.app.data.listName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WordListsTest {
    private val lists = mapOf("en" to "en", "pt" to "pt").mapValues { (_, name) ->
        File("src/main/assets/words/$name.words").inputStream().use(WordList::read)
    }

    private fun fix(text: String, unsure: Set<String>, language: String = "en") =
        correctWords(text, listOf(LanguageSpan(0, language)), unsure) { lists[it] }

    @Test fun readsTheBundledList() {
        val en = lists.getValue("en")
        assertTrue(en.frequency("the") > en.frequency("temple"))
        assertTrue(en.frequency("sarcophagus") >= 0)
        assertEquals(-1, en.frequency("tcmple"))
    }

    @Test fun fixesTypicalMisreadings() {
        assertEquals(
            "The Parthenon was built as a temple dedicated to the goddess Athena.",
            fix("The Parthenon was bui1t as a tcmple dedicatecl to the goddess Athena.", setOf("tcmple", "dedicatecl")),
        )
        assertEquals("modern marble", fix("rnodern marb1e", setOf("rnodern")))
    }

    @Test fun onlyMakesSlipsOcrMakes() {
        // A lost thin letter, not the commoner word one letter away.
        assertEquals("was built", fix("was buit", setOf("buit")))
        // Look-alike "o" for "a".
        assertEquals("near the machine", fix("neor the machine", setOf("neor")))
        assertEquals("in a controlled manner", fix("in a controlled monner", setOf("monner")))
        // Two slips away ("allow"): left as read rather than made "flow".
        assertEquals("do not oflow people", fix("do not oflow people", setOf("oflow")))
    }

    @Test fun oneLetterNonWordsBecomeTheLookalikeThatIs() {
        assertEquals("in a controlled manner", fix("in e controlled manner", setOf("e")))
        assertEquals("in a controlled manner", fix("in o controlled manner", setOf("o")))
        // Sure of it, or a real one-letter word: left alone.
        assertEquals("in e controlled manner", fix("in e controlled manner", emptySet()))
        assertEquals("O templo foi construído", fix("O templo foi construído", setOf("o"), "pt"))
    }

    @Test fun thePlacesOwnWordsMendNamesAndCountAsKnown() {
        val place = setOf("erechtheion", "caryatids")
        fun withPlace(text: String, unsure: Set<String>) = correctWords(text, listOf(LanguageSpan(0, "en")), unsure, place) { lists[it] }
        // A misread name, mended into the place's word for it; without it, left alone as a name.
        assertEquals("the porch of the Erechtheion", withPlace("the porch of the Erechtheiom", setOf("erechtheiom")))
        assertEquals("the porch of the Erechtheiom", fix("the porch of the Erechtheiom", setOf("erechtheiom")))
        // A word only the place knows isn't "corrected" into a common one.
        assertEquals("the caryatids stand", withPlace("the caryatids stand", setOf("caryatids")))
    }

    @Test fun keepsCaseOfTheMisreadWord() {
        assertEquals("THE TEMPLE", fix("THE TCMPLE", setOf("tcmple")))
        assertEquals("Temple of Zeus", fix("Tcmple of Zeus", setOf("tcmple")))
    }

    @Test fun trustsWordsOcrWasSureOf() {
        assertEquals("a tcmple", fix("a tcmple", emptySet()))
    }

    @Test fun leavesNamesAndRepeatedWordsAlone() {
        // Capitalised inside a sentence: a name the list may not know.
        assertEquals("built by Iktinos and Kallikrates", fix("built by Iktinos and Kallikrates", setOf("iktinos", "kallikrates")))
        // Read the same way twice: probably right.
        assertEquals("the tholos and a tholos", fix("the tholos and a tholos", setOf("tholos")))
    }

    @Test fun leavesGarbledParagraphsAlone() {
        // What OCR made of tiny print: mending single words here only invents new mistakes.
        val garbled = "you to perto an uprighnnes 2 St do ent ar the sligh Bhe handie to the elbows clo wer body"
        assertEquals(garbled, fix(garbled, setOf("perto", "uprighnnes", "sligh", "handie", "wer")))
        // The next paragraph, read well, is still mended.
        assertEquals("$garbled\n\nThe temple was built.", fix("$garbled\n\nThe tcmple was built.", setOf("perto", "tcmple")))
    }

    @Test fun eachParagraphUsesItsLanguage() {
        val pt = "O templo foi construido para Atena."
        val en = "The tcmple was built for Athena."
        val text = "$pt\n\n$en"
        val fixed = correctWords(text, listOf(LanguageSpan(0, "pt"), LanguageSpan(text.indexOf("The"), "en")), setOf("construido", "tcmple")) { lists[it] }
        assertEquals("O templo foi construído para Atena.\n\nThe temple was built for Athena.", fixed)
    }

    @Test fun noListOrNoLanguageLeavesTextAlone() {
        assertEquals("a tcmple", correctWords("a tcmple", emptyList(), setOf("tcmple")) { lists[it] })
        assertEquals("Ο ναός", correctWords("Ο ναός", listOf(LanguageSpan(0, "el")), setOf("ναός")) { lists[it] })
    }

    @Test fun namesTheSharedLists() {
        assertEquals("nb", listName("no"))
        assertEquals("sh", listName("hr"))
        assertEquals("pt", listName("pt-BR"))
        assertNull(listName("und"))
    }
}
