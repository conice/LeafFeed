package me.ash.reader.application.service

import android.content.Context
import android.os.SystemClock
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout
import me.ash.reader.domain.model.account.Account
import me.ash.reader.application.data.SyncLogger
import me.ash.reader.infrastructure.rss.ReaderCacheHelper
import me.ash.reader.infrastructure.preference.PersistedSyncState
import me.ash.reader.infrastructure.preference.SyncStatusStore
import me.ash.reader.infrastructure.preference.SyncSummary
import me.ash.reader.infrastructure.preference.SyncScope
import me.ash.reader.infrastructure.widget.WidgetUpdateWorker
import me.ash.reader.domain.model.general.toOperationFailure

@HiltWorker
class SyncWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val rssService: RssService,
    private val readerCacheHelper: ReaderCacheHelper,
    private val syncLogger: SyncLogger,
    private val syncStatusStore: SyncStatusStore,
    private val workManager: WorkManager,
    private val accountService: AccountService,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val data = inputData
        val accountId = data.getInt("accountId", -1)
        require(accountId != -1)
        val account = accountService.getAccountById(accountId)
            ?: return Result.failure(workDataOf(ERROR_MESSAGE to "Account no longer exists"))
        // Resolve the implementation from the worker's account, never from the foreground
        // selection. Multiple accounts may run periodic work concurrently.
        val accountRssService = rssService.get(account.type.id)
        val feedId = data.getString("feedId")
        val groupId = data.getString("groupId")
        val scope = when {
            feedId != null -> SyncScope.FEED
            groupId != null -> SyncScope.GROUP
            else -> SyncScope.ACCOUNT
        }
        val attempt = runAttemptCount
        val startedAt = System.currentTimeMillis()
        var completed = 0
        var total: Int? = null
        var failedFeedIds = emptyList<String>()
        var lastProgressPublishedAt = SystemClock.elapsedRealtime()
        syncStatusStore.write(
            SyncSummary(
                accountId = accountId,
                state = PersistedSyncState.RUNNING,
                startedAtMillis = startedAt,
                attempt = attempt,
                scope = scope,
            )
        )

        return runCatching {
            accountRssService
                .sync(accountId = accountId, feedId = feedId, groupId = groupId) { progress ->
                    completed = progress.completed
                    total = progress.total
                    failedFeedIds = progress.failedFeedIds
                    val now = SystemClock.elapsedRealtime()
                    if (
                        shouldPublishSyncProgress(
                            nowMillis = now,
                            lastPublishedAtMillis = lastProgressPublishedAt,
                            completed = progress.completed,
                            total = progress.total,
                        )
                    ) {
                        lastProgressPublishedAt = now
                        syncStatusStore.write(
                            SyncSummary(
                                accountId = accountId,
                                state = PersistedSyncState.RUNNING,
                                startedAtMillis = startedAt,
                                completed = completed,
                                total = total,
                                failedFeedIds = failedFeedIds,
                                attempt = attempt,
                                scope = scope,
                            )
                        )
                        setProgress(
                            workDataOf(
                                PROGRESS_COMPLETED to progress.completed,
                                PROGRESS_TOTAL to progress.total,
                            )
                        )
                    }
                }
                .let { result ->
                    val shouldStopRetrying =
                        result.javaClass == Result.retry().javaClass &&
                            !hasSyncAttemptsRemaining(attempt)
                    val finalResult =
                        if (shouldStopRetrying) {
                            Result.failure(
                                workDataOf(
                                    ERROR_MESSAGE to
                                        "Synchronization failed after $MAX_SYNC_ATTEMPTS attempts"
                                )
                            )
                        } else {
                            result
                        }
                    val succeeded = finalResult.javaClass == Result.success().javaClass
                    syncStatusStore.write(
                        SyncSummary(
                            accountId = accountId,
                            state = when {
                                succeeded -> PersistedSyncState.SUCCEEDED
                                finalResult.javaClass == Result.retry().javaClass ->
                                    PersistedSyncState.RETRYING
                                else -> PersistedSyncState.FAILED
                            },
                            startedAtMillis = startedAt,
                            finishedAtMillis = System.currentTimeMillis(),
                            completed = completed,
                            total = total,
                            errorMessage = finalResult.outputData.getString(ERROR_MESSAGE),
                            failureKind = finalResult.outputData.getString(ERROR_MESSAGE)?.let {
                                IllegalStateException(it).toOperationFailure().kind
                            },
                            failedFeedIds = failedFeedIds,
                            attempt = attempt,
                            scope = scope,
                        )
                    )
                    if (succeeded) {
                        accountRssService.clearKeepArchivedArticles(accountId).forEach {
                            readerCacheHelper.deleteCacheFor(
                                articleId = it.id,
                                accountId = accountId,
                            )
                        }
                    }
                    // Widget data still reflects the selected account. ReaderWorker receives the
                    // explicit ID so a later account switch cannot redirect queued prefetch work.
                    if (succeeded && accountService.getCurrentAccountId() == accountId) {
                        workManager
                            .beginUniqueWork(
                                uniqueWorkName = postSyncWorkName(accountId),
                                existingWorkPolicy = ExistingWorkPolicy.KEEP,
                                OneTimeWorkRequestBuilder<ReaderWorker>()
                                    .addTag(READER_TAG)
                                    .addTag(ONETIME_WORK_TAG)
                                    .setInputData(workDataOf(ReaderWorker.ACCOUNT_ID to accountId))
                                    .setConstraints(
                                        Constraints.Builder()
                                            .setRequiredNetworkType(
                                                NetworkType.UNMETERED
                                            )
                                            .setRequiresBatteryNotLow(true)
                                            .setRequiresCharging(true)
                                            .build()
                                    )
                                    .setBackoffCriteria(
                                        backoffPolicy = BackoffPolicy.EXPONENTIAL,
                                        backoffDelay = 30,
                                        timeUnit = TimeUnit.SECONDS,
                                    )
                                    .build(),
                            )
                            .enqueue()
                        WidgetUpdateWorker.enqueueOneTimeWork(workManager)
                    }
                    finalResult
                }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) {
                syncStatusStore.write(
                    SyncSummary(
                        accountId = accountId,
                        state = PersistedSyncState.CANCELLED,
                        startedAtMillis = startedAt,
                        finishedAtMillis = System.currentTimeMillis(),
                        completed = completed,
                        total = total,
                        failedFeedIds = failedFeedIds,
                        attempt = attempt,
                        scope = scope,
                    )
                )
                throw throwable
            }
            syncLogger.log(throwable)
            val message = throwable.message ?: throwable.javaClass.simpleName
            val failure = throwable.toOperationFailure()
            syncStatusStore.write(
                SyncSummary(
                    accountId = accountId,
                    state = PersistedSyncState.FAILED,
                    startedAtMillis = startedAt,
                    finishedAtMillis = System.currentTimeMillis(),
                    completed = completed,
                    total = total,
                    errorMessage = message,
                    failureKind = failure.kind,
                    failedFeedIds = failedFeedIds,
                    attempt = attempt,
                    scope = scope,
                )
            )
            Result.failure(workDataOf(ERROR_MESSAGE to message))
        }
    }

    companion object {
        private const val SYNC_WORK_NAME_PERIODIC_PREFIX = "LeafFeed"
        @Deprecated("do not use")
        private const val READER_WORK_NAME_PERIODIC = "FETCH_FULL_CONTENT_PERIODIC"
        private const val POST_SYNC_WORK_NAME_PREFIX = "POST_SYNC_WORK"

        private const val SYNC_ONETIME_NAME_PREFIX = "SYNC_ONETIME"

        const val SYNC_TAG = "SYNC_TAG"
        const val READER_TAG = "READER_TAG"
        const val ONETIME_WORK_TAG = "ONETIME_WORK_TAG"
        const val PERIODIC_WORK_TAG = "PERIODIC_WORK_TAG"
        const val PROGRESS_COMPLETED = "progressCompleted"
        const val PROGRESS_TOTAL = "progressTotal"
        const val ERROR_MESSAGE = "errorMessage"
        internal const val MAX_SYNC_ATTEMPTS = 3

        fun cancelOneTimeWork(workManager: WorkManager, accountId: Int? = null) {
            if (accountId == null) workManager.cancelAllWorkByTag(ONETIME_WORK_TAG)
            else workManager.cancelUniqueWork(oneTimeWorkName(accountId))
        }

        fun cancelPeriodicWork(workManager: WorkManager) {
            workManager.cancelAllWorkByTag(PERIODIC_WORK_TAG)
            workManager.cancelUniqueWork(READER_WORK_NAME_PERIODIC)
        }

        fun enqueueOneTimeWork(
            workManager: WorkManager,
            inputData: Data = workDataOf(),
        ): SyncWorkHandle {
            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .addTag(SYNC_TAG)
                    .addTag(ONETIME_WORK_TAG)
                    .setInputData(inputData)
                    .build()
            val enqueueOperation =
                workManager
                    .beginUniqueWork(
                        oneTimeWorkName(inputData.getInt("accountId", -1)),
                        ExistingWorkPolicy.KEEP,
                        request,
                    )
                    .enqueue()
            return SyncWorkHandle(
                requestedId = request.id,
                uniqueWorkName = oneTimeWorkName(inputData.getInt("accountId", -1)),
                enqueueOperation = enqueueOperation,
            )
        }

        fun enqueuePeriodicWork(account: Account, workManager: WorkManager) {
            val syncInterval = account.syncInterval
            val syncOnlyWhenCharging = account.syncOnlyWhenCharging
            val syncOnlyOnWiFi = account.syncOnlyOnWiFi
            val workName = periodicWorkName(account.id ?: -1)

            workManager.enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SyncWorker>(
                    syncInterval.value,
                    TimeUnit.MINUTES,
                    syncFlexMinutes(syncInterval.value),
                    TimeUnit.MINUTES,
                )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiresBatteryNotLow(true)
                            .setRequiresCharging(syncOnlyWhenCharging.value)
                            .setRequiredNetworkType(
                                if (syncOnlyOnWiFi.value) NetworkType.UNMETERED
                                else NetworkType.CONNECTED
                            )
                            .build()
                    )
                    .setBackoffCriteria(
                        backoffPolicy = BackoffPolicy.EXPONENTIAL,
                        backoffDelay = 30,
                        timeUnit = TimeUnit.SECONDS,
                    )
                    .setInputData(workDataOf("accountId" to account.id))
                    .addTag(SYNC_TAG)
                    .addTag(PERIODIC_WORK_TAG)
                    .setInitialDelay(syncInterval.value, TimeUnit.MINUTES)
                    .build(),
            )

            workManager.cancelUniqueWork(READER_WORK_NAME_PERIODIC)
        }

        private fun oneTimeWorkName(accountId: Int) = "$SYNC_ONETIME_NAME_PREFIX-$accountId"

        private fun periodicWorkName(accountId: Int) = "$SYNC_WORK_NAME_PERIODIC_PREFIX-$accountId"

        private fun postSyncWorkName(accountId: Int) = "$POST_SYNC_WORK_NAME_PREFIX-$accountId"
    }
}

class SyncWorkHandle internal constructor(
    private val requestedId: UUID,
    private val uniqueWorkName: String,
    private val enqueueOperation: Operation,
) {
    fun workInfoFlow(workManager: WorkManager): Flow<WorkInfo> = flow {
        val actualWork =
            withTimeout(SYNC_WORK_RESOLUTION_TIMEOUT_MS) {
                enqueueOperation.await()
                workManager
                    .getWorkInfosForUniqueWorkFlow(uniqueWorkName)
                    .mapNotNull { workInfos ->
                        workInfos.firstOrNull { it.id == requestedId && !it.state.isFinished }
                            ?: workInfos.firstOrNull { !it.state.isFinished }
                            ?: workInfos.firstOrNull { it.id == requestedId }
                            // KEEP may reject the request as the existing work finishes.
                            ?: workInfos.firstOrNull()
                    }
                    .first()
            }
        emit(actualWork)
        if (!actualWork.state.isFinished) {
            emitAll(workManager.getWorkInfoByIdFlow(actualWork.id).filterNotNull())
        }
    }
}

internal const val SYNC_PROGRESS_MIN_INTERVAL_MS = 1_500L
internal const val SYNC_WORK_RESOLUTION_TIMEOUT_MS = 15_000L
internal const val MIN_SYNC_FLEX_MINUTES = 5L
internal const val MAX_SYNC_FLEX_MINUTES = 30L

internal fun hasSyncAttemptsRemaining(runAttemptCount: Int): Boolean =
    runAttemptCount + 1 < SyncWorker.MAX_SYNC_ATTEMPTS

internal fun syncFlexMinutes(intervalMinutes: Long): Long =
    (intervalMinutes / 4).coerceIn(
        MIN_SYNC_FLEX_MINUTES,
        minOf(MAX_SYNC_FLEX_MINUTES, intervalMinutes),
    )

internal fun shouldPublishSyncProgress(
    nowMillis: Long,
    lastPublishedAtMillis: Long,
    completed: Int,
    total: Int,
): Boolean =
    completed >= total || nowMillis - lastPublishedAtMillis >= SYNC_PROGRESS_MIN_INTERVAL_MS
