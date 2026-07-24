package me.ash.reader.infrastructure.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastMetadataTest {
    @Test
    fun parsesItunesDurations() {
        assertEquals(42L, parsePodcastDurationSeconds("42"))
        assertEquals(754L, parsePodcastDurationSeconds("12:34"))
        assertEquals(7_384L, parsePodcastDurationSeconds("2:03:04"))
        assertNull(parsePodcastDurationSeconds("unknown"))
    }

    @Test
    fun recognizesCommonPodcastAudioUrlsAndMimeTypes() {
        assertEquals(true, isPodcastAudio("https://example.com/show?id=42", "Audio/MPEG; charset=binary"))
        assertEquals(true, isPodcastAudio("https://example.com/episode.M4B?token=abc", null))
        assertEquals(true, isPodcastAudio("https://example.com/media", "application/ogg"))
        assertEquals(false, isPodcastAudio("https://example.com/cover.jpg", "image/jpeg"))
    }
}
