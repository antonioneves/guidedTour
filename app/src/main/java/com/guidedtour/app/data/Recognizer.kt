package com.guidedtour.app.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** What OCR found: reading-order text (paragraphs split by a blank line) and how sure the engine was. */
/**
 * [turn] is how far, in degrees clockwise, the photo had to be turned for the text to read upright;
 * [unsure] are the words the engine wasn't sure of, lowercase.
 */
class Reading(val text: String, val confidence: Float, val letters: Int, val turn: Int = 0, val unsure: Set<String> = emptySet()) {
    val isEmpty: Boolean get() = text.isBlank()
}

private class Pass(val script: OcrScript, val blocks: List<OcrBlock>, val width: Int, val height: Int) {
    /** The blocks that belong to the aimed-at text, without neighbouring plaques. */
    val kept: List<OcrBlock> get() = dropIntruders(blocks, width, height)
    val reading: Reading
        get() {
            val text = layoutText(blocks, width, height)
            val kept = kept
            return Reading(text, meanConfidence(kept), text.count { it.isLetterOrDigit() }, unsure = unsureWords(kept))
        }
}

/** On-device OCR in several scripts, plus language detection so speech uses the right voice. */
object Recognizer {
    /**
     * Longest side of a photo we keep: a phone's main camera at full resolution (4000 × 3000 on a
     * Pixel 9a), with a cap so a 50 MP photo doesn't run out of memory.
     */
    const val FULL_SIDE = 4096

    /** Longest side of the copy the text is first looked for on: quick, and plenty to see where it is. */
    private const val OVERVIEW_SIDE = 2600

    /** Longest side of an image handed to ML Kit for reading: more only slows it down. */
    private const val MAX_SIDE = 2600

    /** Lines this many pixels tall read reliably; smaller ones are enlarged towards it in the second pass. */
    private const val SHARP_LETTER = 32f

    /** Enlarging more than this only blurs. */
    private const val MAX_ZOOM = 3f

    private val clients = HashMap<OcrScript, TextRecognizer>()
    private val languageId by lazy { LanguageIdentification.getClient() }

    @Synchronized
    fun client(script: OcrScript): TextRecognizer = clients.getOrPut(if (script == OcrScript.Auto) OcrScript.Latin else script) {
        when (script) {
            OcrScript.Auto, OcrScript.Latin -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrScript.Chinese -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            OcrScript.Japanese -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            OcrScript.Korean -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            OcrScript.Devanagari -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        }
    }

    /** Converts a camera photo to an upright bitmap (at most [FULL_SIDE] on its long side). Always closes [image]. */
    suspend fun toUprightBitmap(image: ImageProxy): Bitmap = try {
        withContext(Dispatchers.Default) {
            val raw = image.toBitmap()
            val matrix = Matrix().apply {
                postRotate(image.imageInfo.rotationDegrees.toFloat())
                val scale = FULL_SIDE.toFloat() / max(raw.width, raw.height)
                if (scale < 1f) postScale(scale, scale)
            }
            if (matrix.isIdentity) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        }
    } finally {
        image.close()
    }

    /**
     * Reads [photo], which isn't changed. The text is first looked for on a smaller copy; with
     * [enhance], only the area it's in is then read again from the full-resolution photo (sharper, and
     * no time spent on the rest), and a second pass straightens it, enlarges small letters and boosts
     * contrast. Whichever reading looks more reliable wins.
     */
    suspend fun read(photo: Bitmap, script: OcrScript, enhance: Boolean = true): Reading {
        val overview = withContext(Dispatchers.Default) { shrunk(photo, OVERVIEW_SIDE) }
        var source = overview
        var turn = 0
        var first = firstPass(overview, script)
        // Sideways text (the phone turned while the screen stayed upright, or a plaque mounted on its
        // side) reads poorly: if this one did, try a quarter turn each way and keep the best.
        if (enhance && (looksSideways(first.blocks) || looksWeak(first.reading))) {
            for (degrees in listOf(90, 270)) {
                val turned = withContext(Dispatchers.Default) { rotated(overview, degrees.toFloat()) }
                val pass = firstPass(turned, script)
                // ML Kit reads sideways text too, so a turn only wins if it lays the lines down flat.
                if (!looksSideways(pass.blocks) && isBetter(pass.reading, first.reading)) {
                    if (source !== overview) source.recycle()
                    source = turned
                    turn = degrees
                    first = pass
                } else {
                    turned.recycle()
                }
            }
        }
        var best = first.reading
        if (enhance && first.kept.isNotEmpty()) {
            if (photo !== overview) {
                // The text area again, from the full photo.
                val scale = max(photo.width, photo.height).toFloat() / max(overview.width, overview.height)
                // Around all the text seen, not just the main plaque's: the pass on the detail sorts that out again.
                val detail = withContext(Dispatchers.Default) { textArea(photo, turn, first.blocks, scale) }
                val pass = Pass(first.script, recognize(client(first.script), detail), detail.width, detail.height)
                if (pass.kept.isNotEmpty() && pass.reading.letters >= first.reading.letters * 0.9) {
                    if (source !== overview) source.recycle()
                    source = detail
                    first = pass
                    best = pass.reading
                } else {
                    detail.recycle()
                }
            }
            // Straighten or crop around the kept text only, so neighbouring plaques fall outside.
            val improved = withContext(Dispatchers.Default) { enhanced(source, first.kept) }
            val second = Pass(first.script, recognize(client(first.script), improved), improved.width, improved.height).reading
            improved.recycle()
            if (isBetter(second, best)) best = second
        }
        if (source !== overview) source.recycle()
        if (overview !== photo) overview.recycle()
        return Reading(best.text, best.confidence, best.letters, turn, best.unsure)
    }

    /** [bitmap] scaled down to [side] on its long side, or itself if it's no bigger. */
    private fun shrunk(bitmap: Bitmap, side: Int): Bitmap {
        val scale = side.toFloat() / max(bitmap.width, bitmap.height)
        return if (scale >= 1f) bitmap else bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
    }

    /**
     * The area of [photo] that [blocks] (found on a copy [scale] times smaller, turned [turn] degrees)
     * cover, with a margin, from the full-resolution photo: at most [MAX_SIDE] on its long side.
     */
    private fun textArea(photo: Bitmap, turn: Int, blocks: List<OcrBlock>, scale: Float): Bitmap {
        val upright = if (turn == 0) photo else rotated(photo, turn.toFloat())
        val left0 = blocks.minOf { it.left }
        val top0 = blocks.minOf { it.top }
        val right0 = blocks.maxOf { it.right }
        val bottom0 = blocks.maxOf { it.bottom }
        // A wide margin: small print set apart (a caution, a credit) may have been missed on the small copy.
        val line = blocks.maxOf { it.medianLineHeight }
        val marginX = max(2 * line, (right0 - left0) / 4)
        val marginY = max(2 * line, (bottom0 - top0) / 4)
        val left = max(0, ((left0 - marginX) * scale).toInt())
        val top = max(0, ((top0 - marginY) * scale).toInt())
        val right = min(upright.width, ((right0 + marginX) * scale).toInt())
        val bottom = min(upright.height, ((bottom0 + marginY) * scale).toInt())
        val area = Bitmap.createBitmap(upright, left, top, max(1, right - left), max(1, bottom - top))
        if (upright !== photo && area !== upright) upright.recycle()
        return shrunk(area, MAX_SIDE).also { if (it !== area && area !== photo) area.recycle() }
    }

    /** Most lines of three or more characters are taller than wide: the text is lying on its side. */
    private fun looksSideways(blocks: List<OcrBlock>): Boolean {
        val lines = blocks.flatMap { it.lines }.filter { it.text.trim().length >= 3 }
        return lines.isNotEmpty() && lines.count { it.height > 1.5 * (it.right - it.left) } * 2 > lines.size
    }

    /** Few letters or low confidence: worth trying the photo turned sideways. */
    private fun looksWeak(reading: Reading) = reading.letters < 40 || reading.confidence < 0.7f

    private fun isBetter(candidate: Reading, current: Reading): Boolean =
        (candidate.letters >= current.letters * 0.9 && candidate.confidence >= current.confidence) ||
            (candidate.letters > current.letters * 1.1 && candidate.confidence >= current.confidence * 0.95)

    private suspend fun firstPass(bitmap: Bitmap, script: OcrScript): Pass {
        if (script != OcrScript.Auto) return pass(script, bitmap)
        // Latin first; only if it finds next to nothing do we try the other scripts.
        val latin = pass(OcrScript.Latin, bitmap)
        if (latin.reading.letters >= 12) return latin
        val others = OcrScript.entries.filter { it != OcrScript.Auto && it != OcrScript.Latin }
            .map { pass(it, bitmap) }
        return (listOf(latin) + others).maxBy { it.reading.letters }
    }

    private suspend fun pass(script: OcrScript, bitmap: Bitmap) =
        Pass(script, recognize(client(script), bitmap), bitmap.width, bitmap.height)

    private suspend fun recognize(recognizer: TextRecognizer, bitmap: Bitmap): List<OcrBlock> =
        suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text ->
                    cont.resume(text.textBlocks.mapNotNull { block ->
                        val lines = block.lines.mapNotNull { line ->
                            line.boundingBox?.let { OcrLine(
                                line.text, it.left, it.top, it.right, it.bottom, line.confidence, line.angle,
                                line.elements.map { e -> e.text to e.confidence },
                                line.cornerPoints?.takeIf { c -> c.size == 4 }?.flatMap { c -> listOf(c.x.toFloat(), c.y.toFloat()) }.orEmpty(),
                            ) }
                        }
                        if (lines.isEmpty()) null else OcrBlock(lines)
                    })
                }
                .addOnFailureListener { cont.resume(emptyList()) }
        }

    /**
     * A copy of [bitmap] cropped to the text, with text seen at an angle stretched back square or
     * tilted text straightened, small letters enlarged, and contrast raised.
     */
    private fun enhanced(bitmap: Bitmap, blocks: List<OcrBlock>): Bitmap {
        val lines = blocks.flatMap { it.lines }
        val tilt = lines.map { it.angle }.sorted()[lines.size / 2]
        // Small letters read poorly: enlarge them towards SHARP_LETTER, as far as MAX_SIDE allows.
        val size = lines.map { it.size }.sorted()[lines.size / 2]
        val zoom = (SHARP_LETTER / max(1, size)).coerceIn(1f, MAX_ZOOM)
        val source = textQuad(lines)?.let { squared(bitmap, it, zoom) } ?: run {
            val crop = cropped(bitmap, lines)
            val straight = if (abs(tilt) in 2f..30f) rotated(crop, -tilt).also { if (crop !== bitmap) crop.recycle() } else crop
            enlarged(straight, zoom).also { if (it !== straight && straight !== bitmap) straight.recycle() }
        }
        return contrast(source).also { if (source !== bitmap) source.recycle() }
    }

    /** [bitmap] cropped to [lines] with a margin, unless they fill most of it anyway. */
    private fun cropped(bitmap: Bitmap, lines: List<OcrLine>): Bitmap {
        val left = lines.minOf { it.left }
        val top = lines.minOf { it.top }
        val right = lines.maxOf { it.right }
        val bottom = lines.maxOf { it.bottom }
        if ((right - left).toLong() * (bottom - top) >= 0.7 * bitmap.width * bitmap.height) return bitmap
        val padX = ((right - left) * 0.08f).toInt() + 8
        val padY = ((bottom - top) * 0.08f).toInt() + 8
        val x = max(0, left - padX)
        val y = max(0, top - padY)
        return Bitmap.createBitmap(bitmap, x, y, min(bitmap.width - x, right - left + 2 * padX), min(bitmap.height - y, bottom - top + 2 * padY))
    }

    /** [bitmap] scaled up by [zoom], or less if that would pass [MAX_SIDE]; itself if that's barely bigger. */
    private fun enlarged(bitmap: Bitmap, zoom: Float): Bitmap {
        val scale = min(zoom, MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height))
        return if (scale < 1.1f) bitmap else bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
    }

    /**
     * The text area [quad] (see [textQuad]) of [bitmap] stretched to a rectangle with a margin around
     * it, or null if it can't be. The text's true proportions are unknown, so each side gets the
     * longer of its two edges, keeping the most detail, enlarged by [zoom] if there's room.
     */
    private fun squared(bitmap: Bitmap, quad: FloatArray, zoom: Float): Bitmap? {
        fun edge(a: Int, b: Int) = kotlin.math.hypot(quad[2 * b] - quad[2 * a], quad[2 * b + 1] - quad[2 * a + 1])
        var width = max(edge(0, 1), edge(3, 2))
        var height = max(edge(0, 3), edge(1, 2))
        if (width < 32f || height < 16f) return null
        var margin = 0.08f * max(width, height)
        val scale = min(zoom, MAX_SIDE / (max(width, height) + 2 * margin))
        width *= scale; height *= scale; margin *= scale
        val target = floatArrayOf(margin, margin, margin + width, margin, margin + width, margin + height, margin, margin + height)
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(quad, 0, target, 0, 4)) return null
        val out = createBitmap((width + 2 * margin).toInt(), (height + 2 * margin).toInt())
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun rotated(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees, bitmap.width / 2f, bitmap.height / 2f) }
        val bounds = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()).also { matrix.mapRect(it) }
        val out = Bitmap.createBitmap(bounds.width().toInt(), bounds.height().toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.translate(-bounds.left, -bounds.top)
        canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun contrast(bitmap: Bitmap, factor: Float = 1.4f): Bitmap {
        val offset = 128f * (1f - factor)
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(ColorMatrix(floatArrayOf(
                factor, 0f, 0f, 0f, offset,
                0f, factor, 0f, 0f, offset,
                0f, 0f, factor, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            )))
        }
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bitmap, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return out
    }

    /** How sharp [bitmap] is (relative score for picking the best of several photos). */
    fun sharpness(bitmap: Bitmap): Double {
        val scale = 640f / max(bitmap.width, bitmap.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        val luma = IntArray(pixels.size) { i ->
            val p = pixels[i]
            (299 * Color.red(p) + 587 * Color.green(p) + 114 * Color.blue(p)) / 1000
        }
        val score = laplacianVariance(luma, small.width, small.height)
        if (small !== bitmap) small.recycle()
        return score
    }

    /**
     * The languages of [text] paragraph by paragraph, so a bilingual plaque gets one span per language.
     * [country] is where the photo was taken, if known (see [pickLanguage]).
     */
    suspend fun detectLanguages(text: String, country: String? = null): List<LanguageSpan> =
        detectSpans(text) { detectLanguage(it, country) }

    /** Every language [text] might be in, with ML Kit's confidence, most likely first. */
    suspend fun possibleLanguages(text: String): List<Pair<String, Float>> = suspendCancellableCoroutine { cont ->
        languageId.identifyPossibleLanguages(text)
            .addOnSuccessListener { list -> cont.resume(list.map { it.languageTag to it.confidence }.filter { it.first != "und" }) }
            .addOnFailureListener { cont.resume(emptyList()) }
    }

    /** The language of [text], or null if unsure; see [pickLanguage]. */
    suspend fun detectLanguage(text: String, country: String? = null): String? = pickLanguage(possibleLanguages(text), country)
}
