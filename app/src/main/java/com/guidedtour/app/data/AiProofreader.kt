package com.guidedtour.app.data

import android.annotation.SuppressLint
import android.content.Context
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.proofreading.Proofreader
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device AI proofreading (Gemini Nano, through Android's AICore) where the phone has it, as a last
 * opinion on words OCR misread. It reads a whole sentence, so it can tell "in e controlled manner" is
 * "a". It's free to rewrite, which a plaque mustn't be: its suggestion only counts as one vote (see
 * [vote]), so it can only turn a non-word into a real word, never change words that read fine.
 */
// Holds the application's context only (see get), which lives as long as the app.
@SuppressLint("StaticFieldLeak")
class AiProofreader private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: AiProofreader? = null
        fun get(context: Context): AiProofreader =
            instance ?: synchronized(this) { instance ?: AiProofreader(context.applicationContext).also { instance = it } }

        /** Paragraphs longer than this are left alone: the model takes short inputs. */
        private const val MAX_CHARS = 1_500

        /** At most this many paragraphs of a scan are proofread: each takes a moment. */
        private const val MAX_PARAGRAPHS = 5

        /** A paragraph's proofreading is given up after this long. */
        private const val TIMEOUT_MS = 8_000L
    }

    private val clients = HashMap<Int, Proofreader>()

    /** The model's language for [tag], or null if it doesn't proofread it. */
    private fun language(tag: String): Int? = when (listName(tag)) {
        "en" -> ProofreaderOptions.Language.ENGLISH
        "de" -> ProofreaderOptions.Language.GERMAN
        "fr" -> ProofreaderOptions.Language.FRENCH
        "it" -> ProofreaderOptions.Language.ITALIAN
        "es" -> ProofreaderOptions.Language.SPANISH
        else -> null
    }

    @Synchronized
    private fun client(language: Int): Proofreader = clients.getOrPut(language) {
        Proofreading.getClient(
            ProofreaderOptions.builder(context)
                .setInputType(ProofreaderOptions.InputType.KEYBOARD)
                .setLanguage(language)
                .build(),
        )
    }

    /** Whether the phone can proofread: available, needs a one-time download, or not at all. */
    enum class Status { AVAILABLE, DOWNLOADABLE, UNAVAILABLE }

    suspend fun status(): Status = runCatching {
        when (client(ProofreaderOptions.Language.ENGLISH).checkFeatureStatus().await()) {
            FeatureStatus.AVAILABLE -> Status.AVAILABLE
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> Status.DOWNLOADABLE
            else -> Status.UNAVAILABLE
        }
    }.getOrDefault(Status.UNAVAILABLE)

    /** Downloads the model; true once it's there. */
    suspend fun download(): Boolean = runCatching {
        client(ProofreaderOptions.Language.ENGLISH).downloadFeature(object : DownloadCallback {
            override fun onDownloadStarted(bytesToDownload: Long) {}
            override fun onDownloadFailed(e: GenAiException) {}
            override fun onDownloadProgress(totalBytesDownloaded: Long) {}
            override fun onDownloadCompleted() {}
        }).await()
        status() == Status.AVAILABLE
    }.getOrDefault(false)

    /**
     * [text] with the paragraphs that have [unsure] words proofread, in language [tag]; only real
     * words ([isWord]) replace non-words. [text] itself if the phone can't, or the language isn't one
     * it proofreads.
     */
    suspend fun proofread(text: String, tag: String?, unsure: Set<String>, isWord: (String) -> Boolean): String {
        val language = tag?.let(::language) ?: return text
        if (status() != Status.AVAILABLE) return text
        val client = client(language)
        var out = text
        val paragraphs = paragraphRanges(text)
            .filter { r -> r.last - r.first < MAX_CHARS && Regex("""[\p{L}\p{M}]+""").findAll(text.substring(r.first, r.last + 1)).any { it.value.lowercase() in unsure } }
            .take(MAX_PARAGRAPHS)
        // From the last, so mending one doesn't move the ones still to do.
        for (range in paragraphs.reversed()) {
            val paragraph = text.substring(range.first, range.last + 1)
            val suggestion = withTimeoutOrNull(TIMEOUT_MS) {
                runCatching { client.runInference(ProofreadingRequest.builder(paragraph).build()).await().results.firstOrNull()?.text }.getOrNull()
            } ?: continue
            val mended = vote(paragraph, listOf(suggestion to 1f), 1f, isWord)
            if (mended != paragraph) out = out.replaceRange(range.first, range.last + 1, mended)
        }
        return out
    }
}

private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
    addListener({ runCatching { get() }.onSuccess { cont.resume(it) }.onFailure { cont.resumeWithException(it) } }, MoreExecutors.directExecutor())
    cont.invokeOnCancellation { cancel(false) }
}
