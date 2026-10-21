package com.guidedtour.app.data

import kotlin.math.max
import kotlin.math.min

/**
 * The text in the camera's view as frames the user can see and pick: each column of a plaque, a
 * heading across columns, a note on the side. [suggested] frames are the ones read unless the user
 * says otherwise: the plaque aimed at (see [dropIntruders]), without its small captions (see
 * [dropNoise]).
 */
data class TextFrame(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val blocks: List<OcrBlock>,
    val suggested: Boolean,
) {
    val lines: List<OcrLine> get() = blocks.flatMap { it.lines }
    val letters: Int get() = blocks.sumOf { it.text.length }
    fun contains(x: Float, y: Float, pad: Float = 0f) = x >= left - pad && x <= right + pad && y >= top - pad && y <= bottom + pad
}

/** At most this many frames are shown; the ones with the least text go first. */
const val MAX_FRAMES = 6

/** The frames for [blocks] seen in a view [width] × [height]. */
fun textFrames(blocks: List<OcrBlock>, width: Int, height: Int): List<TextFrame> {
    val split = blocks.flatMap { splitMergedColumns(it) }
    if (split.isEmpty()) return emptyList()
    val aimed = dropNoise(dropIntruders(split, width, height)).toSet()
    return groupsOf(split).flatMap { group ->
        // Within a plaque, a frame per column (a heading across them gets its own), and the
        // suggested and skipped parts never share one.
        group.groupBy { it in aimed }.flatMap { (suggested, part) -> columnsOf(part).map { frameOf(it, suggested) } }
    }.sortedByDescending { it.letters }.take(MAX_FRAMES)
}

private fun frameOf(blocks: List<OcrBlock>, suggested: Boolean) = TextFrame(
    blocks.minOf { it.left }.toFloat(), blocks.minOf { it.top }.toFloat(),
    blocks.maxOf { it.right }.toFloat(), blocks.maxOf { it.bottom }.toFloat(),
    orderBlocks(blocks), suggested,
)

/**
 * [blocks] as columns: blocks that overlap side to side are one column. A block that spans two
 * columns that don't overlap each other (a heading over both) stands alone.
 */
internal fun columnsOf(blocks: List<OcrBlock>): List<List<OcrBlock>> {
    fun overlaps(a: OcrBlock, b: OcrBlock) = min(a.right, b.right) - max(a.left, b.left) > 0.3 * min(a.width, b.width)
    val spanning = blocks.filter { b ->
        val under = blocks.filter { it !== b && overlaps(it, b) }
        under.any { c1 -> under.any { c2 -> c1 !== c2 && !overlaps(c1, c2) } }
    }.toSet()
    val rest = blocks.filter { it !in spanning }
    val parent = IntArray(rest.size) { it }
    fun root(i: Int): Int = if (parent[i] == i) i else root(parent[i]).also { parent[i] = it }
    for (i in rest.indices) for (j in i + 1 until rest.size) if (overlaps(rest[i], rest[j])) parent[root(i)] = root(j)
    return rest.indices.groupBy(::root).values.map { it.map(rest::get) } + spanning.map { listOf(it) }
}

/**
 * [block] split where it runs across two columns: ML Kit sometimes reads two columns with a narrow
 * gutter as one block of long lines. A gutter is a gap between words, over a line tall, that at least
 * two lines have at the same place and no line's words cross.
 */
fun splitMergedColumns(block: OcrBlock, depth: Int = 0): List<OcrBlock> {
    val lines = block.lines
    if (depth > 2 || lines.size < 2 || lines.any { it.wordBoxes.size != it.words.size || it.words.isEmpty() }) return listOf(block)
    val size = block.medianLineHeight
    val gaps = lines.map { line ->
        line.wordBoxes.zipWithNext().filter { (a, b) -> b.first - a.second >= 1.2 * size }.map { (a, b) -> a.second to b.first }
    }
    val gutter = gaps.flatten().map { (from, to) -> (from + to) / 2 }.sorted().firstOrNull { x ->
        val crossed = lines.any { line -> line.wordBoxes.any { (l, r) -> x in (l + 1) until r } }
        !crossed && gaps.count { g -> g.any { (from, to) -> x in from..to } } >= 2
    } ?: return listOf(block)
    fun side(left: Boolean) = lines.mapNotNull { line ->
        val keep = line.words.indices.filter { (line.wordBoxes[it].second <= gutter) == left }
        if (keep.isEmpty()) null else OcrLine(
            keep.joinToString(" ") { line.words[it].first },
            line.wordBoxes[keep.first()].first, line.top, line.wordBoxes[keep.last()].second, line.bottom,
            line.confidence, line.angle, keep.map { line.words[it] }, wordBoxes = keep.map { line.wordBoxes[it] },
        )
    }
    val left = side(true)
    val right = side(false)
    if (left.isEmpty() || right.isEmpty()) return listOf(block)
    return splitMergedColumns(OcrBlock(left), depth + 1) + splitMergedColumns(OcrBlock(right), depth + 1)
}

/**
 * Keeps frames steady across live preview frames, which ML Kit reads afresh each time: a frame is
 * matched to the last one where it overlaps most, glides towards its new place, survives a few
 * frames unseen, and only changes between suggested and skipped once that holds for a few frames.
 * The user's own choice for a frame stays until it's lost from view.
 */
class FrameTracker {
    class Tracked(val id: Int, var frame: TextFrame) {
        var left = frame.left
        var top = frame.top
        var right = frame.right
        var bottom = frame.bottom
        var suggested = frame.suggested
        /** The user's choice, if they tapped it. */
        var choice: Boolean? = null
        var seen = 1
        internal var missed = 0
        internal var disagree = 0
        val included: Boolean get() = choice ?: suggested
        /** Seen in a few frames running: worth showing. */
        val shown: Boolean get() = seen >= SHOW_AFTER
        fun contains(x: Float, y: Float, pad: Float) = x >= left - pad && x <= right + pad && y >= top - pad && y <= bottom + pad
    }

    private var nextId = 0
    var frames: List<Tracked> = emptyList()
        private set

    /** Takes the frames of the latest preview frame; returns the tracked frames. */
    fun update(latest: List<TextFrame>): List<Tracked> {
        val unmatched = frames.toMutableList()
        val next = mutableListOf<Tracked>()
        for (frame in latest.sortedByDescending { it.letters }) {
            val match = unmatched.maxByOrNull { overlap(it, frame) }?.takeIf { overlap(it, frame) > MATCH_OVERLAP }
            if (match == null) {
                next += Tracked(nextId++, frame)
                continue
            }
            unmatched -= match
            match.frame = frame
            match.left += (frame.left - match.left) * GLIDE
            match.top += (frame.top - match.top) * GLIDE
            match.right += (frame.right - match.right) * GLIDE
            match.bottom += (frame.bottom - match.bottom) * GLIDE
            match.seen++
            match.missed = 0
            if (frame.suggested != match.suggested) {
                if (++match.disagree >= SETTLE) { match.suggested = frame.suggested; match.disagree = 0 }
            } else {
                match.disagree = 0
            }
            next += match
        }
        // Briefly unseen frames stay, so one blurry preview frame doesn't make them blink.
        next += unmatched.filter { ++it.missed <= KEEP_UNSEEN }
        frames = next
        return next
    }

    /** Includes or skips the frame at ([x], [y]); false if there's none there. */
    fun toggleAt(x: Float, y: Float, pad: Float = 0f): Boolean {
        val frame = frames.filter { it.shown && it.contains(x, y, pad) }.minByOrNull { (it.right - it.left) * (it.bottom - it.top) } ?: return false
        frame.choice = !frame.included
        return true
    }

    fun clear() {
        frames = emptyList()
    }

    /** How much [a] and [b] overlap, as a share of their union. */
    private fun overlap(a: Tracked, b: TextFrame): Float {
        val w = min(a.right, b.right) - max(a.left, b.left)
        val h = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (w <= 0 || h <= 0) return 0f
        val both = w * h
        return both / ((a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - both)
    }

    private companion object {
        const val MATCH_OVERLAP = 0.25f
        const val GLIDE = 0.5f
        const val KEEP_UNSEEN = 3
        const val SETTLE = 3
        const val SHOW_AFTER = 2
    }
}
