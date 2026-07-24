package me.ash.reader.ui.component.base

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Material 3 modal bottom sheet used by the feed and group option surfaces.
 *
 * Visibility belongs to the caller instead of being encoded in a Compose state object kept by a
 * view model. This keeps the component lifecycle predictable and lets [ModalBottomSheet] own its
 * show/hide animation and accessibility semantics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomDrawer(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    sheetContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    Box(modifier = modifier) { content() }

    if (visible) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp),
                content = sheetContent,
            )
        }
    }
}
