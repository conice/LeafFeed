package me.ash.reader.domain.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import me.ash.reader.domain.model.account.Account
import me.ash.reader.domain.data.SyncLogger
import me.ash.reader.infrastructure.rss.ReaderCacheHelper
import me.ash.reader.infrastructure.preference.PersistedSyncState
import me.ash.reader.infrastructure.preference.SyncStatusStore
import me.ash.reader.infrastructure.preference.SyncSummary
import me.ash.reader.infrastructure.preference.SyncScope
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
                .also { result ->
                    val succeeded = result.javaClass == Result.success().javaClass
                    syncStatusStore.write(
                        SyncSummary(
                            accountId = accountId,
                            state = when {
                                succeeded -> PersistedSyncState.SUCCEEDED
                                result.javaClass == Result.retry().javaClass -> PersistedSyncState.RETRYING
                                else -> PersistedSyncState.FAILED
                            },
                            startedAtMillis = startedAt,
                            finishedAtMillis = System.currentTimeMillis(),
                            completed = completed,
                            total = total,
                            errorMessage = result.outputData.getString(ERROR_MESSAGE),
                            failureKind = result.outputData.getString(ERROR_MESSAGE)?.let {
                                IllegalStateException(it).toOperationFailure().kind
                            },
                            failedFeedIds = failedFeedIds,
                            attempt = attempt,
                            scope = scope,
                        )
                    )
                    // Reader prefetch and widgets resolve the currently selected account. Do not
                    // run them for a background account, or they could populate the wrong cache.
                    if (succeeded && accountService.getCurrentAccountId() == accountId) {
                        accountRssService.clearKeepArchivedArticles().forEach {
                            readerCacheHelper.deleteCacheFor(articleId = it.id)
                        }
                        workManager
                            .beginUniqueWork(
                                uniqueWorkName = postSyncWorkName(accountId),
                                existingWorkPolicy = ExistingWorkPolicy.KEEP,
                                OneTimeWorkRequestBuilder<ReaderWorker>()
                                    .addTag(READER_TAG)
                                    .addTag(ONETIME_WORK_TAG)
                                    .setBackoffCriteria(
                                        backoffPolicy = BackoffPolicy.EXPONENTIAL,
                                        backoffDelay = 30,
                                        timeUnit = TimeUnit.SECONDS,
                                    )
                                    .build(),
                            )
                            .then(OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build())
                            .enqueue()
                    }
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
        ): java.util.UUID {
            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .addTag(SYNC_TAG)
                    .addTag(ONETIME_WORK_TAG)
                    .setInputData(inputData)
                    .build()
            workManager
                .beginUniqueWork(
                    oneTimeWorkName(inputData.getInt("accountId", -1)),
                    ExistingWorkPolicy.KEEP,
                    request,
                )
                .enqueue()
            return request.id
        }

        fun enqueuePeriodicWork(account: Account, workManager: WorkManager) {
            val syncInterval = account.syncInterval
            val syncOnlyWhenCharging = account.syncOnlyWhenCharging
            val syncOnlyOnWiFi = account.syncOnlyOnWiFi
            val workName = periodicWorkName(account.id ?: -1)

            workManager.enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<SyncWorker>(syncInterval.value, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder()
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
