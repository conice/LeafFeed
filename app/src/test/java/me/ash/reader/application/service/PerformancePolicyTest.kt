package me.ash.reader.application.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformancePolicyTest {
    @Test
    fun localSyncBatchFlushesAtFeedOrArticleThreshold() {
        assertFalse(shouldFlushLocalSyncBatch(feedCount = 11, articleCount = 199))
        assertTrue(shouldFlushLocalSyncBatch(feedCount = 12, articleCount = 0))
        assertTrue(shouldFlushLocalSyncBatch(feedCount = 1, articleCount = 200))
    }

    @Test
    fun syncProgressIsThrottledButCompletionIsImmediate() {
        assertFalse(
            shouldPublishSyncProgress(
                nowMillis = 1_500L,
                lastPublishedAtMillis = 1_000L,
                completed = 20,
                total = 100,
            )
        )
        assertTrue(
            shouldPublishSyncProgress(
                nowMillis = 1_000L + SYNC_PROGRESS_MIN_INTERVAL_MS,
                lastPublishedAtMillis = 1_000L,
                completed = 21,
                total = 100,
            )
        )
        assertTrue(
            shouldPublishSyncProgress(
                nowMillis = 1_001L,
                lastPublishedAtMillis = 1_000L,
                completed = 100,
                total = 100,
            )
        )
    }

    @Test
    fun periodicSyncUsesABoundedCoalescingWindow() {
        assertTrue(syncFlexMinutes(15) >= MIN_SYNC_FLEX_MINUTES)
        assertTrue(syncFlexMinutes(60) < 60)
        assertTrue(syncFlexMinutes(1_440) <= MAX_SYNC_FLEX_MINUTES)
    }

    @Test
    fun syncRetriesHaveAFiniteAttemptLimit() {
        assertTrue(hasSyncAttemptsRemaining(runAttemptCount = 0))
        assertTrue(hasSyncAttemptsRemaining(runAttemptCount = 1))
        assertFalse(hasSyncAttemptsRemaining(runAttemptCount = 2))
    }

    @Test
    fun fullContentPrefetchRemainsBounded() {
        assertTrue(ReaderWorker.PREFETCH_CONCURRENCY <= 2)
        assertTrue(ReaderWorker.PREFETCH_ARTICLE_LIMIT <= 20)
    }

    @Test
    fun automaticUpdateChecksRunDailyAndRecoverFromClockChanges() {
        val now = 10L * AUTOMATIC_UPDATE_CHECK_INTERVAL_MS

        assertTrue(shouldCheckForUpdate(nowMillis = now, lastCheckMillis = null))
        assertFalse(shouldCheckForUpdate(nowMillis = now, lastCheckMillis = now - 1_000L))
        assertTrue(
            shouldCheckForUpdate(
                nowMillis = now,
                lastCheckMillis = now - AUTOMATIC_UPDATE_CHECK_INTERVAL_MS,
            )
        )
        assertTrue(shouldCheckForUpdate(nowMillis = now, lastCheckMillis = now + 1_000L))
    }
}
