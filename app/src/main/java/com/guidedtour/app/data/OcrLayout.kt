package com.guidedtour.app.data

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * One recognised line with its upright pixel box, ML Kit's confidence, its tilt in degrees, its words
 * with their confidence, and its [corners] as the text reads (x, y of the top-left, top-right,
 * bottom-right and bottom-left), which follow the line when it slants, and where each word is.
 */
data class OcrLine(
    val text: String,
    val left: Int, val top: Int, val right: Int, val bottom: Int,
    val confidence: Float = 1f,
    val angle: Float = 0f,
    val words: List<Pair<String, Float>> = emptyList(),
    val corners: List<Float> = emptyList(),
    /** Each of [words]' left and right edge, when known. */
    val wordBoxes: List<Pair<Int, Int>> = emptyList(),
) {
    /** The upright box's height: for a slanted line, more than its letters. */
    val height: Int get() = bottom - top

    /** How tall the letters are, measured across the line even when it slants. */
    val size: Int get() = if (corners.size == 8) ((side(0, 3) + side(1, 2)) / 2).toInt() else height

    /** How long the line is, along its slant. */
    val length: Float get() = if (corners.size == 8) (side(0, 1) + side(3, 2)) / 2 else (right - left).toFloat()

    private fun side(a: Int, b: Int) = hypot(corners[2 * b] - corners[2 * a], corners[2 * b + 1] - corners[2 * a + 1])
}

/** A block of lines, roughly a paragraph. */
data class OcrBlock(val lines: List<OcrLine>) {
    val left: Int get() = lines.minOf { it.left }
    val top: Int get() = lines.minOf { it.top }
    val right: Int get() = lines.maxOf { it.right }
    val bottom: Int get() = lines.maxOf { it.bottom }
    val width: Int get() = right - left
    val medianLineHeight: Int get() = lines.map { it.size }.sorted()[lines.size / 2]
    val text: String get() = joinLines(lines.map { it.text })
}

/** Joins OCR text into a single speakable paragraph. */
fun cleanOcr(raw: String): String = joinLines(raw.split('\n'))

/**
 * Joins lines with spaces. A trailing hyphen is dropped only when the next line continues the word
 * in lowercase ("tem-" + "ple"), so real hyphens ("Greek-" + "Roman") survive. No space is added
 * between Chinese, Japanese or Korean characters.
 */
fun joinLines(lines: List<String>): String {
    val sb = StringBuilder()
    for (raw in lines) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        if (sb.isEmpty()) { sb.append(line); continue }
        val last = sb[sb.length - 1]
        when {
            last == '-' && sb.length > 1 && sb[sb.length - 2].isLetter() && line.first().isLowerCase() -> {
                sb.setLength(sb.length - 1)
                sb.append(line)
            }
            isCjk(last) && isCjk(line.first()) -> sb.append(line)
            else -> sb.append(' ').append(line)
        }
    }
    return sb.toString()
}

private fun isCjk(c: Char): Boolean = when (Character.UnicodeScript.of(c.code)) {
    Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL -> true
    else -> false
}

/**
 * Reading order: top to bottom, but side-by-side columns are read one after the other, and a
 * full-width heading splits the page into bands that are read in turn.
 */
fun orderBlocks(blocks: List<OcrBlock>): List<OcrBlock> {
    if (blocks.size < 2) return blocks
    val pageLeft = blocks.minOf { it.left }
    val pageWidth = blocks.maxOf { it.right } - pageLeft
    val ordered = mutableListOf<OcrBlock>()
    var band = mutableListOf<OcrBlock>()
    fun flush() { ordered += orderColumns(band); band = mutableListOf() }
    for (block in blocks.sortedBy { it.top }) {
        if (block.width > 0.7 * pageWidth) { flush(); ordered += block } else band += block
    }
    flush()
    return ordered
}

private fun orderColumns(blocks: List<OcrBlock>): List<OcrBlock> {
    val columns = mutableListOf<MutableList<OcrBlock>>()
    for (block in blocks.sortedBy { it.left }) {
        val column = columns.firstOrNull { col ->
            val l = col.minOf { it.left }
            val r = col.maxOf { it.right }
            val overlap = min(block.right, r) - max(block.left, l)
            overlap > 0.5 * min(block.width, r - l)
        }
        if (column != null) column += block else columns += mutableListOf(block)
    }
    return columns.sortedBy { col -> col.minOf { it.left } }.flatMap { col -> col.sortedBy { it.top } }
}

/**
 * Keeps the text the camera was aimed at and drops pieces of neighbouring plaques. Blocks that lie
 * close together (gaps of a few line heights) form one group; each group scores its amount of text,
 * less if it is far from the middle of the photo or cut off at its edge. The best-scoring group that
 * reads about as clearly as the clearest one is the main text. Another group is dropped if it's cut
 * off by the photo's edge (the next plaque) or if it has little text in letters of another size (a
 * sign beside the plaque), and its blocks that read much less clearly than the main text go too (a
 * blurry facing page, tiny labels of a chart); the rest stay, as columns and sections of the same
 * page often lie far apart.
 * [width] and [height] are the photo's size.
 */
fun dropIntruders(blocks: List<OcrBlock>, width: Int, height: Int): List<OcrBlock> {
    if (blocks.size < 2 || width <= 0 || height <= 0) return blocks
    val groups = groupsOf(blocks)
    if (groups.size < 2) return blocks
    val scores = groups.map { groupScore(it, width, height) }
    val clarity = groups.map(::confidenceOf)
    val clearest = clarity.max()
    val main = groups.indices.filter { clarity[it] >= clearest - CLARITY_SPREAD }.maxBy { scores[it] }
    val size = textSize(groups[main])
    val kept = groups.indices.filter { i ->
        if (i == main) return@filter true
        val other = textSize(groups[i]).let { it < size * 0.7 || it > size / 0.7 }
        val stray = scores[i] < scores[main] * 0.2 && other
        !cutOff(groups[i], width, height) && !stray
    }
    // Outside the main text, blocks read much less clearly go, block by block: a clear title can sit
    // right beside a blurry facing page.
    val clear = clarity[main] - CLARITY_SPREAD
    return kept.flatMap { i -> if (i == main) groups[i] else groups[i].filter { confidenceOf(listOf(it)) >= clear } }
        .sortedBy { blocks.indexOf(it) }
}

/** Groups read less clearly than the clearest by more than this are doubtful (see [dropIntruders]). */
private const val CLARITY_SPREAD = 0.2

/** How sure ML Kit was of [group]'s text, weighted by how much text each line holds. */
private fun confidenceOf(group: List<OcrBlock>): Double = meanConfidence(group).toDouble()

/** The letter size most of [group]'s text is in. */
private fun textSize(group: List<OcrBlock>): Int = mainTextSize(group)

/** Every block of [group] touches the photo's edge: it goes on outside the photo. */
private fun cutOff(group: List<OcrBlock>, width: Int, height: Int): Boolean {
    val mx = width * 0.015
    val my = height * 0.015
    return group.all { it.left <= mx || it.right >= width - mx || it.top <= my || it.bottom >= height - my }
}

internal fun groupsOf(blocks: List<OcrBlock>): List<List<OcrBlock>> {
    val parent = IntArray(blocks.size) { it }
    fun root(i: Int): Int = if (parent[i] == i) i else root(parent[i]).also { parent[i] = it }
    for (i in blocks.indices) for (j in i + 1 until blocks.size) {
        if (near(blocks[i], blocks[j])) parent[root(i)] = root(j)
    }
    return blocks.indices.groupBy(::root).values.map { group -> group.map { blocks[it] } }
}

private fun near(a: OcrBlock, b: OcrBlock): Boolean {
    val line = max(a.medianLineHeight, b.medianLineHeight)
    val dx = max(0, max(a.left, b.left) - min(a.right, b.right))
    val dy = max(0, max(a.top, b.top) - min(a.bottom, b.bottom))
    // A plaque's parts stacked one above another can be further apart (a caution below the steps)
    // than a neighbouring plaque is beside it.
    return dx <= 2.5 * line && dy <= 5 * line
}

private fun groupScore(group: List<OcrBlock>, width: Int, height: Int): Double {
    val chars = group.sumOf { it.text.length }.toDouble()
    val cx = group.sumOf { (it.left + it.right) / 2.0 * it.text.length } / chars
    val cy = group.sumOf { (it.top + it.bottom) / 2.0 * it.text.length } / chars
    val distance = kotlin.math.hypot(cx - width / 2.0, cy - height / 2.0) / (0.5 * kotlin.math.hypot(width.toDouble(), height.toDouble()))
    return chars * (1 - 0.6 * min(1.0, distance)) * (if (cutOff(group, width, height)) 0.5 else 1.0)
}

/**
 * Drops small captions, credits and page numbers when there is clearly bigger main text. The main
 * text is where most of the letters are, not the biggest: a plaque's large title doesn't make its
 * one-line instructions captions.
 */
fun dropNoise(blocks: List<OcrBlock>): List<OcrBlock> {
    if (blocks.size < 2) return blocks
    val main = mainTextSize(blocks)
    val kept = blocks.filter { b ->
        val tiny = b.medianLineHeight < 0.6 * main && b.text.length < 80
        val pageNumber = b.text.trim().matches(Regex("""\d{1,4}"""))
        !tiny && !pageNumber
    }
    return kept.ifEmpty { blocks }
}

/** Fixes look-alike characters inside lowercase words: "l0ve" -> "love", "tab1e" -> "table". */
fun fixConfusables(text: String): String {
    val letters = text.count { it.isLetter() }
    if (letters == 0 || text.count { it.code < 128 && it.isLetter() } < letters / 2) return text
    return text
        .replace(Regex("""(?<=\p{Ll})0(?=\p{Ll})"""), "o")
        .replace(Regex("""(?<=\p{Ll})[1|](?=\p{Ll})"""), "l")
}

/** The letter size most of the text is written in: the median line size, counting each block by its letters. */
private fun mainTextSize(blocks: List<OcrBlock>): Int {
    val bySize = blocks.sortedBy { it.medianLineHeight }
    val half = bySize.sumOf { it.text.length } / 2.0
    var count = 0
    return bySize.first { count += it.text.length; count >= half }.medianLineHeight
}

/**
 * Ordered, de-noised text with one blank line between paragraphs. With the photo's [width] and
 * [height], text from neighbouring plaques is dropped too. Without [filter], every block is kept
 * (the user picked what to read).
 */
fun layoutText(blocks: List<OcrBlock>, width: Int = 0, height: Int = 0, filter: Boolean = true): String =
    orderBlocks(if (filter) dropNoise(dropIntruders(blocks, width, height)) else blocks)
        .map { fixConfusables(it.text) }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")

/** The words ML Kit was unsure of (lowercase, without punctuation), for correcting against a word list. */
fun unsureWords(blocks: List<OcrBlock>): Set<String> =
    blocks.flatMap { it.lines }.flatMap { it.words }.filter { it.second < SURE_CONFIDENCE }
        .map { it.first.trim { c -> !c.isLetterOrDigit() && c != '|' }.lowercase() }.filter { it.isNotEmpty() }.toSet()

/** Average confidence weighted by how much text each line holds. */
fun meanConfidence(blocks: List<OcrBlock>): Float {
    val lines = blocks.flatMap { it.lines }
    val chars = lines.sumOf { it.text.length }
    return if (chars == 0) 0f else (lines.sumOf { (it.confidence * it.text.length).toDouble() } / chars).toFloat()
}

/** Splits saved text into its paragraphs. */
fun paragraphsOf(text: String): List<String> = text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }

/** A text block in the camera preview, in the preview's pixels, with the preview's size. */
data class ViewArea(val left: Float, val top: Float, val right: Float, val bottom: Float, val viewWidth: Int, val viewHeight: Int)

/** Part of a photo, as fractions of its width and height. */
data class PhotoRegion(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlaps(other: PhotoRegion) = left < other.right && other.left < right && top < other.bottom && other.top < bottom
}

/**
 * Where [area] falls in a photo [width] × [height] that the preview showed filling and centred, its
 * overflow cropped (how the camera preview shows it), with [margin] of the area's size added on each
 * side for the phone moving between the preview frame and the photo. Null if the photo isn't the
 * preview's way round (the phone turned while the screen stayed upright), or the area is empty.
 */
fun photoRegion(area: ViewArea, width: Int, height: Int, margin: Float = 0.15f): PhotoRegion? {
    if (width <= 0 || height <= 0 || area.viewWidth <= 0 || area.viewHeight <= 0) return null
    if ((width > height) != (area.viewWidth > area.viewHeight)) return null
    if (area.right <= area.left || area.bottom <= area.top) return null
    val scale = max(area.viewWidth.toFloat() / width, area.viewHeight.toFloat() / height)
    fun x(v: Float) = ((v - area.viewWidth / 2f) / scale + width / 2f) / width
    fun y(v: Float) = ((v - area.viewHeight / 2f) / scale + height / 2f) / height
    val padX = (x(area.right) - x(area.left)) * margin
    val padY = (y(area.bottom) - y(area.top)) * margin
    return PhotoRegion(
        (x(area.left) - padX).coerceIn(0f, 1f), (y(area.top) - padY).coerceIn(0f, 1f),
        (x(area.right) + padX).coerceIn(0f, 1f), (y(area.bottom) + padY).coerceIn(0f, 1f),
    ).takeIf { it.right > it.left && it.bottom > it.top }
}
