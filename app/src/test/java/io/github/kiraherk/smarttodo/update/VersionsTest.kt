package io.github.kiraherk.smarttodo.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun comparesNumerically() {
        assertTrue(Versions.isNewer("v1.2.10", "1.2.9"))
        assertTrue(Versions.isNewer("v1.0", "0.9.9"))
        assertTrue(Versions.isNewer("0.2.0", "0.1.0-debug"))
        assertFalse(Versions.isNewer("0.1.0", "0.1.0-debug"))
        assertFalse(Versions.isNewer("v0.9.0", "1.0.0"))
        assertEquals(listOf(1, 2, 0), Versions.parse("v1.2"))
    }
}
