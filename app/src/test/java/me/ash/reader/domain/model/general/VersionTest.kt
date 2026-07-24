package me.ash.reader.domain.model.general

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun releaseVersionWithPrefixAndSuffixIsParsed() {
        val latest = "v0.16.2b".toVersion()

        assertEquals("0.16.2", latest.toString())
        assertTrue(latest > "0.16.0".toVersion())
    }

    @Test
    fun revisionVersionIsParsedAndCompared() {
        val latest = "0.16.2.1".toVersion()

        assertEquals("0.16.2.1", latest.toString())
        assertTrue(latest > "0.16.2".toVersion())
    }
}
