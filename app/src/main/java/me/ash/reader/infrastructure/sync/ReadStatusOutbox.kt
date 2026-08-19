package me.ash.reader.infrastructure.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal suspend fun drainReadStatusOutbox(
    pendingSnapshot: () -> Map<String, Diff>,
    sync: suspend (Map<String, Diff>) -> Set<String>,
    removeSynced: (Map<String, Diff>, Set<String>) -> Unit,
    persist: suspend () -> Unit,
    pause: suspend (Long) -> Unit = { delay(it) },
) {
    var retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS
    while (currentCoroutineContext().isActive) {
        val pending = pendingSnapshot()
        if (pending.isEmpty()) return
        val synced =
            try {
                sync(pending)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptySet()
        }
        if (synced.isNotEmpty()) {
            removeSynced(pending, synced)
            persist()
            retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS
        }
        if (pendingSnapshot().isNotEmpty()) {
            pause(retryDelayMillis)
            retryDelayMillis = (retryDelayMillis * 2).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
        }
    }
}

internal const val INITIAL_RETRY_DELAY_MILLIS = 2_000L
internal const val MAX_RETRY_DELAY_MILLIS = 60_000L
