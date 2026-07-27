package me.ash.reader.ui.page.settings.interaction

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.ActionPlacement
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.preference.NavigationCustomization
import me.ash.reader.infrastructure.preference.NavigationItemIds
import me.ash.reader.infrastructure.preference.NavigationItemPreference
import me.ash.reader.infrastructure.preference.NavigationPreferenceKeys
import me.ash.reader.infrastructure.preference.encodeNavigationItems
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.RYSwitch
import me.ash.reader.ui.component.base.RadioDialog
import me.ash.reader.ui.component.base.RadioDialogOption
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.theme.palette.onLight

private data class PlacementRequest(
    val title: String,
    val item: NavigationItemPreference,
    val items: List<NavigationItemPreference>,
    val key: Preferences.Key<String>,
)

@Composable
fun NavigationActionsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = LocalSettings.current.navigationCustomization
    var placementRequest by remember { mutableStateOf<PlacementRequest?>(null) }

    fun writeItems(key: Preferences.Key<String>, items: List<NavigationItemPreference>) {
        scope.launch {
            context.dataStore.edit { it[key] = encodeNavigationItems(items) }
        }
    }

    fun writeInt(key: Preferences.Key<Int>, value: Int) {
        scope.launch { context.dataStore.edit { it[key] = value } }
    }

    RYScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(text = "Navigation and actions", desc = "")
                    Spacer(Modifier.height(16.dp))

                    Subtitle(Modifier.padding(horizontal = 24.dp), "Main screen")
                    ConfigurableItems(
                        title = "Bottom filters",
                        items = settings.mainBottomItems,
                        key = NavigationPreferenceKeys.mainBottomItems,
                        allowMore = false,
                        requireVisible = true,
                        onWrite = ::writeItems,
                        onChoosePlacement = { placementRequest = it },
                    )
                    NumericSlider(
                        title = "Bottom icon size",
                        value = settings.mainBottomIconSize,
                        range = NavigationCustomization.MIN_ICON_SIZE..
                            NavigationCustomization.MAX_ICON_SIZE,
                        suffix = "dp",
                        onChange = {
                            writeInt(NavigationPreferenceKeys.mainBottomIconSize, it)
                        },
                    )
                    NumericSlider(
                        title = "Bottom tonal elevation",
                        value = settings.mainBottomElevation,
                        range = NavigationCustomization.MIN_ELEVATION..
                            NavigationCustomization.MAX_ELEVATION,
                        suffix = "dp",
                        onChange = {
                            writeInt(NavigationPreferenceKeys.mainBottomElevation, it)
                        },
                    )
                    ConfigurableItems(
                        title = "Feed page actions",
                        items = settings.feedTopActions,
                        key = NavigationPreferenceKeys.feedTopActions,
                        onWrite = ::writeItems,
                        onChoosePlacement = { placementRequest = it },
                    )
                    ConfigurableItems(
                        title = "Article list actions",
                        items = settings.articleTopActions,
                        key = NavigationPreferenceKeys.articleTopActions,
                        onWrite = ::writeItems,
                        onChoosePlacement = { placementRequest = it },
                    )
                    NumericSlider(
                        title = "Top icon size",
                        value = settings.mainTopIconSize,
                        range = NavigationCustomization.MIN_ICON_SIZE..
                            NavigationCustomization.MAX_ICON_SIZE,
                        suffix = "dp",
                        onChange = { writeInt(NavigationPreferenceKeys.mainTopIconSize, it) },
                    )
                    NumericSlider(
                        title = "Top tonal elevation",
                        value = settings.mainTopElevation,
                        range = NavigationCustomization.MIN_ELEVATION..
                            NavigationCustomization.MAX_ELEVATION,
                        suffix = "dp",
                        onChange = { writeInt(NavigationPreferenceKeys.mainTopElevation, it) },
                    )

                    Spacer(Modifier.height(24.dp))
                    Subtitle(Modifier.padding(horizontal = 24.dp), "Reading screen")
                    ConfigurableItems(
                        title = "Top actions",
                        items = settings.readingTopActions,
                        key = NavigationPreferenceKeys.readingTopActions,
                        onWrite = ::writeItems,
                        onChoosePlacement = { placementRequest = it },
                    )
                    NumericSlider(
                        title = "Top icon size",
                        value = settings.readingTopIconSize,
                        range = NavigationCustomization.MIN_ICON_SIZE..
                            NavigationCustomization.MAX_ICON_SIZE,
                        suffix = "dp",
                        onChange = { writeInt(NavigationPreferenceKeys.readingTopIconSize, it) },
                    )
                    NumericSlider(
                        title = "Top tonal elevation",
                        value = settings.readingTopElevation,
                        range = NavigationCustomization.MIN_ELEVATION..
                            NavigationCustomization.MAX_ELEVATION,
                        suffix = "dp",
                        onChange = { writeInt(NavigationPreferenceKeys.readingTopElevation, it) },
                    )
                    ConfigurableItems(
                        title = "Bottom actions",
                        items = settings.readingBottomActions,
                        key = NavigationPreferenceKeys.readingBottomActions,
                        allowMore = false,
                        requireVisible = true,
                        onWrite = ::writeItems,
                        onChoosePlacement = { placementRequest = it },
                    )
                    NumericSlider(
                        title = "Bottom icon size",
                        value = settings.readingBottomIconSize,
                        range = NavigationCustomization.MIN_ICON_SIZE..
                            NavigationCustomization.MAX_ICON_SIZE,
                        suffix = "dp",
                        onChange = {
                            writeInt(NavigationPreferenceKeys.readingBottomIconSize, it)
                        },
                    )
                    NumericSlider(
                        title = "Bottom tonal elevation",
                        value = settings.readingBottomElevation,
                        range = NavigationCustomization.MIN_ELEVATION..
                            NavigationCustomization.MAX_ELEVATION,
                        suffix = "dp",
                        onChange = {
                            writeInt(NavigationPreferenceKeys.readingBottomElevation, it)
                        },
                    )

                    TextButton(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        onClick = {
                            scope.launch {
                                context.dataStore.edit { preferences ->
                                    navigationPreferenceKeys.forEach { key ->
                                        preferences.remove(key)
                                    }
                                }
                            }
                        },
                    ) { Text("Reset to defaults") }
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )

    placementRequest?.let { request ->
        RadioDialog(
            visible = true,
            title = request.title,
            options = ActionPlacement.entries.map { placement ->
                RadioDialogOption(
                    text = placement.label,
                    selected = request.item.placement == placement,
                    onClick = {
                        val updated = request.items.map {
                            if (it.id == request.item.id) it.copy(placement = placement) else it
                        }
                        writeItems(request.key, updated)
                    },
                )
            },
            onDismissRequest = { placementRequest = null },
        )
    }
}

@Composable
private fun ConfigurableItems(
    title: String,
    items: List<NavigationItemPreference>,
    key: Preferences.Key<String>,
    allowMore: Boolean = true,
    requireVisible: Boolean = false,
    onWrite: (Preferences.Key<String>, List<NavigationItemPreference>) -> Unit,
    onChoosePlacement: (PlacementRequest) -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    Text(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        text = title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
    items.forEachIndexed { index, item ->
        val itemTitle = item.label
        SettingItem(
            title = itemTitle,
            desc = item.placement.label,
            separatedActions = true,
            onClick = {
                if (allowMore) {
                    onChoosePlacement(PlacementRequest(itemTitle, item, items, key))
                } else {
                    val visibleCount = items.count { it.placement == ActionPlacement.Toolbar }
                    val nextPlacement =
                        if (item.placement == ActionPlacement.Toolbar &&
                            (!requireVisible || visibleCount > 1)
                        ) ActionPlacement.Hidden
                        else ActionPlacement.Toolbar
                    onWrite(
                        key,
                        items.map {
                            if (it.id == item.id) it.copy(placement = nextPlacement) else it
                        },
                    )
                }
            },
            action = {
                Row {
                    IconButton(
                        enabled = index > 0,
                        onClick = {
                            val moved = items.toMutableList()
                            val previous = moved[index - 1]
                            moved[index - 1] = moved[index]
                            moved[index] = previous
                            onWrite(key, moved)
                        },
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, "Move earlier")
                    }
                    IconButton(
                        enabled = index < items.lastIndex,
                        onClick = {
                            val moved = items.toMutableList()
                            val next = moved[index + 1]
                            moved[index + 1] = moved[index]
                            moved[index] = next
                            onWrite(key, moved)
                        },
                    ) {
                        Icon(Icons.Rounded.ArrowDownward, "Move later")
                    }
                    if (!allowMore) {
                        val visibleCount = items.count {
                            it.placement == ActionPlacement.Toolbar
                        }
                        RYSwitch(
                            activated = item.placement == ActionPlacement.Toolbar,
                            enable = item.placement != ActionPlacement.Toolbar ||
                                !requireVisible || visibleCount > 1,
                            onClick = {
                                val next =
                                    if (item.placement == ActionPlacement.Toolbar) {
                                        ActionPlacement.Hidden
                                    } else ActionPlacement.Toolbar
                                onWrite(
                                    key,
                                    items.map {
                                        if (it.id == item.id) it.copy(placement = next) else it
                                    },
                                )
                            },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun NumericSlider(
    title: String,
    value: Int,
    range: IntRange,
    suffix: String,
    onChange: (Int) -> Unit,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("$title: ${sliderValue.toInt()}$suffix", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChange(sliderValue.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

private val ActionPlacement.label: String
    get() = when (this) {
        ActionPlacement.Toolbar -> "Toolbar"
        ActionPlacement.More -> "More"
        ActionPlacement.Hidden -> "Hidden"
    }

private val NavigationItemPreference.label: String
    get() = when (id) {
        NavigationItemIds.STARRED -> "Starred"
        NavigationItemIds.UNREAD -> "Unread"
        NavigationItemIds.ALL -> "All"
        NavigationItemIds.READ_LATER -> "Read later"
        NavigationItemIds.SUBSCRIPTION_REPORT -> "Subscription report"
        NavigationItemIds.ADD_SUBSCRIPTION -> "Add subscription"
        NavigationItemIds.HISTORY -> "History"
        NavigationItemIds.AI_SUMMARY -> "AI summary"
        NavigationItemIds.MARK_ALL_READ -> "Mark all as read"
        NavigationItemIds.SEARCH -> "Search"
        NavigationItemIds.TAGS -> "Tags"
        NavigationItemIds.ADD_NOTE -> "Add note"
        NavigationItemIds.STYLE -> "Style"
        NavigationItemIds.SHARE -> "Share"
        NavigationItemIds.FULL_CONTENT -> "Full content"
        NavigationItemIds.TEXT_TO_SPEECH -> "Text to speech"
        else -> id
    }

private val navigationPreferenceKeys: List<Preferences.Key<*>> = listOf(
    NavigationPreferenceKeys.mainBottomItems,
    NavigationPreferenceKeys.feedTopActions,
    NavigationPreferenceKeys.articleTopActions,
    NavigationPreferenceKeys.readingTopActions,
    NavigationPreferenceKeys.readingBottomActions,
    NavigationPreferenceKeys.mainTopIconSize,
    NavigationPreferenceKeys.mainBottomIconSize,
    NavigationPreferenceKeys.readingTopIconSize,
    NavigationPreferenceKeys.readingBottomIconSize,
    NavigationPreferenceKeys.mainTopElevation,
    NavigationPreferenceKeys.mainBottomElevation,
    NavigationPreferenceKeys.readingTopElevation,
    NavigationPreferenceKeys.readingBottomElevation,
)
