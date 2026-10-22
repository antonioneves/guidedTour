package com.guidedtour.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** A photo of a place, from Wikimedia Commons, with the credit its licence asks for. */
data class PlacePhoto(val bitmap: Bitmap, val author: String, val license: String, val page: String) {
    val image: ImageBitmap = bitmap.asImageBitmap()
    val credit: String get() = listOf(author, license).filter { it.isNotBlank() }.joinToString(" · ")
}

/**
 * The start of the Wikipedia article about a place, in [language] (where Wikipedia has it, else
 * English), for "About this place": the article's [title], its introduction as plain text, and its [page].
 */
data class PlaceAbout(val title: String, val text: String, val language: String, val page: String)

/** What a place's photo is looked up by: where it is and what it's called. */
data class PhotoSpot(val latitude: Double, val longitude: Double, val name: String?)

/**
 * A map place's spot: its centre, named after its first scan, so Guides, the map and the player
 * all ask the same and share one photo.
 */
fun Place.photoSpot() = PhotoSpot(latitude, longitude, scans.firstNotNullOfOrNull { it.placeName })

/** Wikipedia articles this close to a place can illustrate it. */
const val PHOTO_RADIUS_M = 300

/** An article named like the place counts as the place's own if it's this close (a museum is big). */
const val NAMED_RADIUS_M = 1_000

/** A place with no photo is asked about again after this long, in case an article has been written since. */
private const val RETRY_NONE_MS = 30L * 24 * 60 * 60 * 1000

/**
 * Photos of the places where guides were scanned, and (see [words]) the words their plaques likely
 * use. First choice for the photo is the place's own Wikipedia article, the one titled with its
 * name (as the geocoder gave it), if it's nearby: its lead photo, or its Wikidata photo when the
 * lead image is a logo or a map. Otherwise, the lead photo of the most
 * notable article nearby (the longest, weighed down by distance), so a scan in the Louvre shows the
 * Louvre rather than a diamond on display in it. Only files on Wikimedia Commons are used, which are
 * freely licensed. Each place is asked about once, by its name and its position rounded to about
 * 100 m, and its photo is kept in the app's storage, so it also shows offline.
 */
class PlacePhotos private constructor(context: Context) {
    companion object {
        @Volatile private var instance: PlacePhotos? = null
        fun get(context: Context): PlacePhotos =
            instance ?: synchronized(this) { instance ?: PlacePhotos(context.applicationContext).also { instance = it } }

        private const val USER_AGENT = "GuidedTour/1.0 (https://github.com/antonioneves/guidedTour)"
        private val WORD = Regex("""[\p{L}\p{M}]{3,}""")
        /** A long article has a few thousand different words; more only slows correction down. */
        private const val MAX_PLACE_WORDS = 6_000
    }

    private val dir = File(context.filesDir, "place_photos").apply { mkdirs() }
    private val memory = LruCache<String, PlacePhoto>(24)
    private val lock = Mutex()

    /** The cache key: the position rounded to 3 decimals (about 100 m), which is also all that's sent. */
    private fun key(lat: Double, lng: Double, name: String?) =
        "%.3f_%.3f".format(Locale.US, lat, lng) + name?.let { "_" + Integer.toHexString(it.lowercase().hashCode()) }.orEmpty()

    /** A photo of the place at [spot], or null if there's none (or it can't be fetched now). */
    suspend fun photo(spot: PhotoSpot): PlacePhoto? = photo(spot.latitude, spot.longitude, spot.name)

    /** A photo of the place called [name] at [lat], [lng], or null if there's none (or it can't be fetched now). */
    suspend fun photo(lat: Double, lng: Double, name: String?): PlacePhoto? {
        val key = key(lat, lng, name)
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            // One at a time: Wikimedia asks apps not to flood it, and two guides at one place share the answer.
            lock.withLock {
                memory.get(key) ?: (load(key) ?: fetch(key, lat, lng, name)?.let { load(key) })?.also { memory.put(key, it) }
            }
        }
    }

    /**
     * Words from the Wikipedia articles about the place at [spot] and around it (its own article in
     * full, the introductions of the most notable ones nearby), lowercase: the names on its plaques
     * (Erechtheion, Pericles, Caryatids) for recognising and mending them. Asked once per place and
     * kept, like its photo; empty when offline (asked again next time).
     */
    suspend fun words(spot: PhotoSpot): Set<String> = withContext(Dispatchers.IO) {
        val file = File(dir, key(spot.latitude, spot.longitude, spot.name) + ".words")
        if (file.exists()) return@withContext runCatching { file.readLines().filter { it.isNotBlank() }.toSet() }.getOrDefault(emptySet())
        lock.withLock {
            if (file.exists()) return@withLock file.readLines().filter { it.isNotBlank() }.toSet()
            runCatching { fetchWords(spot) }.getOrNull()?.also { words -> runCatching { file.writeText(words.joinToString("\n")) } }.orEmpty()
        }
    }

    /**
     * The introduction of the Wikipedia article about the place at [spot], in [language] where there's
     * one: the place's own article if it's nearby, else the most notable one around (as for its
     * photo). Asked once per place and language and kept, so it also shows offline; null if there's
     * none (asked again after a while) or it can't be fetched now.
     */
    suspend fun about(spot: PhotoSpot, language: String): PlaceAbout? = withContext(Dispatchers.IO) {
        val file = File(dir, key(spot.latitude, spot.longitude, spot.name) + ".about.$language.json")
        lock.withLock {
            val saved = file.takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
            if (saved != null) {
                if (!saved.optBoolean("none")) {
                    return@withLock PlaceAbout(saved.getString("title"), saved.getString("text"), saved.getString("language"), saved.getString("page"))
                }
                if (System.currentTimeMillis() - saved.optLong("checked") < RETRY_NONE_MS) return@withLock null
            }
            runCatching {
                val about = fetchAbout(spot, language)
                file.writeText(
                    about?.let { JSONObject().put("title", it.title).put("text", it.text).put("language", it.language).put("page", it.page) }
                        ?.toString()
                        ?: JSONObject().put("none", true).put("checked", System.currentTimeMillis()).toString(),
                )
                about
            }.getOrNull()
        }
    }

    private fun fetchAbout(spot: PhotoSpot, language: String): PlaceAbout? {
        val coord = "%.3f|%.3f".format(Locale.US, spot.latitude, spot.longitude)
        val english = spot.name?.let { namedTitle(it, coord) } ?: nearbyTitle(coord) ?: return null
        // The same article in the reader's language, when Wikipedia has it.
        val (wiki, title) = if (language == "en") "en" to english else {
            val links = get(
                "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&prop=langlinks" +
                    "&lllang=${encode(language)}&titles=${encode(english)}",
            ).optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0)?.optJSONArray("langlinks")?.optJSONObject(0)?.optString("title")
            if (links.isNullOrBlank()) "en" to english else language to links
        }
        val page = get(
            "https://$wiki.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&prop=extracts|info&inprop=url" +
                "&explaintext=1&exintro=1&redirects=1&titles=${encode(title)}",
        ).optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0) ?: return null
        val text = introOf(page.optString("extract")) ?: return null
        return PlaceAbout(page.optString("title", title), text, wiki, page.optString("fullurl"))
    }

    /** The article titled [name], if it's within [NAMED_RADIUS_M] of [coord]. */
    private fun namedTitle(name: String, coord: String): String? {
        val page = get(
            "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&redirects=1" +
                "&titles=${encode(name)}&prop=coordinates&codistancefrompoint=${encode(coord)}",
        ).optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0) ?: return null
        val distance = page.optJSONArray("coordinates")?.optJSONObject(0)?.optDouble("dist") ?: return null
        return page.getString("title").takeIf { !page.has("missing") && distance <= NAMED_RADIUS_M }
    }

    /** The most notable article near [coord] (the longest, weighed down by distance). */
    private fun nearbyTitle(coord: String): String? {
        val pages = get(
            "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&generator=geosearch" +
                "&ggscoord=${encode(coord)}&ggsradius=$PHOTO_RADIUS_M&ggslimit=20&prop=coordinates|info&codistancefrompoint=${encode(coord)}",
        ).optJSONObject("query")?.optJSONArray("pages") ?: return null
        return (0 until pages.length()).map { pages.getJSONObject(it) }.maxByOrNull { page ->
            val distance = page.optJSONArray("coordinates")?.optJSONObject(0)?.optDouble("dist", PHOTO_RADIUS_M.toDouble()) ?: PHOTO_RADIUS_M.toDouble()
            page.optInt("length") / (1 + (distance / 100).let { it * it })
        }?.getString("title")
    }

    private fun fetchWords(spot: PhotoSpot): Set<String> {
        val coord = "%.3f|%.3f".format(Locale.US, spot.latitude, spot.longitude)
        val titles = mutableListOf<String>()
        spot.name?.let { name ->
            val response = get(
                "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&redirects=1" +
                    "&titles=${encode(name)}&prop=coordinates&codistancefrompoint=${encode(coord)}",
            )
            val page = response.optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0)
            val distance = page?.optJSONArray("coordinates")?.optJSONObject(0)?.optDouble("dist")
            if (page != null && !page.has("missing") && distance != null && distance <= NAMED_RADIUS_M) titles += page.getString("title")
        }
        val nearby = get(
            "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&generator=geosearch" +
                "&ggscoord=${encode(coord)}&ggsradius=$PHOTO_RADIUS_M&ggslimit=20&prop=coordinates|info&codistancefrompoint=${encode(coord)}",
        ).optJSONObject("query")?.optJSONArray("pages")
        if (nearby != null) {
            titles += (0 until nearby.length()).map { nearby.getJSONObject(it) }.sortedByDescending { page ->
                val distance = page.optJSONArray("coordinates")?.optJSONObject(0)?.optDouble("dist", PHOTO_RADIUS_M.toDouble()) ?: PHOTO_RADIUS_M.toDouble()
                page.optInt("length") / (1 + (distance / 100).let { it * it })
            }.map { it.getString("title") }.filter { it !in titles }.take(4)
        }
        if (titles.isEmpty()) return emptySet()
        val texts = mutableListOf<String>()
        // The place's own article in full (or the most notable nearby), the others' introductions.
        get(
            "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&prop=extracts&explaintext=1" +
                "&exlimit=1&titles=${encode(titles.first())}",
        ).optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0)?.optString("extract")?.let(texts::add)
        if (titles.size > 1) {
            val intros = get(
                "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&prop=extracts&explaintext=1" +
                    "&exintro=1&exlimit=20&titles=${encode(titles.drop(1).joinToString("|"))}",
            ).optJSONObject("query")?.optJSONArray("pages")
            if (intros != null) for (i in 0 until intros.length()) intros.getJSONObject(i).optString("extract").let(texts::add)
        }
        return texts.flatMap { text -> WORD.findAll(text).map { it.value.lowercase() } }.distinct().take(MAX_PLACE_WORDS).toSet()
    }

    /** The saved photo, or null if there's none. A recent "nothing here" counts as saved. */
    private fun load(key: String): PlacePhoto? {
        val info = File(dir, "$key.json").takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() } ?: return null
        if (info.optBoolean("none")) return null
        val bitmap = BitmapFactory.decodeFile(File(dir, "$key.jpg").path) ?: return null
        return PlacePhoto(bitmap, info.optString("author"), info.optString("license"), info.optString("page"))
    }

    /** True if a photo was saved; null if there's none (remembered) or the lookup failed (tried again later). */
    private fun fetch(key: String, lat: Double, lng: Double, name: String?): Boolean? {
        val info = File(dir, "$key.json")
        if (info.exists()) {
            val saved = runCatching { JSONObject(info.readText()) }.getOrNull()
            if (saved?.optBoolean("none") == true && System.currentTimeMillis() - saved.optLong("checked") < RETRY_NONE_MS) return null
        }
        return runCatching {
            val coord = "%.3f|%.3f".format(Locale.US, lat, lng)
            val file = (name?.let { namedImage(it, coord) } ?: nearbyImage(coord)) ?: run {
                info.writeText(JSONObject().put("none", true).put("checked", System.currentTimeMillis()).toString())
                return@runCatching null
            }
            val commons = get(
                "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2&prop=imageinfo" +
                    "&iiprop=url|extmetadata&iiurlwidth=960&iiextmetadatafilter=Artist|LicenseShortName&titles=" + encode("File:$file"),
            )
            val image = commons.getJSONObject("query").getJSONArray("pages").getJSONObject(0)
                .optJSONArray("imageinfo")?.optJSONObject(0)
            if (image == null) {
                // Not on Commons: a non-free image only Wikipedia may show. Remember there's nothing usable.
                info.writeText(JSONObject().put("none", true).put("checked", System.currentTimeMillis()).toString())
                return@runCatching null
            }
            val meta = image.optJSONObject("extmetadata")
            download(image.getString("thumburl"), File(dir, "$key.jpg"))
            info.writeText(
                JSONObject()
                    .put("author", plain(meta?.optJSONObject("Artist")?.optString("value")).take(60))
                    .put("license", plain(meta?.optJSONObject("LicenseShortName")?.optString("value")))
                    .put("page", image.optString("descriptionurl"))
                    .toString(),
            )
            true
        }.getOrNull()
    }

    /** A photo from the article titled [name], if there is one within [NAMED_RADIUS_M] of [coord]. */
    private fun namedImage(name: String, coord: String): String? {
        val response = get(
            "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&redirects=1" +
                "&titles=${encode(name)}&prop=pageimages|coordinates|pageprops&piprop=name&ppprop=wikibase_item" +
                "&codistancefrompoint=${encode(coord)}",
        )
        val page = response.optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0) ?: return null
        val distance = page.optJSONArray("coordinates")?.optJSONObject(0)?.optDouble("dist") ?: return null
        if (page.has("missing") || distance > NAMED_RADIUS_M) return null
        page.optString("pageimage").takeIf(::isPhoto)?.let { return it }
        // The lead image is a logo or a map: Wikidata's image of the place is usually a photo of it.
        val item = page.optJSONObject("pageprops")?.optString("wikibase_item")?.takeIf { it.isNotBlank() } ?: return null
        val claims = get("https://www.wikidata.org/w/api.php?action=wbgetclaims&format=json&property=P18&entity=${encode(item)}")
        return claims.optJSONObject("claims")?.optJSONArray("P18")?.optJSONObject(0)
            ?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value")?.takeIf(::isPhoto)
    }

    /** Photos only: drawings, logos, maps, plans and coats of arms come as SVG or PNG. */
    private fun isPhoto(file: String) = file.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") }

    /** The Commons file name of the lead photo of the most notable article near [coord], if any. */
    private fun nearbyImage(coord: String): String? {
        val response = get(
            "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&generator=geosearch" +
                "&ggscoord=${encode(coord)}&ggsradius=$PHOTO_RADIUS_M&ggslimit=20" +
                "&prop=pageimages|coordinates|info&piprop=name&codistancefrompoint=${encode(coord)}",
        )
        val pages = response.optJSONObject("query")?.optJSONArray("pages") ?: return null
        return (0 until pages.length()).map { pages.getJSONObject(it) }
            .filter { isPhoto(it.optString("pageimage")) }
            .maxByOrNull { page ->
                val distance = page.optJSONArray("coordinates")?.optJSONObject(0)?.optDouble("dist", PHOTO_RADIUS_M.toDouble()) ?: PHOTO_RADIUS_M.toDouble()
                page.optInt("length") / (1 + (distance / 100).let { it * it })
            }
            ?.optString("pageimage")
    }

    private fun connect(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        setRequestProperty("User-Agent", USER_AGENT)
        connectTimeout = 10_000
        readTimeout = 15_000
    }

    private fun get(url: String): JSONObject {
        val connection = connect(url)
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            return JSONObject(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, to: File) {
        val connection = connect(url)
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val part = File(to.path + ".part")
            connection.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            part.renameTo(to)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    /** Commons gives the author as HTML, often a link: keep only the text. */
    private fun plain(html: String?): String =
        Html.fromHtml(html.orEmpty(), Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()
}

/**
 * The first paragraphs of an article's introduction, up to about [MAX_ABOUT] characters, without the
 * pronunciation and other bracketed asides speech would trip over; null if there's no real text.
 */
internal fun introOf(extract: String): String? {
    val paragraphs = extract.split(Regex("""\n+""")).map { paragraph ->
        paragraph.replace(Regex("""\s*\([^()]*\)"""), "").replace(Regex("""\s+"""), " ").replace(" ,", ",").trim()
    }.filter { it.length > 40 }
    if (paragraphs.isEmpty()) return null
    val kept = mutableListOf<String>()
    for (paragraph in paragraphs) {
        if (kept.isNotEmpty() && kept.sumOf { it.length } + paragraph.length > MAX_ABOUT) break
        kept += paragraph
    }
    return kept.joinToString("\n\n")
}

/** About a minute of listening. */
private const val MAX_ABOUT = 1_200
