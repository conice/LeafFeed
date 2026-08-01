package me.ash.reader.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.ui.graphics.vector.ImageVector
import me.ash.reader.infrastructure.preference.NavigationItemIds

fun navigationActionIcon(id: String): ImageVector = when (id) {
    NavigationItemIds.SUBSCRIPTION_REPORT -> Icons.Outlined.Insights
    NavigationItemIds.SETTINGS -> Icons.Outlined.Settings
    NavigationItemIds.SYNC -> Icons.Rounded.Sync
    NavigationItemIds.HISTORY -> Icons.Rounded.History
    NavigationItemIds.AI_SUMMARY -> Icons.Outlined.AutoAwesome
    NavigationItemIds.REFRESH -> Icons.Rounded.Refresh
    NavigationItemIds.TAGS -> Icons.Outlined.Label
    NavigationItemIds.ADD_NOTE -> Icons.Outlined.NoteAdd
    NavigationItemIds.STYLE -> Icons.Outlined.Palette
    NavigationItemIds.SHARE -> Icons.Outlined.Share
    NavigationItemIds.OPEN_IN_BROWSER -> Icons.Outlined.OpenInBrowser
    else -> Icons.Rounded.Search
}
