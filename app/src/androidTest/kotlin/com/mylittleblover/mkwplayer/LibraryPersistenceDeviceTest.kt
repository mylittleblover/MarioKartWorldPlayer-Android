package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.library.LibraryStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The library survives a restart, and a corrupted file does not silently erase it.
 *
 * Runs on the device because the real failure modes are filesystem ones: the app's
 * own `filesDir`, an interrupted write, and a file left behind by an older build.
 */
@RunWith(AndroidJUnit4::class)
class LibraryPersistenceDeviceTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var file: File

    @Before
    fun setUp() {
        file = File(context.filesDir, "library-test.json")
        file.delete()
        File(context.filesDir, "library-test.json.corrupt").delete()
    }

    @After
    fun tearDown() {
        file.delete()
        File(context.filesDir, "library-test.json.corrupt").delete()
    }

    private fun row(name: String, playable: Boolean = true) = LibraryStore.Row(
        key = "key-$name",
        name = name,
        frames = 3_373_711,
        sampleRate = 48_000,
        playable = playable,
        reason = if (playable) null else "unsupported codec",
    )

    @Test
    fun aFreshInstallHasAnEmptyLibrary() {
        val loaded = LibraryStore(file).load()
        assertEquals(0, loaded.rows.size)
        assertNull(loaded.problem)
    }

    @Test
    fun rowsSurviveANewStoreInstance() {
        // A new instance is what a process restart produces.
        assertNull(LibraryStore(file).save(listOf(row("Coaster"), row("Juke", playable = false))))

        val loaded = LibraryStore(file).load()
        assertEquals(2, loaded.rows.size)
        assertEquals("Coaster", loaded.rows[0].name)
        assertTrue(loaded.rows[0].playable)
        assertTrue(!loaded.rows[1].playable)
    }

    @Test
    fun theFileLivesInAppStorage() {
        LibraryStore(file).save(listOf(row("A")))
        assertTrue(
            "library leaked outside app storage: ${file.absolutePath}",
            file.absolutePath.startsWith(context.filesDir.absolutePath),
        )
    }

    @Test
    fun aCorruptFileIsQuarantinedNotDeleted() {
        file.writeText("not json at all")

        val loaded = LibraryStore(file).load()
        assertEquals(0, loaded.rows.size)
        assertNotNull(loaded.problem)
        assertTrue(!file.exists())
        assertTrue(File(context.filesDir, "library-test.json.corrupt").exists())
    }

    @Test
    fun overwritingLeavesNoTempFile() {
        val store = LibraryStore(file)
        store.save(listOf(row("A")))
        store.save(listOf(row("B")))

        val temps = context.filesDir.listFiles()?.filter { it.name.startsWith("library-test.json.tmp") }
            ?: emptyList()
        assertEquals(0, temps.size)
    }

    @Test
    fun repeatedSavesReplaceRatherThanDuplicate() {
        val store = LibraryStore(file)
        repeat(5) { store.save(listOf(row("only"))) }
        assertEquals(1, store.load().rows.size)
    }
}
