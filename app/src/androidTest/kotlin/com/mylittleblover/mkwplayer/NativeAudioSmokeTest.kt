package com.mylittleblover.mkwplayer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mylittleblover.player2.platform.audio.NativeAudioLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S5 instrumentation evidence: the native library actually loads and executes
 * on a real Android runtime.
 *
 * Everything here runs on the device. A passing build proves compilation; only
 * this proves the .so can be dlopen'd, JNI can bind, and the engine can be
 * created and destroyed.
 */
@RunWith(AndroidJUnit4::class)
class NativeAudioSmokeTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun nativeLibraryLoads() {
        // If libmkw_native.so were missing or had an unresolved symbol, the
        // object initialiser would have thrown before this line.
        assertNotNull(NativeAudioLibrary)
    }

    @Test
    fun transportSampleRateIsPositive() {
        val rate = NativeAudioLibrary.transportSampleRate()
        assertTrue("transport sample rate must be positive, got $rate", rate > 0)
    }

    @Test
    fun engineCanBeCreatedAndDestroyed() {
        val handle = NativeAudioLibrary.createEngine()
        assertTrue("createEngine returned a null handle", handle != 0L)
        NativeAudioLibrary.destroyEngine(handle)
    }

    @Test
    fun engineSampleRateMatchesTransport() {
        val handle = NativeAudioLibrary.createEngine()
        try {
            val engineRate = NativeAudioLibrary.engineSampleRate(handle)
            assertTrue("engine sample rate must be positive, got $engineRate", engineRate > 0)
            assertEquals(NativeAudioLibrary.transportSampleRate(), engineRate)
        } finally {
            NativeAudioLibrary.destroyEngine(handle)
        }
    }

    @Test
    fun engineSnapshotIsReadable() {
        val handle = NativeAudioLibrary.createEngine()
        try {
            val snapshot = NativeAudioLibrary.snapshot(handle)
            assertTrue("snapshot should return fields, got ${snapshot.size}", snapshot.isNotEmpty())
        } finally {
            NativeAudioLibrary.destroyEngine(handle)
        }
    }

    @Test
    fun renderProducesFramesWithoutCrashing() {
        val handle = NativeAudioLibrary.createEngine()
        try {
            // Stereo interleaved: 2 floats per frame.
            val frames = 512
            val buffer = FloatArray(frames * 2)
            val status = NativeAudioLibrary.renderRealtime(handle, buffer, frames)
            assertTrue("renderRealtime returned status $status", status >= 0)
        } finally {
            NativeAudioLibrary.destroyEngine(handle)
        }
    }

    @Test
    fun nativeLibrariesArePackagedInTheApk() {
        // extractNativeLibs=false on modern AGP: the .so files are mapped straight
        // out of the APK and never appear under nativeLibraryDir. Looking there
        // finds nothing even though the libraries loaded fine, so inspect the
        // installed APK itself.
        val apk = java.io.File(context.applicationInfo.sourceDir)
        assertTrue("APK not found at ${apk.absolutePath}", apk.exists())

        val present = mutableSetOf<String>()
        java.util.zip.ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("lib/") && it.endsWith(".so") }
                .forEach { present += it.substringAfterLast('/') }
        }

        listOf("libmkw_native.so", "libgme.so", "liboboe.so", "libc++_shared.so")
            .forEach { name ->
                assertTrue("$name missing from the APK; found $present", name in present)
            }
    }
}
