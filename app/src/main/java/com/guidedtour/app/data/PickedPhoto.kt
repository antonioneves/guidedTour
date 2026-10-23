package com.guidedtour.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

/**
 * A photo picked from the phone's gallery, upright and at most [Recognizer.FULL_SIDE] on its long
 * side, with when and where it was taken if the photo says (the photo picker leaves the place out
 * unless the user lets it share it).
 */
class PickedPhoto(val bitmap: Bitmap, val taken: Long?, val latitude: Double?, val longitude: Double?)

/** Opens the photo at [uri], or null if it can't be read. */
fun readPickedPhoto(context: Context, uri: Uri): PickedPhoto? = runCatching {
    val resolver = context.contentResolver
    val exif = resolver.openInputStream(uri)?.use { runCatching { ExifInterface(it) }.getOrNull() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    // Decode no bigger than needed (in halvings), then scale the rest of the way.
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= Recognizer.FULL_SIDE) sample *= 2
    val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: return null
    val turn = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val scale = min(1f, Recognizer.FULL_SIDE.toFloat() / max(raw.width, raw.height))
    val bitmap = if (turn == 0f && scale == 1f) raw else {
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(turn); postScale(scale, scale) }, true)
            .also { if (it !== raw) raw.recycle() }
    }
    val place = exif?.latLong
    PickedPhoto(bitmap, exif?.let(::takenAt), place?.get(0), place?.get(1))
}.getOrNull()

/** When the photo was taken, from its EXIF date and time (in its own time zone if it gives one, else the phone's). */
private fun takenAt(exif: ExifInterface): Long? {
    val date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME) ?: return null
    val offset = exif.getAttribute("OffsetTimeOriginal")
    val format = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
        if (offset != null) timeZone = TimeZone.getTimeZone("GMT$offset")
    }
    return runCatching { format.parse(date)?.time }.getOrNull()
}
