package com.conice.morss.infrastructure.preference

import androidx.compose.runtime.Immutable

@Immutable
data class NavigationActionLayout(
    val toolbar: List<NavigationItemPreference>,
    val overflow: List<NavigationItemPreference>,
)

fun resolveNavigationActionLayout(
    actions: List<NavigationItemPreference>,
    availableIds: Set<String>,
    capacity: Int,
): NavigationActionLayout {
    val available = actions.filter {
        it.id in availableIds && it.placement != ActionPlacement.Hidden
    }
    val configuredToolbar = available.filter { it.placement == ActionPlacement.Toolbar }
    val safeCapacity = capacity.coerceAtLeast(1)
    val hasOverflow = available.any { it.placement == ActionPlacement.More } ||
        configuredToolbar.size > safeCapacity
    val toolbarCapacity =
        if (hasOverflow) (safeCapacity - 1).coerceAtLeast(0) else safeCapacity
    val toolbar = configuredToolbar.take(toolbarCapacity)
    val toolbarIds = toolbar.mapTo(mutableSetOf()) { it.id }
    val overflow = available.filter {
        it.placement == ActionPlacement.More ||
            it.placement == ActionPlacement.Toolbar && it.id !in toolbarIds
    }
    return NavigationActionLayout(toolbar, overflow)
}

object NavigationCustomizationEditor {
    fun changePlacement(
        items: List<NavigationItemPreference>,
        itemId: String,
        placement: ActionPlacement,
        requireVisible: Boolean = false,
        maxToolbarItems: Int? = null,
    ): List<NavigationItemPreference> {
        val current = items.firstOrNull { it.id == itemId } ?: return items
        if (
            requireVisible && placement == ActionPlacement.Hidden &&
            current.placement == ActionPlacement.Toolbar &&
            items.count { it.placement == ActionPlacement.Toolbar } <= 1
        ) return items
        if (
            placement == ActionPlacement.Toolbar &&
            current.placement != ActionPlacement.Toolbar &&
            maxToolbarItems != null &&
            items.count { it.placement == ActionPlacement.Toolbar } >= maxToolbarItems
        ) return items
        return items.map {
            if (it.id == itemId) it.copy(placement = placement) else it
        }
    }

    fun move(
        items: List<NavigationItemPreference>,
        itemId: String,
        targetIndex: Int,
    ): List<NavigationItemPreference> {
        val reordered = items.toMutableList()
        val currentIndex = reordered.indexOfFirst { it.id == itemId }
        if (currentIndex == -1) return items
        val item = reordered.removeAt(currentIndex)
        reordered.add(targetIndex.coerceIn(0, reordered.size), item)
        return reordered
    }
}
