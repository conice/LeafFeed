package me.ash.reader.ui.page.settings.interaction

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.ActionPlacement
import me.ash.reader.infrastructure.preference.NavigationCustomizationEditor
import me.ash.reader.infrastructure.preference.NavigationItemPreference
import me.ash.reader.ui.component.navigationActionIcon
import me.ash.reader.ui.component.navigationActionLabel
import me.ash.reader.ui.theme.LayoutTokens

internal enum class NavigationSectionMode {
    Fixed,
    Placement,
    Visibility,
}

internal data class NavigationPlacementRequest(
    val item: NavigationItemPreference,
    val state: NavigationSectionState,
    val key: Preferences.Key<String>,
)

internal data class NavigationMoveRequest(
    val item: NavigationItemPreference,
    val state: NavigationSectionState,
    val key: Preferences.Key<String>,
)

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

    val toolbarCount: Int
        get() = items.count { it.placement == ActionPlacement.Toolbar }

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
        val currentIndex = items.indexOfFirst { it.id == itemId }
        if (currentIndex == -1) return false
        val direction = when {
            dragOffset > 0f -> 1
            dragOffset < 0f -> -1
            else -> 0
        }
        val targetIndex = currentIndex + direction
        if (direction == 0 || targetIndex !in items.indices) return false
        val distance = (
            (itemHeights[itemId] ?: 1) + (itemHeights[items[targetIndex].id] ?: 1)
            ) / 2f
        if (kotlin.math.abs(dragOffset) < distance) return false
        items = NavigationCustomizationEditor.move(items, itemId, targetIndex)
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
        requireVisible: Boolean = false,
        maxToolbarItems: Int? = null,
    ): List<NavigationItemPreference> {
        items = NavigationCustomizationEditor.changePlacement(
            items = items,
            itemId = itemId,
            placement = placement,
            requireVisible = requireVisible,
            maxToolbarItems = maxToolbarItems,
        )
        return items
    }

    fun move(itemId: String, targetIndex: Int): List<NavigationItemPreference> {
        items = NavigationCustomizationEditor.move(items, itemId, targetIndex)
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
    description: String,
    state: NavigationSectionState,
    key: Preferences.Key<String>,
    listState: LazyListState,
    mode: NavigationSectionMode,
    requireVisible: Boolean = false,
    maxToolbarItems: Int? = null,
    helperText: String? = null,
    onCommit: (Preferences.Key<String>, List<NavigationItemPreference>) -> Unit,
    onPlacement: (NavigationPlacementRequest) -> Unit,
    onMove: (NavigationMoveRequest) -> Unit,
    onReset: (Preferences.Key<String>) -> Unit,
) {
    item(key = "$sectionId:header") {
        NavigationSectionHeader(
            title = title,
            description = description,
            helperText = helperText,
            onReset = { onReset(key) },
        )
    }
    item(key = "$sectionId:preview") {
        NavigationActionPreview(
            items = state.items,
            showOverflow = mode == NavigationSectionMode.Placement,
        )
    }
    itemsIndexed(
        items = state.items,
        key = { _, item -> "$sectionId:${item.id}" },
    ) { index, item ->
        ReorderableNavigationItem(
            modifier = Modifier.animateItem(),
            sectionId = sectionId,
            index = index,
            item = item,
            state = state,
            listState = listState,
            mode = mode,
            requireVisible = requireVisible,
            maxToolbarItems = maxToolbarItems,
            onCommit = { onCommit(key, it) },
            onPlacement = {
                onPlacement(NavigationPlacementRequest(item, state, key))
            },
            onMove = { onMove(NavigationMoveRequest(item, state, key)) },
        )
    }
    item(key = "$sectionId:footer") { Spacer(Modifier.height(LayoutTokens.ContentGap)) }
}

@Composable
private fun NavigationSectionHeader(
    title: String,
    description: String,
    helperText: String?,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            start = LayoutTokens.PageHorizontalPadding,
            top = LayoutTokens.SectionSpacing,
            end = LayoutTokens.CompactHorizontalPadding,
            bottom = 8.dp,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = onReset) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.navigation_reset_section))
            }
        }
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        helperText?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun NavigationActionPreview(
    items: List<NavigationItemPreference>,
    showOverflow: Boolean,
) {
    val toolbarItems = items.filter { it.placement == ActionPlacement.Toolbar }
    val hasOverflow = showOverflow && items.any { it.placement == ActionPlacement.More }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = LayoutTokens.CompactHorizontalPadding,
            vertical = 4.dp,
        ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(R.string.navigation_preview),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (toolbarItems.isEmpty() && !hasOverflow) {
                    Text(
                        text = stringResource(R.string.navigation_no_visible_actions),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                toolbarItems.forEach { item ->
                    PreviewAction(item)
                }
                if (hasOverflow) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = CircleShape,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = stringResource(R.string.more),
                            modifier = Modifier.padding(10.dp).size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewAction(item: NavigationItemPreference) {
    val label = navigationActionLabel(item.id)
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = CircleShape,
    ) {
        Icon(
            imageVector = navigationActionIcon(item.id),
            contentDescription = label,
            modifier = Modifier.padding(10.dp).size(22.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ReorderableNavigationItem(
    modifier: Modifier,
    sectionId: String,
    index: Int,
    item: NavigationItemPreference,
    state: NavigationSectionState,
    listState: LazyListState,
    mode: NavigationSectionMode,
    requireVisible: Boolean,
    maxToolbarItems: Int?,
    onCommit: (List<NavigationItemPreference>) -> Unit,
    onPlacement: () -> Unit,
    onMove: () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var edgeScrollJob by remember { mutableStateOf<Job?>(null) }
    val edgeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val isDragging = state.draggedItemId == item.id
    val isVisible = item.placement == ActionPlacement.Toolbar
    val canToggle = if (isVisible) {
        !requireVisible || state.toolbarCount > 1
    } else {
        maxToolbarItems == null || state.toolbarCount < maxToolbarItems
    }
    val label = navigationActionLabel(item.id)
    val moveEarlier = stringResource(R.string.navigation_move_earlier)
    val moveLater = stringResource(R.string.navigation_move_later)

    DisposableEffect(Unit) {
        onDispose { edgeScrollJob?.cancel() }
    }

    val rowClick: (() -> Unit)? = when (mode) {
        NavigationSectionMode.Fixed -> null
        NavigationSectionMode.Placement -> onPlacement
        NavigationSectionMode.Visibility -> {
            if (canToggle) {
                {
                    onCommit(
                        state.changePlacement(
                            itemId = item.id,
                            placement = if (isVisible) {
                                ActionPlacement.Hidden
                            } else {
                                ActionPlacement.Toolbar
                            },
                            requireVisible = requireVisible,
                            maxToolbarItems = maxToolbarItems,
                        )
                    )
                }
            } else null
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LayoutTokens.CompactHorizontalPadding, vertical = 3.dp)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = if (isDragging) state.dragOffset else 0f
                shadowElevation = if (isDragging) 8.dp.toPx() else 0f
                scaleX = if (isDragging) 1.01f else 1f
                scaleY = if (isDragging) 1.01f else 1f
            }
            .onSizeChanged { state.setHeight(item.id, it.height) }
            .clickable(enabled = rowClick != null) { rowClick?.invoke() },
        color = if (item.placement == ActionPlacement.Hidden) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = 14.dp,
                top = 10.dp,
                end = 4.dp,
                bottom = 10.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = if (item.placement == ActionPlacement.Hidden) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = navigationActionIcon(item.id),
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp).size(22.dp),
                    tint = if (item.placement == ActionPlacement.Hidden) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = item.statusLabel(mode),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when (mode) {
                NavigationSectionMode.Fixed -> Unit
                NavigationSectionMode.Placement -> {
                    AssistChip(
                        onClick = onPlacement,
                        label = { Text(item.placement.shortLabel()) },
                    )
                }
                NavigationSectionMode.Visibility -> {
                    Switch(
                        checked = isVisible,
                        enabled = canToggle,
                        onCheckedChange = { rowClick?.invoke() },
                    )
                }
            }
            IconButton(
                modifier = Modifier
                    .semantics {
                        customActions = buildList {
                            if (index > 0) {
                                add(
                                    CustomAccessibilityAction(moveEarlier) {
                                        onCommit(state.move(item.id, index - 1))
                                        true
                                    }
                                )
                            }
                            if (index < state.items.lastIndex) {
                                add(
                                    CustomAccessibilityAction(moveLater) {
                                        onCommit(state.move(item.id, index + 1))
                                        true
                                    }
                                )
                            }
                        }
                    }
                    .pointerInput(sectionId, item.id) {
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
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = stringResource(
                        R.string.navigation_reorder_action,
                        label,
                    ),
                )
            }
        }
    }
}

@Composable
private fun NavigationItemPreference.statusLabel(mode: NavigationSectionMode): String =
    when (mode) {
        NavigationSectionMode.Fixed -> stringResource(R.string.navigation_always_shown)
        NavigationSectionMode.Placement -> placement.longLabel()
        NavigationSectionMode.Visibility -> if (placement == ActionPlacement.Toolbar) {
            stringResource(R.string.navigation_visible)
        } else {
            stringResource(R.string.navigation_hidden)
        }
    }

@Composable
internal fun ActionPlacement.shortLabel(): String = when (this) {
    ActionPlacement.Toolbar -> stringResource(R.string.navigation_placement_toolbar_short)
    ActionPlacement.More -> stringResource(R.string.more)
    ActionPlacement.Hidden -> stringResource(R.string.navigation_hidden)
}

@Composable
internal fun ActionPlacement.longLabel(): String = when (this) {
    ActionPlacement.Toolbar -> stringResource(R.string.navigation_placement_toolbar)
    ActionPlacement.More -> stringResource(R.string.navigation_placement_more)
    ActionPlacement.Hidden -> stringResource(R.string.navigation_placement_hidden)
}
