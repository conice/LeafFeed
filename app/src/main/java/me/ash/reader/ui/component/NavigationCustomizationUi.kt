package me.ash.reader.ui.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.infrastructure.preference.ActionPlacement
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
