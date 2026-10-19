package com.guidedtour.app.data

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Text read from a still photo plus the (upright) photo itself. */
class PhotoResult(val text: String, val bitmap: Bitmap?)

/** On-device OCR in several scripts, plus language detection so speech uses the right voice. */
object Recognizer {
    private val clients = HashMap<OcrScript, TextRecognizer>()
    private val languageId by lazy { LanguageIdentification.getClient() }

    @Synchronized
    fun client(script: OcrScript): TextRecognizer = clients.getOrPut(if (script == OcrScript.Auto) OcrScript.Latin else script) {
        when (script) {
            OcrScript.Auto, OcrScript.Latin -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrScript.Chinese -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            OcrScript.Japanese -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            OcrScript.Korean -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            OcrScript.Devanagari -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        }
    }

    /** Runs OCR on a full-resolution still photo. Always closes [image]. */
    suspend fun readPhoto(image: ImageProxy, script: OcrScript): PhotoResult {
        val bitmap = try {
            withContext(Dispatchers.Default) {
                val raw = image.toBitmap()
                val degrees = image.imageInfo.rotationDegrees
                if (degrees == 0) raw
                else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
            }
        } finally {
            image.close()
        }
        val input = InputImage.fromBitmap(bitmap, 0)
        val text = if (script != OcrScript.Auto) {
            recognize(client(script), input)
        } else {
            // Latin first; only if it finds next to nothing do we try the other scripts.
            val latin = recognize(client(OcrScript.Latin), input)
            if (latin.count { it.isLetterOrDigit() } >= 12) latin
            else (listOf(latin) + OcrScript.entries.filter { it != OcrScript.Auto && it != OcrScript.Latin }
                .map { recognize(client(it), input) }).maxBy { it.length }
        }
        return PhotoResult(text, bitmap)
    }

    private suspend fun recognize(recognizer: TextRecognizer, input: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(input)
                .addOnSuccessListener { cont.resume(cleanOcr(it.text)) }
                .addOnFailureListener { cont.resume("") }
        }

    suspend fun detectLanguage(text: String): String? = suspendCancellableCoroutine { cont ->
        languageId.identifyLanguage(text)
            .addOnSuccessListener { cont.resume(it.takeIf { tag -> tag != "und" }) }
            .addOnFailureListener { cont.resume(null) }
    }
}
