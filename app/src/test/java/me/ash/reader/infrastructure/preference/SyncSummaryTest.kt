package me.ash.reader.infrastructure.preference

import kotlinx.serialization.json.Json
import me.ash.reader.domain.model.general.OperationFailureKind
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncSummaryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `summary serialization remains forward compatible`() {
        val summary = SyncSummary(
            accountId = 7,
            state = PersistedSyncState.FAILED,
            startedAtMillis = 100,
            finishedAtMillis = 200,
            completed = 3,
            total = 10,
            errorMessage = "401 unauthorized",
            failureKind = OperationFailureKind.AUTHENTICATION,
            failedFeedIds = listOf("feed-1", "feed-2"),
        )

        val decoded = json.decodeFromString(
            SyncSummary.serializer(),
            json.encodeToString(SyncSummary.serializer(), summary),
        )

        assertEquals(summary, decoded)
    }

    @Test
    fun `older persisted summary receives reliable defaults`() {
        val decoded = json.decodeFromString(
            SyncSummary.serializer(),
            """{"accountId":7,"state":"RUNNING","startedAtMillis":100}""",
        )

        assertEquals(0, decoded.attempt)
        assertEquals(SyncScope.ACCOUNT, decoded.scope)
        assertEquals(emptyList<String>(), decoded.failedFeedNames)
    }
}
