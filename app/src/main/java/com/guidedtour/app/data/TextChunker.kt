package com.guidedtour.app.data

import java.text.BreakIterator
import java.util.Locale

/** A speakable piece of a longer text; [start] is its offset in the original string. */
data class Chunk(val start: Int, val text: String)

/**
 * Splits [text] into sentences, none longer than [maxLen], so long plaques stay under the speech
 * engine's input limit and can be paused or resumed at sentence boundaries.
 */
fun splitForSpeech(text: String, maxLen: Int, locale: Locale = Locale.getDefault()): List<Chunk> {
    require(maxLen > 0)
    val chunks = mutableListOf<Chunk>()
    val iterator = BreakIterator.getSentenceInstance(locale).apply { setText(text) }
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        addSentence(text, start, end, maxLen, chunks)
        start = end
        end = iterator.next()
    }
    return chunks
}

private fun addSentence(text: String, from: Int, to: Int, maxLen: Int, out: MutableList<Chunk>) {
    var start = from
    var end = to
    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--
    while (end - start > maxLen) {
        var cut = text.lastIndexOf(' ', start + maxLen).takeIf { it > start } ?: (start + maxLen)
        if (cut - start > maxLen) cut = start + maxLen
        out += Chunk(start, text.substring(start, cut).trim())
        start = cut
        while (start < end && text[start].isWhitespace()) start++
    }
    if (end > start) out += Chunk(start, text.substring(start, end))
}
