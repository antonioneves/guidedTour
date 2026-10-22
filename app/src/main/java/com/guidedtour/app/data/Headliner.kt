package com.guidedtour.app.data

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * A short headline naming a guide's topic ("The Parthenon's frieze"), for its card, written by the
 * phone's on-device AI (Gemini Nano, through Android's AICore) where it has it. The model lives in
 * AICore, not in the app. Null where the phone can't, or the answer doesn't look like a headline.
 */
object Headliner {
    /** Only the start of a long text is read: the topic is almost always named there. */
    private const val MAX_CHARS = 2_000

    /** A headline is given up after this long. */
    private const val TIMEOUT_MS = 10_000L

    private val model by lazy { Generation.getClient() }

    suspend fun available(): Boolean = runCatching { model.checkStatus() == FeatureStatus.AVAILABLE }.getOrDefault(false)

    /** [text]'s headline, in the language [tag] (the text's own); null if the phone can't write one. */
    suspend fun headline(text: String, tag: String?): String? {
        if (text.isBlank() || !available()) return null
        val language = tag?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH) }?.takeIf { it.isNotBlank() }
        val prompt = "Write a headline of at most 6 words naming the topic of this text from a museum label or sign" +
            (language?.let { ", in $it" } ?: ", in the text's language") +
            ". Reply with the headline only.\n\nText:\n" + text.take(MAX_CHARS)
        val answer = withTimeoutOrNull(TIMEOUT_MS) {
            runCatching {
                model.generateContent(generateContentRequest(TextPart(prompt)) {
                    temperature = 0.2f
                    topK = 10
                    maxOutputTokens = 24
                }).candidates.firstOrNull()?.text
            }.onFailure { android.util.Log.w("GuidedTour", "headline: failed", it) }.getOrNull()
        } ?: return null
        return cleanHeadline(answer)
    }
}

/** [answer] as a headline: one line, without quotes, markup, a "Headline:" label or a final full stop; null if it isn't one. */
internal fun cleanHeadline(answer: String): String? {
    val line = answer.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
    val marks = "#*_\"'“”‘’«»`"
    val clean = line.trim { it.isWhitespace() || it in marks }
        .replace(Regex("""^(headline|title)\W*:\W*""", RegexOption.IGNORE_CASE), "")
        .trim { it.isWhitespace() || it in marks }
        .removeSuffix(".")
        .trim()
    return clean.takeIf { it.isNotEmpty() && it.length <= MAX_HEADLINE && it.any(Char::isLetter) }
}

/** Longer answers aren't headlines. */
private const val MAX_HEADLINE = 70

/**
 * The title printed on the plaque, as a headline where the phone has no AI to write one: a short
 * first paragraph (OCR reads a heading as its own block) with more text after it, that doesn't end
 * like a sentence. Written in capitals, it's turned into title case. Null if the text has none.
 */
fun printedTitle(text: String): String? {
    val paragraphs = paragraphsOf(text)
    if (paragraphs.size < 2) return null
    val first = paragraphs.first().replace(Regex("""\s+"""), " ").trim().trimEnd(':', '-', '–', '—').trim()
    val words = first.split(' ')
    if (first.length > MAX_TITLE || words.size > MAX_TITLE_WORDS || first.count(Char::isLetter) < 3) return null
    if (first.last() in ".,;") return null
    val letters = first.filter(Char::isLetter)
    if (letters.isNotEmpty() && letters.all(Char::isUpperCase)) {
        return words.mapIndexed { i, word ->
            val lower = word.lowercase()
            if (i > 0 && lower.count(Char::isLetter) <= 3) lower else lower.replaceFirstChar { it.titlecase() }
        }.joinToString(" ")
    }
    return first
}

/** A printed title is at most this long, and this many words. */
private const val MAX_TITLE = 60
private const val MAX_TITLE_WORDS = 9
