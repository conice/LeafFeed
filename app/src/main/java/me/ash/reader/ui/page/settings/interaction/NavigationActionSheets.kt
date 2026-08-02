package me.ash.reader.ui.page.settings.interaction

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.ActionPlacement
import me.ash.reader.infrastructure.preference.NavigationItemPreference
import me.ash.reader.ui.component.navigationActionLabel
import me.ash.reader.ui.theme.LayoutTokens

@Composable
internal fun NavigationActionSheets(
    placementRequest: NavigationPlacementRequest?,
    moveRequest: NavigationMoveRequest?,
    onWrite: (Preferences.Key<String>, List<NavigationItemPreference>) -> Unit,
    onDismissPlacement: () -> Unit,
    onDismissMove: () -> Unit,
) {
    placementRequest?.let { request ->
        PlacementSheet(
            request = request,
            onSelect = { placement ->
                onWrite(
                    request.key,
                    request.state.changePlacement(request.item.id, placement),
                )
                onDismissPlacement()
            },
            onDismiss = onDismissPlacement,
        )
    }

    moveRequest?.let { request ->
        MoveSheet(
            request = request,
            onSelect = { index ->
                onWrite(request.key, request.state.move(request.item.id, index))
                onDismissMove()
            },
            onDismiss = onDismissMove,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlacementSheet(
    request: NavigationPlacementRequest,
    onSelect: (ActionPlacement) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val actionLabel = navigationActionLabel(request.item.id)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        SheetHeader(
            title = actionLabel,
            description = stringResource(R.string.navigation_choose_placement),
        )
        ActionPlacement.entries.forEach { placement ->
            ListItem(
                headlineContent = { Text(placement.longLabel()) },
                supportingContent = { Text(placement.description()) },
                trailingContent = {
                    RadioButton(
                        selected = request.item.placement == placement,
                        onClick = null,
                    )
                },
                modifier = Modifier.fillMaxWidth().selectable(
                    selected = request.item.placement == placement,
                    role = Role.RadioButton,
                    onClick = { onSelect(placement) },
                ),
            )
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSheet(
    request: NavigationMoveRequest,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val actionLabel = navigationActionLabel(request.item.id)
    val currentIndex = request.state.items.indexOfFirst { it.id == request.item.id }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        SheetHeader(
            title = stringResource(R.string.navigation_move_action, actionLabel),
            description = stringResource(R.string.navigation_choose_position),
        )
        request.state.items.forEachIndexed { index, item ->
            ListItem(
                headlineContent = { Text(navigationActionLabel(item.id)) },
                leadingContent = {
                    Text(
                        text = (index + 1).toString(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                trailingContent = {
                    RadioButton(selected = index == currentIndex, onClick = null)
                },
                modifier = Modifier.fillMaxWidth().selectable(
                    selected = index == currentIndex,
                    role = Role.RadioButton,
                    onClick = { onSelect(index) },
                ),
            )
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun SheetHeader(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = LayoutTokens.PageHorizontalPadding,
            vertical = 12.dp,
        ),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ActionPlacement.description(): String = when (this) {
    ActionPlacement.Toolbar -> stringResource(R.string.navigation_placement_toolbar_desc)
    ActionPlacement.More -> stringResource(R.string.navigation_placement_more_desc)
    ActionPlacement.Hidden -> stringResource(R.string.navigation_placement_hidden_desc)
}
