package com.conice.morss.ui.page.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.conice.morss.application.data.toArticleFtsQuery
import com.conice.morss.application.data.ArticleFlowItem
import com.conice.morss.domain.repository.ReadingHistoryDao

private const val HISTORY_SEARCH_DEBOUNCE_MS = 300L

private data class HistoryScope(
    val accountId: Int,
    val groupId: String?,
    val feedId: String?,
    val audioOnly: Boolean,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReadingHistoryViewModel
@Inject
constructor(
    private val readingHistoryDao: ReadingHistoryDao,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val scope = MutableStateFlow<HistoryScope?>(null)
    val searchQuery = savedStateHandle.getStateFlow(SEARCH_QUERY_KEY, "")

    val pagingData: Flow<PagingData<ArticleFlowItem>> =
        combine(
                scope.filterNotNull().distinctUntilChanged(),
                searchQuery.debounce(HISTORY_SEARCH_DEBOUNCE_MS).distinctUntilChanged(),
            ) { historyScope, query -> historyScope to query.trim() }
            .flatMapLatest { (historyScope, query) ->
                Pager(PagingConfig(pageSize = 50, enablePlaceholders = false)) {
                        if (query.isBlank()) {
                            readingHistoryDao.queryReadingHistory(
                                accountId = historyScope.accountId,
                                groupId = historyScope.groupId,
                                feedId = historyScope.feedId,
                                audioOnly = historyScope.audioOnly,
                            )
                        } else {
                            readingHistoryDao.searchReadingHistory(
                                accountId = historyScope.accountId,
                                text = query.toArticleFtsQuery(),
                                groupId = historyScope.groupId,
                                feedId = historyScope.feedId,
                                audioOnly = historyScope.audioOnly,
                            )
                        }
                    }
                    .flow
            }
            .map { pagingData ->
                pagingData.map { articleWithFeed ->
                    val item: ArticleFlowItem = ArticleFlowItem.Article(articleWithFeed)
                    item
                }
            }
            .cachedIn(viewModelScope)

    fun initialize(accountId: Int, groupId: String?, feedId: String?, audioOnly: Boolean) {
        scope.value = HistoryScope(accountId, groupId, feedId, audioOnly)
    }

    fun updateSearchQuery(query: String) {
        savedStateHandle[SEARCH_QUERY_KEY] = query
    }

    private companion object {
        const val SEARCH_QUERY_KEY = "reading-history-search-query"
    }
}
