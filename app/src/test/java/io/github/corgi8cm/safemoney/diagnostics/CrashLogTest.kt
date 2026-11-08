package io.github.corgi8cm.safemoney.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class CrashLogTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() = CrashLog.clear(context)

    @Test
    fun entryContainsTimeThreadAndStackTrace() {
        val text = CrashLog.entry(context, "main", IllegalStateException("壞掉了"), Instant.parse("2026-11-08T12:00:00Z"))
        assertTrue(text.contains("2026-11-08T12:00:00Z"))
        assertTrue(text.contains("Thread: main"))
        assertTrue(text.contains("IllegalStateException: 壞掉了"))
    }

    @Test
    fun appendKeepsOnlyTheNewestPart() {
        assertNull(CrashLog.read(context))
        CrashLog.append(context, "a".repeat(60 * 1024))
        CrashLog.append(context, "b".repeat(10 * 1024))
        val text = CrashLog.read(context)!!
        assertEquals(64 * 1024, text.length)
        assertTrue(text.endsWith("b".repeat(10 * 1024)))
        CrashLog.clear(context)
        assertNull(CrashLog.read(context))
    }
}
