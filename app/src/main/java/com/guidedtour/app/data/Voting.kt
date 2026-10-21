package com.guidedtour.app.data

/**
 * Several readings of the same text (other photos of the burst, the live preview while aiming) vote
 * on each word of the [primary] reading: OCR's slips are mostly random, so where most readings agree
 * on another word than the primary one, that word is likelier right. Each other reading is lined up
 * word by word with the primary one; only words it reads differently in the same place vote (words it
 * adds or misses don't). The primary reading counts [primaryWeight], each other reading its weight.
 * A word only changes for one with more votes, or as many if it's a real word and the primary one
 * isn't, and never from a real word into a non-word ([isWord] takes a word in lowercase, without
 * punctuation).
 */
fun vote(primary: String, others: List<Pair<String, Float>>, primaryWeight: Float, isWord: (String) -> Boolean): String {
    if (others.isEmpty()) return primary
    val tokens = TOKENS.findAll(primary).toList()
    if (tokens.isEmpty()) return primary
    val words = tokens.map { normal(it.value) }
    // For each word of the primary reading: the forms read there, with their votes and as written.
    val ballots = words.map { mutableMapOf(it to primaryWeight) }
    val written = words.map { mutableMapOf<String, String>() }
    for ((reading, weight) in others) {
        val theirs = TOKENS.findAll(reading).map { it.value }.toList()
        if (theirs.isEmpty()) continue
        for ((i, j) in align(words, theirs.map(::normal))) {
            val form = normal(theirs[j])
            if (form.isEmpty()) continue
            ballots[i][form] = (ballots[i][form] ?: 0f) + weight
            written[i].putIfAbsent(form, theirs[j])
        }
    }
    val out = StringBuilder(primary)
    // Right to left, so replacing a word doesn't move the ones still to do.
    for (i in tokens.indices.reversed()) {
        val own = ballots[i][words[i]] ?: 0f
        // The most votes; among equals, a real word.
        val (winner, votes) = ballots[i].entries.maxWithOrNull(compareBy({ it.value }, { isWord(it.key) }))?.toPair() ?: continue
        if (winner == words[i]) continue
        val tieForAWord = votes == own && isWord(winner) && !isWord(words[i])
        if (votes < own || (votes == own && !tieForAWord)) continue
        if (isWord(words[i]) && !isWord(winner)) continue
        val replacement = written[i][winner] ?: continue
        out.replace(tokens[i].range.first, tokens[i].range.last + 1, replacement)
    }
    return out.toString()
}

private val TOKENS = Regex("""\S+""")

/** A word as compared between readings: lowercase, without the punctuation around it. */
private fun normal(token: String) = token.trim { !it.isLetterOrDigit() }.lowercase()

/**
 * The pairs (index in [a], index in [b]) of words lined up by the fewest edits (words kept, changed,
 * added or dropped). Only kept and changed words pair up; a changed word pairs only if it's close
 * enough to be a misreading of the other (at most half its letters differ).
 */
internal fun align(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
    val n = a.size
    val m = b.size
    val cost = Array(n + 1) { IntArray(m + 1) }
    for (i in 0..n) cost[i][0] = i
    for (j in 0..m) cost[0][j] = j
    for (i in 1..n) for (j in 1..m) {
        val change = if (a[i - 1] == b[j - 1]) 0 else 1
        cost[i][j] = minOf(cost[i - 1][j - 1] + change, cost[i - 1][j] + 1, cost[i][j - 1] + 1)
    }
    val pairs = mutableListOf<Pair<Int, Int>>()
    var i = n
    var j = m
    while (i > 0 && j > 0) {
        val change = if (a[i - 1] == b[j - 1]) 0 else 1
        when {
            cost[i][j] == cost[i - 1][j - 1] + change -> {
                if (change == 0 || similar(a[i - 1], b[j - 1])) pairs += (i - 1) to (j - 1)
                i--; j--
            }
            cost[i][j] == cost[i - 1][j] + 1 -> i--
            else -> j--
        }
    }
    return pairs.reversed()
}

/** At most half of the longer word's letters need changing to turn one into the other. */
private fun similar(a: String, b: String): Boolean = letterEdits(a, b) * 2 <= maxOf(a.length, b.length)

private fun letterEdits(a: String, b: String): Int {
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1)
        current[0] = i
        for (j in 1..b.length) {
            current[j] = minOf(previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1, previous[j] + 1, current[j - 1] + 1)
        }
        previous = current
    }
    return previous[b.length]
}

/**
 * [reading]'s text agreed with its [alternatives][Reading.alternatives] (its unsure lines read again,
 * counting as much) and [others] (other readings with their weights), in [reading]'s main language
 * as [lists] know it.
 */
suspend fun agreed(reading: Reading, others: List<Pair<String, Float>>, lists: WordLists, primaryWeight: Float = 1f): String {
    val all = reading.alternatives.map { it to primaryWeight } + others
    if (all.isEmpty() || reading.text.isBlank()) return reading.text
    val list = mainLanguage(reading.text, Recognizer.detectLanguages(reading.text))?.let(lists::of)
    return vote(reading.text, all, primaryWeight) { word -> list != null && list.frequency(word) >= 0 }
}
