package me.ash.reader.domain.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.account.Account
import me.ash.reader.domain.model.article.AutomationActionType
import me.ash.reader.domain.model.article.AutomationCandidate
import me.ash.reader.domain.model.article.AutomationExecutionEntity
import me.ash.reader.domain.model.article.AutomationExecutionStatus
import me.ash.reader.domain.model.article.AutomationRule
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.android.NotificationHelper
import me.ash.reader.infrastructure.audio.PodcastDownloadRepository
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.rss.ReaderCacheHelper
import timber.log.Timber

@Singleton
class AutomationProcessor @Inject constructor(
    private val accountService: AccountService,
    private val repository: AutomationRepository,
    private val articleDao: ArticleDao,
    private val rssService: RssService,
    private val notificationHelper: NotificationHelper,
    private val podcastDownloadRepository: PodcastDownloadRepository,
    private val readerCacheHelper: ReaderCacheHelper,
    private val settingsProvider: SettingsProvider,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = applicationScope.launch(ioDispatcher) {
            accountService.currentAccountFlow.filterNotNull().collectLatest { account ->
                val accountId = account.id ?: return@collectLatest
                combine(
                    repository.observeRules(accountId),
                    articleDao.queryAutomationCandidates(accountId),
                ) { rules, candidates ->
                    ProcessorInput(account, rules, candidates)
                }.collect(::process)
            }
        }
    }

    private suspend fun process(input: ProcessorInput) {
        val sideEffectRules = input.rules.filter { rule ->
            rule.actions.any { it != AutomationActionType.FILTER && it != AutomationActionType.HIGHLIGHT }
        }
        if (sideEffectRules.isEmpty()) return
        val matcher = AutomationMatcher(sideEffectRules)
        input.candidates.forEach { candidate ->
            matcher.matching(candidate).forEach { rule ->
                rule.actions
                    .filterNot { it == AutomationActionType.FILTER || it == AutomationActionType.HIGHLIGHT }
                    .forEach actionLoop@ { action ->
                        val startedAt = System.currentTimeMillis()
                        val claimed = repository.claim(
                            AutomationExecutionEntity(
                                articleId = candidate.articleId,
                                ruleId = rule.id,
                                actionType = action.name,
                                status = AutomationExecutionStatus.RUNNING.name,
                                executedAt = startedAt,
                            )
                        )
                        if (!claimed) return@actionLoop
                        execute(input.account, candidate, rule, action, startedAt)
                    }
            }
        }
    }

    private suspend fun execute(
        account: Account,
        candidate: AutomationCandidate,
        rule: AutomationRule,
        action: AutomationActionType,
        startedAt: Long,
    ) {
        try {
            perform(account, candidate, rule, action)
            repository.record(
                AutomationExecutionEntity(
                    articleId = candidate.articleId,
                    ruleId = rule.id,
                    actionType = action.name,
                    status = AutomationExecutionStatus.SUCCEEDED.name,
                    executedAt = startedAt,
                )
            )
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) {
                repository.release(candidate.articleId, rule.id, action.name)
                throw throwable
            }
            Timber.e(throwable, "Automation action %s failed", action.name)
            repository.record(
                AutomationExecutionEntity(
                    articleId = candidate.articleId,
                    ruleId = rule.id,
                    actionType = action.name,
                    status = AutomationExecutionStatus.FAILED.name,
                    executedAt = startedAt,
                    message = throwable.message ?: throwable.javaClass.simpleName,
                )
            )
        }
    }

    private suspend fun perform(
        account: Account,
        candidate: AutomationCandidate,
        rule: AutomationRule,
        action: AutomationActionType,
    ) {
        val service = rssService.get(account.type.id)
        when (action) {
            AutomationActionType.FILTER, AutomationActionType.HIGHLIGHT -> Unit
            AutomationActionType.STAR -> service.markAsStarred(candidate.articleId, true)
            AutomationActionType.READ_LATER -> service.markAsReadLater(candidate.articleId, true)
            AutomationActionType.MARK_READ, AutomationActionType.MARK_UNREAD -> {
                val isUnread = action == AutomationActionType.MARK_UNREAD
                service.batchMarkAsRead(setOf(candidate.articleId), isUnread)
                service.syncReadStatus(setOf(candidate.articleId), isUnread)
            }
            AutomationActionType.NOTIFY -> {
                val article = articleDao.queryById(candidate.articleId) ?: error("Article no longer exists")
                notificationHelper.notifyAutomation(
                    article.feed,
                    article.article,
                    rule.id,
                    rule.name,
                )
            }
            AutomationActionType.DOWNLOAD_PODCAST -> {
                val article = articleDao.queryById(candidate.articleId)?.article
                    ?: error("Article no longer exists")
                if (article.audioUrl == null) error("Article has no podcast audio")
                val wifiOnly = settingsProvider.get(FeaturePreferenceKeys.podcastWifiOnly) != false
                podcastDownloadRepository.enqueue(article, wifiOnly).getOrThrow()
            }
            AutomationActionType.FETCH_FULL_CONTENT -> {
                val article = articleDao.queryById(candidate.articleId)?.article
                    ?: error("Article no longer exists")
                check(readerCacheHelper.checkOrFetchFullContent(article, account.id!!)) {
                    "Unable to fetch full content"
                }
            }
        }
    }

    private data class ProcessorInput(
        val account: Account,
        val rules: List<AutomationRule>,
        val candidates: List<AutomationCandidate>,
    )
}
