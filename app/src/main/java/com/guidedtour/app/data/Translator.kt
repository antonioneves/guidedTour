package com.guidedtour.app.data

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/** On-device translation. Language models (about 30 MB each) download on first use. */
object Translator {
    sealed interface Result {
        data class Success(val text: String) : Result
        data class Failure(val message: String) : Result
    }

    /** True if [tag] is a language ML Kit can translate and differs from [target]. */
    fun needsTranslation(tag: String?, target: String = Locale.getDefault().language): Boolean {
        val source = tag?.let(::languageOf) ?: return false
        return source != target && TranslateLanguage.fromLanguageTag(source) != null
    }

    private fun languageOf(tag: String) = Locale.forLanguageTag(tag).language

    suspend fun translate(text: String, sourceTag: String?, target: String = Locale.getDefault().language): Result {
        val source = sourceTag?.let { TranslateLanguage.fromLanguageTag(languageOf(it)) }
            ?: return Result.Failure("Couldn't tell which language this text is in.")
        val targetCode = TranslateLanguage.fromLanguageTag(target)
            ?: return Result.Failure("Translation into your language isn't supported.")
        if (source == targetCode) return Result.Success(text)

        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(targetCode).build()
        )
        return try {
            suspendCancellableCoroutine { cont ->
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .addOnSuccessListener {
                        translator.translate(text)
                            .addOnSuccessListener { cont.resume(Result.Success(it)) }
                            .addOnFailureListener { cont.resume(Result.Failure("Translation failed.")) }
                    }
                    .addOnFailureListener {
                        cont.resume(Result.Failure("Couldn't download the language pack. Check your connection."))
                    }
            }
        } finally {
            translator.close()
        }
    }
}
