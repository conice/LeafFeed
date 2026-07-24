@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.page.home.flow

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.LoadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.data.PagerData
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.article.RuleScope
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.domain.model.general.MarkAsReadConditions
import me.ash.reader.infrastructure.preference.LocalFlowArticleListDateStickyHeader
import me.ash.reader.infrastructure.preference.LocalFlowArticleListFeedIcon
import me.ash.reader.infrastructure.preference.LocalFlowArticleListTonalElevation
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarPadding
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarStyle
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarTonalElevation
import me.ash.reader.infrastructure.preference.LocalFlowTopBarTonalElevation
import me.ash.reader.infrastructure.preference.LocalMarkAsReadOnScroll
import me.ash.reader.infrastructure.preference.LocalOpenLink
import me.ash.reader.infrastructure.preference.LocalOpenLinkSpecificBrowser
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.preference.LocalSharedContent
import me.ash.reader.infrastructure.preference.PullToLoadNextFeedPreference
import me.ash.reader.ui.component.FilterBar
import me.ash.reader.ui.component.AiSummaryDialog
import me.ash.reader.ui.component.CompactStatusIndicator
import me.ash.reader.ui.component.RefreshIndicatorResult
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYExtensibleVisibility
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.scrollbar.VerticalScrollIndicatorFactory
import me.ash.reader.ui.component.scrollbar.drawVerticalScrollIndicator
import me.ash.reader.ui.component.scrollbar.scrollIndicator
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.openURL
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.motion.Direction
import me.ash.reader.ui.motion.sharedYAxisTransitionExpressive
import me.ash.reader.ui.page.adaptive.ArticleListReaderViewModel
import me.ash.reader.ui.page.home.SyncOperationState
import me.ash.reader.ui.page.home.reading.PullToLoadDefaults
import me.ash.reader.ui.page.home.reading.PullToLoadDefaults.ContentOffsetMultiple
import me.ash.reader.ui.page.home.reading.PullToLoadState
import me.ash.reader.ui.page.home.reading.pullToLoad
import me.ash.reader.ui.page.home.reading.rememberPullToLoadState

private data class FlowContentKey(
    val filterIndex: Int,
    val groupId: String?,
    val feedId: String?,
    val highlightRuleId: String?,
    val highlightUnreadOnly: Boolean,
)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
fun FlowPage(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    isTwoPane: Boolean,
    viewModel: ArticleListReaderViewModel,
    onNavigateUp: () -> Unit,
    navigateToArticle: (String, Int) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val articleListTonalElevation = LocalFlowArticleListTonalElevation.current
    val articleListFeedIcon = LocalFlowArticleListFeedIcon.current
    val articleListDateStickyHeader = LocalFlowArticleListDateStickyHeader.current
    val topBarTonalElevation = LocalFlowTopBarTonalElevation.current
    val filterBarStyle = LocalFlowFilterBarStyle.current
    val filterBarPadding = LocalFlowFilterBarPadding.current
    val filterBarTonalElevation = LocalFlowFilterBarTonalElevation.current
    val sharedContent = LocalSharedContent.current
    val markAsReadOnScroll = LocalMarkAsReadOnScroll.current.value
    val context = LocalContext.current

    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current

    val settings = LocalSettings.current
    val pullToSwitchFeed = settings.pullToSwitchFeed

    val flowUiState = viewModel.flowUiState.collectAsStateValue()
    if (flowUiState == null) return
    val podcastState = viewModel.podcastPlayer.state.collectAsStateValue()
    val searchContentInput = viewModel.searchContentInput.collectAsStateValue()
    val savedSearches = viewModel.savedSearches.collectAsStateValue()
    val titleSummaryState = viewModel.titleSummaryState.collectAsStateValue()

    val pagerData: PagerData = flowUiState.pagerData

    val filterUiState = pagerData.filterState

    val listStates =
        rememberSaveable(saver = lazyListStateMapSaver) {
            mutableMapOf<String, LazyListState>()
        }
    val listStateKey = remember(filterUiState) { pagerData.listStateKey() }
    val listState =
        remember(listStateKey) {
            listStates.getOrPut(listStateKey) { LazyListState(0, 0) }
        }

    val isTopBarElevated = topBarTonalElevation.value > 0
    val scrolledTopBarContainerColor =
        with(MaterialTheme.colorScheme) { if (isTopBarElevated) surfaceContainer else surface }

    val titleText =
        when {
            filterUiState.group != null -> filterUiState.group.name
            filterUiState.feed != null -> filterUiState.feed.name
            else -> filterUiState.filter.toName()
        }

    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var markAsRead by remember { mutableStateOf(false) }
    var onSearch by rememberSaveable { mutableStateOf(false) }
    var saveSearchDialogVisible by remember { mutableStateOf(false) }
    var savedSearchesDialogVisible by remember { mutableStateOf(false) }
    var savedSearchName by remember { mutableStateOf("") }

    var currentPullToLoadState: PullToLoadState? by remember { mutableStateOf(null) }
    var currentLoadAction: LoadAction? by remember { mutableStateOf(null) }

    val settleSpec = remember { spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy) }

    val lastVisibleIndex =
        remember(listState) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .filterNotNull()
        }

    val onToggleStarred: (ArticleWithFeed) -> Unit = remember(viewModel) {
        { article ->
            viewModel.updateStarredStatus(
                articleId = article.article.id,
                isStarred = !article.article.isStarred,
            )
        }
    }

    val onToggleReadLater: (ArticleWithFeed) -> Unit = remember(viewModel) {
        { article ->
            viewModel.updateReadLaterStatus(
                articleId = article.article.id,
                isReadLater = !article.article.isReadLater,
            )
        }
    }

    val onToggleRead: (ArticleWithFeed) -> Unit = remember(viewModel) {
        { articleWithFeed -> viewModel.diffMapHolder.updateDiff(articleWithFeed) }
    }

    val onMarkAboveAsRead: ((ArticleWithFeed, Int) -> Unit)? =
        remember(viewModel) {
            { article, index ->
                viewModel.markAsReadFromList(
                    articleId = article.article.id,
                    itemIndex = index,
                    markAbove = true,
                )
            }
        }

    val onMarkBelowAsRead: ((ArticleWithFeed, Int) -> Unit)? =
        remember(viewModel) {
            { article, index ->
                viewModel.markAsReadFromList(
                    articleId = article.article.id,
                    itemIndex = index,
                    markAbove = false,
                )
            }
        }

    val onShare: ((ArticleWithFeed) -> Unit)? = remember(sharedContent, context) {
        { articleWithFeed ->
            with(articleWithFeed.article) { sharedContent.share(context, title, link) }
        }
    }

    LaunchedEffect(onSearch) {
        if (!onSearch) {
            keyboardController?.hide()
            viewModel.inputSearchContent(null)
        }
    }

    val topAppBarState = rememberTopAppBarState()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    val scrollAppBarToCollapsed =
        remember(topAppBarState) {
            {
                scope.launch {
                    val initial = topAppBarState.heightOffset
                    val target = topAppBarState.heightOffsetLimit
                    if (initial != target)
                        animate(
                            initialValue = initial,
                            targetValue = target,
                            initialVelocity = 0f,
                            animationSpec = settleSpec,
                        ) { value, _ ->
                            topAppBarState.heightOffset = value
                        }
                }
            }
        }

    val snapAppBarToCollapsed =
        remember(topAppBarState) {
            {
                scope.launch {
                    val initial = topAppBarState.heightOffset
                    val target = topAppBarState.heightOffsetLimit
                    if (initial != target) {
                        topAppBarState.heightOffset = target
                    }
                }
            }
        }

    val readerState = viewModel.readerStateStateFlow.collectAsStateValue()

    var activePagingItems: LazyPagingItems<ArticleFlowItem>? by remember { mutableStateOf(null) }

    if (isTwoPane) {
        LaunchedEffect(readerState) {
            if (readerState.articleId != null) {
                val articleId = readerState.articleId

                val itemList = activePagingItems?.itemSnapshotList

                val index =
                    itemList?.indexOfFirst {
                        it is ArticleFlowItem.Article && it.articleWithFeed.article.id == articleId
                    } ?: -1

                if (index != -1) {
                    scrollAppBarToCollapsed()
                    listState.animateScrollToItem(index, scrollOffset = -200)
                }
            }
        }
    } else {
        LaunchedEffect(Unit) {
            if (readerState.articleId != null) {
                val articleId = readerState.articleId

                val itemList = activePagingItems?.itemSnapshotList

                val index =
                    itemList?.indexOfFirst {
                        it is ArticleFlowItem.Article && it.articleWithFeed.article.id == articleId
                    } ?: -1

                if (index != -1) {
                    snapAppBarToCollapsed()
                    listState.requestScrollToItem(index, scrollOffset = -400)
                }
            }
        }
    }

    val isSyncing = viewModel.isSyncingFlow.collectAsStateValue()
    val syncOperationState = viewModel.syncOperationState.collectAsStateValue()
    val syncIndicatorRunning =
        syncOperationState is SyncOperationState.Running ||
            syncOperationState is SyncOperationState.Idle && isSyncing
    val syncIndicatorMessage =
        when (syncOperationState) {
            is SyncOperationState.Running -> stringResource(R.string.updating_articles)
            SyncOperationState.Completed -> stringResource(R.string.update_complete)
            is SyncOperationState.Failed -> stringResource(R.string.update_articles_failed)
            SyncOperationState.Idle ->
                if (isSyncing) stringResource(R.string.updating_articles) else null
        }
    val syncIndicatorResult =
        when (syncOperationState) {
            SyncOperationState.Completed -> RefreshIndicatorResult.Success
            is SyncOperationState.Failed -> RefreshIndicatorResult.Error
            else -> null
        }

    Box(modifier = Modifier.fillMaxSize()) {
        RYScaffold(
            containerTonalElevation = articleListTonalElevation.value.dp,
            topBar = {
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme,
                    typography =
                        MaterialTheme.typography.copy(
                            headlineMedium = MaterialTheme.typography.displaySmall,
                            titleLarge =
                                MaterialTheme.typography.titleLarge.merge(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                        ),
                ) {
                    LargeTopAppBar(
                        modifier =
                            Modifier.clickable(
                                onClick = {
                                    scope.launch {
                                        if (listState.firstVisibleItemIndex != 0) {
                                            listState.animateScrollToItem(0)
                                        }
                                    }
                                },
                            ),
                        title = {
                            val textStyle = LocalTextStyle.current
                            val color = LocalContentColor.current
                            if (textStyle.fontSize.value > 18f) {
                                BasicText(
                                    modifier =
                                        Modifier.padding(
                                            start = if (articleListFeedIcon.value) 34.dp else 8.dp,
                                            end = 24.dp,
                                        ),
                                    text = titleText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = textStyle,
                                    color = { color },
                                    autoSize =
                                        TextAutoSize.StepBased(
                                            minFontSize = 28.sp,
                                            maxFontSize = textStyle.fontSize,
                                        ),
                                )
                            } else {
                                Text(
                                    text = titleText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        expandedHeight = 136.dp,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            FeedbackIconButton(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onSurface,
                            ) {
                                onSearch = false
                                onNavigateUp()
                            }
                        },
                        actions = {
                            RYExtensibleVisibility(
                                visible =
                                    !onSearch &&
                                        (filterUiState.filter.isUnread() ||
                                            filterUiState.filter.isAll())
                            ) {
                                FeedbackIconButton(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = stringResource(R.string.ai_summary),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    onClick = { viewModel.summarizeCurrentTitles() },
                                )
                            }
                            RYExtensibleVisibility(visible = !filterUiState.filter.isStarred()) {
                                FeedbackIconButton(
                                    imageVector = Icons.Rounded.DoneAll,
                                    contentDescription = stringResource(R.string.mark_all_as_read),
                                    tint =
                                        if (markAsRead) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                ) {
                                    if (markAsRead) {
                                        markAsRead = false
                                    } else {
                                        scope
                                            .launch {
                                                if (listState.firstVisibleItemIndex != 0) {
                                                    listState.animateScrollToItem(0)
                                                }
                                            }
                                            .invokeOnCompletion {
                                                markAsRead = true
                                                onSearch = false
                                            }
                                    }
                                }
                            }
                            FeedbackIconButton(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.search),
                                tint =
                                    if (onSearch) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            ) {
                                if (onSearch) {
                                    onSearch = false
                                } else {
                                    scope
                                        .launch {
                                            if (listState.firstVisibleItemIndex != 0) {
                                                listState.animateScrollToItem(0)
                                            }
                                        }
                                        .invokeOnCompletion {
                                            scope.launch {
                                                onSearch = true
                                                markAsRead = false
                                                delay(100)
                                                focusRequester.requestFocus()
                                            }
                                        }
                                }
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                scrolledContainerColor = scrolledTopBarContainerColor
                            ),
                    )
                }
            },
            content = {
                RYExtensibleVisibility(modifier = Modifier.zIndex(1f), visible = onSearch) {
                    BackHandler(onSearch) { onSearch = false }
                    SearchBar(
                        value = searchContentInput ?: "",
                        placeholder =
                            when {
                                filterUiState.group != null ->
                                    stringResource(
                                        R.string.search_for_in,
                                        filterUiState.filter.toName(),
                                        filterUiState.group.name,
                                    )

                                filterUiState.feed != null ->
                                    stringResource(
                                        R.string.search_for_in,
                                        filterUiState.filter.toName(),
                                        filterUiState.feed.name,
                                    )

                                else ->
                                    stringResource(
                                        R.string.search_for,
                                        filterUiState.filter.toName(),
                                    )
                            },
                        focusRequester = focusRequester,
                        onValueChange = { viewModel.inputSearchContent(it) },
                        onClose = {
                            onSearch = false
                            viewModel.inputSearchContent(null)
                        },
                        onSave = {
                            savedSearchName = ""
                            saveSearchDialogVisible = true
                        },
                        savedSearchCount = savedSearches.size,
                        onShowSaved = { savedSearchesDialogVisible = true },
                    )
                }

                RYExtensibleVisibility(markAsRead) {
                    BackHandler(markAsRead) { markAsRead = false }

                    MarkAsReadBar {
                        markAsRead = false
                        viewModel.updateReadStatus(
                            filterState = filterUiState,
                            articleId = null,
                            conditions = it,
                            isUnread = false,
                        )
                        if (
                            it == MarkAsReadConditions.All &&
                                (filterUiState.group != null || filterUiState.feed != null)
                        ) {
                            onNavigateUp()
                        }
                    }
                }
                val contentTransitionVertical =
                    sharedYAxisTransitionExpressive(direction = Direction.Forward)
                AnimatedContent(
                    targetState = flowUiState,
                    contentKey = {
                        val state = it.pagerData.filterState
                        FlowContentKey(
                            filterIndex = state.filter.index,
                            groupId = state.group?.id,
                            feedId = state.feed?.id,
                            highlightRuleId = state.highlightRuleId,
                            highlightUnreadOnly = state.highlightUnreadOnly,
                        )
                    },
                    transitionSpec = {
                        val targetFilter = targetState.pagerData.filterState
                        val initialFilter = initialState.pagerData.filterState

                        if (targetFilter.filter.index != initialFilter.filter.index) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else if (
                            targetFilter.group?.id != initialFilter.group?.id ||
                                targetFilter.feed?.id != initialFilter.feed?.id
                        ) {
                            contentTransitionVertical
                        } else {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                    },
                ) { flowUiState ->
                    val pager = flowUiState.pagerData.pager
                    val filterState = flowUiState.pagerData.filterState
                    val pagingItems = pager.collectAsLazyPagingItems()
                    SideEffect {
                        if (pager === pagerData.pager) activePagingItems = pagingItems
                    }

                    if (markAsReadOnScroll && filterState.filter.isUnread()) {
                        LaunchedEffect(listState, pager) {
                            snapshotFlow { listState.isScrollInProgress }
                                .filter { isScrolling -> !isScrolling }
                                .collectLatest {
                                    val firstArticle =
                                        listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.contentType == CONTENT_TYPE_ARTICLE }
                                            ?: return@collectLatest
                                    val articleId =
                                        firstArticle.key as? String ?: return@collectLatest
                                    viewModel.markAsReadFromList(
                                        articleId = articleId,
                                        itemIndex = firstArticle.index,
                                        markAbove = true,
                                    )
                                }
                        }
                    }

                    if (settings.flowArticleListDateStickyHeader.value) {
                        LaunchedEffect(lastVisibleIndex) {
                            lastVisibleIndex.collect {
                                if (it in (pagingItems.itemCount - 25..pagingItems.itemCount - 1)) {
                                    pagingItems.get(it)
                                }
                            }
                        }
                    }

                    val listState = remember(pager) { listState }

                    val isSyncing by rememberUpdatedState(isSyncing)

                    LaunchedEffect(pagingItems) {
                        snapshotFlow { pagingItems.loadState.isIdle }
                            .collect {
                                if (isSyncing) {
                                    listState.scrollToItem(0)
                                }
                            }
                    }

                    val loadAction =
                        remember(pager, flowUiState, pullToSwitchFeed) {
                                when (pullToSwitchFeed) {
                                    PullToLoadNextFeedPreference.None -> null
                                    else -> {
                                        when {
                                            flowUiState.nextFilterState != null ->
                                                LoadAction.NextFeed.fromFilterState(
                                                    flowUiState.nextFilterState
                                                )

                                            filterState.filter.isUnread() &&
                                                pullToSwitchFeed ==
                                                    PullToLoadNextFeedPreference
                                                        .MarkAsReadAndLoadNextFeed ->
                                                LoadAction.MarkAllAsRead

                                            else -> null
                                        }
                                    }
                                }
                            }
                    SideEffect {
                        if (pager === pagerData.pager) currentLoadAction = loadAction
                    }

                    val onLoadNext: (() -> Unit)? =
                        when (loadAction) {
                            is LoadAction.NextFeed -> viewModel::loadNextFeedOrGroup
                            LoadAction.MarkAllAsRead -> {
                                {
                                    viewModel.markAllAsRead()
                                    currentPullToLoadState?.animateDistanceTo(
                                        targetValue = 0f,
                                        animationSpec = settleSpec,
                                    )
                                }
                            }

                            else -> null
                        }

                    val onPullToSync: (() -> Unit)? =
                        if (isSyncing) null
                        else {
                            {
                                viewModel.sync()
                                currentPullToLoadState?.animateDistanceTo(
                                    targetValue = 0f,
                                    animationSpec = settleSpec,
                                )
                            }
                        }

                    val pullToLoadState =
                        rememberPullToLoadState(
                                key = pager,
                                onLoadNext = onLoadNext,
                                onLoadPrevious = onPullToSync,
                                loadThreshold = PullToLoadDefaults.loadThreshold(.1f),
                            )
                    SideEffect {
                        if (pager === pagerData.pager) currentPullToLoadState = pullToLoadState
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier =
                                Modifier.pullToLoad(
                                        state = pullToLoadState,
                                        enabled = true,
                                        contentOffsetY = { fraction ->
                                            if (fraction > 0f) {
                                                (fraction * ContentOffsetMultiple * 1.5f)
                                                    .dp
                                                    .roundToPx()
                                            } else {
                                                (fraction * ContentOffsetMultiple * 2f)
                                                    .dp
                                                    .roundToPx()
                                            }
                                        },
                                        onScroll = {
                                            if (it < -10f) {
                                                markAsRead = false
                                            }
                                        },
                                    )
                                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                                    .fillMaxSize()
                                    .drawVerticalScrollIndicator(listState),
                            state = listState,
                        ) {
                            when (pagingItems.loadState.refresh) {
                                is LoadState.Error -> {
                                    item {
                                        FlowLoadError(onRetry = pagingItems::retry)
                                    }
                                }
                                is LoadState.NotLoading -> {
                                    if (pagingItems.itemCount == 0) {
                                        item {
                                            EmptyFlowState(
                                                filter = filterUiState.filter,
                                                isSearch = !searchContentInput.isNullOrBlank(),
                                            )
                                        }
                                    }
                                }
                                else -> Unit
                            }
                            ArticleList(
                                pagingItems = pagingItems,
                                diffMap = viewModel.diffMapHolder.diffMap,
                                isShowFeedIcon = articleListFeedIcon.value,
                                isShowStickyHeader = articleListDateStickyHeader.value,
                                articleListTonalElevation = articleListTonalElevation.value,
                                onClick = { articleWithFeed, index ->
                                    if (articleWithFeed.feed.isBrowser) {
                                        viewModel.diffMapHolder.updateDiff(
                                            articleWithFeed,
                                            isUnread = false,
                                        )
                                        context.openURL(
                                            articleWithFeed.article.link,
                                            openLink,
                                            openLinkSpecificBrowser,
                                        )
                                    } else {
                                        navigateToArticle(articleWithFeed.article.id, index)
                                    }
                                },
                                onToggleStarred = onToggleStarred,
                                onToggleReadLater = onToggleReadLater,
                                onToggleRead = onToggleRead,
                                onMarkAboveAsRead = onMarkAboveAsRead,
                                onMarkBelowAsRead = onMarkBelowAsRead,
                                onShare = onShare,
                                playingPodcastId = podcastState.articleId,
                                isPodcastPlaying = podcastState.isPlaying,
                                podcastPositionMs = podcastState.positionMs,
                                queuedPodcastIds = podcastState.queue.mapTo(mutableSetOf()) { it.id },
                                observePodcastDownload = viewModel::observePodcastDownload,
                                onPodcastPlay = { articleWithFeed ->
                                    if (podcastState.articleId == articleWithFeed.article.id) {
                                        viewModel.podcastPlayer.toggle()
                                    } else {
                                        viewModel.podcastPlayer.play(
                                            articleWithFeed.article,
                                            artist = articleWithFeed.feed.name,
                                            artwork = articleWithFeed.article.img ?: articleWithFeed.feed.icon,
                                        )
                                    }
                                },
                                onPodcastEnqueue = { articleWithFeed ->
                                    viewModel.podcastPlayer.enqueue(
                                        articleWithFeed.article,
                                        artist = articleWithFeed.feed.name,
                                    )
                                    context.showToast(context.getString(R.string.podcast_added))
                                },
                                onPodcastDownload = { articleWithFeed ->
                                    val article = articleWithFeed.article
                                    if (article.downloadedPath == null) {
                                        viewModel.downloadPodcast(article) { result ->
                                            context.showToast(
                                                context.getString(
                                                    if (result.isSuccess) R.string.podcast_download_queued
                                                    else R.string.podcast_download_failed
                                                )
                                            )
                                        }
                                    } else {
                                        viewModel.removePodcastDownload(article) { result ->
                                            context.showToast(
                                                context.getString(
                                                    if (result.isSuccess) R.string.podcast_download_removed
                                                    else R.string.podcast_download_remove_failed
                                                )
                                            )
                                        }
                                    }
                                },
                            )
                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                                Spacer(
                                    modifier =
                                        Modifier.windowInsetsBottomHeight(
                                            WindowInsets.navigationBars
                                        )
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center,
            bottomBar = {
                Column {
                    if (filterUiState.filter.isHighlighted()) {
                        LazyRow(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        ) {
                            item {
                                FilterChip(
                                    selected = filterUiState.highlightRuleId == null,
                                    onClick = {
                                        viewModel.changeFilter(filterUiState.copy(highlightRuleId = null))
                                    },
                                    label = { Text(stringResource(R.string.all)) },
                                )
                            }
                            items(flowUiState.highlightRules, key = { it.id }) { rule ->
                                FilterChip(
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                                        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                        fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                    ),
                                    selected = filterUiState.highlightRuleId == rule.id,
                                    onClick = {
                                        viewModel.changeFilter(filterUiState.copy(highlightRuleId = rule.id))
                                    },
                                    label = {
                                        Text(
                                            text = (if (rule.scope == RuleScope.GLOBAL) "🌐 " else "") +
                                                (if (rule.isRegex) "/${rule.pattern}/" else rule.pattern),
                                            maxLines = 1,
                                        )
                                    },
                                )
                            }
                        }
                    }
                    FilterBar(
                        modifier =
                            with(sharedTransitionScope) {
                                Modifier.sharedElement(
                                    sharedContentState = rememberSharedContentState("filterBar"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )
                            },
                        filter = filterUiState.filter,
                        filterBarStyle = filterBarStyle.value,
                        filterBarFilled = true,
                        filterBarPadding = filterBarPadding.dp,
                        filterBarTonalElevation = filterBarTonalElevation.value.dp,
                        filters = listOf(Filter.Starred, Filter.Unread, Filter.All, Filter.Highlighted, Filter.ReadLater),
                    ) {
                        val nextFilter = if (it == Filter.Highlighted && filterUiState.filter.isHighlighted()) {
                            viewModel.changeFilter(
                                filterUiState.copy(
                                    highlightUnreadOnly = !filterUiState.highlightUnreadOnly,
                                )
                            )
                            null
                        } else if (it == Filter.Highlighted) {
                            viewModel.changeFilter(
                                filterUiState.copy(filter = it, highlightUnreadOnly = true),
                            )
                            null
                        } else {
                            it
                        }
                        if (nextFilter != null && filterUiState.filter != nextFilter) {
                            viewModel.changeFilter(
                                filterUiState.copy(
                                    filter = nextFilter,
                                    highlightUnreadOnly = if (nextFilter.isHighlighted()) true
                                    else filterUiState.highlightUnreadOnly,
                                )
                            )
                        } else if (nextFilter != null) {
                            scope.launch {
                                if (listState.firstVisibleItemIndex != 0) {
                                    listState.animateScrollToItem(0)
                                }
                            }
                        }
                    }
                }
            },
        )
        currentPullToLoadState?.let {
            PullToSyncIndicator(
                pullToLoadState = it,
                isSyncing = syncIndicatorRunning,
                message = syncIndicatorMessage,
                result = syncIndicatorResult,
            )
            PullToLoadIndicator(
                state = it,
                loadAction = currentLoadAction,
                modifier =
                    Modifier.padding(bottom = 36.dp)
                        .windowInsetsPadding(
                            WindowInsets.safeContent.only(WindowInsetsSides.Horizontal)
                        ),
            )
        }
        CompactStatusIndicator(
            visible = !titleSummaryState.visible &&
                titleSummaryState.loading,
            message = stringResource(R.string.ai_summary),
            topPadding = if (syncIndicatorRunning || syncIndicatorResult != null) 128.dp else 72.dp,
        )
    }
    if (saveSearchDialogVisible) {
        AlertDialog(
            onDismissRequest = { saveSearchDialogVisible = false },
            title = { Text(stringResource(R.string.save_search)) },
            text = {
                OutlinedTextField(
                    value = savedSearchName,
                    onValueChange = { savedSearchName = it },
                    label = { Text(stringResource(R.string.search_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = savedSearchName.isNotBlank(),
                    onClick = {
                        viewModel.saveCurrentSearch(savedSearchName)
                        saveSearchDialogVisible = false
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { saveSearchDialogVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (savedSearchesDialogVisible) {
        AlertDialog(
            onDismissRequest = { savedSearchesDialogVisible = false },
            title = { Text(stringResource(R.string.saved_searches)) },
            text = {
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    savedSearches.forEach { search ->
                        Row(Modifier.fillMaxWidth()) {
                            TextButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.applySavedSearch(search)
                                    savedSearchesDialogVisible = false
                                },
                            ) { Text(search.name) }
                            TextButton(onClick = { viewModel.deleteSavedSearch(search) }) {
                                Text(stringResource(R.string.delete))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { savedSearchesDialogVisible = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
    AiSummaryDialog(
        visible = titleSummaryState.visible,
        loading = titleSummaryState.loading,
        summary = titleSummaryState.summary,
        failure = titleSummaryState.failure,
        onDismiss = viewModel::hideTitleSummary,
        sourceDescription = stringResource(R.string.ai_summary_source_titles),
        articleIds = titleSummaryState.articleIds,
        initialScrollOffset = titleSummaryState.scrollOffset,
        onArticleClick = { articleId, scrollOffset ->
            viewModel.saveTitleSummaryScrollOffset(scrollOffset)
            if (isTwoPane) viewModel.hideTitleSummary()
            navigateToArticle(articleId, viewModel.findArticleIndex(articleId).coerceAtLeast(0))
        },
        onMarkSummarizedAsRead =
            if (
                titleSummaryState.failure == null &&
                    titleSummaryState.unreadArticleIds.isNotEmpty()
            ) {
                viewModel::markSummarizedTitlesAsRead
            } else {
                null
            },
        onRegenerate = { viewModel.summarizeCurrentTitles(forceRefresh = true) },
    )
}

@Composable
private fun EmptyFlowState(filter: Filter, isSearch: Boolean) {
    val message =
        when {
            isSearch -> stringResource(R.string.no_search_results)
            filter.isUnread() -> stringResource(R.string.no_unread_articles)
            filter.isStarred() -> stringResource(R.string.no_starred_articles)
            filter.isHighlighted() -> stringResource(R.string.no_highlighted_articles)
            filter.isReadLater() -> stringResource(R.string.no_read_later_articles)
            else -> stringResource(R.string.no_articles)
        }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = filter.iconOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun FlowLoadError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.articles_load_failed),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

private val lazyListStateMapSaver =
    listSaver<MutableMap<String, LazyListState>, Any>(
        save = { states ->
            states.flatMap { (key, state) ->
                listOf(key, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
            }
        },
        restore = { values ->
            mutableMapOf<String, LazyListState>().apply {
                values.chunked(3).forEach { entry ->
                    this[entry[0] as String] =
                        LazyListState(
                            firstVisibleItemIndex = (entry[1] as Number).toInt(),
                            firstVisibleItemScrollOffset = (entry[2] as Number).toInt(),
                        )
                }
            }
        },
    )

private fun PagerData.listStateKey(): String =
    with(filterState) {
        listOf(
                filter.index.toString(),
                group?.id.orEmpty(),
                feed?.id.orEmpty(),
                highlightRuleId.orEmpty(),
                highlightUnreadOnly.toString(),
                searchContent.orEmpty(),
            )
            .joinToString(separator = "|") { value -> "${value.length}:$value" }
    }
