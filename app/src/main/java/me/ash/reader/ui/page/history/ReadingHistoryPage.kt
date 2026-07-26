@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.ash.reader.ui.page.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import androidx.paging.compose.collectAsLazyPagingItems
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
import me.ash.reader.R
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.infrastructure.preference.LocalFlowArticleListFeedIcon
import me.ash.reader.infrastructure.preference.LocalFlowArticleListTonalElevation
import me.ash.reader.infrastructure.preference.LocalOpenLink
import me.ash.reader.infrastructure.preference.LocalOpenLinkSpecificBrowser
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.ext.openURL
import me.ash.reader.ui.page.home.flow.ArticleList
import me.ash.reader.ui.page.home.flow.SearchBar

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
    private val articleDao: ArticleDao,
) : ViewModel() {
    private val scope = MutableStateFlow<HistoryScope?>(null)
    val searchQuery = MutableStateFlow("")

    val pagingData: Flow<PagingData<ArticleFlowItem>> =
        combine(
                scope.filterNotNull().distinctUntilChanged(),
                searchQuery.debounce(HISTORY_SEARCH_DEBOUNCE_MS).distinctUntilChanged(),
            ) { historyScope, query -> historyScope to query.trim() }
            .flatMapLatest { (historyScope, query) ->
                Pager(PagingConfig(pageSize = 50, enablePlaceholders = false)) {
                        if (query.isBlank()) {
                            articleDao.queryReadingHistory(
                                accountId = historyScope.accountId,
                                groupId = historyScope.groupId,
                                feedId = historyScope.feedId,
                                audioOnly = historyScope.audioOnly,
                            )
                        } else {
                            articleDao.searchReadingHistory(
                                accountId = historyScope.accountId,
                                text = query,
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
}

@Composable
fun ReadingHistoryPage(
    accountId: Int,
    groupId: String?,
    feedId: String?,
    audioOnly: Boolean,
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
    viewModel: ReadingHistoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val articleListFeedIcon = LocalFlowArticleListFeedIcon.current
    val articleListTonalElevation = LocalFlowArticleListTonalElevation.current
    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current
    val pagingItems = viewModel.pagingData.collectAsLazyPagingItems()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var searchVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(accountId, groupId, feedId, audioOnly) {
        viewModel.initialize(accountId, groupId, feedId, audioOnly)
    }
    BackHandler(enabled = searchVisible) {
        searchVisible = false
        viewModel.searchQuery.value = ""
    }

    RYScaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.reading_history)) },
                navigationIcon = {
                    FeedbackIconButton(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = onBack,
                    )
                },
                actions = {
                    FeedbackIconButton(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = { searchVisible = !searchVisible },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        content = {
            Column(modifier = Modifier.fillMaxSize()) {
                if (searchVisible) {
                    SearchBar(
                        value = searchQuery,
                        placeholder =
                            stringResource(
                                R.string.search_for,
                                stringResource(R.string.reading_history),
                            ),
                        onValueChange = { viewModel.searchQuery.value = it },
                        onClose = {
                            searchVisible = false
                            viewModel.searchQuery.value = ""
                        },
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier =
                            Modifier.fillMaxSize()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                        state = listState,
                    ) {
                        ArticleList(
                            pagingItems = pagingItems,
                            diffMap = emptyMap(),
                            isShowFeedIcon = articleListFeedIcon.value,
                            isShowStickyHeader = false,
                            articleListTonalElevation = articleListTonalElevation.value,
                            isMenuEnabled = false,
                            onClick = { articleWithFeed, _ ->
                                if (articleWithFeed.feed.isBrowser) {
                                    context.openURL(
                                        articleWithFeed.article.link,
                                        openLink,
                                        openLinkSpecificBrowser,
                                    )
                                } else {
                                    onOpenArticle(articleWithFeed.article.id)
                                }
                            },
                        )
                    }

                    when {
                        pagingItems.loadState.refresh is LoadState.Loading ->
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        pagingItems.loadState.refresh is LoadState.Error ->
                            TextButton(
                                modifier = Modifier.align(Alignment.Center),
                                onClick = pagingItems::retry,
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        pagingItems.itemCount == 0 ->
                            Column(
                                modifier =
                                    Modifier.align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                )
                                Text(
                                    modifier = Modifier.padding(top = 12.dp),
                                    text =
                                        stringResource(
                                            if (searchQuery.isBlank()) {
                                                R.string.no_reading_history
                                            } else {
                                                R.string.no_search_results
                                            }
                                        ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                    }
                }
            }
        },
    )
}
