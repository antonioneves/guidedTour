package com.guidedtour.app

import com.guidedtour.app.data.Scan
import com.guidedtour.app.data.groupVisits
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class VisitsTest {
    private fun scan(id: Long, day: String, hour: Int, place: String?) = Scan(
        id = id, text = "t$id",
        timestamp = LocalDate.parse(day).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        latitude = null, longitude = null, placeName = place, languageTag = null,
    )

    @Test fun groupsConsecutiveScansByDayAndPlace() {
        val newestFirst = listOf(
            scan(4, "2026-10-02", 11, "Louvre"),
            scan(3, "2026-10-02", 10, "Louvre"),
            scan(2, "2026-10-02", 9, "Orsay"),
            scan(1, "2026-10-01", 9, "Orsay"),
        )
        val visits = groupVisits(newestFirst, ZoneOffset.UTC)
        assertEquals(3, visits.size)
        assertEquals(listOf(4L, 3L), visits[0].scans.map { it.id })
        assertEquals(listOf(3L, 4L), visits[0].chronological.map { it.id })
        assertEquals("Orsay", visits[1].place)
        assertEquals(LocalDate.parse("2026-10-01"), visits[2].day)
    }

    @Test fun emptyListHasNoVisits() = assertEquals(emptyList<Any>(), groupVisits(emptyList()))
}
