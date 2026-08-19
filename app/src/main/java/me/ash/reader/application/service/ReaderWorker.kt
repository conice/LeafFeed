package me.ash.reader.application.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.rss.ReaderCacheHelper

@HiltWorker
class ReaderWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val articleDao: ArticleDao,
    private val cacheHelper: ReaderCacheHelper,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(ioDispatcher) {
            try {
                val accountId = inputData.getInt(ACCOUNT_ID, -1)
                if (accountId == -1) return@withContext Result.failure()
                // Prefetch the newest articles first and cap each run. Opening an older article
                // still fetches it on demand, while a large unread backlog cannot hold the radio
                // and CPU active for hours after every synchronization.
                val articleList =
                    articleDao.queryUnreadFullContentArticles(
                        accountId = accountId,
                        limit = PREFETCH_ARTICLE_LIMIT,
                    )
                var failedCount = 0

                // Do not allocate one Deferred for every unread article. Large accounts can have
                // tens of thousands of rows; small batches keep memory and network usage bounded.
                articleList.chunked(PREFETCH_CONCURRENCY).forEach { batch ->
                    failedCount +=
                        batch
                            .map { article ->
                                async {
                                    try {
                                        cacheHelper.checkOrFetchFullContent(
                                            article = article,
                                            accountId = accountId,
                                        )
                                    } catch (throwable: Throwable) {
                                        if (throwable is CancellationException) throw throwable
                                        false
                                    }
                                }
                            }
                            .awaitAll()
                            .count { succeeded -> !succeeded }
                }

                // Individual pages can fail permanently. The next regular sync will try them
                // again; retrying the entire batch indefinitely wastes network and battery.
                Result.success(workDataOf(FAILED_COUNT to failedCount))
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                Result.retry()
            }
        }
    }

    companion object {
        internal const val FAILED_COUNT = "failedCount"
        internal const val ACCOUNT_ID = "accountId"
        internal const val PREFETCH_CONCURRENCY = 2
        internal const val PREFETCH_ARTICLE_LIMIT = 20
    }
}
