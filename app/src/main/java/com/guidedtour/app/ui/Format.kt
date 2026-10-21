package com.guidedtour.app.ui

import android.text.format.DateUtils
import com.guidedtour.app.data.Scan
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

fun Scan.placeLabel(): String =
    placeName ?: if (latitude != null) "%.4f, %.4f".format(latitude, longitude) else "Unknown location"

fun Scan.timeLabel(): String =
    DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** A short "how long ago" for tight spaces: now, 12 min, 6 h, 3 d, then a date. */
fun Scan.shortTimeLabel(): String = shortAge(System.currentTimeMillis(), timestamp)

fun shortAge(now: Long, then: Long, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String {
    val minutes = (now - then).coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "$minutes min"
        minutes < 24 * 60 -> "${minutes / 60} h"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} d"
        else -> {
            val date = Instant.ofEpochMilli(then).atZone(zone)
            val sameYear = date.year == Instant.ofEpochMilli(now).atZone(zone).year
            val skeleton = if (sameYear) "dMMM" else "MMMyyyy"
            DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(date)
        }
    }
}

fun Scan.whenAndWhere(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp)) + " · " + placeLabel()

/** The text's languages by name, e.g. "Portuguese + English" for a bilingual plaque. */
fun Scan.languageLabel(): String? =
    allLanguages().mapNotNull { Locale.forLanguageTag(it).displayLanguage.takeIf { l -> l.isNotBlank() } }
        .joinToString(" + ").ifEmpty { null }
