package com.guidedtour.app.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The album saved photos go to, under Pictures. */
private const val ALBUM = "Guided Tour"

/** Whether saving to the gallery needs the storage permission first: only before Android 10. */
val savingNeedsPermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

/** Copies the photo at [path] (a JPEG in the app's private storage) into the phone's gallery; true once it's there. */
suspend fun saveToGallery(context: Context, path: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val source = File(path)
        val name = "GuidedTour_${source.nameWithoutExtension}.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            try {
                resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } ?: error("can't write $uri")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            true
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM).apply { mkdirs() }
            val target = File(dir, name)
            source.copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("image/jpeg"), null)
            true
        }
    }.onFailure { android.util.Log.w("GuidedTour", "gallery: can't save $path", it) }.getOrDefault(false)
}
