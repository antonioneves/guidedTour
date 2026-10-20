package com.guidedtour.app.data

import com.guidedtour.app.BuildConfig
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional second opinion from Google Cloud Vision for plaques the on-device OCR struggles with.
 * It uploads the photo, so it only runs when the user asks for it on a specific scan, and it is
 * available only if a `VISION_API_KEY` was supplied in local.properties at build time.
 */
object CloudOcr {
    val available: Boolean get() = BuildConfig.VISION_API_KEY.isNotBlank()

    sealed interface Result {
        data class Text(val text: String) : Result
        data class Failure(val message: String) : Result
    }

    suspend fun read(photo: File): Result = withContext(Dispatchers.IO) {
        if (!available) return@withContext Result.Failure("Cloud reading isn't set up in this build.")
        runCatching {
            val image = Base64.encodeToString(photo.readBytes(), Base64.NO_WRAP)
            val body = JSONObject().put("requests", JSONArray().put(JSONObject()
                .put("image", JSONObject().put("content", image))
                .put("features", JSONArray().put(JSONObject().put("type", "DOCUMENT_TEXT_DETECTION")))
            ))
            val connection = URL("https://vision.googleapis.com/v1/images:annotate?key=${BuildConfig.VISION_API_KEY}")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                if (connection.responseCode != 200) return@runCatching Result.Failure("Cloud reading failed (${connection.responseCode}).")
                val response = JSONObject(connection.inputStream.bufferedReader().readText())
                val text = response.getJSONArray("responses").getJSONObject(0)
                    .optJSONObject("fullTextAnnotation")?.optString("text").orEmpty()
                if (text.isBlank()) Result.Failure("The cloud service found no text.")
                else Result.Text(fixConfusables(cleanParagraphs(text)))
            } finally {
                connection.disconnect()
            }
        }.getOrElse { Result.Failure("Couldn't reach the cloud service. Check your connection.") }
    }

    /** Vision returns one line per row; rejoin lines, keeping blank-line paragraph breaks. */
    private fun cleanParagraphs(raw: String): String =
        raw.split(Regex("\n\\s*\n")).map { joinLines(it.split('\n')) }.filter { it.isNotBlank() }.joinToString("\n\n")
}
