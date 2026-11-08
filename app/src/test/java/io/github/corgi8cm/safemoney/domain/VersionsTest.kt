package io.github.corgi8cm.safemoney.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun parsesCommonForms() {
        assertEquals(Version(0, 8, 0), Version.parse("v0.8.0"))
        assertEquals(Version(1, 0, 0), Version.parse("1.0"))
        assertEquals(Version(0, 8, 0), Version.parse("0.8.0-debug"))
        assertNull(Version.parse("latest"))
    }

    @Test
    fun comparesNumericallyNotAlphabetically() {
        assertTrue(isNewerVersion("0.10.0", "0.9.0"))
        assertTrue(isNewerVersion("v1.0.0", "0.10.3"))
        assertFalse(isNewerVersion("0.8.0", "0.8.0-debug"))
        assertFalse(isNewerVersion("0.7.0", "0.8.0"))
        assertFalse(isNewerVersion("???", "0.8.0"))
    }
}
