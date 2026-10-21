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

    /** The language to translate into: the chosen [setting], or the phone's language when it is empty. */
    fun target(setting: String): String = setting.ifEmpty { Locale.getDefault().language }

    /** Every language ML Kit can translate into, as language codes sorted by their name. */
    val languages: List<String> by lazy { TranslateLanguage.getAllLanguages().sortedBy(::nameOf) }

    /** A language code's name in the phone's language, e.g. "en" -> "English". */
    fun nameOf(code: String): String =
        Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }

    /** True if [tag] is a language ML Kit can translate and differs from [target]. */
    fun needsTranslation(tag: String?, target: String = Locale.getDefault().language): Boolean {
        val source = tag?.let(::languageOf) ?: return false
        return source != target && TranslateLanguage.fromLanguageTag(source) != null
    }

    /** True if any of [tags] (a text's languages) needs translating into [target]. */
    fun needsTranslation(tags: List<String>, target: String): Boolean = tags.any { needsTranslation(it, target) }

    private fun languageOf(tag: String) = Locale.forLanguageTag(tag).language

    /**
     * Translates a text that may mix languages: each span from its own language, leaving parts
     * already in [target] as they are, and keeping the blank lines between parts.
     */
    suspend fun translate(text: String, spans: List<LanguageSpan>, target: String): Result {
        if (spans.size <= 1) return translate(text, spans.firstOrNull()?.tag, target)
        val out = StringBuilder()
        for ((tag, piece) in pieces(text, spans)) {
            val body = piece.trimEnd()
            if (languageOf(tag) == target) {
                out.append(body)
            } else {
                when (val result = translate(body, tag, target)) {
                    is Result.Success -> out.append(result.text)
                    is Result.Failure -> return result
                }
            }
            out.append(piece.substring(body.length))
        }
        return Result.Success(out.toString())
    }

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
