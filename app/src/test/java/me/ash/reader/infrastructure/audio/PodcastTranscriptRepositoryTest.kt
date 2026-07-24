package me.ash.reader.infrastructure.audio

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastTranscriptRepositoryTest {
    private val repository = PodcastTranscriptRepository(OkHttpClient())

    @Test
    fun parsesPodcastingJsonTranscript() {
        val cues = repository.parse(
            """[{"startTime":1.5,"body":"First line"},{"startTime":3,"body":"Second line"}]"""
        )

        assertEquals(2, cues.size)
        assertEquals(1_500L, cues.first().startMs)
        assertEquals("Second line", cues.last().text)
    }

    @Test
    fun parsesWebVttTranscript() {
        val cues = repository.parse(
            """
            WEBVTT

            00:00:02.000 --> 00:00:04.000
            Hello world
            """.trimIndent()
        )

        assertEquals(listOf(PodcastTranscriptCue(2_000L, "Hello world")), cues)
    }
}
