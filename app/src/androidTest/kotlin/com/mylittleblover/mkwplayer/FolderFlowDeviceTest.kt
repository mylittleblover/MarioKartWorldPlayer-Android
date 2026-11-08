package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.mkwplayer.audio.AudioEngine
import com.mylittleblover.mkwplayer.graph.StageFlowCompiler
import com.mylittleblover.mkwplayer.library.FolderAudioSource
import com.mylittleblover.mkwplayer.library.StageFlowDiscovery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The flow path end to end, from a folder to real audio.
 *
 * This is the verification the document picker made impossible. Files are copied from the test
 * fixtures into the app's own `stages` folder, scanned the same way the UI scans them, compiled the
 * same way the UI compiles them, and played by the same engine.
 *
 * What it does not cover: the picker itself. A user tapping through SAF is still unverified, and
 * that is stated rather than implied.
 */
@RunWith(AndroidJUnit4::class)
class FolderFlowDeviceTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val folder: File get() = File(context.filesDir, "stages")

    private val sources = mapOf(
        "BGM_Stage_Coaster_Pre_Short.bwav" to "/data/local/tmp/coaster-PreShort.bwav",
        "BGM_Stage_Coaster_Pre.bwav" to "/data/local/tmp/coaster-Pre.bwav",
        "BGM_Stage_Coaster_Bridge.bwav" to "/data/local/tmp/coaster-Bridge.bwav",
        "BGM_Stage_Coaster.bwav" to "/data/local/tmp/coaster.bwav",
        "BGM_Stage_Coaster_Outro00.bwav" to "/data/local/tmp/coaster-Outro00.bwav",
    )

    @Before
    fun setUp() {
        folder.deleteRecursively()
        folder.mkdirs()
    }

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    /** Copies the fixtures in, or returns false when they are absent. */
    private fun populate(): Boolean {
        var copied = 0
        for ((logical, actual) in sources) {
            val source = File(actual)
            if (!source.exists()) continue
            source.copyTo(File(folder, logical), overwrite = true)
            copied++
        }
        return copied == sources.size
    }

    @Test
    fun aPopulatedFolderScansIntoOneFlow() {
        if (!populate()) return

        val scan = FolderAudioSource.scan(folder)
        assertEquals("every part should scan", 5, scan.found.size)
        assertEquals(0, scan.rejected.size)

        val group = StageFlowDiscovery.dominant(scan.groups())
        assertNotNull("the parts should group into one stage", group)
        assertEquals("BGM_Stage_Coaster", group!!.prefix)
        assertEquals(5, group.parts.size)
    }

    @Test
    fun theScannedFlowCompilesAndPlays() {
        if (!populate()) return

        val scan = FolderAudioSource.scan(folder)
        val flow = StageFlowDiscovery.flowFor(StageFlowDiscovery.dominant(scan.groups())!!).getOrThrow()
        val compiled = StageFlowCompiler.compileFromBytes(flow, scan.bytesByName())

        val engine = AudioEngine.create()!!
        try {
            assertEquals(0, engine.loadGraph(compiled.packet))
            compiled.assetPaths.forEachIndexed { index, path ->
                assertEquals(
                    "binding $path",
                    0,
                    engine.bindBwav(index, scan.bytesByName()[path]!!, path),
                )
            }

            val first = compiled.segmentByRole[com.mylittleblover.mkwplayer.library.StageFlow.Role.Pre]!!
            assertEquals(0, engine.play(first))

            var nonZero = 0
            repeat(4) {
                val block = engine.render(frames = 512)
                assertNotNull("render returned null", block)
                nonZero += block!!.count { sample -> sample != 0f }
            }
            assertTrue("the flow from a folder produced no audio", nonZero > 0)
        } finally {
            engine.close()
        }
    }

    @Test
    fun anEmptyFolderIsReportedAsMissingContentNotAsAnError() {
        val scan = FolderAudioSource.scan(folder)
        assertTrue(!scan.missingFolder)
        assertEquals(0, scan.found.size)
        assertEquals(0, scan.rejected.size)
    }

    @Test
    fun anAbsentFolderIsDistinguishedFromAnEmptyOne() {
        folder.deleteRecursively()
        val scan = FolderAudioSource.scan(folder)
        assertTrue("an absent folder must be distinguishable", scan.missingFolder)
    }

    @Test
    fun aFolderHoldingOnlyOnePartStillCompiles() {
        // A user who has only the main file should get a one-part listen, not a refusal.
        val main = File("/data/local/tmp/coaster.bwav")
        if (!main.exists()) return
        main.copyTo(File(folder, "BGM_Stage_Coaster.bwav"), overwrite = true)

        val scan = FolderAudioSource.scan(folder)
        assertEquals(1, scan.found.size)

        val flow = StageFlowDiscovery.flowFor(StageFlowDiscovery.dominant(scan.groups())!!).getOrThrow()
        val compiled = StageFlowCompiler.compileFromBytes(flow, scan.bytesByName())
        assertTrue(compiled.packet.isNotEmpty())
    }

}
