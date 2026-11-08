package io.github.corgi8cm.safemoney.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseFeedTest {
    @Test
    fun parsesLatestRelease() {
        val body = """{"tag_name":"v0.9.0","html_url":"https://github.com/corgi8cm/SafeMoney/releases/tag/v0.9.0","name":"x","assets":[]}"""
        assertEquals(
            ReleaseInfo("0.9.0", "https://github.com/corgi8cm/SafeMoney/releases/tag/v0.9.0"),
            parseGitHubLatestRelease(body),
        )
    }

    @Test
    fun ignoresDraftsPrereleasesAndGarbage() {
        assertNull(parseGitHubLatestRelease("""{"tag_name":"v1","html_url":"u","prerelease":true}"""))
        assertNull(parseGitHubLatestRelease("""{"tag_name":"v1","html_url":"u","draft":true}"""))
        assertNull(parseGitHubLatestRelease("""{"message":"Not Found"}"""))
        assertNull(parseGitHubLatestRelease("<html>"))
    }
}
