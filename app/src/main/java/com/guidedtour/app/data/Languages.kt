package com.guidedtour.app.data

/**
 * A run of a text in one language, from [start] (a character offset) up to the next span's start.
 * Plaques are often bilingual, e.g. the local language and then English, so each run is read with
 * its own voice and translated from its own language.
 */
data class LanguageSpan(val start: Int, val tag: String)

/**
 * A language ML Kit confuses with a far more widely written one, and the countries where it's written.
 * A Portuguese plaque can come out as 86% Galician: written Galician and Portuguese can differ by an
 * accent. So outside those countries, or with no location, the text is taken as the wider language.
 */
data class CloseLanguage(val smaller: String, val wider: String, val countries: Set<String>)

val CLOSE_LANGUAGES = listOf(CloseLanguage("gl", "pt", setOf("ES"))).associateBy { it.smaller }

/** The identifier's least confidence (for a language, or close languages together) to name a language. */
const val LANGUAGE_MIN_CONFIDENCE = 0.5f

/**
 * Picks the language from the identifier's [candidates] (language, confidence), or null if unsure.
 * Close languages count together, so a split vote between Portuguese and Galician still names one;
 * the smaller one only where it's written ([country], the photo's country code, if known).
 */
fun pickLanguage(candidates: List<Pair<String, Float>>, country: String? = null): String? {
    val family = candidates.groupBy({ CLOSE_LANGUAGES[it.first]?.wider ?: it.first }, { it })
        .maxByOrNull { (_, members) -> members.sumOf { it.second.toDouble() } } ?: return null
    if (family.value.sumOf { it.second.toDouble() } < LANGUAGE_MIN_CONFIDENCE) return null
    val top = family.value.maxBy { it.second }.first
    val writtenHere = CLOSE_LANGUAGES[top]?.let { country != null && country.uppercase() in it.countries } ?: true
    return if (writtenHere) top else family.key
}

/** Paragraphs with fewer letters than this (a title, a date) are too short to tell their language. */
const val MIN_LETTERS_TO_DETECT = 20

/** Where each paragraph (separated by blank lines) starts and ends, without the whitespace around it. */
fun paragraphRanges(text: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var from = 0
    for (separator in Regex("\n\\s*\n").findAll(text) + sequenceOf(null)) {
        val to = separator?.range?.first ?: text.length
        var start = from
        var end = to
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        if (end > start) ranges += start until end
        from = separator?.range?.last?.plus(1) ?: text.length
    }
    return ranges
}

/**
 * The languages of [text], paragraph by paragraph, with [detect] telling the language of one paragraph
 * (null if unsure). A paragraph too short or unclear to tell takes the language of the paragraph after
 * it (a title belongs with the text below it), else of the one before. Neighbouring paragraphs in the
 * same language form one span; the first span starts at 0. Empty when no language could be told.
 */
suspend fun detectSpans(text: String, detect: suspend (String) -> String?): List<LanguageSpan> {
    val paragraphs = paragraphRanges(text)
    val found = paragraphs.map { range ->
        val paragraph = text.substring(range.first, range.last + 1)
        if (paragraph.count(Char::isLetter) >= MIN_LETTERS_TO_DETECT) detect(paragraph) else null
    }
    val filled = found.indices.map { i ->
        found[i] ?: found.drop(i + 1).firstOrNull { it != null } ?: found.take(i).lastOrNull { it != null }
    }
    val spans = mutableListOf<LanguageSpan>()
    paragraphs.forEachIndexed { i, range ->
        val tag = filled[i] ?: return@forEachIndexed
        if (spans.lastOrNull()?.tag != tag) spans += LanguageSpan(if (spans.isEmpty()) 0 else range.first, tag)
    }
    return spans
}

/** The language with the most text, which names the guide and is the default for anything unmarked. */
fun mainLanguage(text: String, spans: List<LanguageSpan>): String? =
    pieces(text, spans).groupBy({ it.first }, { it.second.length }).maxByOrNull { it.value.sum() }?.key

/** The span covering character [offset], if any. */
fun List<LanguageSpan>.at(offset: Int): LanguageSpan? = lastOrNull { it.start <= offset } ?: firstOrNull()

/** [text] cut into (language, piece) pairs, one per span; the pieces join back into [text]. */
fun pieces(text: String, spans: List<LanguageSpan>): List<Pair<String, String>> =
    spans.mapIndexed { i, span ->
        val end = spans.getOrNull(i + 1)?.start ?: text.length
        span.tag to text.substring(span.start.coerceAtMost(text.length), end.coerceIn(span.start, text.length))
    }

/** Stored as "0:pt;812:en"; null when there's a single language (the scan's languageTag says it all). */
fun encodeSpans(spans: List<LanguageSpan>): String? =
    spans.takeIf { it.size > 1 }?.joinToString(";") { "${it.start}:${it.tag}" }

fun decodeSpans(encoded: String?): List<LanguageSpan> =
    encoded.orEmpty().split(';').mapNotNull { part ->
        val (start, tag) = part.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        start.toIntOrNull()?.let { LanguageSpan(it, tag) }
    }
