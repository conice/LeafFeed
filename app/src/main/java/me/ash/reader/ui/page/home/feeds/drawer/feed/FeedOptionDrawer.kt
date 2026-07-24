package me.ash.reader.ui.page.home.feeds.drawer.feed

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.domain.data.ArticleContentType
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.infrastructure.preference.LocalOpenLink
import me.ash.reader.infrastructure.preference.LocalOpenLinkSpecificBrowser
import me.ash.reader.ui.component.ChangeUrlDialog
import me.ash.reader.ui.component.ArticleRuleDialog
import me.ash.reader.domain.model.article.RuleScope
import me.ash.reader.domain.model.article.RuleType
import me.ash.reader.ui.component.FeedIcon
import me.ash.reader.ui.component.RenameDialog
import me.ash.reader.ui.component.base.BottomDrawer
import me.ash.reader.ui.component.base.ExpressiveIconButton
import me.ash.reader.ui.component.base.TextFieldDialog
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.openURL
import me.ash.reader.ui.ext.roundClick
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.interaction.alphaIndicationClickable
import me.ash.reader.ui.page.home.feeds.FeedOptionView
import me.ash.reader.ui.page.home.feeds.ContentTypeSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedOptionDrawer(
    visible: Boolean,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    contentTypeForFeed: (Feed) -> ArticleContentType = { ArticleContentType.ARTICLE },
    onViewContent: (Feed, ArticleContentType) -> Unit = { _, _ -> },
    feedOptionViewModel: FeedOptionViewModel = hiltViewModel(),
    content: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current
    val feedOptionUiState = feedOptionViewModel.feedOptionUiState.collectAsStateValue()
    val feed = feedOptionUiState.feed
    val toastString = stringResource(R.string.rename_toast, feedOptionUiState.newName)


    BottomDrawer(
        visible = visible,
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
        sheetContent = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    ExpressiveIconButton(onClick = feedOptionViewModel::reloadIcon) {
                        FeedIcon(feedName = feed?.name, iconUrl = feed?.icon, size = 24.dp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        modifier = Modifier.alphaIndicationClickable {
                            if (feedOptionViewModel.rssService.get().updateSubscription) {
                                feedOptionViewModel.showRenameDialog()
                            }
                        },
                        text = feed?.name ?: stringResource(R.string.unknown),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                ContentTypeSelector(
                    modifier = Modifier.fillMaxWidth(),
                    selectedContentType = feed?.let(contentTypeForFeed)
                        ?: ArticleContentType.ARTICLE,
                    onSelect = { contentType ->
                        feed?.let { onViewContent(it, contentType) }
                    },
                )
                Spacer(modifier = Modifier.height(26.dp))
                FeedOptionView(
                    link = feed?.url ?: stringResource(R.string.unknown),
                    groups = feedOptionUiState.groups,
                    selectedAllowNotificationPreset = feedOptionUiState.feed?.isNotification
                        ?: false,
                    selectedParseFullContentPreset = feedOptionUiState.feed?.isFullContent ?: false,
                    selectedOpenInBrowserPreset = feedOptionUiState.feed?.isBrowser ?: false,
                    isMoveToGroup = true,
                    showGroup = feedOptionViewModel.rssService.get().moveSubscription,
                    showUnsubscribe = feedOptionViewModel.rssService.get().deleteSubscription,
                    notSubscribeMode = true,
                    selectedGroupId = feedOptionUiState.feed?.groupId ?: "",
                    allowNotificationPresetOnClick = {
                        feedOptionViewModel.changeAllowNotificationPreset()
                    },
                    parseFullContentPresetOnClick = {
                        feedOptionViewModel.changeParseFullContentPreset()
                    },
                    openInBrowserPresetOnClick = {
                        feedOptionViewModel.changeOpenInBrowserPreset()
                    },
                    clearArticlesOnClick = {
                        feedOptionViewModel.showClearDialog()
                    },
                    unsubscribeOnClick = {
                        feedOptionViewModel.showDeleteDialog()
                    },
                    onGroupClick = {
                        feedOptionViewModel.selectedGroup(it)
                    },
                    onAddNewGroup = {
                        feedOptionViewModel.showNewGroupDialog()
                    },
                    onFeedUrlClick = {
                        context.openURL(feed?.url, openLink, openLinkSpecificBrowser)
                    },
                    onFeedUrlLongClick = {
                        if (feedOptionViewModel.rssService.get().updateSubscription) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            feedOptionViewModel.showFeedUrlDialog()
                        }
                    },
                    filterRulesOnClick = { feedOptionViewModel.showRuleDialog(RuleType.FILTER) },
                    highlightRulesOnClick = { feedOptionViewModel.showRuleDialog(RuleType.HIGHLIGHT) },
                )
            }
        }
    ) {
        content()
    }

    DeleteFeedDialog(
        feedName = feed?.name ?: "",
        onConfirm = onDismissRequest)

    ClearFeedDialog(
        feedName = feed?.name ?: "",
        onConfirm = onDismissRequest)

    TextFieldDialog(
        visible = feedOptionUiState.newGroupDialogVisible,
        title = stringResource(R.string.create_new_group),
        icon = Icons.Outlined.CreateNewFolder,
        value = feedOptionUiState.newGroupContent,
        placeholder = stringResource(R.string.name),
        onValueChange = {
            feedOptionViewModel.inputNewGroup(it)
        },
        onDismissRequest = {
            feedOptionViewModel.hideNewGroupDialog()
        },
        onConfirm = {
            feedOptionViewModel.addNewGroup()
        }
    )

    RenameDialog(
        visible = feedOptionUiState.renameDialogVisible,
        value = feedOptionUiState.newName,
        onValueChange = {
            feedOptionViewModel.inputNewName(it)
        },
        onDismissRequest = {
            feedOptionViewModel.hideRenameDialog()
        },
        onConfirm = {
            feedOptionViewModel.renameFeed()
            onDismissRequest()
            context.showToast(toastString)
        }
    )

    ChangeUrlDialog(
        visible = feedOptionUiState.changeUrlDialogVisible,
        value = feedOptionUiState.newUrl,
        onValueChange = {
            feedOptionViewModel.inputNewUrl(it)
        },
        onDismissRequest = {
            feedOptionViewModel.hideFeedUrlDialog()
        },
        onConfirm = {
            feedOptionViewModel.changeFeedUrl()
            onDismissRequest()
        }
    )
    ArticleRuleDialog(
        visible = feedOptionUiState.ruleDialogType != null,
        title = stringResource(if (feedOptionUiState.ruleDialogType == RuleType.FILTER) R.string.filter_rules else R.string.highlight_rules),
        rules = feedOptionViewModel.articleRules.collectAsStateValue().filter {
            ((it.scope == RuleScope.FEED && it.scopeId == feed?.id) || it.scope == RuleScope.GLOBAL) &&
                it.accountId == feed?.accountId && it.type == feedOptionUiState.ruleDialogType
        },
        onDismiss = feedOptionViewModel::hideRuleDialog,
        onAdd = feedOptionViewModel::addRule,
        onEdit = feedOptionViewModel::editRule,
        onReorder = feedOptionViewModel::reorderRules,
        onDelete = feedOptionViewModel::deleteRule,
    )
}
