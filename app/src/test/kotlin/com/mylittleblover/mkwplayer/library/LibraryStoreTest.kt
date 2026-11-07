package com.mylittleblover.mkwplayer.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Persistence is the one place data can be lost silently, so the failure paths matter
 * more than the happy path: a corrupt file must not look like an empty library, and a
 * schema change must not delete what the user had.
 */
class LibraryStoreTest {

    private fun tempDir(): File =
        createTempDir(prefix = "library-store").also { it.deleteOnExit() }

    private fun store(dir: File = tempDir()) = LibraryStore(File(dir, "library.json"))

    private fun row(name: String, playable: Boolean = true) = LibraryStore.Row(
        key = "key-$name",
        name = name,
        frames = 3_373_711,
        sampleRate = 48_000,
        playable = playable,
        reason = if (playable) null else "unsupported codec",
    )

    @Test
    fun missingFileIsAnEmptyLibraryNotAProblem() {
        val loaded = store().load()
        assertEquals(0, loaded.rows.size)
        assertNull(loaded.problem)
    }

    @Test
    fun savesAndReloadsRows() {
        val store = store()
        assertNull(store.save(listOf(row("Coaster"), row("PeachBeach", playable = false))))

        val loaded = store.load()
        assertNull(loaded.problem)
        assertEquals(2, loaded.rows.size)
        assertEquals("Coaster", loaded.rows[0].name)
        assertEquals(3_373_711L, loaded.rows[0].frames)
        assertEquals(48_000, loaded.rows[0].sampleRate)
        assertTrue(loaded.rows[0].playable)
        assertTrue(!loaded.rows[1].playable)
        assertEquals("unsupported codec", loaded.rows[1].reason)
    }

    @Test
    fun savingReplacesRatherThanAppends() {
        val store = store()
        store.save(listOf(row("A"), row("B")))
        store.save(listOf(row("C")))

        val loaded = store.load()
        assertEquals(1, loaded.rows.size)
        assertEquals("C", loaded.rows[0].name)
    }

    @Test
    fun emptyListIsWritable() {
        val store = store()
        store.save(listOf(row("A")))
        assertNull(store.save(emptyList()))
        assertEquals(0, store.load().rows.size)
    }

    @Test
    fun corruptFileIsReportedAndSetAside() {
        val dir = tempDir()
        val file = File(dir, "library.json")
        file.writeText("{ this is not json")

        val loaded = LibraryStore(file).load()
        assertEquals(0, loaded.rows.size)
        assertNotNull(loaded.problem, "a corrupt file must be reported, not silently empty")
        assertTrue(file.exists().not(), "the unreadable file should have been moved aside")
        assertTrue(File(dir, "library.json.corrupt").exists(), "the original should be kept")
    }

    @Test
    fun unknownSchemaVersionIsSetAsideRatherThanMisread() {
        val dir = tempDir()
        val file = File(dir, "library.json")
        file.writeText("""{"schemaVersion":999,"rows":[{"key":"k","name":"n"}]}""")

        val loaded = LibraryStore(file).load()
        assertEquals(0, loaded.rows.size)
        assertNotNull(loaded.problem)
        assertTrue(File(dir, "library.json.corrupt").exists())
    }

    @Test
    fun aRowWithoutAKeyIsSkippedAndCounted() {
        val dir = tempDir()
        val file = File(dir, "library.json")
        file.writeText(
            """{"schemaVersion":1,"rows":[
                 {"key":"good","name":"Good","frames":10,"sampleRate":48000,"playable":true},
                 {"key":"","name":"NoKey"}
               ]}""",
        )

        val loaded = LibraryStore(file).load()
        assertEquals(1, loaded.rows.size)
        assertEquals("Good", loaded.rows[0].name)
        assertNotNull(loaded.problem)
        assertTrue(loaded.problem!!.contains("1"))
    }

    @Test
    fun nameFallsBackToTheKey() {
        val dir = tempDir()
        val file = File(dir, "library.json")
        file.writeText("""{"schemaVersion":1,"rows":[{"key":"only-key","name":""}]}""")

        val loaded = LibraryStore(file).load()
        assertEquals("only-key", loaded.rows[0].name)
    }

    @Test
    fun missingOptionalFieldsDoNotBreakTheRow() {
        val dir = tempDir()
        val file = File(dir, "library.json")
        file.writeText("""{"schemaVersion":1,"rows":[{"key":"k"}]}""")

        val loaded = LibraryStore(file).load()
        assertEquals(1, loaded.rows.size)
        assertEquals(0L, loaded.rows[0].frames)
        assertTrue(!loaded.rows[0].playable, "a row with no playable flag must default to not playable")
        assertNull(loaded.rows[0].reason)
    }

    @Test
    fun blankFileIsTreatedAsEmpty() {
        val dir = tempDir()
        val file = File(dir, "library.json")
        file.writeText("   \n  ")

        val loaded = LibraryStore(file).load()
        assertEquals(0, loaded.rows.size)
        assertNull(loaded.problem)
    }

    @Test
    fun directoryIsCreatedWhenMissing() {
        val dir = File(tempDir(), "nested/where")
        val file = File(dir, "library.json")
        val store = LibraryStore(file)

        assertNull(store.save(listOf(row("A"))))
        assertTrue(file.exists())
        assertEquals(1, store.load().rows.size)
    }

    @Test
    fun noTemporaryFileIsLeftBehindAfterASave() {
        val dir = tempDir()
        val store = LibraryStore(File(dir, "library.json"))
        store.save(listOf(row("A")))

        val leftovers = dir.listFiles()?.filter { it.name.endsWith(".tmp") } ?: emptyList()
        assertEquals(0, leftovers.size, "a completed save must not leave a temp file")
    }

    @Test
    fun unplayableRowsKeepTheirReasonAcrossAReload() {
        val store = store()
        store.save(listOf(row("Juke", playable = false)))

        val reloaded = store.load()
        assertEquals("unsupported codec", reloaded.rows[0].reason)
    }

    @Test
    fun manyRowsRoundTrip() {
        val rows = (1..200).map { row("Track$it", playable = it % 3 != 0) }
        val store = store()
        assertNull(store.save(rows))

        val loaded = store.load()
        assertEquals(200, loaded.rows.size)
        assertEquals(rows.map { it.name }, loaded.rows.map { it.name })
        assertEquals(rows.map { it.playable }, loaded.rows.map { it.playable })
    }
}
