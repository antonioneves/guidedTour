package com.guidedtour.app.data

import android.content.Context
import java.io.File

/**
 * Words the user typed when correcting a guide that no word list knows, mostly names: later scans
 * then know them, and mend misreadings of them instead of "correcting" them into something else.
 */
class PersonalWords private constructor(context: Context) {
    companion object {
        @Volatile private var instance: PersonalWords? = null
        fun get(context: Context): PersonalWords =
            instance ?: synchronized(this) { instance ?: PersonalWords(context.applicationContext).also { instance = it } }

        /** Enough for a long trip's names; the oldest go first. */
        private const val MAX_WORDS = 2_000
    }

    private val file = File(context.filesDir, "personal_words.txt")
    private var cache: List<String>? = null

    /** The words, lowercase. */
    @get:Synchronized
    val words: Set<String> get() = load().toSet()

    /** Adds the words of [after] that weren't in [before] and that [known] doesn't know. */
    @Synchronized
    fun learn(before: String, after: String, known: (String) -> Boolean) {
        val old = wordsOf(before)
        val new = wordsOf(after).filter { it !in old && it.length >= 3 && !known(it) }
        if (new.isEmpty()) return
        val all = (load() - new.toSet() + new).takeLast(MAX_WORDS)
        cache = all
        runCatching { file.writeText(all.joinToString("\n")) }
    }

    private fun load(): List<String> = cache ?: runCatching { file.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList()).also { cache = it }

    private fun wordsOf(text: String) = Regex("""[\p{L}\p{M}]+""").findAll(text).map { it.value.lowercase() }.toSet()
}
