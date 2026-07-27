package me.ash.reader.ui.page.settings.interaction

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.ActionPlacement
import me.ash.reader.infrastructure.preference.FlowFilterBarStylePreference
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarStyle
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
import kotlin.math.roundToInt

private data class PlacementRequest(
    val title: String,
    val item: NavigationItemPreference,
    val items: List<NavigationItemPreference>,
    val key: Preferences.Key<String>,
)

private data class AddRequest(
    val title: String,
    val items: List<NavigationItemPreference>,
    val key: Preferences.Key<String>,
)

private data class MoveRequest(
    val item: NavigationItemPreference,
    val items: List<NavigationItemPreference>,
    val key: Preferences.Key<String>,
)

@Composable
fun NavigationActionsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = LocalSettings.current.navigationCustomization
    val automaticMainBottomHeight =
        if (LocalFlowFilterBarStyle.current == FlowFilterBarStylePreference.Icon) 64 else 80
    var placementRequest by remember { mutableStateOf<PlacementRequest?>(null) }
    var addRequest by remember { mutableStateOf<AddRequest?>(null) }
    var moveRequest by remember { mutableStateOf<MoveRequest?>(null) }

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
                        onAdd = { addRequest = it },
                        onMove = { moveRequest = it },
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
                    AutomaticNumericSlider(
                        title = "Bottom background height",
                        value = settings.mainBottomHeight,
                        automaticValue = automaticMainBottomHeight,
                        range = NavigationCustomization.MIN_BOTTOM_HEIGHT..
                            NavigationCustomization.MAX_BOTTOM_HEIGHT,
                        suffix = "dp",
                        onChange = {
                            writeInt(NavigationPreferenceKeys.mainBottomHeight, it)
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
                        onAdd = { addRequest = it },
                        onMove = { moveRequest = it },
                    )
                    ConfigurableItems(
                        title = "Article list actions",
                        items = settings.articleTopActions,
                        key = NavigationPreferenceKeys.articleTopActions,
                        onWrite = ::writeItems,
                        onChoosePlacement = { placementRequest = it },
                        onAdd = { addRequest = it },
                        onMove = { moveRequest = it },
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
                        onAdd = { addRequest = it },
                        onMove = { moveRequest = it },
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
                        onAdd = { addRequest = it },
                        onMove = { moveRequest = it },
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
                        title = "Bottom background height",
                        value = settings.readingBottomHeight,
                        range = NavigationCustomization.MIN_BOTTOM_HEIGHT..
                            NavigationCustomization.MAX_BOTTOM_HEIGHT,
                        suffix = "dp",
                        onChange = {
                            writeInt(NavigationPreferenceKeys.readingBottomHeight, it)
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

    addRequest?.let { request ->
        val hiddenItems = request.items.filter { it.placement == ActionPlacement.Hidden }
        RadioDialog(
            visible = true,
            title = request.title,
            options = hiddenItems.map { item ->
                RadioDialogOption(
                    text = item.label,
                    onClick = {
                        writeItems(
                            request.key,
                            request.items.map {
                                if (it.id == item.id) {
                                    it.copy(placement = ActionPlacement.Toolbar)
                                } else {
                                    it
                                }
                            },
                        )
                    },
                )
            },
            onDismissRequest = { addRequest = null },
        )
    }

    moveRequest?.let { request ->
        val activeItems = request.items.filter { it.placement != ActionPlacement.Hidden }
        val currentIndex = activeItems.indexOfFirst { it.id == request.item.id }
        RadioDialog(
            visible = true,
            title = "Reorder ${request.item.label}",
            options = activeItems.mapIndexed { index, item ->
                RadioDialogOption(
                    text = "${index + 1}. ${item.label}",
                    selected = index == currentIndex,
                    onClick = {
                        writeItems(
                            request.key,
                            moveActiveItem(request.items, request.item.id, index),
                        )
                    },
                )
            },
            onDismissRequest = { moveRequest = null },
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
    onAdd: (AddRequest) -> Unit,
    onMove: (MoveRequest) -> Unit,
) {
    val view = LocalView.current
    Spacer(Modifier.height(16.dp))
    Text(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        text = title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
    val activeItems = items.withIndex().filter {
        it.value.placement != ActionPlacement.Hidden
    }
    activeItems.forEachIndexed { activeIndex, indexedItem ->
        val item = indexedItem.value
        val itemTitle = item.label
        var dragOffset by remember(item.id) { mutableFloatStateOf(0f) }
        var rowHeight by remember(item.id) { mutableIntStateOf(1) }
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .zIndex(if (dragOffset != 0f) 1f else 0f)
                    .graphicsLayer {
                        translationY = dragOffset
                        if (dragOffset != 0f) {
                            shadowElevation = 8.dp.toPx()
                            alpha = 0.96f
                        }
                    }
                    .onSizeChanged { rowHeight = it.height.coerceAtLeast(1) },
        ) {
            SettingItem(
                title = itemTitle,
                desc = item.placement.label,
                separatedActions = true,
                onClick = {
                    if (allowMore) {
                        onChoosePlacement(PlacementRequest(itemTitle, item, items, key))
                    } else {
                        val visibleCount = items.count {
                            it.placement == ActionPlacement.Toolbar
                        }
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
                            modifier = Modifier.pointerInput(item.id, activeIndex, rowHeight) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragOffset = 0f
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.LONG_PRESS
                                        )
                                    },
                                    onDragCancel = { dragOffset = 0f },
                                    onDragEnd = {
                                        val targetIndex = (
                                            activeIndex + (dragOffset / rowHeight).roundToInt()
                                            ).coerceIn(0, activeItems.lastIndex)
                                        if (targetIndex != activeIndex) {
                                            onWrite(
                                                key,
                                                moveActiveItem(items, item.id, targetIndex),
                                            )
                                            view.performHapticFeedback(
                                                HapticFeedbackConstants.CLOCK_TICK
                                            )
                                        }
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                    },
                                )
                            },
                            onClick = { onMove(MoveRequest(item, items, key)) },
                        ) {
                            Icon(Icons.Rounded.DragHandle, "Reorder $itemTitle")
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
                                            if (it.id == item.id) {
                                                it.copy(placement = next)
                                            } else it
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
    val addableItems = items.filter { it.placement == ActionPlacement.Hidden }
    if (addableItems.isNotEmpty()) {
        TextButton(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            onClick = {
                onAdd(
                    AddRequest(
                        title = if (title == "Bottom filters") "Add filter" else "Add action",
                        items = items,
                        key = key,
                    )
                )
            },
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (title == "Bottom filters") "Add filter" else "Add action")
        }
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

@Composable
private fun AutomaticNumericSlider(
    title: String,
    value: Int,
    automaticValue: Int,
    range: IntRange,
    suffix: String,
    onChange: (Int) -> Unit,
) {
    val effectiveValue = value.takeIf { it > 0 } ?: automaticValue
    var sliderValue by remember(effectiveValue) { mutableFloatStateOf(effectiveValue.toFloat()) }
    var isAutomatic by remember(value) {
        mutableStateOf(value == NavigationCustomization.AUTOMATIC_BOTTOM_HEIGHT)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            if (isAutomatic) {
                "$title: Auto (${effectiveValue}$suffix)"
            } else {
                "$title: ${sliderValue.toInt()}$suffix"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                isAutomatic = false
            },
            onValueChangeFinished = { onChange(sliderValue.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

private fun moveActiveItem(
    items: List<NavigationItemPreference>,
    itemId: String,
    targetIndex: Int,
): List<NavigationItemPreference> {
    val reordered = items.filter { it.placement != ActionPlacement.Hidden }.toMutableList()
    val currentIndex = reordered.indexOfFirst { it.id == itemId }
    if (currentIndex == -1) return items
    val item = reordered.removeAt(currentIndex)
    reordered.add(targetIndex.coerceIn(0, reordered.size), item)
    val iterator = reordered.iterator()
    return items.map {
        if (it.placement == ActionPlacement.Hidden) it else iterator.next()
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
        NavigationItemIds.SETTINGS -> "Settings"
        NavigationItemIds.SYNC -> "Sync"
        NavigationItemIds.HISTORY -> "History"
        NavigationItemIds.AI_SUMMARY -> "AI summary"
        NavigationItemIds.MARK_ALL_READ -> "Mark all as read"
        NavigationItemIds.SEARCH -> "Search"
        NavigationItemIds.REFRESH -> "Refresh"
        NavigationItemIds.TAGS -> "Tags"
        NavigationItemIds.ADD_NOTE -> "Add note"
        NavigationItemIds.STYLE -> "Style"
        NavigationItemIds.SHARE -> "Share"
        NavigationItemIds.FULL_CONTENT -> "Full content"
        NavigationItemIds.TEXT_TO_SPEECH -> "Text to speech"
        NavigationItemIds.OPEN_IN_BROWSER -> "Open in browser"
        NavigationItemIds.PREVIOUS_ARTICLE -> "Previous article"
        NavigationItemIds.NEXT_ARTICLE -> "Next article"
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
    NavigationPreferenceKeys.mainBottomHeight,
    NavigationPreferenceKeys.readingTopIconSize,
    NavigationPreferenceKeys.readingBottomIconSize,
    NavigationPreferenceKeys.readingBottomHeight,
    NavigationPreferenceKeys.mainTopElevation,
    NavigationPreferenceKeys.mainBottomElevation,
    NavigationPreferenceKeys.readingTopElevation,
    NavigationPreferenceKeys.readingBottomElevation,
)
