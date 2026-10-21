package com.guidedtour.app.data

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

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
        var languagesChecked = 0
        var languagesRight = 0
        for (file in images) {
            val truth = File(dir, file.nameWithoutExtension + ".txt").readText()
            val bitmap = decode(file) ?: continue
            val basic = characterErrorRate(truth, Recognizer.read(bitmap, script, enhance = false).text)
            val reading = Recognizer.read(bitmap, script, enhance = true)
            val improved = characterErrorRate(truth, reading.text)
            basicTotal += basic
            improvedTotal += improved
            report.appendLine("%-24s basic %5.1f%%   improved %5.1f%%".format(Locale.US, file.name.take(24), basic * 100, improved * 100))
            File(dir, file.nameWithoutExtension + ".lang").takeIf { it.exists() }?.let { expectedFile ->
                val expected = expectedFile.readText().trim().split(',').map { it.trim() }
                val found = Recognizer.detectLanguages(reading.text).map { it.tag }
                languagesChecked++
                if (found == expected) languagesRight++
                report.appendLine("%-24s languages %s (expected %s)%s".format(Locale.US, "", found.joinToString("+").ifEmpty { "unknown" }, expected.joinToString("+"), if (found == expected) "" else "  wrong"))
            }
        }
        val n = images.size
        report.appendLine()
        report.appendLine("%d photos. Average character error: basic %.1f%%, improved %.1f%%".format(Locale.US, n, basicTotal / n * 100, improvedTotal / n * 100))
        if (languagesChecked > 0) report.appendLine("Languages detected right: %d of %d".format(Locale.US, languagesRight, languagesChecked))
        report.toString().also { runCatching { File(dir, "report.txt").writeText(it) } }
    }

    private fun decode(file: File) = BitmapFactory.decodeFile(file.path)?.let { raw ->
        val scale = 2600f / maxOf(raw.width, raw.height)
        if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true) else raw
    }
}
