package com.mylittleblover.mkwplayer.pack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The importer's job is to install a pack without damaging what it replaced, so the tests are mostly about
 * what must NOT happen: a broken pack must not be installed, a half-written pack must not be visible, and a
 * failed import must not touch the previous one.
 *
 * Real ZIP archives are built here rather than mocked, because the checks being tested are about bytes on
 * disk and about the central directory. A fake archive would test the fake.
 */
class PackInstallerTest {

    private fun tempDir(): File = Files.createTempDirectory("mkw-pack-test").toFile()

    /** A minimal but valid manifest, as a string. */
    private fun manifestJson(
        packId: String = "test.pack",
        packVersion: Int = 1,
        audioDigest: String,
        audioSize: Long,
        frames: Long = 480_000,
        kind: String = "FULL",
        basePackId: String? = null,
        basePackVersion: Int? = null,
        unlockStages: List<String> = listOf("Coaster"),
        frozen: Boolean = true,
    ): String {
        val baseId = basePackId?.let { "\"$it\"" } ?: "null"
        val baseVersion = basePackVersion?.toString() ?: "null"
        val trackKey = "course:${audioDigest.take(32)}"
        val stages = unlockStages.joinToString(", ") { "\"$it\"" }
        return """
            {
              "formatVersion": 1,
              "packId": "$packId",
              "packVersion": $packVersion,
              "name": "Test pack",
              "kind": "$kind",
              "basePackId": $baseId,
              "basePackVersion": $baseVersion,
              "coverage": {
                "totalLogicalTracks": 1,
                "playableLogicalTracks": 1,
                "codecKinds": [1]
              },
              "assets": [
                {
                  "digest": "$audioDigest",
                  "byteSize": $audioSize,
                  "mediaType": "audio/x-bwav",
                  "codecKind": 1,
                  "sampleRate": 48000,
                  "channels": 2,
                  "frames": $frames
                }
              ],
              "tracks": [
                {
                  "key": "$trackKey",
                  "kind": "COURSE",
                  "title": "Coaster",
                  "primary": "$audioDigest",
                  "isFlow": false,
                  "parts": [
                    {"asset": "$audioDigest", "role": "MAIN"}
                  ]
                }
              ],
              "stages": [
                {"code": "Coaster", "name": "Coaster", "tracks": ["$trackKey"]}
              ],
              "provenance": [
                {"subject": "pack", "field": "assets", "level": "MEASURED_L2", "note": "test"}
              ],
              "unlock": {
                "frozen": $frozen,
                "stages": [${stages.ifEmpty { "" }}]
              }
            }
        """.trimIndent()
    }

    /** Writes a ZIP with the given members. */
    private fun archive(directory: File, name: String, members: Map<String, ByteArray>): File {
        val file = File(directory, name)
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            for ((memberName, bytes) in members) {
                zip.putNextEntry(ZipEntry(memberName))
                if (bytes.isNotEmpty()) zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        return PackInstaller.run { digest.toHex() }
    }

    /** A payload that stands in for a BWAV. The importer never decodes, so any bytes work. */
    private fun payload(size: Int = 4096): ByteArray = ByteArray(size) { (it % 251).toByte() }

    // ---------------------------------------------------------------- the happy path

    @Test
    fun aValidPackInstalls() {
        val root = tempDir()
        val audio = payload()
        val digest = sha256(audio)
        val manifest = manifestJson(audioDigest = digest, audioSize = audio.size.toLong())
        val pack = archive(
            root,
            "pack.mkwpack",
            mapOf(
                "manifest.json" to manifest.toByteArray(),
                "checksums.json" to "{}".toByteArray(),
                "catalog.json" to "{}".toByteArray(),
                "audio/$digest" to audio,
            ),
        )

        val result = PackInstaller.install(pack, root)
        assertTrue(result is PackInstaller.Result.Installed, result.toString())
        result as PackInstaller.Result.Installed

        assertEquals("test.pack", result.manifest.packId)
        assertTrue(File(result.directory, "manifest.json").isFile)
        assertTrue(File(result.directory, "audio/$digest").isFile)
    }

    @Test
    fun theInstalledPackSurvivesInTheRegistry() {
        val root = tempDir()
        val audio = payload()
        val digest = sha256(audio)
        val pack = archive(
            root,
            "pack.mkwpack",
            mapOf(
                "manifest.json" to manifestJson(audioDigest = digest, audioSize = audio.size.toLong())
                    .toByteArray(),
                "audio/$digest" to audio,
            ),
        )

        PackInstaller.install(pack, root)
        val entries = PackRegistry(root).load()
        assertEquals(1, entries.size)
        assertEquals("test.pack", entries.single().packId)
    }

    @Test
    fun theUnlockPackIsIdentified() {
        val root = tempDir()
        val audio = payload()
        val digest = sha256(audio)
        val pack = archive(
            root,
            "pack.mkwpack",
            mapOf(
                "manifest.json" to manifestJson(audioDigest = digest, audioSize = audio.size.toLong())
                    .toByteArray(),
                "audio/$digest" to audio,
            ),
        )

        PackInstaller.install(pack, root)
        assertNotNull(PackRegistry(root).unlockPack())
    }

    // ---------------------------------------------------------------- refusals

    @Test
    fun aFileWithNoManifestIsRefused() {
        // A pack is identified by its manifest, never by its extension.
        val root = tempDir()
        val pack = archive(root, "notapack.mkwpack", mapOf("audio/abc" to payload()))
        val result = PackInstaller.install(pack, root)

        assertTrue(result is PackInstaller.Result.Refused, result.toString())
        assertTrue((result as PackInstaller.Result.Refused).reason.contains("manifest.json"))
    }

    @Test
    fun aTraversalMemberIsRefusedBeforeAnythingIsWritten() {
        val root = tempDir()
        val audio = payload()
        val digest = sha256(audio)
        val pack = archive(
            root,
            "evil.mkwpack",
            mapOf(
                "manifest.json" to manifestJson(audioDigest = digest, audioSize = audio.size.toLong())
                    .toByteArray(),
                "audio/$digest" to audio,
                "../library.json" to "stolen".toByteArray(),
            ),
        )

        val result = PackInstaller.install(pack, root)
        assertTrue(result is PackInstaller.Result.Refused, result.toString())
        assertTrue(!File(root.parentFile, "library.json").exists(), "the traversal wrote outside the root")
    }

    @Test
    fun aMissingAssetIsRefused() {
        val root = tempDir()
        val audio = payload()
        val digest = sha256(audio)
        // The manifest declares the asset, the archive does not contain it.
        val pack = archive(
            root,
            "incomplete.mkwpack",
            mapOf(
                "manifest.json" to manifestJson(audioDigest = digest, audioSize = audio.size.toLong())
                    .toByteArray(),
            ),
        )

        val result = PackInstaller.install(pack, root)
        assertTrue(result is PackInstaller.Result.Refused, result.toString())
        assertTrue((result as PackInstaller.Result.Refused).reason.contains("does not contain"))
    }

    @Test
    fun anAssetWhoseBytesDoNotMatchItsNameIsRefused() {
        // The one that matters most: the name IS the digest, so a mismatch means the archive is not what it
        // claims, and every other member is suspect too.
        val root = tempDir()
        val audio = payload()
        val digest = sha256(audio)
        val different = payload().also { it[0] = (it[0] + 1).toByte() }
        val pack = archive(
            root,
            "corrupt.mkwpack",
            mapOf(
                "manifest.json" to manifestJson(audioDigest = digest, audioSize = audio.size.toLong())
                    .toByteArray(),
                "audio/$digest" to different,
            ),
        )

        val result = PackInstaller.install(pack, root)
        assertTrue(result is PackInstaller.Result.Refused, result.toString())
        assertTrue((result as PackInstaller.Result.Refused).reason.contains("does not match its own name"))
    }

    @Test
    fun anAssetWithTheWrongDeclaredSizeIsRefused() {
        val root = tempDir()
        val audio = payload()
        val digest = sha256(audio)
        val pack = archive(
            root,
            "sizemismatch.mkwpack",
            mapOf(
                // Declares one byte more than the archive holds.
                "manifest.json" to manifestJson(audioDigest = digest, audioSize = audio.size + 1L)
                    .toByteArray(),
                "audio/$digest" to audio,
            ),
        )

        val result = PackInstaller.install(pack, root)
        assertTrue(result is PackInstaller.Result.Refused, result.toString())
    }

    @Test
    fun anUnlistedAssetIsRefused() {
        // Content the manifest cannot describe, so describing it would mean trusting a file name.
        val root = tempDir()
        val audio = payload()
        val digest = sha256(audio)
        val extra = payload(100)
        val pack = archive(
            root,
            "extra.mkwpack",
            mapOf(
                "manifest.json" to manifestJson(audioDigest = digest, audioSize = audio.size.toLong())
                    .toByteArray(),
                "audio/$digest" to audio,
                "audio/${sha256(extra)}" to extra,
            ),
        )

        val result = PackInstaller.install(pack, root)
        assertTrue(result is PackInstaller.Result.Refused, result.toString())
        assertTrue((result as PackInstaller.Result.Refused).reason.contains("does not list"))
    }

    @Test
    fun aMissingFileIsRefused() {
        val result = PackInstaller.install(File(tempDir(), "absent.mkwpack"), tempDir())
        assertTrue(result is PackInstaller.Result.Refused, result.toString())
    }

    @Test
    fun aFileThatIsNotAnArchiveIsRefused() {
        val root = tempDir()
        val pack = File(root, "notzip.mkwpack")
        pack.writeText("this is not a zip")
        val result = PackInstaller.install(pack, root)

        assertTrue(result is PackInstaller.Result.Refused, result.toString())
        assertTrue((result as PackInstaller.Result.Refused).reason.contains("could not be opened"))
    }

    // ---------------------------------------------------------------- atomicity

    @Test
    fun aFailedImportLeavesNoStagingDirectory() {
        // A staging tree left behind would be deleted eventually by nothing, and would occupy the user's
        // storage with a pack they never got.
        val root = tempDir()
        val audio = payload()
        val digest = sha256(audio)
        val different = payload().also { it[0] = (it[0] + 7).toByte() }
        val pack = archive(
            root,
            "corrupt.mkwpack",
            mapOf(
                "manifest.json" to manifestJson(audioDigest = digest, audioSize = audio.size.toLong())
                    .toByteArray(),
                "audio/$digest" to different,
            ),
        )

        PackInstaller.install(pack, root)
        val staging = File(root, ".staging")
        val leftovers = staging.listFiles()?.toList() ?: emptyList()
        assertTrue(leftovers.isEmpty(), "staging was left behind: $leftovers")
    }

    @Test
    fun aFailedImportDoesNotTouchAnInstalledPack() {
        // Replacing a working pack with a broken one is the failure that actually hurts: the user had a
        // library and now has neither the old pack nor the new one.
        val root = tempDir()
        val audio = payload()
        val digest = sha256(audio)
        val good = archive(
            root,
            "good.mkwpack",
            mapOf(
                "manifest.json" to manifestJson(
                    audioDigest = digest,
                    audioSize = audio.size.toLong(),
                    packVersion = 1,
                ).toByteArray(),
                "audio/$digest" to audio,
            ),
        )
        val installed = PackInstaller.install(good, root)
        assertTrue(installed is PackInstaller.Result.Installed, installed.toString())
        val directory = (installed as PackInstaller.Result.Installed).directory

        // A version 2 pack of the same id that is corrupt.
        val bad = archive(
            root,
            "bad.mkwpack",
            mapOf(
                "manifest.json" to manifestJson(
                    audioDigest = digest,
                    audioSize = audio.size.toLong(),
                    packVersion = 2,
                ).toByteArray(),
                "audio/$digest" to payload(10),
            ),
        )
        val refused = PackInstaller.install(bad, root)
        assertTrue(refused is PackInstaller.Result.Refused, refused.toString())

        assertTrue(directory.isFile || directory.isDirectory, "the installed pack disappeared")
        assertTrue(File(directory, "manifest.json").isFile, "the installed pack lost its manifest")
        assertEquals(1, PackRegistry(root).load().size, "the registry gained an entry for a failed import")
    }

    // ---------------------------------------------------------------- the registry

    @Test
    fun anAbsentRegistryIsAnEmptyList() {
        assertTrue(PackRegistry(tempDir()).load().isEmpty())
    }

    @Test
    fun aCorruptRegistryIsReportedRatherThanThrown() {
        // Losing the pointer must not hide every pack on disk, so it is reported and the packs are still
        // listed from their directories.
        val root = tempDir()
        File(root, PackRegistry.FILE_NAME).writeText("{ this is not json")
        val registry = PackRegistry(root)

        assertTrue(registry.load().isEmpty())
        assertNotNull(registry.loadProblem())
    }

    @Test
    fun recordingTheSamePackTwiceKeepsOneEntry() {
        val root = tempDir()
        val audio = payload()
        val digest = sha256(audio)
        val manifest = manifestJson(audioDigest = digest, audioSize = audio.size.toLong())
        val parsed = PackManifest.parse(manifest)
        assertTrue(parsed is PackManifest.Result.Ok, parsed.toString())
        val model = (parsed as PackManifest.Result.Ok).manifest

        val registry = PackRegistry(root)
        registry.record(model, File(root, "a"))
        registry.record(model, File(root, "b"))

        assertEquals(1, registry.load().size)
    }

    @Test
    fun theUnlockPackIsTheHighestVersionRatherThanTheLastInstalled() {
        // Determinism: eligibility must not depend on install order, which changes silently.
        val root = tempDir()
        val audio = payload()
        val digest = sha256(audio)
        val registry = PackRegistry(root)

        for (version in listOf(3, 1, 2)) {
            val parsed = PackManifest.parse(
                manifestJson(
                    audioDigest = digest,
                    audioSize = audio.size.toLong(),
                    packId = "test.pack",
                    packVersion = version,
                ),
            )
            assertTrue(parsed is PackManifest.Result.Ok, parsed.toString())
            registry.record((parsed as PackManifest.Result.Ok).manifest, File(root, "v$version"))
        }

        assertEquals(3, registry.unlockPack()?.packVersion)
    }

    @Test
    fun theUnlockPackIsNullWhenNoPackFrozeAScope() {
        // A pack that does not freeze a scope must not define the cup, or installing any pack would open it.
        val root = tempDir()
        val audio = payload()
        val digest = sha256(audio)
        val parsed = PackManifest.parse(
            manifestJson(audioDigest = digest, audioSize = audio.size.toLong(), frozen = false),
        )
        assertTrue(parsed is PackManifest.Result.Ok, parsed.toString())
        PackRegistry(root).record((parsed as PackManifest.Result.Ok).manifest, File(root, "a"))

        assertNull(PackRegistry(root).unlockPack())
    }

    @Test
    fun removingTheOnlyUnlockPackIsRefused() {
        // Deleting it would silently take the Special Cup away from a user who had earned it.
        val root = tempDir()
        val audio = payload()
        val digest = sha256(audio)
        val parsed = PackManifest.parse(manifestJson(audioDigest = digest, audioSize = audio.size.toLong()))
        assertTrue(parsed is PackManifest.Result.Ok, parsed.toString())
        val registry = PackRegistry(root)
        val directory = File(root, "pack")
        directory.mkdirs()
        registry.record((parsed as PackManifest.Result.Ok).manifest, directory)

        assertTrue(!registry.remove("test.pack", 1))
        assertEquals(1, registry.load().size)
    }
}
