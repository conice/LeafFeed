package com.conice.morss.application.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.conice.morss.domain.model.account.Account
import com.conice.morss.domain.model.article.AutomationActionType
import com.conice.morss.domain.model.article.AutomationCandidate
import com.conice.morss.domain.model.article.AutomationExecutionEntity
import com.conice.morss.domain.model.article.AutomationExecutionStatus
import com.conice.morss.domain.model.article.AutomationRule
import com.conice.morss.domain.repository.ArticleDao
import com.conice.morss.domain.repository.AutomationArticleDao
import com.conice.morss.application.service.AccountService
import com.conice.morss.application.service.RssService
import com.conice.morss.infrastructure.android.NotificationHelper
import com.conice.morss.infrastructure.audio.PodcastDownloadRepository
import com.conice.morss.infrastructure.di.ApplicationScope
import com.conice.morss.infrastructure.di.IODispatcher
import com.conice.morss.infrastructure.preference.FeaturePreferenceKeys
import com.conice.morss.infrastructure.preference.SettingsProvider
import com.conice.morss.infrastructure.rss.ReaderCacheHelper
import timber.log.Timber

@Singleton
class AutomationProcessor @Inject constructor(
    private val accountService: AccountService,
    private val repository: AutomationRepository,
    private val articleDao: ArticleDao,
    private val automationArticleDao: AutomationArticleDao,
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
                repository.recoverStaleExecutions(accountId)
                combine(
                    repository.observeRules(accountId),
                    automationArticleDao.queryAutomationCandidates(accountId),
                ) { rules, candidates ->
                    ProcessorInput(accountId, account, rules, candidates)
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
                        val execution = repository.claim(
                            articleId = candidate.articleId,
                            ruleId = rule.id,
                            actionType = action.name,
                            startedAt = startedAt,
                        )
                        if (execution == null) return@actionLoop
                        execute(input.account, candidate, rule, action, execution)
                    }
            }
        }
        repository.trimHistory(input.accountId)
    }

    private suspend fun execute(
        account: Account,
        candidate: AutomationCandidate,
        rule: AutomationRule,
        action: AutomationActionType,
        execution: AutomationExecutionEntity,
    ) {
        try {
            perform(account, candidate, rule, action)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) {
                withContext(NonCancellable) { repository.interrupt(execution) }
                throw throwable
            }
            Timber.e(throwable, "Automation action %s failed", action.name)
            completeSafely(
                execution = execution,
                status = AutomationExecutionStatus.FAILED,
                message = throwable.message ?: throwable.javaClass.simpleName,
                retryable = throwable !is PermanentAutomationException,
            )
            return
        }
        completeSafely(
            execution = execution,
            status = AutomationExecutionStatus.SUCCEEDED,
        )
    }

    private suspend fun completeSafely(
        execution: AutomationExecutionEntity,
        status: AutomationExecutionStatus,
        message: String? = null,
        retryable: Boolean = false,
    ) {
        try {
            repository.complete(execution, status, message, retryable)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            Timber.e(throwable, "Unable to persist automation execution %s", execution.id)
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
                val article = articleDao.queryById(candidate.articleId)
                    ?: throw PermanentAutomationException("Article no longer exists")
                notificationHelper.notifyAutomation(
                    article.feed,
                    article.article,
                    rule.id,
                    rule.name,
                )
            }
            AutomationActionType.DOWNLOAD_PODCAST -> {
                val article = articleDao.queryById(candidate.articleId)?.article
                    ?: throw PermanentAutomationException("Article no longer exists")
                if (article.audioUrl == null) {
                    throw PermanentAutomationException("Article has no podcast audio")
                }
                val wifiOnly = settingsProvider.get(FeaturePreferenceKeys.podcastWifiOnly) != false
                podcastDownloadRepository.enqueue(article, wifiOnly).getOrThrow()
            }
            AutomationActionType.FETCH_FULL_CONTENT -> {
                val article = articleDao.queryById(candidate.articleId)?.article
                    ?: throw PermanentAutomationException("Article no longer exists")
                check(readerCacheHelper.checkOrFetchFullContent(article, account.id!!)) {
                    "Unable to fetch full content"
                }
            }
        }
    }

    private data class ProcessorInput(
        val accountId: Int,
        val account: Account,
        val rules: List<AutomationRule>,
        val candidates: List<AutomationCandidate>,
    )

    private class PermanentAutomationException(message: String) : Exception(message)
}
