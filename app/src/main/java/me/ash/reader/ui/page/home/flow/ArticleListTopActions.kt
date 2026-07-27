package me.ash.reader.ui.page.home.flow

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.ActionPlacement
import me.ash.reader.infrastructure.preference.NavigationItemIds
import me.ash.reader.infrastructure.preference.NavigationItemPreference
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.responsiveToolbarCapacity

@Composable
fun ArticleListTopActions(
    actions: List<NavigationItemPreference>,
    iconSize: Int,
    isUnread: Boolean,
    isAll: Boolean,
    isStarred: Boolean,
    searchActive: Boolean,
    markAsReadActive: Boolean,
    onHistory: () -> Unit,
    onAiSummary: () -> Unit,
    onMarkAllRead: () -> Unit,
    onSearch: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val capacity = responsiveToolbarCapacity(
        iconSize = iconSize,
        screenWidthDp = configuration.screenWidthDp,
        fontScale = fontScale,
        normalCapacity = 4,
    )
    val available = actions.filter { action ->
        action.placement != ActionPlacement.Hidden && when (action.id) {
            NavigationItemIds.HISTORY -> !searchActive && isUnread
            NavigationItemIds.AI_SUMMARY -> !searchActive && (isUnread || isAll)
            NavigationItemIds.MARK_ALL_READ -> !isStarred
            NavigationItemIds.SEARCH -> true
            else -> false
        }
    }
    val configuredToolbar = available.filter { it.placement == ActionPlacement.Toolbar }
    val hasOverflow = available.any { it.placement == ActionPlacement.More } ||
        configuredToolbar.size > capacity
    val toolbarCapacity = if (hasOverflow) (capacity - 1).coerceAtLeast(1) else capacity
    val toolbar = configuredToolbar.take(toolbarCapacity)
    val toolbarIds = toolbar.mapTo(mutableSetOf()) { it.id }
    val more = available.filter {
        it.placement == ActionPlacement.More ||
            it.placement == ActionPlacement.Toolbar && it.id !in toolbarIds
    }
    var menuExpanded by remember { mutableStateOf(false) }

    toolbar.forEach { action ->
        ActionIcon(
            id = action.id,
            iconSize = iconSize,
            searchActive = searchActive,
            markAsReadActive = markAsReadActive,
            onClick = when (action.id) {
                NavigationItemIds.HISTORY -> onHistory
                NavigationItemIds.AI_SUMMARY -> onAiSummary
                NavigationItemIds.MARK_ALL_READ -> onMarkAllRead
                else -> onSearch
            },
        )
    }
    if (more.isNotEmpty()) {
        FeedbackIconButton(
            modifier = Modifier.size(iconSize.dp),
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.more),
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = { menuExpanded = true },
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            more.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label()) },
                    leadingIcon = { Icon(action.icon(), null) },
                    onClick = {
                        menuExpanded = false
                        when (action.id) {
                            NavigationItemIds.HISTORY -> onHistory()
                            NavigationItemIds.AI_SUMMARY -> onAiSummary()
                            NavigationItemIds.MARK_ALL_READ -> onMarkAllRead()
                            NavigationItemIds.SEARCH -> onSearch()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ActionIcon(
    id: String,
    iconSize: Int,
    searchActive: Boolean,
    markAsReadActive: Boolean,
    onClick: () -> Unit,
) {
    val active = when (id) {
        NavigationItemIds.SEARCH -> searchActive
        NavigationItemIds.MARK_ALL_READ -> markAsReadActive
        else -> false
    }
    FeedbackIconButton(
        modifier = Modifier.size(iconSize.dp),
        imageVector = when (id) {
            NavigationItemIds.HISTORY -> Icons.Rounded.History
            NavigationItemIds.AI_SUMMARY -> Icons.Outlined.AutoAwesome
            NavigationItemIds.MARK_ALL_READ -> Icons.Rounded.DoneAll
            else -> Icons.Rounded.Search
        },
        contentDescription = when (id) {
            NavigationItemIds.HISTORY -> stringResource(R.string.reading_history)
            NavigationItemIds.AI_SUMMARY -> stringResource(R.string.ai_summary)
            NavigationItemIds.MARK_ALL_READ -> stringResource(R.string.mark_all_as_read)
            else -> stringResource(R.string.search)
        },
        tint = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        onClick = onClick,
    )
}

private fun NavigationItemPreference.label(): String = when (id) {
    NavigationItemIds.HISTORY -> "History"
    NavigationItemIds.AI_SUMMARY -> "AI summary"
    NavigationItemIds.MARK_ALL_READ -> "Mark all as read"
    else -> "Search"
}

private fun NavigationItemPreference.icon() = when (id) {
    NavigationItemIds.HISTORY -> Icons.Rounded.History
    NavigationItemIds.AI_SUMMARY -> Icons.Outlined.AutoAwesome
    NavigationItemIds.MARK_ALL_READ -> Icons.Rounded.DoneAll
    else -> Icons.Rounded.Search
}
