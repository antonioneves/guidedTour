package com.guidedtour.app.ui

import android.text.format.DateUtils
import com.guidedtour.app.data.Scan
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun Scan.placeLabel(): String =
    placeName ?: if (latitude != null) "%.4f, %.4f".format(latitude, longitude) else "Unknown location"

fun Scan.timeLabel(): String =
    DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

fun Scan.whenAndWhere(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp)) + " · " + placeLabel()

fun Scan.languageLabel(): String? =
    languageTag?.let { Locale.forLanguageTag(it).displayLanguage.takeIf { l -> l.isNotBlank() } }
