package me.ash.reader.ui.page.settings.interaction

import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import me.ash.reader.infrastructure.preference.ActionPlacement
import me.ash.reader.infrastructure.preference.NavigationActionCatalog
import me.ash.reader.infrastructure.preference.NavigationItemPreference
import me.ash.reader.ui.component.base.RadioDialog
import me.ash.reader.ui.component.base.RadioDialogOption

internal data class PlacementRequest(
    val item: NavigationItemPreference,
    val state: NavigationSectionState,
    val key: Preferences.Key<String>,
)

internal data class AddRequest(
    val title: String,
    val state: NavigationSectionState,
    val key: Preferences.Key<String>,
)

internal data class MoveRequest(
    val item: NavigationItemPreference,
    val state: NavigationSectionState,
    val key: Preferences.Key<String>,
)

@Composable
internal fun NavigationActionDialogs(
    placementRequest: PlacementRequest?,
    addRequest: AddRequest?,
    moveRequest: MoveRequest?,
    onWrite: (Preferences.Key<String>, List<NavigationItemPreference>) -> Unit,
    onDismissPlacement: () -> Unit,
    onDismissAdd: () -> Unit,
    onDismissMove: () -> Unit,
) {
    placementRequest?.let { request ->
        RadioDialog(
            visible = true,
            title = NavigationActionCatalog.label(request.item.id),
            options = ActionPlacement.entries.map { placement ->
                RadioDialogOption(
                    text = placement.label,
                    selected = request.item.placement == placement,
                    onClick = {
                        onWrite(
                            request.key,
                            request.state.changePlacement(
                                request.item.id,
                                placement,
                                requireVisible = false,
                            ),
                        )
                    },
                )
            },
            onDismissRequest = onDismissPlacement,
        )
    }

    addRequest?.let { request ->
        RadioDialog(
            visible = true,
            title = request.title,
            options = request.state.hiddenItems.map { item ->
                RadioDialogOption(
                    text = NavigationActionCatalog.label(item.id),
                    onClick = { onWrite(request.key, request.state.add(item.id)) },
                )
            },
            onDismissRequest = onDismissAdd,
        )
    }

    moveRequest?.let { request ->
        val activeItems = request.state.activeItems
        val currentIndex = activeItems.indexOfFirst { it.id == request.item.id }
        RadioDialog(
            visible = true,
            title = "Reorder ${NavigationActionCatalog.label(request.item.id)}",
            options = activeItems.mapIndexed { index, item ->
                RadioDialogOption(
                    text = "${index + 1}. ${NavigationActionCatalog.label(item.id)}",
                    selected = index == currentIndex,
                    onClick = {
                        onWrite(
                            request.key,
                            request.state.move(request.item.id, index),
                        )
                    },
                )
            },
            onDismissRequest = onDismissMove,
        )
    }
}
