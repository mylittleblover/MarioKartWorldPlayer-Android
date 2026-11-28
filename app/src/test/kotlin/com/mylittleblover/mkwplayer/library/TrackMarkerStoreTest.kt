package com.mylittleblover.mkwplayer.library

import com.mylittleblover.mkwplayer.interactive.CourseFlowRecipeBuilder
import com.mylittleblover.mkwplayer.interactive.CourseMedleyCatalog
import com.mylittleblover.mkwplayer.ui.interactive.decodeMedleyToFlowDraft
import com.mylittleblover.mkwplayer.ui.markers.formatMarkerTimestampMs
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackMarkerStoreTest {

    @Test
    fun addListUpdateDeleteAndSortByPosition() {
        val dir = Files.createTempDirectory("mkw-markers-crud").toFile()
        try {
            val store = TrackMarkerStore.inFilesDir(dir)
            assertTrue(store.allMarkers().isEmpty())
            assertTrue(store.markersFor("FirstCircuit").isEmpty())

            val late = store.add(
                trackId = "FirstCircuit",
                positionMs = 95_000L,
                label = "Final Lap Drop",
                colorArgb = TrackMarkerColors.VIOLET,
                createdAtEpochMs = 2_000L,
            )
            val early = store.add(
                trackId = "FirstCircuit",
                positionMs = 18_500L,
                label = "Bridge Entry",
                colorArgb = TrackMarkerColors.CRIMSON,
                createdAtEpochMs = 1_000L,
            )
            val otherTrack = store.add(
                trackId = "jb:ost_01",
                positionMs = 42_000L,
                label = "Sax Solo",
                colorArgb = TrackMarkerColors.EMERALD,
                createdAtEpochMs = 3_000L,
            )

            // Sorted ascending by positionMs for a given track
            val circuitMarkers = store.markersFor("FirstCircuit")
            assertEquals(listOf(early.id, late.id), circuitMarkers.map { it.id })
            assertEquals(setOf("FirstCircuit", "jb:ost_01"), store.trackIdsWithMarkers())

            // Reload from disk using a fresh store instance
            val reloaded = TrackMarkerStore.inFilesDir(dir)
            assertEquals(3, reloaded.allMarkers().size)
            assertEquals(listOf("Bridge Entry", "Final Lap Drop"), reloaded.markersFor("FirstCircuit").map { it.label })
            assertEquals(listOf(otherTrack), reloaded.markersFor("jb:ost_01"))

            // Update marker label, position, and color
            val updated = reloaded.update(
                late.copy(positionMs = 5_000L, label = "Opening Riff", colorArgb = TrackMarkerColors.SKY),
            )
            assertNotNull(updated)
            assertEquals("Opening Riff", updated.label)
            assertEquals(TrackMarkerColors.SKY, updated.colorArgb)
            assertEquals(listOf(late.id, early.id), reloaded.markersFor("FirstCircuit").map { it.id })

            // Updating an unknown id returns null without mutating
            assertNull(reloaded.update(late.copy(id = "missing-id")))

            // Delete single marker
            assertTrue(reloaded.delete(early.id))
            assertFalse(reloaded.delete(early.id))
            assertEquals(listOf(late.id), reloaded.markersFor("FirstCircuit").map { it.id })

            // Delete all markers for one track leaves other tracks untouched
            assertEquals(1, reloaded.deleteForTrack("FirstCircuit"))
            assertTrue(reloaded.markersFor("FirstCircuit").isEmpty())
            assertEquals(listOf(otherTrack), reloaded.markersFor("jb:ost_01"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun replaceAllAndMergeAllPreserveOrderAndPreferNewerTimestamp() {
        val dir = Files.createTempDirectory("mkw-markers-replace-merge").toFile()
        try {
            val store = TrackMarkerStore.inFilesDir(dir)
            val m1 = TrackMarker("m1", "Rainbow", 120_000L, "Area 2", TrackMarkerColors.SKY, createdAtEpochMs = 1_000L)
            val m2 = TrackMarker("m2", "Rainbow", 30_000L, "Area 1", TrackMarkerColors.CRIMSON, createdAtEpochMs = 2_000L)

            val replaced = store.replaceAll(listOf(m1, m2))
            assertEquals(listOf("m2", "m1"), replaced.map { it.id })

            val m1Newer = m1.copy(label = "Area 2 Updated", createdAtEpochMs = 5_000L)
            val m2Older = m2.copy(label = "Stale Name", createdAtEpochMs = 500L)
            val m3 = TrackMarker("m3", "Cinema", 60_000L, "Pattern 2", TrackMarkerColors.AMBER, createdAtEpochMs = 3_000L)

            val merged = store.mergeAll(listOf(m1Newer, m2Older, m3))
            assertEquals(3, merged.size)
            assertEquals("Area 2 Updated", merged.first { it.id == "m1" }.label)
            assertEquals("Area 1", merged.first { it.id == "m2" }.label)
            assertEquals("Pattern 2", merged.first { it.id == "m3" }.label)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun paletteProvidesEightDistinctOpaqueColorsAndNormalization() {
        assertEquals(8, TrackMarkerColors.palette.size)
        assertEquals(8, TrackMarkerColors.palette.distinct().size)
        assertEquals(TrackMarkerColors.palette, TrackMarkerColors.options.map { it.argb })
        TrackMarkerColors.palette.forEach { argb ->
            assertEquals(0xFF000000L, argb and 0xFF000000L)
        }
        assertEquals(0xFFC52D49L, TrackMarkerColors.normalize(0x00C52D49L))
        assertEquals(0xFF0B7D55L, TrackMarkerColors.normalize(0xFF0B7D55L))
    }

    @Test
    fun corruptFileIsNotSilentlyClobberedOnAddButRecoverableViaReplaceAll() {
        val dir = Files.createTempDirectory("mkw-markers-corrupt").toFile()
        try {
            val file = File(dir, TrackMarkerStore.FILE_NAME)
            file.writeText("{corrupt json")
            val store = TrackMarkerStore(file)

            assertTrue(store.read().isFailure)
            assertTrue(store.allMarkers().isEmpty())
            assertTrue(runCatching { store.add("FirstCircuit", 10_000L, "Test") }.isFailure)
            assertEquals("{corrupt json", file.readText())

            // Backup restore via replaceAll cleanly overwrites the corrupt file
            val restored = store.replaceAll(
                listOf(TrackMarker("r1", "FirstCircuit", 12_000L, "Restored", TrackMarkerColors.ORANGE, 4_000L)),
            )
            assertEquals(1, restored.size)
            assertEquals(1, store.read().getOrThrow().size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun concurrentMultiThreadAddsRemainConsistent() {
        val dir = Files.createTempDirectory("mkw-markers-concurrent").toFile()
        try {
            val store = TrackMarkerStore.inFilesDir(dir)
            val threadCount = 6
            val perThread = 8
            val pool = Executors.newFixedThreadPool(threadCount)
            val startGate = CountDownLatch(1)
            val doneGate = CountDownLatch(threadCount)
            val errors = mutableListOf<Throwable>()

            repeat(threadCount) { tIdx ->
                pool.execute {
                    try {
                        startGate.await(5, TimeUnit.SECONDS)
                        repeat(perThread) { i ->
                            store.add(
                                trackId = "Course_$tIdx",
                                positionMs = (i * 5_000L),
                                label = "T${tIdx}_M$i",
                                colorArgb = TrackMarkerColors.palette[i % TrackMarkerColors.palette.size],
                            )
                        }
                    } catch (t: Throwable) {
                        synchronized(errors) { errors += t }
                    } finally {
                        doneGate.countDown()
                    }
                }
            }

            startGate.countDown()
            assertTrue(doneGate.await(15, TimeUnit.SECONDS))
            pool.shutdownNow()
            assertTrue(errors.isEmpty(), "Unexpected concurrent errors: $errors")
            assertEquals(threadCount * perThread, TrackMarkerStore.inFilesDir(dir).read().getOrThrow().size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun backupRoundTripIncludesMarkersAndRemainsBackwardCompatibleWhenOmitted() {
        val markers = listOf(
            TrackMarker("mk-1", "FirstCircuit", 24_000L, "Bridge", TrackMarkerColors.CRIMSON, 1_100L),
            TrackMarker("mk-2", "jb:ost_07", 88_500L, "Chorus", TrackMarkerColors.SKY, 2_200L),
        )

        val jsonWithMarkers = UserBackupManager.exportToJson(
            playlists = emptyList(),
            favorites = setOf("FirstCircuit"),
            presets = emptyList(),
            customRoutes = emptyList(),
            recentCourses = listOf("FirstCircuit"),
            playStats = emptyMap(),
            preferences = BackupPreferencesSnapshot(),
            appVersion = "0.1.0-beta06",
            exportedAtEpochMs = 123_456L,
            markers = markers,
        )

        val parsed = UserBackupManager.parseBackupJson(jsonWithMarkers).getOrThrow()
        assertEquals(markers, parsed.markers)

        // Legacy backup JSON without "markers" field must still parse cleanly
        val legacyRoot = JSONObject(jsonWithMarkers)
        legacyRoot.remove("markers")
        val legacyParsed = UserBackupManager.parseBackupJson(legacyRoot.toString()).getOrThrow()
        assertTrue(legacyParsed.markers.isEmpty())
        assertEquals(listOf("FirstCircuit"), legacyParsed.favorites)
    }

    @Test
    fun formatMarkerTimestampFormatsMinutesSecondsAndHours() {
        assertEquals("00:00", formatMarkerTimestampMs(-100L))
        assertEquals("00:00", formatMarkerTimestampMs(0L))
        assertEquals("01:15", formatMarkerTimestampMs(75_400L))
        assertEquals("1:01:05", formatMarkerTimestampMs(3_665_000L))
    }

    @Test
    fun decodeMedleyToFlowDraftHandlesAllBuiltInCourseMedleySuites() {
        val assetFile = listOf(
            File("src/main/assets/course_medley_catalog.json"),
            File("app/src/main/assets/course_medley_catalog.json"),
        ).first { it.isFile }
        val catalog = CourseMedleyCatalog.parse(assetFile.readText())
        assertTrue(catalog.medleys.size >= 120)

        fun syntheticInventory(courseCode: String, medleys: List<CourseMedleyCatalog.Medley>): CourseFlowRecipeBuilder.CourseInventory {
            val sr = catalog.sampleRate
            val builtIns = medleys.map { m ->
                CourseFlowRecipeBuilder.BuiltInPresetSummary(
                    ref = PresetRefs.forSuiteVariant(m.normalizedSuiteVariant),
                    medley = m,
                    isKnockout = m.isKnockoutVariant,
                    outroToNextPreFrame = null,
                    incomingPrePhase = 0L,
                )
            }
            return CourseFlowRecipeBuilder.CourseInventory(
                courseCode = courseCode,
                parts = emptyList(),
                outros = emptyList(),
                builtInPresets = builtIns,
                hasFanfareAsset = true,
                sampleRate = sr,
            )
        }

        // Every medley in the bundled catalog must decode to a valid non-empty draft
        catalog.medleys.groupBy { it.courseCode }.forEach { (courseCode, medleys) ->
            val inv = syntheticInventory(courseCode, medleys)
            medleys.forEach { medley ->
                val draft = decodeMedleyToFlowDraft(medley, inv)
                assertTrue(draft.steps.isNotEmpty(), "Empty steps for $courseCode:${medley.variant}")
                if (draft.endingOutro == null) {
                    assertEquals(10_000, draft.fadeOutMs, "Expected 10s fadeOut for $courseCode:${medley.variant}")
                    assertFalse(draft.outroToNextPre)
                } else {
                    assertEquals(0, draft.fadeOutMs, "Expected 0ms fadeOut when Outro present for $courseCode:${medley.variant}")
                    assertTrue(draft.outroToNextPre)
                }
            }
        }

        // Spot-check key multi-variant courses
        val fcInv = syntheticInventory("FirstCircuit", catalog.forCourse("FirstCircuit"))
        val fcA = decodeMedleyToFlowDraft(catalog.forCourse("FirstCircuit").first { it.normalizedSuiteVariant == "a" }, fcInv)
        assertEquals(listOf("Pre", "Bridge", "Main"), fcA.steps.map { it.roleKey })
        assertEquals(1, fcA.steps.last().loopCount)
        assertEquals(0, fcA.endingOutro)

        val fcB = decodeMedleyToFlowDraft(catalog.forCourse("FirstCircuit").first { it.normalizedSuiteVariant == "b" }, fcInv)
        assertEquals(listOf("Pre_Short", "Bridge", "Main"), fcB.steps.map { it.roleKey })
        assertEquals(1, fcB.steps.first().loopCount)

        val fcC = decodeMedleyToFlowDraft(catalog.forCourse("FirstCircuit").first { it.normalizedSuiteVariant == "c" }, fcInv)
        assertEquals(listOf("Main", "Fast"), fcC.steps.map { it.roleKey })
        assertNull(fcC.endingOutro)
        assertEquals(10_000, fcC.fadeOutMs)

        val cinemaInv = syntheticInventory("Cinema", catalog.forCourse("Cinema"))
        val cinemaA = decodeMedleyToFlowDraft(catalog.forCourse("Cinema").first { it.normalizedSuiteVariant == "a" }, cinemaInv)
        assertEquals(listOf("Pre", "Main", "Pattern2"), cinemaA.steps.map { it.roleKey })
        assertEquals(5, cinemaA.endingOutro)

        val koopaInv = syntheticInventory("KoopaTroopaBeach", catalog.forCourse("KoopaTroopaBeach"))
        val koopaA = decodeMedleyToFlowDraft(catalog.forCourse("KoopaTroopaBeach").first { it.normalizedSuiteVariant == "a" }, koopaInv)
        assertEquals(listOf("Pre", "Bridge", "Main", "Main_Speaker"), koopaA.steps.map { it.roleKey })
        assertEquals(0, koopaA.endingOutro)

        val dkInv = syntheticInventory("DonkeyKong", catalog.forCourse("DonkeyKong"))
        val dkA = decodeMedleyToFlowDraft(catalog.forCourse("DonkeyKong").first { it.normalizedSuiteVariant == "a" }, dkInv)
        assertEquals(listOf("Pre", "Main", "Region2", "Region3", "Region4", "Region5", "Fast"), dkA.steps.map { it.roleKey })
        assertNull(dkA.endingOutro)
        assertEquals(10_000, dkA.fadeOutMs)

        val dkAAlt = decodeMedleyToFlowDraft(catalog.forCourse("DonkeyKong").first { it.normalizedSuiteVariant == "a_alt" }, dkInv)
        assertEquals(listOf("Pre", "Main"), dkAAlt.steps.map { it.roleKey })
        assertEquals(1, dkAAlt.steps.last().loopCount)
        assertEquals(0, dkAAlt.endingOutro)

        val dkB = decodeMedleyToFlowDraft(catalog.forCourse("DonkeyKong").first { it.normalizedSuiteVariant == "b" }, dkInv)
        assertEquals(listOf("Pre_Short", "Main"), dkB.steps.map { it.roleKey })
        assertEquals(1, dkB.steps.first().loopCount)
        assertEquals(1, dkB.steps.last().loopCount)
        assertEquals(0, dkB.endingOutro)

        val rainbowInv = syntheticInventory("Rainbow", catalog.forCourse("Rainbow"))
        val rainbowA = decodeMedleyToFlowDraft(catalog.forCourse("Rainbow").first { it.normalizedSuiteVariant == "a" }, rainbowInv)
        assertEquals(
            listOf("Area1_Pre", "Area1_Bridge", "Area1", "Area2", "Area2_Outro04", "Area3_Pre", "Area3", "Final_Pre", "Final"),
            rainbowA.steps.map { it.roleKey },
        )
        assertNull(rainbowA.endingOutro)
        assertEquals(10_000, rainbowA.fadeOutMs)
    }
}
