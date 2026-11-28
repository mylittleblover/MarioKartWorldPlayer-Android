package com.mylittleblover.mkwplayer.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateCheckerTest {

    @Test
    fun semVerComparesPrereleasesAndStableVersionsProperly() {
        assertTrue(UpdateChecker.isNewerVersion("v0.1.0-beta07", "0.1.0-beta06"))
        assertTrue(UpdateChecker.isNewerVersion("v0.1.0-beta10", "0.1.0-beta06"))
        assertTrue(UpdateChecker.isNewerVersion("v0.1.0-beta10", "0.1.0-beta9"))
        assertTrue(UpdateChecker.isNewerVersion("0.1.0-rc1", "0.1.0-beta07"))
        assertTrue(UpdateChecker.isNewerVersion("v0.1.0", "0.1.0-beta07"))
        assertTrue(UpdateChecker.isNewerVersion("0.1.0-beta01", "0.1.0-alpha24"))
        assertTrue(UpdateChecker.isNewerVersion("v0.2.0-beta01", "0.1.0"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.0", "0.9.9"))

        assertFalse(UpdateChecker.isNewerVersion("v0.1.0-beta06", "0.1.0-beta06"))
        assertFalse(UpdateChecker.isNewerVersion("0.1.0-beta05", "0.1.0-beta06"))
        assertFalse(UpdateChecker.isNewerVersion("0.1.0-beta07", "0.1.0"))
        assertNull(AppSemVer.parse("not-a-version"))
    }

    @Test
    fun parseReleasesJsonFindsNewerBetaPrereleaseAndIgnoresDrafts() {
        val json = """
            [
              {
                "tag_name": "v9.9.9",
                "name": "Internal Draft",
                "draft": true,
                "prerelease": false,
                "html_url": "https://github.com/mylittleblover/JukeKart-Android/releases/tag/v9.9.9",
                "body": "Should be skipped"
              },
              {
                "tag_name": "v0.1.0-beta06",
                "name": "Beta 06",
                "draft": false,
                "prerelease": true,
                "html_url": "https://github.com/mylittleblover/JukeKart-Android/releases/tag/v0.1.0-beta06",
                "body": "Old release"
              },
              {
                "tag_name": "v0.1.0-beta07",
                "name": "Beta 07",
                "draft": false,
                "prerelease": true,
                "html_url": "https://github.com/mylittleblover/JukeKart-Android/releases/tag/v0.1.0-beta07",
                "body": "New quiz & character themes"
              }
            ]
        """.trimIndent()

        val result = UpdateChecker.parseReleasesJson(json, currentVersion = "0.1.0-beta06")
        val available = assertIs<UpdateCheckResult.UpdateAvailable>(result)
        assertEquals("v0.1.0-beta07", available.latestTag)
        assertEquals("https://github.com/mylittleblover/JukeKart-Android/releases/tag/v0.1.0-beta07", available.htmlUrl)
        assertEquals("New quiz & character themes", available.releaseNotes)
        assertTrue(available.isPrerelease)
    }

    @Test
    fun parseReleasesJsonReportsUpToDateWhenCurrentMatchesOrExceedsLatest() {
        val json = """
            [
              {
                "tag_name": "v0.1.0-beta06",
                "draft": false,
                "prerelease": true,
                "html_url": "https://github.com/mylittleblover/JukeKart-Android/releases/tag/v0.1.0-beta06",
                "body": ""
              }
            ]
        """.trimIndent()

        val same = UpdateChecker.parseReleasesJson(json, currentVersion = "0.1.0-beta06")
        assertIs<UpdateCheckResult.UpToDate>(same)

        val newerLocal = UpdateChecker.parseReleasesJson(json, currentVersion = "0.1.0-beta07")
        assertIs<UpdateCheckResult.UpToDate>(newerLocal)
    }

    @Test
    fun parseReleasesJsonHandlesEmptyAndMalformedPayloads() {
        val empty = UpdateChecker.parseReleasesJson("[]", currentVersion = "0.1.0-beta06")
        assertEquals(UpdateErrorKind.NO_RELEASES, assertIs<UpdateCheckResult.Error>(empty).kind)

        val bad = UpdateChecker.parseReleasesJson("{not-an-array}", currentVersion = "0.1.0-beta06")
        assertEquals(UpdateErrorKind.PARSE_ERROR, assertIs<UpdateCheckResult.Error>(bad).kind)
    }

    @Test
    fun newChannelPrefixVersionFormatParsesAndComparesProperly() {
        assertEquals(AppSemVer.parse("Beta 0.1.6"), AppSemVer.parse("Beta-0.1.6"))
        assertEquals(AppSemVer.parse("Beta 0.1.6"), AppSemVer.parse("v0.1.6-beta"))

        assertTrue(UpdateChecker.isNewerVersion("Beta 0.1.6", "0.1.0-beta05"))
        assertTrue(UpdateChecker.isNewerVersion("Beta-0.1.6", "v0.1.0-beta07"))
        assertTrue(UpdateChecker.isNewerVersion("Beta 0.1.7", "Beta 0.1.6"))
        assertTrue(UpdateChecker.isNewerVersion("Beta-0.2.0", "Beta 0.1.9"))
        assertTrue(UpdateChecker.isNewerVersion("RC 0.1.6", "Beta 0.1.6"))
        assertTrue(UpdateChecker.isNewerVersion("Beta 0.1.6", "Alpha 0.1.6"))
        assertTrue(UpdateChecker.isNewerVersion("0.1.6", "Beta 0.1.6"))
        assertTrue(UpdateChecker.isNewerVersion("v1.0.0", "Beta 0.1.6"))

        assertFalse(UpdateChecker.isNewerVersion("Beta-0.1.6", "Beta 0.1.6"))
        assertFalse(UpdateChecker.isNewerVersion("v0.1.0-beta05", "Beta 0.1.6"))
        assertFalse(UpdateChecker.isNewerVersion("Beta 0.1.5", "Beta 0.1.6"))

        val json = """
            [
              {
                "tag_name": "v0.1.0-beta05",
                "name": "JukeKart v0.1.0-beta05",
                "draft": false,
                "prerelease": true,
                "html_url": "https://github.com/mylittleblover/JukeKart-Android/releases/tag/v0.1.0-beta05",
                "body": "Legacy"
              },
              {
                "tag_name": "Beta-0.1.6",
                "name": "JukeKart Beta 0.1.6",
                "draft": false,
                "prerelease": true,
                "html_url": "https://github.com/mylittleblover/JukeKart-Android/releases/tag/Beta-0.1.6",
                "body": "New version format"
              }
            ]
        """.trimIndent()

        val fromOld = assertIs<UpdateCheckResult.UpdateAvailable>(
            UpdateChecker.parseReleasesJson(json, currentVersion = "0.1.0-beta05"),
        )
        assertEquals("Beta-0.1.6", fromOld.latestTag)

        assertIs<UpdateCheckResult.UpToDate>(
            UpdateChecker.parseReleasesJson(json, currentVersion = "Beta 0.1.6"),
        )
    }
}
