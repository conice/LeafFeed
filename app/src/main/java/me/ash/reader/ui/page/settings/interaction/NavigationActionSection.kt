package me.ash.reader.ui.page.settings.interaction

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.preference.ActionPlacement
import me.ash.reader.infrastructure.preference.NavigationActionCatalog
import me.ash.reader.infrastructure.preference.NavigationCustomizationEditor
import me.ash.reader.infrastructure.preference.NavigationItemPreference
import me.ash.reader.ui.component.base.RYSwitch
import me.ash.reader.ui.page.settings.SettingItem

@Stable
internal class NavigationSectionState(initialItems: List<NavigationItemPreference>) {
    var items by mutableStateOf(initialItems)
        private set
    var draggedItemId by mutableStateOf<String?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    private val itemHeights = mutableMapOf<String, Int>()
    private var dragStartItems: List<NavigationItemPreference> = initialItems

    val activeItems: List<NavigationItemPreference>
        get() = items.filter { it.placement != ActionPlacement.Hidden }

    val hiddenItems: List<NavigationItemPreference>
        get() = items.filter { it.placement == ActionPlacement.Hidden }

    fun sync(source: List<NavigationItemPreference>) {
        if (draggedItemId == null && items != source) items = source
    }

    fun setHeight(itemId: String, height: Int) {
        itemHeights[itemId] = height.coerceAtLeast(1)
    }

    fun startDrag(itemId: String) {
        dragStartItems = items
        draggedItemId = itemId
        dragOffset = 0f
    }

    fun dragBy(delta: Float): Boolean {
        val itemId = draggedItemId ?: return false
        dragOffset += delta
        val active = activeItems
        val currentIndex = active.indexOfFirst { it.id == itemId }
        if (currentIndex == -1) return false
        val direction = when {
            dragOffset > 0f -> 1
            dragOffset < 0f -> -1
            else -> 0
        }
        val targetIndex = currentIndex + direction
        if (direction == 0 || targetIndex !in active.indices) return false
        val distance = (
            (itemHeights[itemId] ?: 1) + (itemHeights[active[targetIndex].id] ?: 1)
            ) / 2f
        if (kotlin.math.abs(dragOffset) < distance) return false
        items = NavigationCustomizationEditor.moveActive(items, itemId, targetIndex)
        dragOffset -= distance * direction
        return true
    }

    fun finishDrag(): List<NavigationItemPreference> {
        draggedItemId = null
        dragOffset = 0f
        return items
    }

    fun cancelDrag() {
        items = dragStartItems
        draggedItemId = null
        dragOffset = 0f
    }

    fun changePlacement(
        itemId: String,
        placement: ActionPlacement,
        requireVisible: Boolean,
    ): List<NavigationItemPreference> {
        items = NavigationCustomizationEditor.changePlacement(
            items,
            itemId,
            placement,
            requireVisible,
        )
        return items
    }

    fun add(itemId: String): List<NavigationItemPreference> {
        items = NavigationCustomizationEditor.add(items, itemId)
        return items
    }

    fun move(itemId: String, targetIndex: Int): List<NavigationItemPreference> {
        items = NavigationCustomizationEditor.moveActive(items, itemId, targetIndex)
        return items
    }
}

@Composable
internal fun rememberNavigationSectionState(
    source: List<NavigationItemPreference>,
): NavigationSectionState {
    val state = remember { NavigationSectionState(source) }
    LaunchedEffect(source) { state.sync(source) }
    return state
}

internal fun LazyListScope.navigationActionSection(
    sectionId: String,
    title: String,
    state: NavigationSectionState,
    key: Preferences.Key<String>,
    listState: LazyListState,
    allowMore: Boolean = true,
    requireVisible: Boolean = false,
    addLabel: String = "Add action",
    onCommit: (Preferences.Key<String>, List<NavigationItemPreference>) -> Unit,
    onPlacement: (NavigationItemPreference) -> Unit,
    onAdd: () -> Unit,
    onMove: (NavigationItemPreference) -> Unit,
) {
    item(key = "$sectionId:header") {
        Spacer(Modifier.height(16.dp))
        Text(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
    itemsIndexed(
        items = state.activeItems,
        key = { _, item -> "$sectionId:${item.id}" },
    ) { _, item ->
        ReorderableNavigationItem(
            sectionId = sectionId,
            item = item,
            state = state,
            listState = listState,
            allowMore = allowMore,
            requireVisible = requireVisible,
            onCommit = { onCommit(key, it) },
            onPlacement = { onPlacement(item) },
            onMove = { onMove(item) },
        )
    }
    if (state.hiddenItems.isNotEmpty()) {
        item(key = "$sectionId:add") {
            TextButton(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                onClick = onAdd,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(addLabel)
            }
        }
    }
}

@Composable
private fun ReorderableNavigationItem(
    sectionId: String,
    item: NavigationItemPreference,
    state: NavigationSectionState,
    listState: LazyListState,
    allowMore: Boolean,
    requireVisible: Boolean,
    onCommit: (List<NavigationItemPreference>) -> Unit,
    onPlacement: () -> Unit,
    onMove: () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var edgeScrollJob by remember { mutableStateOf<Job?>(null) }
    val edgeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val isDragging = state.draggedItemId == item.id
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .animateItem()
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    if (isDragging) {
                        translationY = state.dragOffset
                        shadowElevation = 8.dp.toPx()
                        alpha = 0.96f
                    }
                }
                .onSizeChanged { state.setHeight(item.id, it.height) },
    ) {
        SettingItem(
            title = NavigationActionCatalog.label(item.id),
            desc = item.placement.label,
            separatedActions = true,
            onClick = {
                if (allowMore) {
                    onPlacement()
                } else {
                    val placement = if (item.placement == ActionPlacement.Toolbar) {
                        ActionPlacement.Hidden
                    } else {
                        ActionPlacement.Toolbar
                    }
                    onCommit(state.changePlacement(item.id, placement, requireVisible))
                }
            },
            action = {
                Row {
                    IconButton(
                        modifier = Modifier.pointerInput(sectionId, item.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    state.startDrag(item.id)
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                },
                                onDragCancel = {
                                    edgeScrollJob?.cancel()
                                    state.cancelDrag()
                                },
                                onDragEnd = {
                                    edgeScrollJob?.cancel()
                                    onCommit(state.finishDrag())
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    if (state.dragBy(amount.y)) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    val key = "$sectionId:${item.id}"
                                    val info = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == key }
                                    if (info != null) {
                                        val viewportStart = listState.layoutInfo.viewportStartOffset
                                        val viewportEnd = listState.layoutInfo.viewportEndOffset
                                        val draggedTop = info.offset + state.dragOffset
                                        val draggedBottom = draggedTop + info.size
                                        val scroll = when {
                                            draggedTop < viewportStart + edgeThresholdPx ->
                                                -info.size / 4f
                                            draggedBottom > viewportEnd - edgeThresholdPx ->
                                                info.size / 4f
                                            else -> 0f
                                        }
                                        edgeScrollJob?.cancel()
                                        if (scroll != 0f) {
                                            edgeScrollJob = scope.launch {
                                                while (isActive) {
                                                    if (listState.scrollBy(scroll) == 0f) break
                                                    delay(16)
                                                }
                                            }
                                        } else {
                                            edgeScrollJob = null
                                        }
                                    }
                                },
                            )
                        },
                        onClick = onMove,
                    ) {
                        Icon(
                            Icons.Rounded.DragHandle,
                            "Reorder ${NavigationActionCatalog.label(item.id)}",
                        )
                    }
                    if (!allowMore) {
                        val visibleCount = state.items.count {
                            it.placement == ActionPlacement.Toolbar
                        }
                        RYSwitch(
                            activated = item.placement == ActionPlacement.Toolbar,
                            enable = !requireVisible || visibleCount > 1,
                            onClick = {
                                val placement = if (item.placement == ActionPlacement.Toolbar) {
                                    ActionPlacement.Hidden
                                } else {
                                    ActionPlacement.Toolbar
                                }
                                onCommit(
                                    state.changePlacement(item.id, placement, requireVisible)
                                )
                            },
                        )
                    }
                }
            },
        )
    }
}

internal val ActionPlacement.label: String
    get() = when (this) {
        ActionPlacement.Toolbar -> "Toolbar"
        ActionPlacement.More -> "More"
        ActionPlacement.Hidden -> "Hidden"
    }
