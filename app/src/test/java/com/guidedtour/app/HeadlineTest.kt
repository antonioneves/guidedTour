package com.guidedtour.app

import com.guidedtour.app.data.cleanHeadline
import com.guidedtour.app.data.printedTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadlineTest {
    @Test fun labelsQuotesAndMarkupAreDropped() {
        assertEquals("The Parthenon Frieze", cleanHeadline("**Headline:** \"The Parthenon Frieze.\""))
        assertEquals("Die Stadtmauer von 1250", cleanHeadline("\n# Die Stadtmauer von 1250\nMore words"))
    }

    @Test fun answersThatAreNoHeadlineAreRejected() {
        assertNull(cleanHeadline("  "))
        assertNull(cleanHeadline("\"...\""))
        assertNull(cleanHeadline("This text describes the history of the old town walls, which were built in the thirteenth century."))
    }

    @Test fun aShortFirstParagraphIsThePrintedTitle() {
        assertEquals("The Acropolis of Athens", printedTitle("THE ACROPOLIS OF ATHENS\n\nThe citadel rose above the city."))
        assertEquals("Die Stadtmauer", printedTitle("Die Stadtmauer\n\nDie Mauer wurde um 1250 errichtet."))
    }

    @Test fun aTextWithoutAHeadingHasNoPrintedTitle() {
        assertNull(printedTitle("The Parthenon was built between 447 and 432 BC."))
        assertNull(printedTitle("It was built in 447 BC.\n\nIts sculptures are famous."))
        assertNull(printedTitle("12\n\nThe citadel rose above the city."))
        assertNull(printedTitle("This first paragraph is far too long to be the title printed at the top of a plaque\n\nMore."))
    }
}
