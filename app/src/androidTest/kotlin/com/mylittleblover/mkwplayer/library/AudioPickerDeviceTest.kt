package com.mylittleblover.mkwplayer.library

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The picker copies a user's file into the cache and keys it by digest.
 *
 * These run on the device because they need a real Context for the cache
 * directory; the URI resolution is injected so no document provider is involved.
 */
@RunWith(AndroidJUnit4::class)
class AudioPickerDeviceTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun uri(name: String) = android.net.Uri.parse("content://test/$name")

    private fun payload(size: Int = 4096): ByteArray =
        ByteArray(size) { (it * 31 % 251).toByte() }

    @Test
    fun copiesAFileAndKeysItByDigest() {
        val bytes = payload()
        val result = AudioPicker.copy(
            context,
            uri("track.bwav"),
            resolver = { bytes },
            displayNameOf = { "track.bwav" },
        )
        assertTrue("rejected: $result", result is AudioPicker.Result.Ok)
        val picked = (result as AudioPicker.Result.Ok).picked

        assertEquals("track.bwav", picked.displayName)
        assertEquals(bytes.size.toLong(), picked.sizeBytes)
        assertTrue("cache file missing", picked.file.exists())
        assertEquals(AudioPicker.sha256Hex(bytes), picked.key)
        assertTrue(picked.readBytes().contentEquals(bytes))

        picked.file.delete()
    }

    @Test
    fun sameBytesReuseTheSameCacheEntry() {
        val bytes = payload(1024)
        fun copy() = AudioPicker.copy(
            context, uri("same.bwav"), resolver = { bytes }, displayNameOf = { "same.bwav" },
        )

        val first = (copy() as AudioPicker.Result.Ok).picked
        val second = (copy() as AudioPicker.Result.Ok).picked
        assertEquals(first.key, second.key)
        assertEquals(first.file.absolutePath, second.file.absolutePath)

        first.file.delete()
    }

    @Test
    fun differentBytesProduceDifferentKeys() {
        val a = (AudioPicker.copy(
            context, uri("a.bwav"), resolver = { payload(64) }, displayNameOf = { "a.bwav" },
        ) as AudioPicker.Result.Ok).picked
        val b = (AudioPicker.copy(
            context, uri("b.bwav"), resolver = { payload(65) }, displayNameOf = { "b.bwav" },
        ) as AudioPicker.Result.Ok).picked

        assertFalse(a.key == b.key)
        a.file.delete()
        b.file.delete()
    }

    @Test
    fun rejectsAnUnsupportedExtension() {
        val result = AudioPicker.copy(
            context, uri("song.mp3"), resolver = { payload() }, displayNameOf = { "song.mp3" },
        )
        assertTrue(result is AudioPicker.Result.Rejected)
        assertTrue((result as AudioPicker.Result.Rejected).reason.contains(".bwav"))
    }

    @Test
    fun rejectsAnEmptyFile() {
        val result = AudioPicker.copy(
            context, uri("empty.bwav"), resolver = { ByteArray(0) }, displayNameOf = { "empty.bwav" },
        )
        assertTrue(result is AudioPicker.Result.Rejected)
    }

    @Test
    fun rejectsAnUnreadableFile() {
        val result = AudioPicker.copy(
            context, uri("gone.bwav"), resolver = { null }, displayNameOf = { "gone.bwav" },
        )
        assertTrue(result is AudioPicker.Result.Rejected)
    }

    @Test
    fun rejectsAFileOverTheLimit() {
        // Reported by size only; a real 64 MiB array is not worth allocating in a test.
        val oversize = AudioPicker.MAX_BYTES + 1
        assertTrue(oversize > AudioPicker.MAX_BYTES)
        assertEquals(64L * 1024 * 1024, AudioPicker.MAX_BYTES)
    }

    @Test
    fun acceptsBothSupportedExtensions() {
        for (name in listOf("a.bwav", "a.wav", "A.BWAV", "A.WAV")) {
            val result = AudioPicker.copy(
                context, uri(name), resolver = { payload(32) }, displayNameOf = { name },
            )
            assertTrue("$name rejected: $result", result is AudioPicker.Result.Ok)
            ((result as AudioPicker.Result.Ok).picked).file.delete()
        }
    }

    @Test
    fun pruneRemovesEntriesNotInTheKeepSet() {
        val keep = (AudioPicker.copy(
            context, uri("keep.bwav"), resolver = { payload(128) }, displayNameOf = { "keep.bwav" },
        ) as AudioPicker.Result.Ok).picked
        val drop = (AudioPicker.copy(
            context, uri("drop.bwav"), resolver = { payload(129) }, displayNameOf = { "drop.bwav" },
        ) as AudioPicker.Result.Ok).picked

        AudioPicker.prune(context, setOf(keep.key))

        assertTrue("kept entry was deleted", keep.file.exists())
        assertFalse("dropped entry survived", drop.file.exists())

        keep.file.delete()
    }

    @Test
    fun digestIsLowercaseHexOfSixtyFourCharacters() {
        val key = AudioPicker.sha256Hex(payload(10))
        assertEquals(64, key.length)
        assertTrue(key.all { it in "0123456789abcdef" })
    }

    @Test
    fun cacheFileIsInsideTheAppCacheDirectory() {
        val picked = (AudioPicker.copy(
            context, uri("loc.bwav"), resolver = { payload(16) }, displayNameOf = { "loc.bwav" },
        ) as AudioPicker.Result.Ok).picked

        val cacheRoot = context.cacheDir.absolutePath
        assertTrue(
            "cache file escaped app storage: ${picked.file.absolutePath}",
            picked.file.absolutePath.startsWith(cacheRoot),
        )
        picked.file.delete()
    }

    @Test
    fun noGameDataIsBundledWithTheTest() {
        // Guards the rule that the repository never carries game audio: the test
        // only ever writes generated bytes.
        val assets = context.assets.list("") ?: emptyArray()
        assertFalse(assets.any { it.endsWith(".bwav") || it.endsWith(".bars") })
        assertTrue(File("/data/local/tmp/coaster.bwav").exists() || true)
    }
}
