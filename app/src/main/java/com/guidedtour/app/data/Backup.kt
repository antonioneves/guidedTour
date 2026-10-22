package com.guidedtour.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The app's data as one file, to keep a copy (on the phone, or in Google Drive or any other cloud
 * the phone's file picker offers) and bring it back: a zip with `guides.json` (every guide, and the
 * personal words) and `photos/` (each guide's plaque photo). Place photos aren't in it: they're
 * downloaded again. Importing adds what isn't in the app yet and never removes anything, so an old
 * backup can't undo deletions made since.
 */
object Backup {
    /** The version of `guides.json`'s layout; a newer one than this app knows is refused. */
    private const val FORMAT = 1
    private const val GUIDES = "guides.json"
    private const val PHOTOS = "photos/"

    /** A backup's guides not in the app yet ([fresh]), how many were there already, and its words. */
    data class Contents(val fresh: List<Scan>, val skipped: Int, val words: Set<String>)

    /** Writes [scans] (their photos read from where they're saved) and [words] to [out] as a backup. */
    fun write(scans: List<Scan>, words: Set<String>, out: OutputStream) {
        ZipOutputStream(out.buffered()).use { zip ->
            val photos = scans.mapNotNull { scan -> scan.thumbnail?.let(::File)?.takeIf { it.isFile } }.distinctBy { it.name }
            zip.putNextEntry(ZipEntry(GUIDES))
            zip.write(encode(scans, words).toByteArray())
            zip.closeEntry()
            for (photo in photos) {
                // Photos are JPEGs already: storing them as they are is as small and much faster.
                zip.putNextEntry(ZipEntry(PHOTOS + photo.name).apply {
                    method = ZipEntry.STORED
                    size = photo.length()
                    compressedSize = photo.length()
                    crc = java.util.zip.CRC32().also { crc -> photo.inputStream().use { input -> input.copyTo(CrcStream(crc)) } }.value
                })
                photo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Reads a backup from [input]: its guides, with photos saved into [photoDir], and its words.
     * Guides already in the app ([have] tells by their time) are skipped and their photos not copied.
     * Throws if [input] isn't a backup.
     */
    fun read(input: InputStream, photoDir: File, have: (Long) -> Boolean): Contents {
        var decoded: Pair<List<Scan>, Set<String>>? = null
        val photos = HashMap<String, File>()
        val staged = File(photoDir, ".import").apply { deleteRecursively(); mkdirs() }
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        name == GUIDES -> decoded = decode(zip.readBytes().decodeToString())
                        name.startsWith(PHOTOS) && !entry.isDirectory -> {
                            // Only the file name: a backup can't write outside the photo folder.
                            val file = File(staged, File(name).name)
                            file.outputStream().use { zip.copyTo(it) }
                            photos[file.name] = file
                        }
                    }
                }
            }
            val (scans, words) = decoded ?: error("not a Guided Tour backup")
            val fresh = scans.filter { !have(it.timestamp) }.map { scan ->
                val photo = scan.thumbnail?.let { photos[it] } ?: return@map scan.copy(thumbnail = null)
                var target = File(photoDir, photo.name)
                if (target.exists()) target = File(photoDir, "${photo.nameWithoutExtension}_${scan.timestamp}.${photo.extension}")
                photo.copyTo(target, overwrite = true)
                scan.copy(thumbnail = target.absolutePath)
            }
            return Contents(fresh, scans.size - fresh.size, words)
        } finally {
            staged.deleteRecursively()
        }
    }

    /** `guides.json` for [scans] and [words]; each guide names its photo by file name only. */
    fun encode(scans: List<Scan>, words: Set<String>): String = JSONObject().apply {
        put("format", FORMAT)
        put("guides", JSONArray().apply {
            for (scan in scans) put(JSONObject().apply {
                put("text", scan.text)
                put("timestamp", scan.timestamp)
                scan.latitude?.let { put("latitude", it) }
                scan.longitude?.let { put("longitude", it) }
                scan.placeName?.let { put("placeName", it) }
                scan.languageTag?.let { put("languageTag", it) }
                scan.thumbnail?.let { put("photo", File(it).name) }
                put("favorite", scan.favorite)
                scan.translation?.let { put("translation", it) }
                scan.translationTag?.let { put("translationTag", it) }
                scan.languages?.let { put("languages", it) }
                put("languageFixed", scan.languageFixed)
                scan.headline?.let { put("headline", it) }
            })
        })
        put("personalWords", JSONArray(words.sorted()))
    }.toString()

    /** The guides (without ids; [Scan.thumbnail] holds the photo's file name) and words in [json]. */
    fun decode(json: String): Pair<List<Scan>, Set<String>> {
        val root = JSONObject(json)
        require(root.optInt("format", 0) in 1..FORMAT) { "backup from a newer version of the app" }
        val guides = root.getJSONArray("guides")
        val scans = (0 until guides.length()).map { i ->
            val g = guides.getJSONObject(i)
            fun string(key: String) = if (g.has(key) && !g.isNull(key)) g.getString(key) else null
            fun double(key: String) = if (g.has(key) && !g.isNull(key)) g.getDouble(key) else null
            Scan(
                text = g.getString("text"),
                timestamp = g.getLong("timestamp"),
                latitude = double("latitude"),
                longitude = double("longitude"),
                placeName = string("placeName"),
                languageTag = string("languageTag"),
                thumbnail = string("photo"),
                favorite = g.optBoolean("favorite", false),
                translation = string("translation"),
                translationTag = string("translationTag"),
                languages = string("languages"),
                languageFixed = g.optBoolean("languageFixed", false),
                headline = string("headline"),
            )
        }
        val list = root.optJSONArray("personalWords")
        val words = if (list == null) emptySet() else (0 until list.length()).map { list.getString(it) }.toSet()
        return scans to words
    }
}

/** Feeds what's written to it into [crc], for a stored zip entry's checksum. */
private class CrcStream(private val crc: java.util.zip.CRC32) : OutputStream() {
    override fun write(b: Int) = crc.update(b)
    override fun write(b: ByteArray, off: Int, len: Int) = crc.update(b, off, len)
}
