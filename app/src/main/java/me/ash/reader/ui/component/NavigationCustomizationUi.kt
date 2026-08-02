package me.ash.reader.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import me.ash.reader.R
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.infrastructure.preference.ActionPlacement
import me.ash.reader.infrastructure.preference.NavigationActionCatalog
import me.ash.reader.infrastructure.preference.NavigationCustomization
import me.ash.reader.infrastructure.preference.NavigationItemIds

fun NavigationCustomization.visibleMainFilters(): List<Filter> =
    mainBottomItems.mapNotNull { item ->
        if (item.placement != ActionPlacement.Toolbar) return@mapNotNull null
        when (item.id) {
            NavigationItemIds.STARRED -> Filter.Starred
            NavigationItemIds.UNREAD -> Filter.Unread
            NavigationItemIds.ALL -> Filter.All
            NavigationItemIds.READ_LATER -> Filter.ReadLater
            else -> null
        }
    }.ifEmpty { listOf(Filter.All) }

fun responsiveToolbarCapacity(
    iconSize: Int,
    screenWidthDp: Int,
    fontScale: Float,
    normalCapacity: Int,
): Int = when {
    iconSize >= 30 || screenWidthDp < 360 || fontScale > 1.4f -> 2
    iconSize >= 26 || screenWidthDp < 400 || fontScale > 1.15f -> 3
    else -> normalCapacity
}

fun Int.navigationTonalElevation(): Dp = when {
    this <= 0 -> 0.dp
    this <= 2 -> 1.dp
    this <= 4 -> 3.dp
    this <= 7 -> 6.dp
    else -> 8.dp
}

@Composable
fun navigationActionLabel(id: String): String = when (id) {
    NavigationItemIds.STARRED -> stringResource(R.string.starred)
    NavigationItemIds.UNREAD -> stringResource(R.string.unread)
    NavigationItemIds.ALL -> stringResource(R.string.all)
    NavigationItemIds.READ_LATER -> stringResource(R.string.read_later)
    NavigationItemIds.SUBSCRIPTION_REPORT -> stringResource(R.string.subscription_report)
    NavigationItemIds.ADD_SUBSCRIPTION -> stringResource(R.string.subscribe)
    NavigationItemIds.SYNC -> stringResource(R.string.sync_interval)
    NavigationItemIds.HISTORY -> stringResource(R.string.reading_history)
    NavigationItemIds.AI_SUMMARY -> stringResource(R.string.ai_summary)
    NavigationItemIds.SEARCH -> stringResource(R.string.search)
    NavigationItemIds.REFRESH -> stringResource(R.string.refresh)
    NavigationItemIds.TAGS -> stringResource(R.string.manage_tags)
    NavigationItemIds.ADD_NOTE -> stringResource(R.string.add_note)
    NavigationItemIds.STYLE -> stringResource(R.string.style)
    NavigationItemIds.SHARE -> stringResource(R.string.share)
    NavigationItemIds.FULL_CONTENT -> stringResource(R.string.full_content)
    NavigationItemIds.TEXT_TO_SPEECH -> stringResource(R.string.read_aloud)
    NavigationItemIds.OPEN_IN_BROWSER -> stringResource(R.string.open_in_browser)
    NavigationItemIds.PREVIOUS_ARTICLE -> stringResource(R.string.previous_article)
    NavigationItemIds.NEXT_ARTICLE -> stringResource(R.string.next_article)
    else -> NavigationActionCatalog.label(id)
}
