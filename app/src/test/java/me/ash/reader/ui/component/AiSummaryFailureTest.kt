package me.ash.reader.ui.component

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AiSummaryFailureTest {
    @Test
    fun `classifies configuration failures`() {
        assertEquals(
            AiSummaryFailure.NotConfigured,
            AiSummaryFailure.from(IllegalArgumentException("AI URL is not configured")),
        )
    }

    @Test
    fun `classifies HTTP failures`() {
        assertEquals(
            AiSummaryFailure.Authentication,
            AiSummaryFailure.from(IllegalStateException("AI request failed (401): unauthorized")),
        )
        assertEquals(
            AiSummaryFailure.Authentication,
            AiSummaryFailure.from(IllegalStateException("AI request failed (400): invalid API key")),
        )
        assertEquals(
            AiSummaryFailure.RateLimited,
            AiSummaryFailure.from(IllegalStateException("AI request failed (429): try later")),
        )
        assertEquals(
            AiSummaryFailure.ServiceUnavailable,
            AiSummaryFailure.from(IllegalStateException("AI request failed (503): unavailable")),
        )
    }

    @Test
    fun `classifies connection failures through their cause chain`() {
        assertEquals(
            AiSummaryFailure.Network,
            AiSummaryFailure.from(IllegalStateException("request failed", UnknownHostException())),
        )
        assertEquals(
            AiSummaryFailure.Timeout,
            AiSummaryFailure.from(SocketTimeoutException()),
        )
    }

    @Test
    fun `classifies malformed service responses`() {
        val error = runCatching { Json.parseToJsonElement("not json") }.exceptionOrNull()!!
        assertEquals(AiSummaryFailure.InvalidResponse, AiSummaryFailure.from(error))
    }
}
