package me.ash.reader.domain.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedUrlTest {
    @Test
    fun equivalentUrlsIgnoreHostCaseDefaultPortAndTrailingSlash() {
        assertTrue(feedUrlsEquivalent("HTTPS://Example.COM:443/feed/", "https://example.com/feed"))
    }

    @Test
    fun equivalentUrlsIgnoreFragments() {
        assertTrue(feedUrlsEquivalent("https://example.com/feed#latest", "https://example.com/feed"))
    }

    @Test
    fun differentPathsOrQueriesAreNotEquivalent() {
        assertFalse(feedUrlsEquivalent("https://example.com/feed", "https://example.com/other"))
        assertFalse(feedUrlsEquivalent("https://example.com/feed?user=1", "https://example.com/feed?user=2"))
    }
}
