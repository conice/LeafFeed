@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.conice.morss.ui.page.history

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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.conice.morss.R
import com.conice.morss.infrastructure.preference.LocalFlowArticleListFeedIcon
import com.conice.morss.infrastructure.preference.LocalFlowArticleListTonalElevation
import com.conice.morss.ui.component.base.FeedbackIconButton
import com.conice.morss.ui.component.base.RYScaffold
import com.conice.morss.ui.page.home.flow.ArticleList
import com.conice.morss.ui.page.home.flow.SearchBar

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
    val uriHandler = LocalUriHandler.current
    val articleListFeedIcon = LocalFlowArticleListFeedIcon.current
    val articleListTonalElevation = LocalFlowArticleListTonalElevation.current
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
        viewModel.updateSearchQuery("")
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
                        onValueChange = viewModel::updateSearchQuery,
                        onClose = {
                            searchVisible = false
                            viewModel.updateSearchQuery("")
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
                                    uriHandler.openUri(articleWithFeed.article.link)
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
