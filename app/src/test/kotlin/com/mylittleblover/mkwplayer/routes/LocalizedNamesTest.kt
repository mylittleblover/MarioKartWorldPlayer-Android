package com.mylittleblover.mkwplayer.routes

import java.io.File
import org.json.JSONObject
import kotlin.test.*

class LocalizedNamesTest {
    private val source get() = File("src/main/assets/localized_names.json").readText()
    @Test fun allFiftyTwoSourceBackedNamesCoverAllFourLanguages() {
        val rows = JSONObject(source).getJSONArray("entries")
        assertEquals(52, rows.length())
        for (index in 0 until rows.length()) {
            val entry = rows.getJSONObject(index)
            for (language in listOf("en", "zh-Hans", "zh-Hant", "ja")) assertTrue(entry.getJSONObject("names").getString(language).isNotBlank())
            assertTrue(entry.getString("sourceSha256").matches(Regex("[0-9a-f]{64}")))
        }
        val names = assertNotNull(LocalizedNames.parse(source))
        assertEquals("马力欧兄弟赛道", names.name("FirstCircuit", "zh-CN"))
        assertEquals("瑪利歐兄弟賽道", names.name("FirstCircuit_00", "zh-TW"))
        assertEquals("マリオブラザーズサーキット", names.name("FirstCircuit", "ja"))
        assertEquals("Mario Bros. Circuit", names.name("FirstCircuit", "de"))
        assertNull(names.name("UnknownStage", "en"))
    }
    @Test fun missingTranslationFallsBackToEnglishWithoutChangingTheStageId() {
        val root = JSONObject(source)
        root.getJSONArray("entries").getJSONObject(0).getJSONObject("names").remove("ja")
        assertEquals("Mario Bros. Circuit", assertNotNull(LocalizedNames.parse(root.toString())).name("FirstCircuit", "ja"))
    }
    @Test fun duplicateNameIdentitiesAreRejected() {
        val root = JSONObject(source)
        root.getJSONArray("entries").getJSONObject(1).put("id", "FirstCircuit")
        assertNull(LocalizedNames.parse(root.toString()))
    }
}
