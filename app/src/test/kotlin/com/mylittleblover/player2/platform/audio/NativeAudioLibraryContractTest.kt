package com.mylittleblover.player2.platform.audio

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the JNI boundary against silent signature drift.
 *
 * The native library exports its functions under a fully qualified name derived
 * from the Kotlin package and object. If someone moves the package, renames the
 * object, or adds a declaration without the matching C++ side, the failure at
 * runtime is a `NoSuchMethodError` on first call -- far from the change that
 * caused it.
 *
 * `jni-symbols.txt` is captured from the built `.so` with `llvm-nm`. Any declared
 * function not in that list fails here instead of on a device.
 */
class NativeAudioLibraryContractTest {

    private fun exportedSymbols(): List<String> =
        javaClass.classLoader!!
            .getResourceAsStream("jni-symbols.txt")
            ?.bufferedReader()?.readLines()
            ?.filter { it.isNotBlank() && !it.startsWith("#") }
            ?: error("jni-symbols.txt missing from test resources")

    /** Names of the `external` members, read reflectively from the compiled object. */
    private fun declaredFunctionNames(): List<String> =
        NativeAudioLibrary::class.java.declaredMethods
            .filter { java.lang.reflect.Modifier.isNative(it.modifiers) }
            .map { it.name }
            .sorted()

    @Test
    fun `every declared function exists in the native library`() {
        val exported = exportedSymbols().toSet()
        val missing = declaredFunctionNames().filter { it !in exported }

        assertTrue(
            missing.isEmpty(),
            "declared in Kotlin but absent from libmkw_native.so: $missing",
        )
    }

    @Test
    fun `native library exports no fewer functions than declared`() {
        val exported = exportedSymbols().toSet()
        val declared = declaredFunctionNames().toSet()

        assertTrue(
            declared.isNotEmpty(),
            "reflection found no native methods; the JNI object shape changed",
        )
        assertTrue(
            exported.containsAll(declared),
            "symbol manifest does not cover every declaration",
        )
    }

    @Test
    fun `symbol manifest is not empty`() {
        assertTrue(exportedSymbols().size >= 40, "symbol manifest looks truncated")
    }

    @Test
    fun `jni symbol name keeps the player2 package`() {
        // JNI binding requires this exact package; renaming it silently breaks
        // every native call. The native side is what pins it, not preference.
        val pkg = NativeAudioLibrary::class.java.packageName
        assertTrue(
            pkg == "com.mylittleblover.player2.platform.audio",
            "JNI symbols are compiled as Java_com_mylittleblover_player2_platform_audio_*, " +
                "so the package must not change; got '$pkg'",
        )
    }

    @Test
    fun `object name keeps the native library class name`() {
        assertTrue(
            NativeAudioLibrary::class.java.simpleName == "NativeAudioLibrary",
            "native symbols embed the class name NativeAudioLibrary",
        )
    }
}
