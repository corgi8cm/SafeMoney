package io.github.corgi8cm.safemoney.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class UpdateCheckerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var now = Instant.parse("2026-11-08T12:00:00Z")
    private var latest: ReleaseInfo? = ReleaseInfo("0.9.0", "https://example.test/v0.9.0")
    private var calls = 0
    private val feed = object : ReleaseFeed {
        override suspend fun latest(): ReleaseInfo? {
            calls++
            return latest
        }
    }

    private fun checker(feed: ReleaseFeed? = this.feed) = UpdateChecker(context, "0.8.0", feed) { now }

    @After
    fun tearDown() {
        context.getSharedPreferences("update", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun showsNewerVersionAndChecksAtMostDaily() = runTest {
        val c = checker()
        c.checkIfDue()
        assertEquals("0.9.0", c.available.value?.version)
        now = now.plusSeconds(3600)
        c.checkIfDue()
        assertEquals(1, calls)
        now = now.plusSeconds(24 * 3600)
        c.checkIfDue()
        assertEquals(2, calls)
    }

    @Test
    fun sameOrOlderVersionIsNotShown() = runTest {
        latest = ReleaseInfo("0.8.0", "u")
        val c = checker()
        c.checkNow()
        assertNull(c.available.value)
    }

    @Test
    fun dismissHidesUntilANewerReleaseAppears() = runTest {
        val c = checker()
        c.checkNow()
        c.dismiss("0.9.0")
        assertNull(c.available.value)
        latest = ReleaseInfo("0.10.0", "u2")
        c.checkNow()
        assertEquals("0.10.0", c.available.value?.version)
    }

    @Test
    fun disabledOrUnsupportedNeverChecks() = runTest {
        val c = checker()
        c.setEnabled(false)
        c.checkIfDue()
        assertEquals(0, calls)
        val play = checker(feed = null)
        assertFalse(play.supported)
        play.checkIfDue()
        assertNull(play.available.value)
    }
}
