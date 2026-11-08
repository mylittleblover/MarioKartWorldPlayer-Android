package com.mylittleblover.mkwplayer.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The v2 library adds logical tracks alongside the flat rows.
 *
 * The upgrade path is the reason this file exists. A v1 file has no track array, and the
 * tempting implementations are both wrong: refusing the file loses the user's list, and reading
 * it as empty does the same silently. Its rows become single-file tracks instead.
 */
class LibraryStoreTracksTest {

    private fun tempDir(): File =
        createTempDir(prefix = "library-tracks").also { it.deleteOnExit() }

    private fun store(dir: File = tempDir()) = LibraryStore(File(dir, "library.json"))

    private fun row(name: String) = LibraryStore.Row(
        key = "key-$name",
        name = name,
        frames = 1000,
        sampleRate = 48_000,
        playable = true,
    )

    private fun track(id: String, assets: List<String>, isFlow: Boolean = assets.size > 1) =
        LibraryStore.TrackRow(
            id = id,
            title = "Track $id",
            kind = "COURSE",
            primary = assets.first(),
            assets = assets,
            frames = 3_373_711,
            sampleRate = 48_000,
            isFlow = isFlow,
        )

    // ---------------------------------------------------------------- round trip

    @Test
    fun tracksSurviveASaveAndReload() {
        val store = store()
        val tracks = listOf(
            track("course:aaa", listOf("a1", "a2", "a3")),
            track("single:bbb", listOf("b1"), isFlow = false),
        )
        assertNull(store.save(listOf(row("A"), row("B")), tracks))

        val loaded = store.load()
        assertNull(loaded.problem)
        assertEquals(2, loaded.tracks.size)
        assertEquals(listOf("a1", "a2", "a3"), loaded.tracks[0].assets)
        assertTrue(loaded.tracks[0].isFlow)
        assertTrue(!loaded.tracks[1].isFlow)
        assertEquals("b1", loaded.tracks[1].primary)
    }

    @Test
    fun aSavedFileIsVersionTwo() {
        val dir = tempDir()
        val file = File(dir, "library.json")
        LibraryStore(file).save(emptyList(), listOf(track("x", listOf("a"))))
        val text = file.readText()
        assertTrue(text.contains("\"schemaVersion\":2"), text.take(80))
        assertTrue(text.contains("\"tracks\""), text.take(200))
    }

    // ---------------------------------------------------------------- upgrade

    @Test
    fun aVersionOneFileStillLoadsAndBecomesSingleTracks() {
        // The upgrade path: no data loss, and the next save writes the new shape.
        val dir = tempDir()
        val file = File(dir, "library.json")
        file.writeText(
            """{"schemaVersion":1,"rows":[
                 {"key":"aaa","name":"First","frames":100,"sampleRate":48000,"playable":true},
                 {"key":"bbb","name":"Second","frames":200,"sampleRate":48000,"playable":false}
               ]}""",
        )

        val loaded = LibraryStore(file).load()
        assertNull(loaded.problem, "a v1 file must not be treated as corrupt")
        assertEquals(2, loaded.rows.size)
        assertEquals(2, loaded.tracks.size)
        assertTrue(loaded.tracks.all { !it.isFlow })
        assertEquals(listOf("aaa"), loaded.tracks[0].assets)
        assertEquals(100L, loaded.tracks[0].frames)
        assertTrue(!file.exists().not(), "the v1 file must not be quarantined")
    }

    @Test
    fun aVersionOneFileKeepsItsNamesAsTrackTitles() {
        val dir = tempDir()
        val file = File(dir, "library.json")
        file.writeText(
            """{"schemaVersion":1,"rows":[{"key":"k","name":"BGM_Stage_Coaster","playable":true}]}""",
        )
        val loaded = LibraryStore(file).load()
        assertEquals("BGM_Stage_Coaster", loaded.tracks.single().title)
    }

    @Test
    fun anUnknownFutureVersionIsStillQuarantined() {
        // Forward compatibility is not claimed: a version this build does not know is set aside
        // rather than half-read.
        val dir = tempDir()
        val file = File(dir, "library.json")
        file.writeText("""{"schemaVersion":9,"rows":[]}""")
        val loaded = LibraryStore(file).load()
        assertNotNull(loaded.problem)
        assertTrue(File(dir, "library.json.corrupt").exists())
    }

    // ---------------------------------------------------------------- malformed tracks

    @Test
    fun aTrackWithoutAssetsIsSkipped() {
        val dir = tempDir()
        val file = File(dir, "library.json")
        file.writeText(
            """{"schemaVersion":2,"rows":[],"tracks":[
                 {"id":"good","title":"G","primary":"a","assets":["a"],"frames":10,"sampleRate":48000},
                 {"id":"bad","title":"B","primary":"a","assets":[],"frames":10,"sampleRate":48000}
               ]}""",
        )
        val loaded = LibraryStore(file).load()
        assertEquals(1, loaded.tracks.size)
        assertEquals("good", loaded.tracks[0].id)
    }

    @Test
    fun aTrackWhosePrimaryIsNotAmongItsAssetsIsSkipped() {
        // That inconsistency would make "play this track" ambiguous; refusing it is safer than
        // picking one of the two claims.
        val dir = tempDir()
        val file = File(dir, "library.json")
        file.writeText(
            """{"schemaVersion":2,"rows":[],"tracks":[
                 {"id":"x","title":"X","primary":"zzz","assets":["a","b"],"frames":10,"sampleRate":48000}
               ]}""",
        )
        assertEquals(0, LibraryStore(file).load().tracks.size)
    }

    @Test
    fun aTrackWithoutAnIdIsSkipped() {
        val dir = tempDir()
        val file = File(dir, "library.json")
        file.writeText(
            """{"schemaVersion":2,"rows":[],"tracks":[
                 {"id":"","title":"X","primary":"a","assets":["a"],"frames":10,"sampleRate":48000}
               ]}""",
        )
        assertEquals(0, LibraryStore(file).load().tracks.size)
    }

    @Test
    fun aTrackTitleFallsBackToItsId() {
        val dir = tempDir()
        val file = File(dir, "library.json")
        file.writeText(
            """{"schemaVersion":2,"rows":[],"tracks":[
                 {"id":"only-id","title":"","primary":"a","assets":["a"],"frames":10,"sampleRate":48000}
               ]}""",
        )
        assertEquals("only-id", LibraryStore(file).load().tracks.single().title)
    }

    @Test
    fun isFlowIsInferredWhenAbsent() {
        val dir = tempDir()
        val file = File(dir, "library.json")
        file.writeText(
            """{"schemaVersion":2,"rows":[],"tracks":[
                 {"id":"multi","title":"M","primary":"a","assets":["a","b"],"frames":10,"sampleRate":48000}
               ]}""",
        )
        assertTrue(LibraryStore(file).load().tracks.single().isFlow)
    }

    @Test
    fun aMissingTrackArrayYieldsNoTracksButKeepsRows() {
        val dir = tempDir()
        val file = File(dir, "library.json")
        file.writeText(
            """{"schemaVersion":2,"rows":[{"key":"k","name":"N","playable":true}]}""",
        )
        val loaded = LibraryStore(file).load()
        assertEquals(1, loaded.rows.size)
        assertEquals(0, loaded.tracks.size)
    }

    @Test
    fun savingWithoutTracksStillWritesTheArray() {
        // An empty array is what lets the next load tell "no tracks yet" from "old schema".
        val dir = tempDir()
        val file = File(dir, "library.json")
        val store = LibraryStore(file)
        store.save(listOf(row("A")))
        assertTrue(file.readText().contains("\"tracks\":[]"))
        assertEquals(0, store.load().tracks.size)
    }
}
