package com.guidedtour.app

import com.guidedtour.app.data.Backup
import com.guidedtour.app.data.Scan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupTest {
    @get:Rule val folder = TemporaryFolder()

    private fun scan(time: Long, photo: String? = null) = Scan(
        text = "The Parthenon was built between 447 and 432 BC.", timestamp = time, latitude = 37.97, longitude = 23.72,
        placeName = "Acropolis", languageTag = "en", thumbnail = photo, favorite = true, translation = "Der Parthenon…",
        translationTag = "de", headline = "The Parthenon", translatedHeadline = "Der Parthenon",
    )

    @Test fun guidesAndPhotosComeBackAsTheyWere() {
        val photos = folder.newFolder("thumbs")
        val photo = File(photos, "1000.jpg").apply { writeBytes(ByteArray(5_000) { it.toByte() }) }
        val out = ByteArrayOutputStream()
        Backup.write(listOf(scan(1000, photo.absolutePath), scan(2000)), setOf("phidias"), out)

        val restored = folder.newFolder("restored")
        val contents = Backup.read(ByteArrayInputStream(out.toByteArray()), restored) { false }
        assertEquals(2, contents.fresh.size)
        assertEquals(setOf("phidias"), contents.words)
        val first = contents.fresh.first { it.timestamp == 1000L }
        assertEquals(scan(1000).copy(thumbnail = first.thumbnail), first)
        assertTrue(File(first.thumbnail!!).readBytes().contentEquals(photo.readBytes()))
        assertNull(contents.fresh.first { it.timestamp == 2000L }.thumbnail)
    }

    @Test fun guidesAlreadyHereAreSkipped() {
        val out = ByteArrayOutputStream()
        Backup.write(listOf(scan(1000), scan(2000)), emptySet(), out)
        val contents = Backup.read(ByteArrayInputStream(out.toByteArray()), folder.newFolder()) { it == 1000L }
        assertEquals(listOf(2000L), contents.fresh.map { it.timestamp })
        assertEquals(1, contents.skipped)
    }

    @Test fun aPhotoCantBeWrittenOutsideThePhotoFolder() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("guides.json"))
            zip.write(Backup.encode(listOf(scan(1000, "/x/evil.jpg")), emptySet()).toByteArray())
            zip.putNextEntry(ZipEntry("photos/../../evil.jpg"))
            zip.write(byteArrayOf(1, 2, 3))
        }
        val photos = folder.newFolder("in", "thumbs")
        val contents = Backup.read(ByteArrayInputStream(out.toByteArray()), photos) { false }
        assertEquals(File(photos, "evil.jpg").absolutePath, contents.fresh.single().thumbnail)
        assertTrue(File(folder.root, "in/evil.jpg").exists().not())
    }

    @Test(expected = Exception::class) fun aFileThatIsntABackupIsRefused() {
        Backup.read(ByteArrayInputStream("not a zip".toByteArray()), folder.newFolder()) { false }
    }
}
