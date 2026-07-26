package me.ash.reader.domain.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.paging.ItemSnapshotList
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import javax.inject.Inject
import kotlin.text.trim
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleHighlightMatcher
import me.ash.reader.domain.model.article.ArticleRule
import me.ash.reader.domain.model.article.RuleType
import me.ash.reader.domain.model.article.insertDateSeparators
import me.ash.reader.domain.model.article.mapPagingArticleItems
import me.ash.reader.domain.model.article.mapPagingFlowItem
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.android.AndroidStringsHelper
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys

private fun emptyArticleSnapshot() =
    ItemSnapshotList<ArticleFlowItem>(
        placeholdersBefore = 0,
        placeholdersAfter = 0,
        items = emptyList(),
    )

class ArticlePagingListUseCase
@Inject
constructor(
    private val rssService: RssService,
    private val androidStringsHelper: AndroidStringsHelper,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val settingsProvider: SettingsProvider,
    private val filterStateUseCase: FilterStateUseCase,
    private val accountService: AccountService,
    private val articleRuleRepository: ArticleRuleRepository,
) {

    private val mutablePagerFlow =
        MutableStateFlow<PagerData>(
            PagerData(filterState = filterStateUseCase.filterStateFlow.value)
        )
    val pagerFlow: StateFlow<PagerData> = mutablePagerFlow
    val highlightRules: StateFlow<List<ArticleRule>> =
        accountService.currentAccountIdFlow.filterNotNull()
            .combine(articleRuleRepository.rules) { accountId, rules ->
                rules.filter { it.accountId == accountId && it.type == RuleType.HIGHLIGHT }
            }
            .stateIn(applicationScope, SharingStarted.Eagerly, emptyList())

    var itemSnapshotList by
        mutableStateOf(emptyArticleSnapshot())
        private set

    private val pagingDataPresenter =
        object : PagingDataPresenter<ArticleFlowItem>() {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<ArticleFlowItem>) {
                itemSnapshotList = snapshot()
            }
        }

    private var pagerCacheJob: Job? = null

    init {
        applicationScope.launch(ioDispatcher) {
            filterStateUseCase.filterStateFlow
                .combine(accountService.currentAccountIdFlow) { filterState, accountId ->
                    filterState to accountId
                }
                .combine(articleRuleRepository.rules) { (filterState, accountId), rules ->
                    filterState to
                        rules.filter {
                            it.accountId == accountId && it.type == RuleType.HIGHLIGHT
                        }
                }
                .combine(settingsProvider.preferencesFlow) { (filterState, rules), preferences ->
                    Triple(
                        filterState,
                        if (preferences[FeaturePreferenceKeys.highlightRulesEnabled] != false) {
                            rules
                        } else {
                            emptyList()
                        },
                        preferences[FeaturePreferenceKeys.showHighlightMatches] != false,
                    )
                }
                .distinctUntilChanged()
                .collect { (filterState, rules, showHighlightMatches) ->
                    val searchContent = filterState.searchContent
                    pagerCacheJob?.cancel()
                    itemSnapshotList = emptyArticleSnapshot()
                    val cacheJob = SupervisorJob(applicationScope.coroutineContext[Job])
                    pagerCacheJob = cacheJob
                    val pagerCacheScope =
                        CoroutineScope(applicationScope.coroutineContext + cacheJob)

                    mutablePagerFlow.value =
                        PagerData(
                            Pager(
                                    config = PagingConfig(pageSize = 50, enablePlaceholders = false)
                                ) {
                                    if (!searchContent.isNullOrBlank()) {
                                        rssService
                                            .get()
                                            .searchArticles(
                                                content = searchContent.trim(),
                                                groupId = filterState.group?.id,
                                                feedId = filterState.feed?.id,
                                                isStarred = filterState.filter.isStarred(),
                                                isUnread = filterState.filter.isUnread() ||
                                                    (filterState.filter.isHighlighted() && filterState.highlightUnreadOnly),
                                                sortAscending =
                                                    settingsProvider.settings.flowSortUnreadArticles
                                                        .value,
                                            )
                                    } else {
                                        rssService
                                            .get()
                                            .pullArticles(
                                                groupId = filterState.group?.id,
                                                feedId = filterState.feed?.id,
                                                isStarred = filterState.filter.isStarred(),
                                                isUnread = filterState.filter.isUnread() ||
                                                    (filterState.filter.isHighlighted() && filterState.highlightUnreadOnly),
                                                sortAscending =
                                                    settingsProvider.settings.flowSortUnreadArticles
                                                        .value,
                                            )
                                    }
                                }
                                .flow
                                .map { pagingData ->
                                    pagingData.filter { articleWithFeed ->
                                        filterState.contentType.includes(
                                            articleWithFeed.article.audioUrl
                                        )
                                    }
                                }
                                .map { pagingData ->
                                    if (filterState.filter.isReadLater()) {
                                        pagingData.filter { it.article.isReadLater }
                                    } else pagingData
                                }
                                .map { pagingData ->
                                    if (filterState.filter.isHighlighted()) {
                                        val selectedRule = rules.firstOrNull {
                                            it.id == filterState.highlightRuleId &&
                                                it.type == RuleType.HIGHLIGHT
                                        }
                                        val applicableRules =
                                            when {
                                                filterState.highlightRuleId == null -> rules
                                                selectedRule != null -> listOf(selectedRule)
                                                else -> emptyList()
                                            }
                                        val matcher =
                                            ArticleHighlightMatcher.from(applicableRules)
                                        val matchedItems = pagingData
                                            .mapPagingArticleItems(androidStringsHelper, matcher)
                                            .filter { item -> item.highlightRanges.isNotEmpty() }
                                        (if (showHighlightMatches) {
                                            matchedItems
                                        } else {
                                            matchedItems.map { item ->
                                                ArticleFlowItem.Article(item.articleWithFeed)
                                            }
                                        }).insertDateSeparators(androidStringsHelper)
                                    } else {
                                        pagingData.mapPagingFlowItem(
                                            androidStringsHelper,
                                            if (showHighlightMatches) rules else emptyList(),
                                        )
                                    }
                                }
                                .cachedIn(pagerCacheScope),
                            filterState = filterState,
                        )
                }
        }
        applicationScope.launch {
            pagerFlow.collectLatest { (pager, _) ->
                pager.collectLatest { pagingDataPresenter.collectFrom(it) }
            }
        }
    }
}

data class PagerData(
    val pager: Flow<PagingData<ArticleFlowItem>> = emptyFlow(),
    val filterState: FilterState = FilterState(),
)
