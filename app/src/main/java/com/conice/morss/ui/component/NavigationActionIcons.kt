package com.conice.morss.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.ui.graphics.vector.ImageVector
import com.conice.morss.infrastructure.preference.NavigationItemIds

fun navigationActionIcon(id: String): ImageVector = when (id) {
    NavigationItemIds.STARRED -> Icons.Rounded.StarOutline
    NavigationItemIds.UNREAD -> Icons.Outlined.FiberManualRecord
    NavigationItemIds.ALL -> Icons.AutoMirrored.Rounded.Subject
    NavigationItemIds.READ_LATER -> Icons.Outlined.BookmarkBorder
    NavigationItemIds.SUBSCRIPTION_REPORT -> Icons.Outlined.Insights
    NavigationItemIds.ADD_SUBSCRIPTION -> Icons.Rounded.Add
    NavigationItemIds.SYNC -> Icons.Rounded.Sync
    NavigationItemIds.HISTORY -> Icons.Rounded.History
    NavigationItemIds.AI_SUMMARY -> Icons.Outlined.AutoAwesome
    NavigationItemIds.REFRESH -> Icons.Rounded.Refresh
    NavigationItemIds.TAGS -> Icons.Outlined.Label
    NavigationItemIds.ADD_NOTE -> Icons.Outlined.NoteAdd
    NavigationItemIds.STYLE -> Icons.Outlined.Palette
    NavigationItemIds.SHARE -> Icons.Outlined.Share
    NavigationItemIds.FULL_CONTENT -> Icons.AutoMirrored.Outlined.Article
    NavigationItemIds.TEXT_TO_SPEECH -> Icons.Outlined.Headphones
    NavigationItemIds.OPEN_IN_BROWSER -> Icons.Outlined.OpenInBrowser
    NavigationItemIds.PREVIOUS_ARTICLE -> Icons.Rounded.SkipPrevious
    NavigationItemIds.NEXT_ARTICLE -> Icons.Rounded.SkipNext
    else -> Icons.Rounded.Search
}
