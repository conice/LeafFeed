@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.conice.morss.ui.page.home.feeds

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.eventFlow
import androidx.work.WorkInfo
import kotlinx.coroutines.launch
import com.conice.morss.R
import com.conice.morss.application.service.OpmlImportPhase
import com.conice.morss.infrastructure.preference.LocalFeedsFilterBarPadding
import com.conice.morss.infrastructure.preference.LocalFeedsFilterBarStyle
import com.conice.morss.infrastructure.preference.LocalFeedsFilterBarTonalElevation
import com.conice.morss.infrastructure.preference.LocalFeedsGroupListExpand
import com.conice.morss.infrastructure.preference.LocalFeedsGroupListTonalElevation
import com.conice.morss.infrastructure.preference.LocalFeedsTopBarTonalElevation
import com.conice.morss.infrastructure.preference.LocalNewVersionNumber
import com.conice.morss.infrastructure.preference.LocalSkipVersionNumber
import com.conice.morss.infrastructure.preference.LocalSettings
import com.conice.morss.infrastructure.preference.NavigationItemIds
import com.conice.morss.infrastructure.preference.resolveNavigationActionLayout
import com.conice.morss.ui.component.FilterBar
import com.conice.morss.ui.component.navigationTonalElevation
import com.conice.morss.ui.component.navigationActionIcon
import com.conice.morss.ui.component.responsiveToolbarCapacity
import com.conice.morss.ui.component.visibleMainFilters
import com.conice.morss.ui.component.RefreshIndicatorResult
import com.conice.morss.ui.component.base.DisplayText
import com.conice.morss.ui.component.base.AnimatedIcon
import com.conice.morss.ui.component.base.FeedbackIconButton
import com.conice.morss.ui.component.base.ExpressiveIconButton
import com.conice.morss.ui.component.base.RYScaffold
import com.conice.morss.ui.component.scrollbar.drawVerticalScrollIndicator
import com.conice.morss.ui.ext.collectAsStateValue
import com.conice.morss.infrastructure.android.findActivity
import com.conice.morss.infrastructure.android.getCurrentVersion
import com.conice.morss.ui.ext.surfaceColorAtElevation
import com.conice.morss.infrastructure.android.showToast
import com.conice.morss.ui.page.home.SyncOperationState
import com.conice.morss.ui.page.home.progressFraction
import com.conice.morss.ui.page.home.feeds.accounts.AccountsTab
import com.conice.morss.ui.page.home.feeds.drawer.feed.FeedOptionDrawer
import com.conice.morss.ui.page.home.feeds.drawer.group.GroupOptionDrawer
import com.conice.morss.ui.page.home.feeds.subscribe.SubscribeDialog
import com.conice.morss.ui.page.home.feeds.subscribe.ImportOperationState
import com.conice.morss.ui.page.home.feeds.subscribe.SubscribeViewModel
import com.conice.morss.ui.page.settings.accounts.AccountViewModel

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun FeedsPage(
    //    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    accountViewModel: AccountViewModel = hiltViewModel(),
    feedsViewModel: FeedsViewModel = hiltViewModel(),
    subscribeViewModel: SubscribeViewModel = hiltViewModel(),
    navigateToSettings: () -> Unit,
    navigateToSubscriptionReport: () -> Unit,
    navigationToFlow: () -> Unit,
    navigateToAccountList: () -> Unit,
    navigateToAccountDetail: (Int) -> Unit,
) {
    var accountTabVisible by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val topBarTonalElevation = LocalFeedsTopBarTonalElevation.current
    val groupListTonalElevation = LocalFeedsGroupListTonalElevation.current
    val groupListExpand = LocalFeedsGroupListExpand.current
    val filterBarStyle = LocalFeedsFilterBarStyle.current
    val filterBarPadding = LocalFeedsFilterBarPadding.current
    val filterBarTonalElevation = LocalFeedsFilterBarTonalElevation.current
    val navigationCustomization = LocalSettings.current.navigationCustomization
    val visibleFilters = remember(navigationCustomization.mainBottomItems) {
        navigationCustomization.visibleMainFilters()
    }
    var actionMenuExpanded by remember { mutableStateOf(false) }

    val accounts = accountViewModel.accounts.collectAsStateValue(initial = emptyList())

    val feedsUiState = feedsViewModel.feedsUiState.collectAsStateValue()
    val syncOperationState = feedsViewModel.syncOperationState.collectAsStateValue()
    val subscribeUiState = subscribeViewModel.subscribeUiState.collectAsStateValue()
    val filterState = feedsViewModel.filterStateFlow.collectAsStateValue()
    val importantSum = feedsUiState.importantSum
    val groupWithFeedList = feedsViewModel.groupWithFeedsListFlow.collectAsStateValue()
    val groupsVisible: SnapshotStateMap<String, Boolean> = feedsUiState.groupsVisible
    val hasGroupVisible by
        remember(groupWithFeedList) {
            derivedStateOf { groupWithFeedList.fastAny { groupsVisible[it.group.id] == true } }
        }

    val newVersion = LocalNewVersionNumber.current
    val skipVersion = LocalSkipVersionNumber.current
    val currentVersion = remember { context.getCurrentVersion() }
    val listState =
        if (groupWithFeedList.isNotEmpty()) feedsUiState.listState else rememberLazyListState()

    val owner = LocalLifecycleOwner.current

    var isSyncing by remember { mutableStateOf(false) }
    val syncingState = rememberPullToRefreshState()
    val doSync: () -> Unit = {
        feedsViewModel.sync()
    }

    LaunchedEffect(visibleFilters, filterState.filter) {
        if (filterState.filter !in visibleFilters) {
            feedsViewModel.changeFilter(filterState.copy(filter = visibleFilters.first()))
        }
    }

    DisposableEffect(owner) {
        scope.launch {
            owner.lifecycle.eventFlow.collect {
                when (it) {
                    Lifecycle.Event.ON_RESUME,
                    Lifecycle.Event.ON_PAUSE -> {
                        feedsViewModel.commitDiffs()
                    }

                    else -> {
                        /* no-op */
                    }
                }
            }
        }
        feedsViewModel.syncWorkLiveData.observe(owner) { workInfoList ->
            workInfoList.let {
                isSyncing = it.any { workInfo -> workInfo.state == WorkInfo.State.RUNNING }
            }
        }
        onDispose { feedsViewModel.syncWorkLiveData.removeObservers(owner) }
    }

    val importOperation = subscribeUiState.importOperation
    val indicatorRunning =
        when (importOperation) {
            is ImportOperationState.Importing,
            is ImportOperationState.Updating -> true
            is ImportOperationState.Completed,
            is ImportOperationState.Failed -> false
            ImportOperationState.Idle ->
                syncOperationState is SyncOperationState.Running ||
                    syncOperationState is SyncOperationState.Idle && isSyncing
        }
    val indicatorMessage =
        when (val operation = importOperation) {
            is ImportOperationState.Importing ->
                when (operation.progress.phase) {
                    OpmlImportPhase.Parsing -> stringResource(R.string.parsing_opml)
                    OpmlImportPhase.Saving ->
                        operation.progress.total?.let { total ->
                            stringResource(
                                R.string.importing_subscriptions_progress,
                                operation.progress.processed,
                                total,
                            )
                        } ?: stringResource(R.string.importing_subscriptions)
                }
            is ImportOperationState.Updating -> stringResource(R.string.updating_articles)
            is ImportOperationState.Completed ->
                stringResource(
                    R.string.import_result,
                    operation.result.imported,
                    operation.result.skipped,
                )
            is ImportOperationState.Failed ->
                stringResource(
                    if (operation.importResult == null) {
                        R.string.import_failed
                    } else {
                        R.string.update_articles_failed
                    }
                )
            ImportOperationState.Idle ->
                when (syncOperationState) {
                    is SyncOperationState.Running -> stringResource(R.string.updating_articles)
                    SyncOperationState.Completed -> stringResource(R.string.update_complete)
                    is SyncOperationState.Failed -> stringResource(R.string.update_articles_failed)
                    SyncOperationState.Idle ->
                        if (isSyncing) stringResource(R.string.updating_articles) else null
                }
        }
    val indicatorProgress =
        when (val operation = importOperation) {
            is ImportOperationState.Importing ->
                operation.progress.total
                    ?.takeIf { it > 0 }
                    ?.let { total ->
                        (operation.progress.processed.toFloat() / total).coerceIn(0f, 1f)
                    }
            is ImportOperationState.Updating ->
                operation.total
                    ?.takeIf { it > 0 }
                    ?.let { total -> (operation.completed.toFloat() / total).coerceIn(0f, 1f) }
            ImportOperationState.Idle -> syncOperationState.progressFraction()
            else -> null
        }
    val indicatorResult =
        when (importOperation) {
            is ImportOperationState.Completed -> RefreshIndicatorResult.Success
            is ImportOperationState.Failed -> RefreshIndicatorResult.Error
            ImportOperationState.Idle ->
                when (syncOperationState) {
                    SyncOperationState.Completed -> RefreshIndicatorResult.Success
                    is SyncOperationState.Failed -> RefreshIndicatorResult.Error
                    else -> null
                }
            else -> null
        }

    LaunchedEffect(importOperation) {
        if (importOperation is ImportOperationState.Failed && importOperation.message.isNotBlank()) {
            context.showToast(importOperation.message)
        }
    }

    fun expandAllGroups() {
        groupWithFeedList.forEach { groupWithFeed -> groupsVisible[groupWithFeed.group.id] = true }
    }

    fun collapseAllGroups() {
        groupWithFeedList.forEach { groupWithFeed -> groupsVisible[groupWithFeed.group.id] = false }
    }

    var groupDrawerVisible by rememberSaveable { mutableStateOf(false) }
    var feedDrawerVisible by rememberSaveable { mutableStateOf(false) }
    val groupDrawerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val feedDrawerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismissGroupDrawer = {
        scope.launch { groupDrawerState.hide() }
            .invokeOnCompletion { groupDrawerVisible = false }
        Unit
    }
    val dismissFeedDrawer = {
        scope.launch { feedDrawerState.hide() }
            .invokeOnCompletion { feedDrawerVisible = false }
        Unit
    }

    BackHandler(true) { context.findActivity()?.moveTaskToBack(false) }

    RYScaffold(
        topBarTonalElevation = topBarTonalElevation.value.dp,
        topBar = {
            TopAppBar(
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
                title = {},
                navigationIcon = {
                    FeedbackIconButton(
                        modifier = Modifier.size(22.dp),
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings),
                        tint = MaterialTheme.colorScheme.onSurface,
                        showBadge = newVersion.whetherNeedUpdate(currentVersion, skipVersion),
                    ) {
                        navigateToSettings()
                    }
                },
                actions = {
                    val configuration = LocalConfiguration.current
                    val fontScale = LocalDensity.current.fontScale
                    val addSubscriptionAvailable =
                        subscribeViewModel.rssService.get().addSubscription
                    val availableIds = remember(
                        filterState.filter,
                        addSubscriptionAvailable,
                    ) {
                        buildSet {
                            if (filterState.filter.isAll()) {
                                add(NavigationItemIds.SUBSCRIPTION_REPORT)
                            }
                            if (filterState.filter.isUnread() && addSubscriptionAvailable) {
                                add(NavigationItemIds.ADD_SUBSCRIPTION)
                            }
                            add(NavigationItemIds.SYNC)
                        }
                    }
                    val capacity = responsiveToolbarCapacity(
                        iconSize = navigationCustomization.mainTopIconSize,
                        screenWidthDp = configuration.screenWidthDp,
                        fontScale = fontScale,
                        normalCapacity = 4,
                    )
                    val actionLayout = remember(
                        navigationCustomization.feedTopActions,
                        availableIds,
                        capacity,
                    ) {
                        resolveNavigationActionLayout(
                            navigationCustomization.feedTopActions,
                            availableIds,
                            capacity,
                        )
                    }
                    actionLayout.toolbar
                        .forEach { item ->
                            when (item.id) {
                                NavigationItemIds.SUBSCRIPTION_REPORT -> FeedbackIconButton(
                                    modifier = Modifier.size(
                                        navigationCustomization.mainTopIconSize.dp
                                    ),
                                    imageVector = navigationActionIcon(item.id),
                                    contentDescription = stringResource(
                                        R.string.subscription_report
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    onClick = navigateToSubscriptionReport,
                                )
                                NavigationItemIds.ADD_SUBSCRIPTION -> FeedbackIconButton(
                                    modifier = Modifier.size(
                                        navigationCustomization.mainTopIconSize.dp
                                    ),
                                    imageVector = navigationActionIcon(item.id),
                                    contentDescription = stringResource(R.string.subscribe),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    onClick = subscribeViewModel::showDrawer,
                                )
                                NavigationItemIds.SYNC -> FeedbackIconButton(
                                    modifier = Modifier.size(
                                        navigationCustomization.mainTopIconSize.dp
                                    ),
                                    imageVector = navigationActionIcon(item.id),
                                    contentDescription = stringResource(R.string.sync_interval),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    enabled = !indicatorRunning,
                                    onClick = doSync,
                                )
                            }
                        }
                    val moreActions = actionLayout.overflow
                    if (moreActions.isNotEmpty()) {
                        FeedbackIconButton(
                            modifier = Modifier.size(navigationCustomization.mainTopIconSize.dp),
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.more),
                            tint = MaterialTheme.colorScheme.onSurface,
                            onClick = { actionMenuExpanded = true },
                        )
                        DropdownMenu(
                            expanded = actionMenuExpanded && moreActions.isNotEmpty(),
                            onDismissRequest = { actionMenuExpanded = false },
                        ) {
                            moreActions.forEach { item ->
                                when (item.id) {
                                    NavigationItemIds.SUBSCRIPTION_REPORT -> DropdownMenuItem(
                                        text = { Text(stringResource(R.string.subscription_report)) },
                                        leadingIcon = { Icon(navigationActionIcon(item.id), null) },
                                        onClick = {
                                            actionMenuExpanded = false
                                            navigateToSubscriptionReport()
                                        },
                                    )
                                    NavigationItemIds.ADD_SUBSCRIPTION -> DropdownMenuItem(
                                        text = { Text(stringResource(R.string.subscribe)) },
                                        leadingIcon = { Icon(navigationActionIcon(item.id), null) },
                                        onClick = {
                                            actionMenuExpanded = false
                                            subscribeViewModel.showDrawer()
                                        },
                                    )
                                    NavigationItemIds.SYNC -> DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sync_interval)) },
                                        leadingIcon = { Icon(navigationActionIcon(item.id), null) },
                                        enabled = !indicatorRunning,
                                        onClick = {
                                            actionMenuExpanded = false
                                            doSync()
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceColorAtElevation(
                                maxOf(
                                    topBarTonalElevation.value,
                                    navigationCustomization.mainTopElevation,
                                ).navigationTonalElevation()
                            )
                    ),
            )
        },
        content = {
            PullToRefreshBox(
                state = syncingState,
                isRefreshing = indicatorRunning,
                onRefresh = doSync,
                indicator = {
                    PullToRefreshStatusIndicator(
                        state = syncingState,
                        isRunning = indicatorRunning,
                        message = indicatorMessage,
                        progress =
                            if (importOperation is ImportOperationState.Idle) {
                                null
                            } else {
                                indicatorProgress
                            },
                        result = indicatorResult,
                    )
                },
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize().drawVerticalScrollIndicator(listState), state = listState) {
                    item {
                        DisplayText(text = feedsUiState.account?.name ?: "", desc = "") {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            accountTabVisible = true
                        }
                    }
                    item {
                        FeedsBanner(
                            filter = filterState.filter,
                            desc = importantSum.ifEmpty { stringResource(R.string.loading) },
                        ) {
                            feedsViewModel.changeFilter(filterState.copy(group = null, feed = null))
                            navigationToFlow()
                        }
                    }

                    if (groupWithFeedList.isEmpty() && !indicatorRunning) {
                        item {
                            Column(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.no_subscriptions),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                if (subscribeViewModel.rssService.get().addSubscription) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    FilledTonalButton(onClick = subscribeViewModel::showDrawer) {
                                        Icon(Icons.Rounded.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text(stringResource(R.string.subscribe))
                                    }
                                }
                            }
                        }
                    }

                    if (groupWithFeedList.isNotEmpty()) item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 26.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.feeds),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            ExpressiveIconButton(
                                onClick = {
                                    if (hasGroupVisible) collapseAllGroups() else expandAllGroups()
                                },
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                AnimatedIcon(
                                    imageVector =
                                        if (hasGroupVisible) Icons.Rounded.UnfoldLess
                                        else Icons.Rounded.UnfoldMore,
                                    contentDescription =
                                        stringResource(
                                            if (hasGroupVisible) R.string.unfold_less
                                            else R.string.unfold_more
                                        ),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    groupWithFeedList.forEach { (group, feeds) ->
                        val articleCount =
                            if (filterState.filter.isAll() || filterState.filter.isUnread()) {
                                feeds.sumOf { it.important }
                            } else {
                                null
                            }
                        val isGroupExpanded = {
                            groupsVisible.getOrPut(group.id, groupListExpand::value)
                        }
                        val groupItem: @Composable () -> Unit = {
                            GroupItem(
                                isExpanded = isGroupExpanded,
                                group = group,
                                articleCount = articleCount,
                                onExpanded = {
                                    groupsVisible[group.id] = isGroupExpanded().not()
                                },
                                onLongClick = { groupDrawerVisible = true },
                            ) {
                                feedsViewModel.openFilter(
                                    filterState.copy(group = group, feed = null),
                                    navigationToFlow,
                                )
                            }
                        }
                        val stickyHeaderEnabled =
                            filterState.filter.isAll() || filterState.filter.isUnread()
                        val headerContent: @Composable () -> Unit = {
                            StickyGroupHeaderContainer(
                                standalone = feeds.isEmpty() || !isGroupExpanded(),
                            ) {
                                groupItem()
                            }
                        }
                        if (stickyHeaderEnabled) {
                            stickyHeader(key = "group-${group.id}") {
                                headerContent()
                            }
                        } else {
                            item(key = "group-${group.id}") {
                                headerContent()
                            }
                        }
                        if (isGroupExpanded()) {
                            feeds.forEachIndexed { index, feed ->
                                item(key = "feed-${feed.id}", contentType = "feed") {
                                    StickyGroupFeedContainer(
                                        isLast = index == feeds.lastIndex,
                                        modifier =
                                            Modifier.animateItem(
                                                fadeInSpec =
                                                    MaterialTheme.motionScheme.slowEffectsSpec(),
                                                placementSpec =
                                                    MaterialTheme.motionScheme.defaultSpatialSpec(),
                                                fadeOutSpec =
                                                    MaterialTheme.motionScheme.fastEffectsSpec(),
                                            ),
                                    ) {
                                        FeedItem(
                                            feed = feed,
                                            isLastItem = { index == feeds.lastIndex },
                                            isExpanded = { true },
                                            onClick = {
                                                feedsViewModel.openFilter(
                                                    filterState.copy(feed = feed, group = null),
                                                    navigationToFlow,
                                                )
                                            },
                                            onLongClick = { feedDrawerVisible = true },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                        Spacer(
                            modifier =
                                Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)
                        )
                    }
                }
            }
        },
        bottomBar = {
            FilterBar(
                modifier =
                    with(sharedTransitionScope) {
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState("filterBar"),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    },
                filter = filterState.filter,
                filters = visibleFilters,
                filterBarStyle = filterBarStyle.value,
                filterBarFilled = true,
                filterBarPadding = filterBarPadding.dp,
                filterBarTonalElevation = maxOf(
                    filterBarTonalElevation.value,
                    navigationCustomization.mainBottomElevation,
                ).navigationTonalElevation(),
                iconSize = navigationCustomization.mainBottomIconSize.dp,
                backgroundHeight = navigationCustomization.mainBottomHeight
                    .takeIf { it > 0 }
                    ?.dp,
            ) {
                feedsViewModel.changeFilter(filterState.copy(filter = it))
            }
        },
    )

    SubscribeDialog(subscribeViewModel = subscribeViewModel)

    GroupOptionDrawer(
        visible = groupDrawerVisible,
        sheetState = groupDrawerState,
        onDismissRequest = dismissGroupDrawer,
        contentTypeForGroup = { group ->
            feedsViewModel.contentTypeForScope(
                filterState.copy(group = group, feed = null)
            )
        },
        onViewContent = { group, contentType ->
            feedsViewModel.selectContentType(
                filterState.copy(group = group, feed = null, contentType = contentType),
            ) {
                dismissGroupDrawer()
                navigationToFlow()
            }
        },
    )
    FeedOptionDrawer(
        visible = feedDrawerVisible,
        sheetState = feedDrawerState,
        onDismissRequest = dismissFeedDrawer,
        contentTypeForFeed = { feed ->
            feedsViewModel.contentTypeForScope(
                filterState.copy(feed = feed, group = null)
            )
        },
        onViewContent = { feed, contentType ->
            feedsViewModel.selectContentType(
                filterState.copy(feed = feed, group = null, contentType = contentType),
            ) {
                dismissFeedDrawer()
                navigationToFlow()
            }
        },
    )

    val currentAccountId = feedsUiState.account?.id

    AccountsTab(
        visible = accountTabVisible,
        accounts = accounts,
        currentAccountId = currentAccountId,
        onAccountSwitch = { accountViewModel.switchAccount(it) { accountTabVisible = false } },
        onClickSettings = {
            accountTabVisible = false
            navigateToAccountDetail(currentAccountId!!)
        },
        onClickManage = {
            accountTabVisible = false
            navigateToAccountList()
        },
        onDismissRequest = { accountTabVisible = false },
    )
}
