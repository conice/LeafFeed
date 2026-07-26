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
import javax.inject.Inject
import kotlin.text.trim
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.mapPagingFlowItem
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.android.AndroidStringsHelper
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.SettingsProvider

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
    private val automationRepository: AutomationRepository,
) {

    private val mutablePagerFlow =
        MutableStateFlow<PagerData>(
            PagerData(filterState = filterStateUseCase.filterStateFlow.value)
        )
    val pagerFlow: StateFlow<PagerData> = mutablePagerFlow
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
            val accountRules = accountService.currentAccountIdFlow.filterNotNull()
                .flatMapLatest { accountId ->
                    automationRepository.observeRules(accountId).map { rules -> accountId to rules }
                }
            filterStateUseCase.filterStateFlow
                .combine(accountRules) { filterState, (_, rules) -> filterState to rules }
                .distinctUntilChanged()
                .collect { (filterState, rules) ->
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
                                                isUnread = filterState.filter.isUnread(),
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
                                                isUnread = filterState.filter.isUnread(),
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
                                    val matcher = AutomationMatcher(rules)
                                    pagingData
                                        .filter { !matcher.isFiltered(it) }
                                        .mapPagingFlowItem(androidStringsHelper, matcher)
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
