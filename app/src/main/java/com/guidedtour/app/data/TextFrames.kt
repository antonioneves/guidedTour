package com.guidedtour.app.data

import kotlin.math.abs
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

/** The share of the smaller of two boxes that the other covers: 1 when one lies inside the other. */
internal fun coverShare(l1: Float, t1: Float, r1: Float, b1: Float, l2: Float, t2: Float, r2: Float, b2: Float): Float {
    val w = min(r1, r2) - max(l1, l2)
    val h = min(b1, b2) - max(t1, t2)
    if (w <= 0 || h <= 0) return 0f
    return w * h / min((r1 - l1) * (b1 - t1), (r2 - l2) * (b2 - t2))
}

/** The share of the first box that the second covers. */
internal fun coveredShare(l1: Float, t1: Float, r1: Float, b1: Float, l2: Float, t2: Float, r2: Float, b2: Float): Float {
    val w = min(r1, r2) - max(l1, l2)
    val h = min(b1, b2) - max(t1, t2)
    if (w <= 0 || h <= 0) return 0f
    return w * h / ((r1 - l1) * (b1 - t1))
}

/** Frames covering this much of the smaller one are one frame. */
internal const val SAME_FRAME = 0.6f

/** Frames that are both suggested (or both skipped) and overlap this much are one frame too: tilted text makes boxes that overlap at the corners. */
internal const val PARTLY_OVER = 0.4f

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
    }.let(::mergeOverlapping).let { withoutEdges(it, width, height) }.sortedByDescending { it.letters }.take(MAX_FRAMES)
}

/** A frame this close to the view's edge (as a share of its smaller side) is cut off by it. */
private const val EDGE = 0.02f

/**
 * [frames] without those touching the edge of the view: their text is cut off, so they'd only read
 * half a line. If every frame touches it (a plaque filling the view), they all stay.
 */
internal fun withoutEdges(frames: List<TextFrame>, width: Int, height: Int): List<TextFrame> {
    val margin = EDGE * min(width, height)
    val inside = frames.filter { it.left > margin && it.top > margin && it.right < width - margin && it.bottom < height - margin }
    return inside.ifEmpty { frames }
}

/** [frames] with those that overlap (one mostly inside another) joined into one, so text isn't framed twice. */
internal fun mergeOverlapping(frames: List<TextFrame>): List<TextFrame> {
    val out = frames.toMutableList()
    while (true) {
        val pair = out.indices.flatMap { i -> (i + 1 until out.size).map { i to it } }.firstOrNull { (i, j) ->
            val a = out[i]
            val b = out[j]
            coverShare(a.left, a.top, a.right, a.bottom, b.left, b.top, b.right, b.bottom) > (if (a.suggested == b.suggested) PARTLY_OVER else SAME_FRAME)
        } ?: return out
        val (a, b) = out[pair.first] to out[pair.second]
        out[pair.first] = frameOf((a.blocks + b.blocks).distinct(), a.suggested || b.suggested)
        out.removeAt(pair.second)
    }
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
        /** Frames running its place has hardly changed; once it's [LOCK]ed it holds still. */
        internal var calm = 0
        /** Frames running the text has been somewhere else than a locked frame. */
        internal var drift = 0
        val included: Boolean get() = choice ?: suggested
        /** Appeared in an empty view: shown at once, so pointing at text shows its frames without a wait. */
        internal var quick = false
        /** Seen in a few frames running (or at once in an empty view): worth showing. */
        val shown: Boolean get() = quick || seen >= SHOW_AFTER
        fun contains(x: Float, y: Float, pad: Float) = x >= left - pad && x <= right + pad && y >= top - pad && y <= bottom + pad
    }

    private var nextId = 0
    private var emptyRun = 0
    var frames: List<Tracked> = emptyList()
        private set

    /** Takes the frames of the latest preview frame; returns the tracked frames. */
    fun update(latest: List<TextFrame>): List<Tracked> {
        // Nothing seen twice running: the camera has left the text, so the frames go now.
        if (latest.isEmpty()) {
            emptyRun++
            frames = if (emptyRun >= EMPTY_KEEP) emptyList() else frames.onEach { it.missed++ }
            return frames
        }
        emptyRun = 0
        val wasEmpty = frames.isEmpty()
        val unmatched = frames.toMutableList()
        val next = mutableListOf<Tracked>()
        for (frame in latest.sortedByDescending { it.letters }) {
            val match = unmatched.maxByOrNull { overlap(it, frame) }?.takeIf { overlap(it, frame) > MATCH_OVERLAP }
            if (match == null) {
                next += Tracked(nextId++, frame).also { it.quick = wasEmpty }
                continue
            }
            unmatched -= match
            match.frame = frame
            follow(match, frame)
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
        // A frame not seen this time stays a few frames, unless frames now seen mostly cover its place
        // (a small piece of text read inside it doesn't count: the rest of it may just be blurred).
        next += unmatched.filter { ghost ->
            ++ghost.missed <= KEEP_UNSEEN && next.none { coveredShare(ghost.left, ghost.top, ghost.right, ghost.bottom, it.left, it.top, it.right, it.bottom) > GHOST_COVERED }
        }
        frames = withoutDuplicates(next)
        return frames
    }

    /**
     * Moves [tracked] towards [frame]: gliding while it settles; once its place has held (the camera
     * is steady) it stays put through small changes, and only moves when the text is clearly
     * elsewhere for a few frames running.
     */
    private fun follow(tracked: Tracked, frame: TextFrame) {
        val size = max(tracked.right - tracked.left, tracked.bottom - tracked.top).coerceAtLeast(1f)
        val shift = maxOf(abs(frame.left - tracked.left), abs(frame.top - tracked.top), abs(frame.right - tracked.right), abs(frame.bottom - tracked.bottom)) / size
        if (tracked.calm >= LOCK) {
            if (shift < MOVE_AWAY) { tracked.drift = 0; return }
            if (++tracked.drift < MOVE_AFTER) return
            tracked.calm = 0
            tracked.drift = 0
        }
        tracked.calm = if (shift < HOLD) tracked.calm + 1 else 0
        tracked.left += (frame.left - tracked.left) * GLIDE
        tracked.top += (frame.top - tracked.top) * GLIDE
        tracked.right += (frame.right - tracked.right) * GLIDE
        tracked.bottom += (frame.bottom - tracked.bottom) * GLIDE
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

    /**
     * How well [a] and [b] fit: their overlap as a share of their union, or, for one that grew or
     * shrank around the other, how far it's inside it (only counted past [SAME_FRAME]).
     */
    private fun overlap(a: Tracked, b: TextFrame): Float {
        val w = min(a.right, b.right) - max(a.left, b.left)
        val h = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (w <= 0 || h <= 0) return 0f
        val both = w * h
        val union = both / ((a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - both)
        val inside = coverShare(a.left, a.top, a.right, a.bottom, b.left, b.top, b.right, b.bottom)
        // Only frames of a similar size are the same text grown or shrunk: a few words read inside a
        // big block aren't that block.
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val similar = min(areaA, areaB) >= SIMILAR_SIZE * max(areaA, areaB)
        return max(union, if (inside > SAME_FRAME && similar) inside - SAME_FRAME + MATCH_OVERLAP + 0.01f else 0f)
    }

    /** [list] without frames mostly inside another: the longer seen (or the user's choice) stays. */
    private fun withoutDuplicates(list: List<Tracked>): List<Tracked> {
        val kept = list.sortedWith(compareByDescending<Tracked> { it.choice != null }.thenByDescending { it.seen }).toMutableList()
        var i = 0
        while (i < kept.size) {
            val a = kept[i]
            var j = i + 1
            while (j < kept.size) {
                val b = kept[j]
                if (coverShare(a.left, a.top, a.right, a.bottom, b.left, b.top, b.right, b.bottom) > SAME_FRAME) {
                    if (a.choice == null) a.choice = b.choice
                    kept.removeAt(j)
                } else {
                    j++
                }
            }
            i++
        }
        return list.filter { it in kept }
    }

    private companion object {
        const val MATCH_OVERLAP = 0.25f
        const val GLIDE = 0.3f
        const val KEEP_UNSEEN = 6
        const val SETTLE = 6
        const val SHOW_AFTER = 2
        /** Updates with no text at all running before every frame is dropped. */
        const val EMPTY_KEEP = 2
        /** An unseen frame goes at once when frames seen now cover this share of it. */
        const val GHOST_COVERED = 0.6f
        /** The smaller of two frames matched by one lying in the other is at least this share of the larger. */
        const val SIMILAR_SIZE = 0.4f
        /** A shift under this share of the frame's size counts as calm. */
        const val HOLD = 0.05f
        /** Calm frames running before a frame locks. */
        const val LOCK = 4
        /** A locked frame moves only for a shift over this share of its size... */
        const val MOVE_AWAY = 0.12f
        /** ...that lasts this many frames. */
        const val MOVE_AFTER = 5
    }
}
