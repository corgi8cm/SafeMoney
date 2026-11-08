package io.github.corgi8cm.safemoney.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 一個發佈版本。 */
data class ReleaseInfo(val version: String, val url: String)

/** 查詢最新版本的來源；play 版沒有（null），見各 flavor 的 [UpdateFeedProvider]。 */
interface ReleaseFeed {
    suspend fun latest(): ReleaseInfo?
}

@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

private val json = Json { ignoreUnknownKeys = true }

/** 解析 GitHub `GET /repos/{owner}/{repo}/releases/latest` 的回應；草稿與預覽版不算。 */
fun parseGitHubLatestRelease(body: String): ReleaseInfo? {
    val release = runCatching { json.decodeFromString(GitHubRelease.serializer(), body) }.getOrNull() ?: return null
    if (release.draft || release.prerelease) return null
    return ReleaseInfo(version = release.tagName.removePrefix("v"), url = release.htmlUrl)
}
