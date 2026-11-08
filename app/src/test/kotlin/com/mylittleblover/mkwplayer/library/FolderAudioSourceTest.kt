package com.mylittleblover.mkwplayer.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Scanning a folder is how the flow path gets verified without a document picker.
 *
 * The behaviours that matter are the refusals: one unreadable file must not hide the other four,
 * an absent folder must not look like an error, and a stray deep tree must not be walked.
 *
 * These tests build files on disk rather than mocking, because the failure modes are filesystem
 * ones -- permissions, missing paths, depth.
 */
class FolderAudioSourceTest {

    private fun tempDir(): File =
        createTempDir(prefix = "folder-scan").also { it.deleteOnExit() }

    /** A minimal but valid mono DSP-ADPCM header, enough for BwavHeader to accept. */
    private fun bwavBytes(
        codecKind: Int = 1,
        sampleRate: Int = 48_000,
        channels: Int = 2,
        frames: Int = 1_000,
    ): ByteArray {
        val headerSize = 16 + channels * 76
        val data = ByteArray(headerSize + 8 + 64)
        data[0] = 'B'.code.toByte()
        data[1] = 'W'.code.toByte()
        data[2] = 'A'.code.toByte()
        data[3] = 'V'.code.toByte()
        // Version 0xFEFF is required; without it every file is rejected as an unsupported container.
        data[4] = 0xFF.toByte()
        data[5] = 0xFE.toByte()
        // Channel count at offset 14.
        data[14] = (channels and 0xFF).toByte()
        data[15] = ((channels shr 8) and 0xFF).toByte()

        for (channel in 0 until channels) {
            val start = 16 + channel * 76
            put16(data, start + 0, codecKind)
            put32(data, start + 4, sampleRate)
            put32(data, start + 8, 0)              // loopFirst
            put32(data, start + 12, frames)
            put32(data, start + 52, headerSize + channel * 72)
            put32(data, start + 60, 0xFFFFFFFF.toInt())   // loopLast: none
            put32(data, start + 64, 0)
            if (channel == 0) {
                // Predictor seed in range; the decoder rejects above 0x7f.
                data[start + 68] = 0x20
                data[start + 69] = 0x00
            }
        }
        return data
    }

    private fun put16(target: ByteArray, at: Int, value: Int) {
        target[at] = (value and 0xFF).toByte()
        target[at + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun put32(target: ByteArray, at: Int, value: Int) {
        target[at] = (value and 0xFF).toByte()
        target[at + 1] = ((value shr 8) and 0xFF).toByte()
        target[at + 2] = ((value shr 16) and 0xFF).toByte()
        target[at + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun write(folder: File, name: String, bytes: ByteArray = bwavBytes()): File {
        val file = File(folder, name)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    // ---------------------------------------------------------------- happy path

    @Test
    fun aStageFolderIsScannedIntoParts() {
        val dir = tempDir()
        write(dir, "BGM_Stage_Coaster.bwav")
        write(dir, "BGM_Stage_Coaster_Pre.bwav")
        write(dir, "BGM_Stage_Coaster_Bridge.bwav")

        val scan = FolderAudioSource.scan(dir)
        assertTrue(!scan.missingFolder)
        assertEquals(3, scan.found.size)
        assertEquals(0, scan.rejected.size)
    }

    @Test
    fun theScannedPartsGroupIntoOneStage() {
        val dir = tempDir()
        write(dir, "BGM_Stage_Coaster.bwav")
        write(dir, "BGM_Stage_Coaster_Pre.bwav")

        val groups = FolderAudioSource.scan(dir).groups()
        assertEquals(1, groups.size)
        assertEquals("BGM_Stage_Coaster", groups[0].prefix)
        assertTrue(groups[0].hasStructure)
    }

    @Test
    fun bytesAreKeyedByTheLogicalName() {
        // The compiler binds by the name the graph declares, so the key must be the file name.
        val dir = tempDir()
        write(dir, "BGM_Stage_Coaster.bwav")

        val bytes = FolderAudioSource.scan(dir).bytesByName()
        assertEquals(setOf("BGM_Stage_Coaster.bwav"), bytes.keys)
    }

    @Test
    fun aNestedFolderIsScanned() {
        val dir = tempDir()
        write(File(dir, "stages"), "BGM_Stage_Coaster.bwav")

        val scan = FolderAudioSource.scan(dir)
        assertEquals(1, scan.found.size)
    }

    // ---------------------------------------------------------------- refusals

    @Test
    fun anAbsentFolderIsAnEmptyScanNotAnError() {
        val scan = FolderAudioSource.scan(File(tempDir(), "does-not-exist"))
        assertTrue(scan.missingFolder)
        assertEquals(0, scan.found.size)
        assertEquals(0, scan.rejected.size)
    }

    @Test
    fun oneUnreadableFileDoesNotHideTheOthers() {
        // The whole point of collecting rejections: a single bad file must not abort the walk.
        val dir = tempDir()
        write(dir, "BGM_Stage_Coaster.bwav")
        write(dir, "BGM_Stage_Coaster_Pre.bwav")
        write(dir, "BGM_Stage_Coaster_Bridge.bwav", bytes = ByteArray(64))

        val scan = FolderAudioSource.scan(dir)
        assertEquals(2, scan.found.size)
        assertEquals(1, scan.rejected.size)
        assertTrue(scan.rejected[0].contains("Bridge"))
    }

    @Test
    fun anUndecodableCodecIsReportedRatherThanIncluded() {
        val dir = tempDir()
        write(dir, "BGM_Stage_Coaster.bwav")
        write(dir, "BGM_Stage_Coaster_Pre.bwav", bytes = bwavBytes(codecKind = 2))

        val scan = FolderAudioSource.scan(dir)
        assertEquals(1, scan.found.size)
        assertTrue(scan.rejected[0].contains("codec 2"))
    }

    @Test
    fun nonAudioFilesAreIgnoredNotReported() {
        // A folder the user also keeps notes in should not produce a wall of warnings.
        val dir = tempDir()
        write(dir, "BGM_Stage_Coaster.bwav")
        write(dir, "notes.txt", "hello".toByteArray())

        val scan = FolderAudioSource.scan(dir)
        assertEquals(1, scan.found.size)
        assertEquals(0, scan.rejected.size)
    }

    @Test
    fun aFileWithANonStructuralNameIsRejectedWithAReason() {
        val dir = tempDir()
        write(dir, "BGM_Stage_Coaster_Fast.bwav")

        val scan = FolderAudioSource.scan(dir)
        assertEquals(0, scan.found.size)
        assertEquals(1, scan.rejected.size)
        // The reason is the reader's own wording; what matters is that the file is named in it.
        assertTrue(scan.rejected[0].contains("structural part"), scan.rejected[0])
    }

    @Test
    fun depthIsBounded() {
        // A deep tree must not be walked at startup; two levels is what a stage layout needs.
        val dir = tempDir()
        val deep = File(dir, "a/b/c/d")
        deep.mkdirs()
        deep.resolve("BGM_Stage_Coaster.bwav").writeBytes(bwavBytes())
        write(dir, "BGM_Stage_Coaster_Pre.bwav")

        val scan = FolderAudioSource.scan(dir)
        assertEquals(
            1,
            scan.found.size,
            "only the shallow file should be found",
        )
    }

    @Test
    fun emptyFolderIsAnEmptyScan() {
        val scan = FolderAudioSource.scan(tempDir())
        assertTrue(!scan.missingFolder)
        assertEquals(0, scan.found.size)
    }

    @Test
    fun aFileNamedLikeAFolderIsRead() {
        val dir = tempDir()
        val file = write(dir, "BGM_Stage_Coaster.bwav")
        assertNotNull(file)
        assertEquals(1, FolderAudioSource.scan(dir).found.size)
    }
}
