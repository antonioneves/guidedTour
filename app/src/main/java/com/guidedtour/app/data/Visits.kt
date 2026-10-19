package com.guidedtour.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Consecutive scans made on the same day at the same place. [scans] is newest first. */
data class Visit(val day: LocalDate, val place: String?, val scans: List<Scan>) {
    val id: String get() = "${day}|${place.orEmpty()}|${scans.last().id}"
    /** Oldest first, the order a visit is walked and should be played. */
    val chronological: List<Scan> get() = scans.reversed()
}

/** [scans] must be sorted newest first. */
fun groupVisits(scans: List<Scan>, zone: ZoneId = ZoneId.systemDefault()): List<Visit> {
    val visits = mutableListOf<Visit>()
    for (scan in scans) {
        val day = Instant.ofEpochMilli(scan.timestamp).atZone(zone).toLocalDate()
        val last = visits.lastOrNull()
        if (last != null && last.day == day && last.place == scan.placeName) {
            visits[visits.lastIndex] = last.copy(scans = last.scans + scan)
        } else {
            visits += Visit(day, scan.placeName, listOf(scan))
        }
    }
    return visits
}
