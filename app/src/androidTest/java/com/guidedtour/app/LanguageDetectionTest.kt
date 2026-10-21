package com.guidedtour.app

import android.util.Log
import com.guidedtour.app.data.Recognizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks ML Kit's language detection on plaque-like texts, with the real on-device model. Bilingual
 * plaques must come out as one span per language; the harder cases (very short texts, close
 * languages) are only logged under "LANGUAGE_REPORT", as a measure rather than a pass/fail.
 */
class LanguageDetectionTest {
    private val portuguese = "O Partenon foi construído entre 447 e 432 a.C. como templo dedicado à deusa Atena, protetora da cidade."
    private val english = "The Parthenon was built between 447 and 432 BC as a temple dedicated to the goddess Athena, protector of the city."
    private val french = "La Victoire de Samothrace a été découverte en 1863 sur l'île de Samothrace, dans le nord de la mer Égée."
    private val italian = "La cappella fu affrescata da Giotto tra il 1303 e il 1305 su commissione di Enrico Scrovegni."
    private val german = "Die Kapelle wurde zwischen 1303 und 1305 von Giotto im Auftrag von Enrico Scrovegni ausgemalt."
    private val spanish = "El cuadro fue pintado por Velázquez en 1656 y muestra a la infanta Margarita rodeada de su corte."

    private fun tags(text: String) = runBlocking { Recognizer.detectLanguages(text).map { it.tag } }

    @Test fun bilingualPlaques() {
        assertEquals(listOf("pt", "en"), tags("$portuguese\n\n$english"))
        assertEquals(listOf("fr", "en"), tags("LA VICTOIRE DE SAMOTHRACE\n\n$french\n\nTHE WINGED VICTORY\n\n$english"))
        assertEquals(listOf("it", "de"), tags("$italian\n\n$german"))
    }

    @Test fun singleLanguage() {
        assertEquals(listOf("en"), tags(english))
        assertEquals(listOf("es"), tags(spanish))
    }

    @Test fun report() {
        val cases = listOf(
            "short title" to "VENUS DE MILO",
            "name and date" to "Auguste Rodin, 1880",
            "one short line es" to "Óleo sobre lienzo",
            "one short line pt" to "Óleo sobre tela",
            "close es" to "La iglesia fue construida en el siglo XII.",
            "close pt" to "A igreja foi construída no século XII.",
            "close ca" to "L'església va ser construïda al segle XII.",
            "pt paragraph" to portuguese,
            "pt plaque photo" to "O Partenon foi construído entre 447 e 432 a.C. como templo dedicado à deusa Atena.",
            "latin inscription" to "SENATVS POPVLVSQVE ROMANVS IMP CAESARI",
        )
        for ((name, text) in cases) {
            val single = runBlocking { Recognizer.detectLanguage(text) } ?: "unknown"
            Log.i("LANGUAGE_REPORT", "%-20s %-8s %s".format(name, single, text))
            val possible = runBlocking { Recognizer.possibleLanguages(text) }
            Log.i("LANGUAGE_REPORT", "%-20s   candidates %s".format("", possible.joinToString { "%s %.2f".format(it.first, it.second) }))
        }
    }
}
