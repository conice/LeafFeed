package me.ash.reader.ui.page.home.flow

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.rounded.MoreVert
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
import me.ash.reader.infrastructure.preference.NavigationActionCatalog
import me.ash.reader.infrastructure.preference.NavigationItemIds
import me.ash.reader.infrastructure.preference.NavigationItemPreference
import me.ash.reader.infrastructure.preference.resolveNavigationActionLayout
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.navigationActionIcon
import me.ash.reader.ui.component.responsiveToolbarCapacity

@Composable
fun ArticleListTopActions(
    actions: List<NavigationItemPreference>,
    iconSize: Int,
    isUnread: Boolean,
    isAll: Boolean,
    isReadLater: Boolean,
    searchActive: Boolean,
    onHistory: () -> Unit,
    onAiSummary: () -> Unit,
    onClearReadLater: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val capacity = responsiveToolbarCapacity(
        iconSize = iconSize,
        screenWidthDp = configuration.screenWidthDp,
        fontScale = fontScale,
        normalCapacity = 4,
    )
    val availableIds = remember(isUnread, isAll, searchActive) {
        buildSet {
            if (!searchActive && isUnread) add(NavigationItemIds.HISTORY)
            if (!searchActive && (isUnread || isAll)) add(NavigationItemIds.AI_SUMMARY)
            add(NavigationItemIds.SEARCH)
            if (!searchActive) add(NavigationItemIds.REFRESH)
        }
    }
    val layout = remember(actions, availableIds, capacity) {
        resolveNavigationActionLayout(actions, availableIds, capacity)
    }
    val showClearReadLater = isReadLater && !searchActive
    var menuExpanded by remember { mutableStateOf(false) }

    layout.toolbar.forEach { action ->
        ActionIcon(
            id = action.id,
            iconSize = iconSize,
            searchActive = searchActive,
            onClick = when (action.id) {
                NavigationItemIds.HISTORY -> onHistory
                NavigationItemIds.AI_SUMMARY -> onAiSummary
                NavigationItemIds.SEARCH -> onSearch
                else -> onRefresh
            },
        )
    }
    if (layout.overflow.isNotEmpty() || showClearReadLater) {
        FeedbackIconButton(
            modifier = Modifier.size(iconSize.dp),
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.more),
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = { menuExpanded = true },
        )
        DropdownMenu(
            expanded = menuExpanded && (layout.overflow.isNotEmpty() || showClearReadLater),
            onDismissRequest = { menuExpanded = false },
        ) {
            layout.overflow.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label()) },
                    leadingIcon = { Icon(action.icon(), null) },
                    onClick = {
                        menuExpanded = false
                        when (action.id) {
                            NavigationItemIds.HISTORY -> onHistory()
                            NavigationItemIds.AI_SUMMARY -> onAiSummary()
                            NavigationItemIds.SEARCH -> onSearch()
                            NavigationItemIds.REFRESH -> onRefresh()
                        }
                    },
                )
            }
            if (showClearReadLater) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_read_from_read_later)) },
                    leadingIcon = { Icon(Icons.Outlined.BookmarkRemove, null) },
                    onClick = {
                        menuExpanded = false
                        onClearReadLater()
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
    onClick: () -> Unit,
) {
    val active = id == NavigationItemIds.SEARCH && searchActive
    FeedbackIconButton(
        modifier = Modifier.size(iconSize.dp),
        imageVector = navigationActionIcon(id),
        contentDescription = when (id) {
            NavigationItemIds.HISTORY -> stringResource(R.string.reading_history)
            NavigationItemIds.AI_SUMMARY -> stringResource(R.string.ai_summary)
            NavigationItemIds.REFRESH -> stringResource(R.string.refresh)
            else -> stringResource(R.string.search)
        },
        tint = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        onClick = onClick,
    )
}

private fun NavigationItemPreference.label(): String = NavigationActionCatalog.label(id)

private fun NavigationItemPreference.icon() = navigationActionIcon(id)
