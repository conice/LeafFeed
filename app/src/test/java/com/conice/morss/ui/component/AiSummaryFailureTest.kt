package com.conice.morss.ui.component

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `reports the concrete cause chain`() {
        val error =
            IllegalStateException(
                "Unable to create AI request",
                IllegalArgumentException("Expected URL scheme http or https"),
            )

        assertEquals(
            "IllegalStateException: Unable to create AI request\n" +
                "Caused by: IllegalArgumentException: Expected URL scheme http or https",
            error.aiSummaryFailureDetail(),
        )
    }

    @Test
    fun `redacts credentials from reported details`() {
        val detail =
            IllegalArgumentException(
                "Authorization: Bearer sk-super-secret-value api_key=another-secret " +
                    "https://user:password@example.com/v1",
            ).aiSummaryFailureDetail()

        assertFalse(detail.contains("super-secret-value"))
        assertFalse(detail.contains("another-secret"))
        assertFalse(detail.contains("password"))
        assertTrue(detail.contains("Bearer ***"))
        assertTrue(detail.contains("api_key=***"))
        assertTrue(detail.contains("https://user:***@example.com/v1"))
    }
}
