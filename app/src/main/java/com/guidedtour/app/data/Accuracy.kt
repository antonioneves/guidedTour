package com.guidedtour.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.min

/**
 * Developer tool: measures OCR accuracy on your own photos. Put `name.jpg` (or .png) and `name.txt`
 * (the true text) pairs in the app's `testset` folder, e.g.
 * `adb push plaque1.jpg plaque1.txt /sdcard/Android/data/com.guidedtour.app/files/testset/`.
 * An optional `name.lang` lists the languages expected in reading order (e.g. "pt,en"), to check detection.
 */
object Accuracy {
    fun folder(context: Context): File = File(context.getExternalFilesDir(null), "testset").apply { mkdirs() }

    suspend fun run(context: Context, script: OcrScript, dir: File = folder(context)): String = withContext(Dispatchers.Default) {
        val images = dir.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") && File(dir, f.nameWithoutExtension + ".txt").exists() }
            .orEmpty().sortedBy { it.name }
        if (images.isEmpty()) return@withContext "No test photos found.\nAdd name.jpg + name.txt pairs to:\n${dir.path}"

        val report = StringBuilder()
        var basicTotal = 0.0
        var improvedTotal = 0.0
        var correctedTotal = 0.0
        val lists = WordLists.get(context)
        var languagesChecked = 0
        var languagesRight = 0
        for (file in images) {
            val truth = File(dir, file.nameWithoutExtension + ".txt").readText()
            val bitmap = decode(file) ?: continue
            val basic = characterErrorRate(truth, Recognizer.read(bitmap, script, enhance = false).text)
            val reading = Recognizer.read(bitmap, script, enhance = true)
            val improved = characterErrorRate(truth, reading.text)
            val spans = Recognizer.detectLanguages(reading.text)
            val fixed = lists.correct(reading.text, spans, reading.unsure)
            val corrected = characterErrorRate(truth, fixed)
            basicTotal += basic
            improvedTotal += improved
            correctedTotal += corrected
            report.appendLine("%-24s basic %5.1f%%   improved %5.1f%%   corrected %5.1f%%".format(Locale.US, file.name.take(24), basic * 100, improved * 100, corrected * 100))
            changedWords(reading.text, fixed).forEach { report.appendLine("    $it") }
            // What was read, when it went wrong, to see how.
            if (corrected > 0.02) report.appendLine("    read: " + fixed.replace(Regex("\\s+"), " ").take(1200))
            File(dir, file.nameWithoutExtension + ".lang").takeIf { it.exists() }?.let { expectedFile ->
                val expected = expectedFile.readText().trim().split(',').map { it.trim() }
                val found = spans.map { it.tag }
                languagesChecked++
                if (found == expected) languagesRight++
                report.appendLine("%-24s languages %s (expected %s)%s".format(Locale.US, "", found.joinToString("+").ifEmpty { "unknown" }, expected.joinToString("+"), if (found == expected) "" else "  wrong"))
            }
        }
        val n = images.size
        report.appendLine()
        report.appendLine("%d photos. Average character error: basic %.1f%%, improved %.1f%%, corrected %.1f%%".format(Locale.US, n, basicTotal / n * 100, improvedTotal / n * 100, correctedTotal / n * 100))
        if (languagesChecked > 0) report.appendLine("Languages detected right: %d of %d".format(Locale.US, languagesRight, languagesChecked))
        report.toString().also { runCatching { File(dir, "report.txt").writeText(it) } }
    }

    /** The words correction changed, as "misread → corrected". */
    private fun changedWords(before: String, after: String): List<String> {
        val a = before.split(Regex("\\s+"))
        val b = after.split(Regex("\\s+"))
        return if (a.size != b.size) emptyList() else a.indices.filter { a[it] != b[it] }.map { "${a[it]} → ${b[it]}" }
    }

    /** The photo upright, as the camera would have given it (phone photos are often stored sideways with an EXIF tag). */
    private fun decode(file: File) = BitmapFactory.decodeFile(file.path)?.let { raw ->
        val turn = when (runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0) }.getOrDefault(0)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = min(1f, Recognizer.FULL_SIDE.toFloat() / maxOf(raw.width, raw.height))
        if (turn == 0f && scale == 1f) raw
        else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(turn); postScale(scale, scale) }, true)
    }
}
