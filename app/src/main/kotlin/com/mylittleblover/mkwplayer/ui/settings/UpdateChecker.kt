package com.mylittleblover.mkwplayer.ui.settings

import com.mylittleblover.mkwplayer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal enum class UpdateErrorKind {
    NETWORK,
    HTTP_ERROR,
    RATE_LIMITED,
    NO_RELEASES,
    PARSE_ERROR,
}

internal sealed interface UpdateCheckResult {
    data class UpToDate(
        val currentVersion: String,
        val latestTag: String,
        val htmlUrl: String,
    ) : UpdateCheckResult

    data class UpdateAvailable(
        val currentVersion: String,
        val latestTag: String,
        val releaseName: String,
        val htmlUrl: String,
        val releaseNotes: String,
        val isPrerelease: Boolean,
    ) : UpdateCheckResult

    data class Error(
        val kind: UpdateErrorKind,
        val detail: String? = null,
    ) : UpdateCheckResult
}

/**
 * Semantic version parser and comparator handling both the new channel-prefix format
 * (`Beta 0.1.6`, `Beta-0.1.6`, `Alpha 0.2.0`, `RC 1.0.0`, `1.0.0`) and legacy release tags
 * (`0.1.0-beta05`, `v0.1.0-beta06`, `v0.1.0-rc1`, `v0.1.0`).
 */
internal data class AppSemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<PrereleaseToken> = emptyList(),
) : Comparable<AppSemVer> {

    sealed interface PrereleaseToken : Comparable<PrereleaseToken> {
        data class Numeric(val value: Long) : PrereleaseToken {
            override fun compareTo(other: PrereleaseToken): Int = when (other) {
                is Numeric -> value.compareTo(other.value)
                is Alpha -> -1 // SemVer 2.0.0: numeric identifiers have lower precedence than non-numeric.
            }
        }

        data class Alpha(val value: String) : PrereleaseToken {
            override fun compareTo(other: PrereleaseToken): Int = when (other) {
                is Numeric -> 1
                is Alpha -> value.compareTo(other.value)
            }
        }
    }

    override fun compareTo(other: AppSemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        val selfPre = prerelease
        val otherPre = other.prerelease
        if (selfPre.isEmpty() && otherPre.isEmpty()) return 0
        // Stable release has higher precedence than a pre-release of the same major.minor.patch.
        if (selfPre.isEmpty()) return 1
        if (otherPre.isEmpty()) return -1

        val minLen = minOf(selfPre.size, otherPre.size)
        for (i in 0 until minLen) {
            val cmp = selfPre[i].compareTo(otherPre[i])
            if (cmp != 0) return cmp
        }
        return selfPre.size.compareTo(otherPre.size)
    }

    companion object {
        private val CHANNEL_PREFIX_REGEX = Regex(
            "^(alpha|beta|rc|pre|preview|dev|canary)[\\s\\-_]+v?(.+)$",
            RegexOption.IGNORE_CASE,
        )
        private val CORE_REGEX = Regex("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:[-\\s_]+(.+))?$")
        private val TOKEN_SPLIT_REGEX = Regex("[0-9]+|[a-zA-Z]+")

        fun parse(raw: String): AppSemVer? {
            val cleaned = raw.trim().substringBefore('+').trim()
            if (cleaned.isEmpty()) return null

            val channelMatch = CHANNEL_PREFIX_REGEX.matchEntire(cleaned)
            val prefixChannel = channelMatch?.groupValues?.get(1)?.lowercase(Locale.ROOT)
            val versionBody = (channelMatch?.groupValues?.get(2) ?: cleaned)
                .trim()
                .removePrefix("v")
                .removePrefix("V")
                .trim()
            if (versionBody.isEmpty()) return null

            val match = CORE_REGEX.matchEntire(versionBody) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val patch = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val preRaw = match.groupValues[4]

            val preTokens = buildList {
                if (prefixChannel != null) {
                    add(PrereleaseToken.Alpha(prefixChannel))
                }
                if (preRaw.isNotEmpty()) {
                    TOKEN_SPLIT_REGEX.findAll(preRaw).forEach { m ->
                        val seg = m.value
                        val num = seg.toLongOrNull()
                        if (num != null) {
                            add(PrereleaseToken.Numeric(num))
                        } else {
                            val lower = seg.lowercase(Locale.ROOT)
                            // Avoid duplicating the channel token if both prefix and suffix carry it.
                            if (prefixChannel == null || lower != prefixChannel || size > 1) {
                                add(PrereleaseToken.Alpha(lower))
                            }
                        }
                    }
                }
            }
            return AppSemVer(major = major, minor = minor, patch = patch, prerelease = preTokens)
        }
    }
}

/**
 * Checks GitHub Releases for newer JukeKart builds (supporting both `Beta X.Y.Z` / `Beta-X.Y.Z`
 * and legacy `v0.1.0-betaXX` tags).
 */
internal object UpdateChecker {
    const val RELEASES_API_URL = "https://api.github.com/repos/mylittleblover/JukeKart-Android/releases"
    const val RELEASES_PAGE_URL = "https://github.com/mylittleblover/JukeKart-Android/releases"
    const val GITHUB_REPO_URL = "https://github.com/mylittleblover/JukeKart-Android"
    const val GITHUB_ISSUES_URL = "https://github.com/mylittleblover/JukeKart-Android/issues"
    const val AUTHOR_BILIBILI_URL = "https://b23.tv/mWNhhtI"

    const val USER_AGENT = "JukeKart-Android"
    const val TIMEOUT_MS = 8_000

    private data class ReleaseCandidate(
        val tagName: String,
        val name: String,
        val htmlUrl: String,
        val body: String,
        val prerelease: Boolean,
        val parsedVersion: AppSemVer?,
    )

    /**
     * Queries GitHub Releases on [Dispatchers.IO] and compares the highest published release
     * (including prereleases, excluding drafts) against [currentVersion].
     */
    suspend fun checkForUpdates(
        currentVersion: String = BuildConfig.VERSION_NAME,
        apiUrl: String = RELEASES_API_URL,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val code = connection.responseCode
            when {
                code in 200..299 -> {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    parseReleasesJson(body, currentVersion)
                }
                code == 403 || code == 429 -> {
                    UpdateCheckResult.Error(UpdateErrorKind.RATE_LIMITED, detail = code.toString())
                }
                else -> {
                    UpdateCheckResult.Error(UpdateErrorKind.HTTP_ERROR, detail = code.toString())
                }
            }
        } catch (e: IOException) {
            UpdateCheckResult.Error(UpdateErrorKind.NETWORK, detail = e.message)
        } catch (e: Exception) {
            UpdateCheckResult.Error(UpdateErrorKind.PARSE_ERROR, detail = e.message)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Parses a GitHub Releases JSON array and compares the latest non-draft release against
     * [currentVersion]. Pure and offline-testable on the JVM.
     */
    fun parseReleasesJson(json: String, currentVersion: String): UpdateCheckResult {
        val array = try {
            JSONArray(json)
        } catch (e: Exception) {
            return UpdateCheckResult.Error(UpdateErrorKind.PARSE_ERROR, detail = e.message)
        }

        val candidates = ArrayList<ReleaseCandidate>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optBoolean("draft", false)) continue
            val tag = obj.optString("tag_name").trim()
            if (tag.isEmpty()) continue
            val htmlUrl = obj.optString("html_url").trim().ifEmpty { RELEASES_PAGE_URL }
            val name = obj.optString("name").trim().ifEmpty { tag }
            val body = obj.optString("body").trim()
            val prerelease = obj.optBoolean("prerelease", false)
            candidates += ReleaseCandidate(
                tagName = tag,
                name = name,
                htmlUrl = htmlUrl,
                body = body,
                prerelease = prerelease,
                parsedVersion = AppSemVer.parse(tag) ?: AppSemVer.parse(name),
            )
        }

        if (candidates.isEmpty()) {
            return UpdateCheckResult.Error(UpdateErrorKind.NO_RELEASES)
        }

        val latest = candidates
            .filter { it.parsedVersion != null }
            .maxWithOrNull(compareBy { it.parsedVersion })
            ?: candidates.first()

        val currentSemVer = AppSemVer.parse(currentVersion)
        val latestSemVer = latest.parsedVersion

        val isNewer = when {
            currentSemVer != null && latestSemVer != null -> latestSemVer > currentSemVer
            else -> !latest.tagName.removePrefix("v").removePrefix("V")
                .equals(currentVersion.trim().removePrefix("v").removePrefix("V"), ignoreCase = true)
        }

        return if (isNewer) {
            UpdateCheckResult.UpdateAvailable(
                currentVersion = currentVersion,
                latestTag = latest.tagName,
                releaseName = latest.name,
                htmlUrl = latest.htmlUrl,
                releaseNotes = latest.body,
                isPrerelease = latest.prerelease,
            )
        } else {
            UpdateCheckResult.UpToDate(
                currentVersion = currentVersion,
                latestTag = latest.tagName,
                htmlUrl = latest.htmlUrl,
            )
        }
    }

    /** Convenience semantic comparison helper for tests and callers. */
    fun isNewerVersion(candidateTag: String, currentVersion: String): Boolean {
        val cand = AppSemVer.parse(candidateTag) ?: return false
        val curr = AppSemVer.parse(currentVersion) ?: return false
        return cand > curr
    }
}
