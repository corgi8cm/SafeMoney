package io.github.corgi8cm.safemoney.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** github 版：從 GitHub Releases 查最新版本。 */
object UpdateFeedProvider {
    private const val LATEST_URL = "https://api.github.com/repos/corgi8cm/SafeMoney/releases/latest"

    val feed: ReleaseFeed? = object : ReleaseFeed {
        override suspend fun latest(): ReleaseInfo? = withContext(Dispatchers.IO) {
            val connection = URL(LATEST_URL).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "SafeMoney-Android")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                parseGitHubLatestRelease(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }
    }
}
