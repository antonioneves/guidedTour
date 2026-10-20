package com.guidedtour.app

import com.guidedtour.app.ui.shortAge
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    private val now = 1_700_000_000_000L
    private fun ago(minutes: Long) = shortAge(now, now - minutes * 60_000)

    @Test fun recentTimesAreShort() {
        assertEquals("now", ago(0))
        assertEquals("12 min", ago(12))
        assertEquals("6 h", ago(6 * 60 + 20))
        assertEquals("3 d", ago(3 * 24 * 60))
    }
}
