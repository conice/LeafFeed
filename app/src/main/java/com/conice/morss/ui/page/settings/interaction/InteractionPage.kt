package com.conice.morss.ui.page.settings.interaction

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.conice.morss.R
import com.conice.morss.infrastructure.preference.InitialFilterPreference
import com.conice.morss.infrastructure.preference.InitialPagePreference
import com.conice.morss.infrastructure.preference.LocalArticleListSwipeEndAction
import com.conice.morss.infrastructure.preference.LocalArticleListSwipeStartAction
import com.conice.morss.infrastructure.preference.LocalHideEmptyGroups
import com.conice.morss.infrastructure.preference.LocalInitialFilter
import com.conice.morss.infrastructure.preference.LocalInitialPage
import com.conice.morss.infrastructure.preference.LocalMarkAsReadOnScroll
import com.conice.morss.infrastructure.preference.LocalPullToSwitchArticle
import com.conice.morss.infrastructure.preference.LocalSettings
import com.conice.morss.infrastructure.preference.LocalSharedContent
import com.conice.morss.infrastructure.preference.LocalSortUnreadArticles
import com.conice.morss.infrastructure.preference.OpenLinkPreference
import com.conice.morss.infrastructure.preference.PullToLoadNextFeedPreference
import com.conice.morss.infrastructure.preference.SharedContentPreference
import com.conice.morss.infrastructure.preference.SortUnreadArticlesPreference
import com.conice.morss.infrastructure.preference.SwipeEndActionPreference
import com.conice.morss.infrastructure.preference.SwipeStartActionPreference
import com.conice.morss.ui.component.base.DisplayText
import com.conice.morss.ui.component.base.FeedbackIconButton
import com.conice.morss.ui.component.base.RYScaffold
import com.conice.morss.ui.component.base.RYSwitch
import com.conice.morss.ui.component.base.RadioDialog
import com.conice.morss.ui.component.base.RadioDialogOption
import com.conice.morss.ui.component.base.Subtitle
import com.conice.morss.infrastructure.android.getBrowserAppList
import com.conice.morss.ui.page.settings.SettingItem
import com.conice.morss.ui.page.settings.SettingItemType
import com.conice.morss.ui.page.settings.SettingKeys
import com.conice.morss.ui.theme.palette.onLight

@Composable
fun InteractionPage(
    onBack: () -> Unit,
    targetSetting: String? = null,
    navigateToLanguages: () -> Unit,
    navigateToNavigationActions: () -> Unit,
) {
    val context = LocalContext.current
    val initialPage = LocalInitialPage.current
    val initialFilter = LocalInitialFilter.current
    val swipeToStartAction = LocalArticleListSwipeStartAction.current
    val swipeToEndAction = LocalArticleListSwipeEndAction.current
    val markAsReadOnScroll = LocalMarkAsReadOnScroll.current
    val hideEmptyGroups = LocalHideEmptyGroups.current
    val sortUnreadArticles = LocalSortUnreadArticles.current
    val pullToSwitchArticle = LocalPullToSwitchArticle.current
    val sharedContent = LocalSharedContent.current
    val settings = LocalSettings.current
    val openLink = settings.openLink
    val openLinkSpecificBrowser = settings.openLinkSpecificBrowser
    val pullToSwitchFeed = settings.pullToSwitchFeed

    val scope = rememberCoroutineScope()
    val isOpenLinkSpecificBrowserItemEnabled = remember(openLink) {
        openLink == OpenLinkPreference.SpecificBrowser
    }
    var initialPageDialogVisible by remember { mutableStateOf(false) }
    var initialFilterDialogVisible by remember { mutableStateOf(false) }
    var swipeStartDialogVisible by remember { mutableStateOf(false) }
    var swipeEndDialogVisible by remember { mutableStateOf(false) }
    var openLinkDialogVisible by remember { mutableStateOf(false) }
    var openLinkSpecificBrowserDialogVisible by remember { mutableStateOf(false) }
    var sharedContentDialogVisible by remember { mutableStateOf(false) }
    var showSortUnreadArticlesDialog by remember { mutableStateOf(false) }
    var showPullToLoadDialog by remember { mutableStateOf(false) }

    RYScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack
            )
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(text = stringResource(R.string.settings_general_title), desc = "")
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.on_start),
                    )
                    SettingItem(
                        title = stringResource(R.string.initial_page),
                        desc = initialPage.toDesc(context),
                        settingKey = SettingKeys.InteractionInitialPage,
                        targetKey = targetSetting,
                        type = SettingItemType.Choice,
                        onClick = {
                            initialPageDialogVisible = true
                        },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.initial_filter),
                        desc = initialFilter.toDesc(context),
                        settingKey = SettingKeys.InteractionInitialFilter,
                        targetKey = targetSetting,
                        type = SettingItemType.Choice,
                        onClick = {
                            initialFilterDialogVisible = true
                        },
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))

                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.navigation_section),
                    )
                    SettingItem(
                        title = stringResource(R.string.navigation_actions_title),
                        desc = stringResource(R.string.navigation_actions_setting_desc),
                        icon = Icons.Outlined.Tune,
                        settingKey = SettingKeys.InteractionNavigation,
                        targetKey = targetSetting,
                        type = SettingItemType.Navigation,
                        onClick = navigateToNavigationActions,
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))

                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.feeds_page),
                    )
                    SettingItem(
                        title = stringResource(R.string.hide_empty_groups),
                        settingKey = SettingKeys.InteractionHideEmpty,
                        targetKey = targetSetting,
                        onClick = {
                            hideEmptyGroups.toggle(context, scope)
                        },
                    ) {
                        RYSwitch(activated = hideEmptyGroups.value) {
                            hideEmptyGroups.toggle(context, scope)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.article_list),
                    )
                    SettingItem(
                        title = stringResource(R.string.swipe_to_start),
                        desc = swipeToStartAction.desc,
                        settingKey = SettingKeys.InteractionSwipeStart,
                        targetKey = targetSetting,
                        type = SettingItemType.Choice,
                        onClick = {
                            swipeStartDialogVisible = true
                        },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.swipe_to_end),
                        desc = swipeToEndAction.desc,
                        settingKey = SettingKeys.InteractionSwipeEnd,
                        targetKey = targetSetting,
                        type = SettingItemType.Choice,
                        onClick = {
                            swipeEndDialogVisible = true
                        },
                    ) {}

                    SettingItem(
                        title = stringResource(R.string.sort_unread_articles),
                        settingKey = SettingKeys.InteractionSortUnread,
                        targetKey = targetSetting,
                        type = SettingItemType.Choice,
                        onClick = {
                            showSortUnreadArticlesDialog = true
                        },
                        desc = sortUnreadArticles.description()
                    ) {
                    }

                    SettingItem(
                        title = stringResource(R.string.mark_as_read_on_scroll),
                        settingKey = SettingKeys.InteractionMarkScroll,
                        targetKey = targetSetting,
                        onClick = {
                            markAsReadOnScroll.toggle(context, scope)
                        },
                    ) {
                        RYSwitch(activated = markAsReadOnScroll.value) {
                            markAsReadOnScroll.toggle(context, scope)
                        }
                    }

                    SettingItem(
                        title = stringResource(R.string.pull_from_bottom),
                        desc = pullToSwitchFeed.description(),
                        settingKey = SettingKeys.InteractionPullFeed,
                        targetKey = targetSetting,
                        type = SettingItemType.Choice,
                        onClick = {
                            showPullToLoadDialog = true
                        },
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.reading_page),
                    )
                    SettingItem(
                        title = stringResource(id = R.string.pull_to_switch_article),
                        settingKey = SettingKeys.InteractionPullArticle,
                        targetKey = targetSetting,
                        onClick = { pullToSwitchArticle.toggle(context, scope) }) {
                        RYSwitch(activated = pullToSwitchArticle.value) {
                            pullToSwitchArticle.toggle(context, scope)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.external_links),
                    )
                    SettingItem(
                        title = stringResource(R.string.initial_open_app),
                        desc = openLink.toDesc(context),
                        settingKey = SettingKeys.InteractionOpenLinks,
                        targetKey = targetSetting,
                        type = SettingItemType.Choice,
                        onClick = {
                            openLinkDialogVisible = true
                        },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.open_link_specific_browser),
                        desc = openLinkSpecificBrowser.toDesc(context),
                        enabled = isOpenLinkSpecificBrowserItemEnabled,
                        settingKey = SettingKeys.InteractionBrowser,
                        targetKey = targetSetting,
                        type = SettingItemType.Choice,
                        onClick = {

                            if (isOpenLinkSpecificBrowserItemEnabled) {
                                openLinkSpecificBrowserDialogVisible = true
                            }
                        },
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))

                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.share),
                    )
                    SettingItem(
                        title = stringResource(R.string.shared_content),
                        desc = sharedContent.toDesc(context),
                        settingKey = SettingKeys.InteractionShare,
                        targetKey = targetSetting,
                        type = SettingItemType.Choice,
                        onClick = {
                            sharedContentDialogVisible = true
                        },
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))

                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.settings_system_section),
                    )
                    SettingItem(
                        title = stringResource(R.string.languages),
                        settingKey = SettingKeys.InteractionLanguages,
                        targetKey = targetSetting,
                        type = SettingItemType.Navigation,
                        onClick = navigateToLanguages,
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
    )

    RadioDialog(
        visible = initialPageDialogVisible,
        title = stringResource(R.string.initial_page),
        options = InitialPagePreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == initialPage,
            ) {
                it.put(context, scope)
            }
        },
    ) {
        initialPageDialogVisible = false
    }

    RadioDialog(
        visible = initialFilterDialogVisible,
        title = stringResource(R.string.initial_filter),
        options = InitialFilterPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == initialFilter,
            ) {
                it.put(context, scope)
            }
        },
    ) {
        initialFilterDialogVisible = false
    }

    RadioDialog(
        visible = swipeStartDialogVisible,
        title = stringResource(R.string.swipe_to_start),
        options = SwipeStartActionPreference.values.map {
            RadioDialogOption(
                text = it.desc,
                selected = it == swipeToStartAction,
            ) {
                it.put(context, scope)
            }
        },
    ) {
        swipeStartDialogVisible = false
    }

    RadioDialog(
        visible = swipeEndDialogVisible,
        title = stringResource(R.string.swipe_to_end),
        options = SwipeEndActionPreference.values.map {
            RadioDialogOption(
                text = it.desc,
                selected = it == swipeToEndAction,
            ) {
                it.put(context, scope)
            }
        },
    ) {
        swipeEndDialogVisible = false
    }


    RadioDialog(
        visible = openLinkDialogVisible,
        title = stringResource(R.string.initial_open_app),
        options = OpenLinkPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == openLink,
            ) {
                it.put(context, scope)
            }
        },
    ) {
        openLinkDialogVisible = false
    }

    val browserList = remember(context) {
        context.getBrowserAppList()
    }

    RadioDialog(
        visible = openLinkSpecificBrowserDialogVisible,
        title = stringResource(R.string.open_link_specific_browser),
        options = browserList.map {
            RadioDialogOption(
                text = it.loadLabel(context.packageManager).toString(),
                selected = it.activityInfo.packageName == openLinkSpecificBrowser.packageName,
            ) {
                openLinkSpecificBrowser.copy(packageName = it.activityInfo.packageName)
                    .put(context, scope)
            }
        },
        onDismissRequest = {
            openLinkSpecificBrowserDialogVisible = false
        }
    )

    RadioDialog(
        visible = sharedContentDialogVisible,
        title = stringResource(R.string.shared_content),
        options = SharedContentPreference.values.map {
            RadioDialogOption(
                text = it.toDesc(context),
                selected = it == sharedContent,
            ) {
                it.put(context, scope)
            }
        },
    ) {
        sharedContentDialogVisible = false
    }

    RadioDialog(
        visible = showSortUnreadArticlesDialog,
        title = stringResource(R.string.sort_unread_articles),
        options = SortUnreadArticlesPreference.values.map {
            RadioDialogOption(
                text = it.description(),
                selected = it == sortUnreadArticles,
            ) {
                it.put(context, scope)
            }
        },
        onDismissRequest = {
            showSortUnreadArticlesDialog = false
        }
    )

    RadioDialog(
        visible = showPullToLoadDialog,
        title = stringResource(R.string.pull_from_bottom),
        options = PullToLoadNextFeedPreference.values.map {
            RadioDialogOption(
                text = it.description(),
                selected = it == pullToSwitchFeed,
            ) {
                it.put(context, scope)
            }
        },
        onDismissRequest = {
            showPullToLoadDialog = false
        }
    )
}
