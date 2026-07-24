package me.ash.reader.domain.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.account.Account
import me.ash.reader.domain.model.article.ArticleFilterCandidate
import me.ash.reader.domain.model.article.ArticleRule
import me.ash.reader.domain.model.article.RuleScope
import me.ash.reader.domain.model.article.RuleType
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.datastore.get
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys
import me.ash.reader.infrastructure.preference.SettingsProvider
import timber.log.Timber

@Singleton
class ArticleFilterProcessor
@Inject
constructor(
    private val accountService: AccountService,
    private val articleRuleRepository: ArticleRuleRepository,
    private val articleDao: ArticleDao,
    private val rssService: RssService,
    private val settingsProvider: SettingsProvider,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private var processingJob: Job? = null

    fun start() {
        if (processingJob != null) return

        processingJob =
            applicationScope.launch(ioDispatcher) {
                accountService.currentAccountFlow
                    .filterNotNull()
                    .combine(articleRuleRepository.rules) { account, rules ->
                        account to
                            rules.filter {
                                it.accountId == account.id && it.type == RuleType.FILTER
                            }
                    }
                    .combine(settingsProvider.preferencesFlow) { (account, rules), preferences ->
                        ProcessorInput(
                            account = account,
                            rules = rules,
                            includeDescription =
                                preferences.get<Boolean>(
                                    FeaturePreferenceKeys.ruleMatchDescription.name
                                ) != false,
                            enabled =
                                preferences.get<Boolean>(
                                    FeaturePreferenceKeys.filterRulesEnabled.name
                                ) != false,
                        )
                    }
                    .distinctUntilChanged()
                    .map { input ->
                        MatcherInput(
                            account = input.account,
                            matcher =
                                ArticleFilterMatcher.from(
                                    input.rules,
                                    input.includeDescription,
                                ),
                            enabled = input.enabled,
                        )
                    }
                    .flatMapLatest { (account, matcher, enabled) ->
                        val accountId = account.id
                        if (!enabled || accountId == null || matcher.isEmpty) {
                            emptyFlow()
                        } else {
                            var previousCandidates = emptyMap<String, ArticleFilterCandidate>()
                            articleDao
                                .queryUnreadFilterCandidates(
                                    accountId = accountId,
                                    includeDescription = matcher.includeDescription,
                                )
                                .distinctUntilChanged()
                                // A sync or a large mark-as-read operation can invalidate the
                                // article table repeatedly. Only the newest unread snapshot is
                                // useful; processing stale snapshots wastes matching work and may
                                // enqueue redundant remote read-status updates.
                                .conflate()
                                .map { articles ->
                                    val changed =
                                        articles.filter { article ->
                                            previousCandidates[article.articleId] != article
                                        }
                                    previousCandidates = articles.associateBy { it.articleId }
                                    FilterResult(
                                        account = account,
                                        articleIds = matcher.matchingArticleIds(changed),
                                    )
                                }
                        }
                    }
                    .collect { result ->
                        runCatching { process(result) }
                            .onFailure { Timber.e(it, "Failed to process article filter rules") }
                    }
            }
    }

    private suspend fun process(result: FilterResult) {
        if (
            settingsProvider.get<Boolean>(FeaturePreferenceKeys.filterRulesEnabled.name) == false
        ) return
        if (result.articleIds.isEmpty()) return

        result.articleIds.chunked(500).forEach { ids ->
            articleDao.markAsReadByIdSet(
                accountId = result.account.id ?: return,
                ids = ids.toSet(),
                isUnread = false,
            )
        }

        if (accountService.currentAccountIdFlow.value == result.account.id) {
            rssService.get(result.account.type.id).syncReadStatus(result.articleIds, false)
        }
    }

    private data class FilterResult(
        val account: Account,
        val articleIds: Set<String>,
    )

    private data class ProcessorInput(
        val account: Account,
        val rules: List<ArticleRule>,
        val includeDescription: Boolean,
        val enabled: Boolean,
    )

    private data class MatcherInput(
        val account: Account,
        val matcher: ArticleFilterMatcher,
        val enabled: Boolean,
    )
}

internal fun matchingFilterArticleIds(
    articles: List<ArticleFilterCandidate>,
    rules: List<ArticleRule>,
): Set<String> = ArticleFilterMatcher.from(rules).matchingArticleIds(articles)

internal class ArticleFilterMatcher private constructor(
    private val globalRules: Map<Int, List<CompiledRule>>,
    private val groupRules: Map<Int, Map<String, List<CompiledRule>>>,
    private val feedRules: Map<Int, Map<String, List<CompiledRule>>>,
    val includeDescription: Boolean,
) {
    val isEmpty: Boolean
        get() = globalRules.isEmpty() && groupRules.isEmpty() && feedRules.isEmpty()

    fun matchingArticleIds(articles: List<ArticleFilterCandidate>): Set<String> {
        val matchingIds = mutableSetOf<String>()
        articles.forEach { article ->
            if (matches(article)) matchingIds += article.articleId
        }
        return matchingIds
    }

    private fun matches(article: ArticleFilterCandidate): Boolean {
        val accountId = article.accountId
        val title = article.title
        val description = if (includeDescription) article.rawDescription else ""
        var combinedText: String? = null

        fun List<CompiledRule>?.matchesAny(): Boolean {
            this ?: return false
            for (rule in this) {
                val text =
                    if (rule.requiresCombinedText) {
                        combinedText ?: "$title\n$description".also { combinedText = it }
                    } else {
                        null
                    }
                if (rule.matches(title, description, text)) return true
            }
            return false
        }

        return globalRules[accountId].matchesAny() ||
            groupRules[accountId]?.get(article.groupId).matchesAny() ||
            feedRules[accountId]?.get(article.feedId).matchesAny()
    }

    companion object {
        fun from(
            rules: List<ArticleRule>,
            includeDescription: Boolean = true,
        ): ArticleFilterMatcher {
            val globalRules = mutableMapOf<Int, MutableList<CompiledRule>>()
            val groupRules =
                mutableMapOf<Int, MutableMap<String, MutableList<CompiledRule>>>()
            val feedRules =
                mutableMapOf<Int, MutableMap<String, MutableList<CompiledRule>>>()

            fun MutableMap<Int, MutableMap<String, MutableList<CompiledRule>>>.add(
                rule: ArticleRule,
                compiledRule: CompiledRule,
            ) {
                getOrPut(rule.accountId) { mutableMapOf() }
                    .getOrPut(rule.scopeId) { mutableListOf() }
                    .add(compiledRule)
            }

            rules.forEach { rule ->
                if (rule.type != RuleType.FILTER) return@forEach

                val compiledRule = CompiledRule.from(rule) ?: return@forEach
                when (rule.scope) {
                    RuleScope.GLOBAL ->
                        globalRules.getOrPut(rule.accountId) { mutableListOf() }.add(compiledRule)
                    RuleScope.GROUP -> groupRules.add(rule, compiledRule)
                    RuleScope.FEED -> feedRules.add(rule, compiledRule)
                }
            }

            return ArticleFilterMatcher(
                globalRules,
                groupRules,
                feedRules,
                includeDescription,
            )
        }
    }

    private class CompiledRule private constructor(
        private val pattern: String,
        private val regex: Regex?,
        private val isRegex: Boolean,
        private val caseSensitive: Boolean,
    ) {
        val requiresCombinedText: Boolean
            get() = isRegex || '\n' in pattern

        fun matches(title: String, description: String, combinedText: String?): Boolean =
            when {
                isRegex -> combinedText != null && regex?.containsMatchIn(combinedText) == true
                '\n' in pattern -> combinedText?.contains(pattern, ignoreCase = !caseSensitive) == true
                else ->
                    title.contains(pattern, ignoreCase = !caseSensitive) ||
                        description.contains(pattern, ignoreCase = !caseSensitive)
            }

        companion object {
            fun from(rule: ArticleRule): CompiledRule? {
                val regex =
                    if (rule.isRegex) {
                        runCatching {
                            if (rule.caseSensitive) Regex(rule.pattern)
                            else Regex(rule.pattern, RegexOption.IGNORE_CASE)
                        }.getOrNull()
                            ?: return null
                    } else {
                        null
                    }
                return CompiledRule(
                    pattern = rule.pattern,
                    regex = regex,
                    isRegex = rule.isRegex,
                    caseSensitive = rule.caseSensitive,
                )
            }
        }
    }
}
