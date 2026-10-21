package com.guidedtour.app.data

import android.content.Context
import android.content.res.AssetManager
import android.util.LruCache
import java.io.InputStream
import java.text.Normalizer
import java.util.zip.GZIPInputStream

/**
 * A language's common words and how common each is, from wordfreq (see tools/make_wordlists.py).
 * [codes] are 6 × log10(frequency × 10⁹): 0 is one word in a billion, 54 is every word.
 */
class WordList(private val words: Array<String>, private val codes: ByteArray) {
    /** The letters the language's words are written with, to try in place of a misread one. */
    val letters: CharArray = run {
        val counts = HashMap<Char, Int>()
        words.forEach { word -> word.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        counts.filterValues { it >= 20 }.keys.sorted().toCharArray()
    }

    /** How common [word] (lowercase) is, or -1 if it isn't on the list. */
    fun frequency(word: String): Int {
        val i = words.binarySearch(word)
        return if (i >= 0) codes[i].toInt() else -1
    }

    companion object {
        /** Reads a list written by tools/make_wordlists.py. */
        fun read(input: InputStream): WordList {
            val data = GZIPInputStream(input, 1 shl 16).readBytes().toString(Charsets.UTF_8)
            val count = data.count { it == '\n' } + 1
            val words = arrayOfNulls<String>(count)
            val codes = ByteArray(count)
            val word = StringBuilder()
            var n = 0
            var start = 0
            while (start < data.length) {
                val end = data.indexOf('\n', start).let { if (it < 0) data.length else it }
                if (end - start >= 2) {
                    word.setLength(minOf(data[start] - '0', word.length))
                    word.append(data, start + 2, end)
                    codes[n] = (data[start + 1] - '0').toByte()
                    words[n++] = word.toString()
                }
                start = end + 1
            }
            @Suppress("UNCHECKED_CAST")
            return WordList((words.copyOf(n) as Array<String>), codes.copyOf(n))
        }
    }
}

/** The word lists bundled with the app, loaded when a language first needs one. */
class WordLists private constructor(private val assets: AssetManager) {
    companion object {
        @Volatile private var instance: WordLists? = null
        fun get(context: Context): WordLists =
            instance ?: synchronized(this) { instance ?: WordLists(context.applicationContext.assets).also { instance = it } }
    }

    private val available: Set<String> by lazy { assets.list("words").orEmpty().map { it.removeSuffix(".words") }.toSet() }

    // A list takes a few MB of memory; a plaque rarely has more than two languages.
    private val loaded = LruCache<String, WordList>(3)

    /** The list for language [tag] (as language identification names it), or null if there's none. */
    @Synchronized
    fun of(tag: String): WordList? {
        val name = listName(tag)?.takeIf { it in available } ?: return null
        return loaded.get(name) ?: runCatching { assets.open("words/$name.words").use(WordList::read) }.getOrNull()
            ?.also { loaded.put(name, it) }
    }

    /** [text] with words OCR probably misread replaced by the likeliest real word, each paragraph in its own language. */
    fun correct(text: String, spans: List<LanguageSpan>, unsure: Set<String>): String =
        correctWords(text, spans, unsure, ::of)
}

/** The list that covers language [tag]: Norwegian is Bokmål, Croatian, Bosnian and Serbian share one. */
fun listName(tag: String): String? = when (val language = tag.substringBefore('-').lowercase()) {
    "", "und" -> null
    "no", "nb", "nn" -> "nb"
    "hr", "bs", "sr", "sh" -> "sh"
    "tl", "fil" -> "fil"
    else -> language
}

/** How sure ML Kit must be of a word to trust it even when it isn't on the language's list. */
const val SURE_CONFIDENCE = 0.8f

/** Only words at least this common replace a misread one (one in ten million words). */
private const val MIN_SCORE = 12

/**
 * A word on the list loses its accents to a far more common accented form: OCR often drops an accent,
 * and the unaccented "construido" is on the list only because people type it that way.
 */
private const val ACCENT_GAIN = 6

/** An edit that isn't a typical OCR slip costs this much commonness (two orders of magnitude). */
private const val EDIT_COST = 12

private val TOKEN = Regex("""[\p{L}\p{M}0-9|]+""")

/** Characters OCR mistakes for each other; swapping them is nearly free. */
private val LOOKALIKES = listOf("0o", "1l", "1i", "|l", "|i", "li", "5s", "8b", "2z", "ce", "eo", "co", "uv", "ft", "hb", "rn")
    .flatMap { listOf(it[0] to it[1], it[1] to it[0]) }.toSet()

/** Letter groups OCR reads as one letter, or one letter as two. */
private val MERGES = listOf("rn" to "m", "cl" to "d", "vv" to "w", "ri" to "n", "in" to "m", "nn" to "m", "li" to "h", "ii" to "u")
    .flatMap { listOf(it, it.second to it.first) }

/**
 * [text] with each word that isn't on its paragraph's word list, and that OCR wasn't sure of (in
 * [unsure], lowercase) or that has a digit in it, replaced by the likeliest common word one slip away.
 * Short words, words that appear twice and names (capitalised inside a sentence) are left alone.
 */
fun correctWords(text: String, spans: List<LanguageSpan>, unsure: Set<String>, lists: (String) -> WordList?): String {
    if (spans.isEmpty()) return text
    val counts = TOKEN.findAll(text).groupingBy { it.value.lowercase() }.eachCount()
    return pieces(text, spans).joinToString("") { (tag, piece) ->
        val list = lists(tag) ?: return@joinToString piece
        TOKEN.replace(piece) { match -> corrected(match.value, piece, match.range.first, list, unsure, counts) ?: match.value }
    }
}

private fun corrected(token: String, piece: String, start: Int, list: WordList, unsure: Set<String>, counts: Map<String, Int>): String? {
    if (token.length < 3 || token.count { it.isLetter() } * 2 < token.length) return null
    val lower = token.lowercase()
    if ((counts[lower] ?: 0) > 1) return null
    val digits = token.any { it.isDigit() || it == '|' }
    if (!digits && lower !in unsure) return null
    val name = token[0].isUpperCase() && token.drop(1).any { it.isLowerCase() }
    if (name && !digits && !startsSentence(piece, start)) return null
    val known = list.frequency(lower)
    val best = if (known >= 0) {
        accented(lower, list).maxByOrNull { it.second }?.takeIf { it.second >= known + ACCENT_GAIN }
    } else {
        candidates(lower, list).maxByOrNull { it.second }?.takeIf { it.second >= MIN_SCORE }
    } ?: return null
    return when {
        token.filter { it.isLetter() }.all { it.isUpperCase() } && token.count { it.isLetter() } > 1 -> best.first.uppercase()
        token[0].isUpperCase() -> best.first.replaceFirstChar { it.uppercaseChar() }
        else -> best.first
    }
}

/** Nothing but spaces since a full stop, a line break or the start: a capital here needn't mean a name. */
private fun startsSentence(piece: String, start: Int): Boolean {
    val before = piece.substring(0, start).trimEnd(' ', '\t', '"', '“', '«', '(', '‘', '\'')
    return before.isEmpty() || before.last() in ".!?:\n"
}

/** Real words one slip from [word], each with how common it is less what the slip costs. */
private fun candidates(word: String, list: WordList): List<Pair<String, Int>> {
    val found = HashMap<String, Int>()
    fun consider(candidate: String, cost: Int) {
        if (candidate.isEmpty() || candidate == word) return
        val frequency = list.frequency(candidate)
        if (frequency < 0) return
        val score = frequency - cost
        if (score > (found[candidate] ?: Int.MIN_VALUE)) found[candidate] = score
    }
    for (i in word.indices) {
        val c = word[i]
        val head = word.substring(0, i)
        val tail = word.substring(i + 1)
        consider(head + tail, if (!c.isLetter()) 0 else EDIT_COST)
        for (letter in list.letters) {
            if (letter == c) continue
            val alike = (c to letter) in LOOKALIKES || base(c) == base(letter)
            consider(head + letter + tail, if (alike) 0 else EDIT_COST)
        }
    }
    for (i in 0..word.length) for (letter in list.letters) {
        consider(word.substring(0, i) + letter + word.substring(i), EDIT_COST)
    }
    for ((from, to) in MERGES) {
        var i = word.indexOf(from)
        while (i >= 0) {
            consider(word.substring(0, i) + to + word.substring(i + from.length), 0)
            i = word.indexOf(from, i + 1)
        }
    }
    return found.toList()
}

/** Words on the list that are [word] with one letter's accent added or changed, with how common each is. */
private fun accented(word: String, list: WordList): List<Pair<String, Int>> = word.indices.flatMap { i ->
    list.letters.filter { it != word[i] && base(it) == base(word[i]) }.mapNotNull { letter ->
        val candidate = word.substring(0, i) + letter + word.substring(i + 1)
        list.frequency(candidate).takeIf { it >= 0 }?.let { candidate to it }
    }
}

/** The letter without its accent: "é" is "e". */
private fun base(c: Char): Char = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)[0]
