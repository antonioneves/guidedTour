package com.guidedtour.app.data

import android.content.Context
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
data class PlacePhoto(val image: ImageBitmap, val author: String, val license: String, val page: String) {
    val credit: String get() = listOf(author, license).filter { it.isNotBlank() }.joinToString(" · ")
}

/** Wikipedia articles this close to a place can illustrate it. */
const val PHOTO_RADIUS_M = 300

/** An article named like the place counts as the place's own if it's this close (a museum is big). */
const val NAMED_RADIUS_M = 1_000

/** A place with no photo is asked about again after this long, in case an article has been written since. */
private const val RETRY_NONE_MS = 30L * 24 * 60 * 60 * 1000

/**
 * Photos of the places where guides were scanned. First choice is the place's own Wikipedia
 * article, the one titled with its name (as the geocoder gave it), if it's nearby: its lead photo,
 * or its Wikidata photo when the lead image is a logo or a map. Otherwise, the lead photo of the most
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
    }

    private val dir = File(context.filesDir, "place_photos").apply { mkdirs() }
    private val memory = LruCache<String, PlacePhoto>(24)
    private val lock = Mutex()

    /** The cache key: the position rounded to 3 decimals (about 100 m), which is also all that's sent. */
    private fun key(lat: Double, lng: Double, name: String?) =
        "%.3f_%.3f".format(Locale.US, lat, lng) + name?.let { "_" + Integer.toHexString(it.lowercase().hashCode()) }.orEmpty()

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

    /** The saved photo, or null if there's none. A recent "nothing here" counts as saved. */
    private fun load(key: String): PlacePhoto? {
        val info = File(dir, "$key.json").takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() } ?: return null
        if (info.optBoolean("none")) return null
        val bitmap = BitmapFactory.decodeFile(File(dir, "$key.jpg").path) ?: return null
        return PlacePhoto(bitmap.asImageBitmap(), info.optString("author"), info.optString("license"), info.optString("page"))
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
