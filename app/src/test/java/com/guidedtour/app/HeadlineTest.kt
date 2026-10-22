package com.guidedtour.app

import com.guidedtour.app.data.cleanHeadline
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
}
