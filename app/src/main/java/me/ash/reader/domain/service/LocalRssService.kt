package me.ash.reader.domain.service

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import me.ash.reader.domain.data.SyncLogger
import me.ash.reader.domain.model.account.AccountType
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.feed.FeedWithArticle
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.infrastructure.android.NotificationHelper
import me.ash.reader.infrastructure.audio.PodcastDownloadRepository
import me.ash.reader.infrastructure.di.DefaultDispatcher
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.rss.RssHelper
import me.ash.reader.infrastructure.rss.normalizeArticleUrl
import timber.log.Timber

private const val TAG = "LocalRssService"
private const val LOCAL_SYNC_CONCURRENCY = 4
internal const val LOCAL_SYNC_BATCH_FEEDS = 12
internal const val LOCAL_SYNC_BATCH_ARTICLES = 200
private const val LOCAL_SYNC_BATCH_WINDOW_MS = 2_000L
private const val LOCAL_SYNC_RESULT_BUFFER = 16

internal fun shouldFlushLocalSyncBatch(feedCount: Int, articleCount: Int): Boolean =
    feedCount >= LOCAL_SYNC_BATCH_FEEDS || articleCount >= LOCAL_SYNC_BATCH_ARTICLES

class LocalRssService
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val rssHelper: RssHelper,
    private val notificationHelper: NotificationHelper,
    private val podcastDownloadRepository: PodcastDownloadRepository,
    private val groupDao: GroupDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val workManager: WorkManager,
    private val accountService: AccountService,
    private val syncLogger: SyncLogger,
    private val settingsProvider: SettingsProvider,
) :
    AbstractRssRepository(
        articleDao,
        groupDao,
        feedDao,
        workManager,
        rssHelper,
        notificationHelper,
        ioDispatcher,
        defaultDispatcher,
        accountService,
    ) {

    override suspend fun sync(
        accountId: Int,
        feedId: String?,
        groupId: String?,
        onProgress: suspend (SyncProgress) -> Unit,
    ): ListenableWorker.Result = supervisorScope {
        return@supervisorScope runCatching {
            val preTime = System.currentTimeMillis()
            val preDate = Date(preTime)
            val currentAccount = accountService.getAccountById(accountId)!!
            require(currentAccount.type.id == AccountType.Local.id) {
                "Account type is invalid"
            }
            // RSS parsing is partly CPU-bound. Excessive parallelism makes background refreshes
            // compete with Compose rendering on mid-range devices.
            val semaphore = Semaphore(LOCAL_SYNC_CONCURRENCY)

            val feedsToSync =
                when {
                    feedId != null -> listOfNotNull(feedDao.queryById(feedId))
                    groupId != null -> feedDao.queryByGroupId(accountId, groupId)
                    else -> feedDao.queryAll(accountId)
                }
            val progressMutex = Mutex()
            var completed = 0
            var lastReportedPercent = 0
            val failedFeedIds = mutableListOf<String>()
            onProgress(SyncProgress(completed = 0, total = feedsToSync.size))

            // Fetch concurrently into a bounded buffer. A single writer commits successful
            // results in small batches so new articles become visible without waiting for the
            // slowest feed, while still limiting Room/Paging invalidations.
            val resultChannel =
                Channel<Result<FeedWithArticle>>(capacity = LOCAL_SYNC_RESULT_BUFFER)
            val writer = async(ioDispatcher) { persistFetchResults(resultChannel) }

            try {
                feedsToSync
                    .map { currentFeed ->
                        async(ioDispatcher) {
                            val result =
                                try {
                                    val feedWithArticles = semaphore.withPermit {
                                        val archivedArticles =
                                            feedDao
                                                .queryArchivedArticleLinks(currentFeed.id)
                                                .map(::normalizeArticleUrl)
                                                .toSet()
                                        val fetchedFeed = syncFeed(currentFeed, preDate)
                                        val deduplicationMode =
                                            settingsProvider.get(
                                                FeaturePreferenceKeys.deduplicationMode
                                            ) ?: 1
                                        val fetchedArticles =
                                            fetchedFeed.articles
                                                .distinctBy { article ->
                                                    when (deduplicationMode) {
                                                        0 -> article.id
                                                        2 ->
                                                            "${article.link}\u0000${article.title}\u0000${article.date.time / 86_400_000}"
                                                        else ->
                                                            normalizeArticleUrl(article.link)
                                                    }
                                                }
                                                .filterNot {
                                                    archivedArticles.contains(
                                                        normalizeArticleUrl(it.link)
                                                    )
                                                }

                                        FeedWithArticle(
                                            feed = currentFeed,
                                            articles = fetchedArticles,
                                        )
                                    }
                                    Result.success(feedWithArticles)
                                } catch (throwable: Throwable) {
                                    if (throwable is CancellationException) throw throwable
                                    progressMutex.withLock { failedFeedIds += currentFeed.id }
                                    Result.failure(throwable)
                                }

                            resultChannel.send(result)

                            progressMutex.withLock {
                                completed += 1
                                val percent =
                                    if (feedsToSync.isEmpty()) 100
                                    else completed * 100 / feedsToSync.size
                                if (
                                    percent != lastReportedPercent ||
                                        completed == feedsToSync.size
                                ) {
                                    lastReportedPercent = percent
                                    onProgress(
                                        SyncProgress(
                                            completed = completed,
                                            total = feedsToSync.size,
                                            failedFeedIds = failedFeedIds.toList(),
                                        )
                                    )
                                }
                            }
                        }
                    }
                    .awaitAll()
            } finally {
                resultChannel.close()
            }

            // The writer keeps draining after ordinary failures, preventing producers from
            // blocking on a full channel and preserving all successful results from this run.
            writer.await()?.let { throw it }

            Timber.tag("RlOG").i("onCompletion: ${System.currentTimeMillis() - preTime}")
            accountService.update(currentAccount.copy(updateAt = Date()))
            ListenableWorker.Result.success()
        }
            .onFailure { syncLogger.log(it) }
            .getOrNull() ?: ListenableWorker.Result.retry()
    }

    private suspend fun persistFetchResults(
        results: ReceiveChannel<Result<FeedWithArticle>>,
    ): Throwable? {
        val pending = mutableListOf<FeedWithArticle>()
        var pendingArticleCount = 0
        var batchStartedAt = 0L
        var firstFailure: Throwable? = null

        fun rememberFailure(throwable: Throwable) {
            if (firstFailure == null) firstFailure = throwable
        }

        suspend fun flush() {
            if (pending.isEmpty()) return
            val batch = pending.toList()
            pending.clear()
            pendingArticleCount = 0
            batchStartedAt = 0L
            try {
                persistBatch(batch)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                rememberFailure(throwable)
            }
        }

        while (true) {
            val received =
                if (pending.isEmpty()) {
                    results.receiveCatching()
                } else {
                    val elapsed = SystemClock.elapsedRealtime() - batchStartedAt
                    val remaining = (LOCAL_SYNC_BATCH_WINDOW_MS - elapsed).coerceAtLeast(0L)
                    if (remaining == 0L) {
                        flush()
                        continue
                    }
                    withTimeoutOrNull(remaining) { results.receiveCatching() }
                }

            if (received == null) {
                flush()
                continue
            }

            if (received.isClosed) {
                flush()
                break
            }

            received.getOrThrow().fold(
                onSuccess = { feedWithArticles ->
                    if (pending.isEmpty()) batchStartedAt = SystemClock.elapsedRealtime()
                    pending += feedWithArticles
                    pendingArticleCount += feedWithArticles.articles.size
                },
                onFailure = ::rememberFailure,
            )

            if (shouldFlushLocalSyncBatch(pending.size, pendingArticleCount)) flush()
        }

        return firstFailure
    }

    private suspend fun persistBatch(batch: List<FeedWithArticle>) {
        val insertedFeeds = articleDao.insertFeedArticlesIfNotExist(batch)
        var firstPostProcessingFailure: Throwable? = null

        insertedFeeds.forEach { feedWithArticles ->
            val newArticles = feedWithArticles.articles
            if (feedWithArticles.feed.isNotification && newArticles.isNotEmpty()) {
                try {
                    notificationHelper.notify(feedWithArticles)
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    if (firstPostProcessingFailure == null) {
                        firstPostProcessingFailure = throwable
                    }
                }
            }
            try {
                maybeDownloadPodcastEpisodes(newArticles)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                if (firstPostProcessingFailure == null) firstPostProcessingFailure = throwable
            }
        }

        firstPostProcessingFailure?.let { throw it }
    }

    private suspend fun syncFeed(feed: Feed, preDate: Date = Date()): FeedWithArticle {
        val forceFullContent =
            settingsProvider.get(FeaturePreferenceKeys.syncFullContent) == true
        val effectiveFeed = if (forceFullContent) feed.copy(isFullContent = true) else feed
        val articles = rssHelper.queryRssXml(effectiveFeed, "", preDate)
        if (feed.icon == null) {
            val iconLink = rssHelper.queryRssIconLink(feed.url)
            if (iconLink != null) {
                rssHelper.saveRssIcon(feedDao, feed, iconLink)
            }
        }
        return FeedWithArticle(
            feed = effectiveFeed.copy(isNotification = feed.isNotification && articles.isNotEmpty()),
            articles = articles,
        )
    }

    private suspend fun maybeDownloadPodcastEpisodes(articles: List<me.ash.reader.domain.model.article.Article>) {
        if (settingsProvider.get(FeaturePreferenceKeys.podcastAutoDownload) != true) return
        val wifiOnly = settingsProvider.get(FeaturePreferenceKeys.podcastWifiOnly) != false
        val networkIsMetered = context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
        if (wifiOnly && networkIsMetered) return
        val scope = settingsProvider.get(FeaturePreferenceKeys.podcastDownloadScope) ?: 0
        articles.asSequence()
            .filter { it.audioUrl != null }
            .filter { article ->
                when (scope) {
                    1 -> article.isStarred
                    2 -> article.isReadLater
                    else -> true
                }
            }
            .forEach { podcastDownloadRepository.enqueue(it, wifiOnly) }
    }
}
