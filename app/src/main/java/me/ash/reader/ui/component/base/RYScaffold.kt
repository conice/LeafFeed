package me.ash.reader.ui.component.base

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.ash.reader.ui.ext.surfaceColorAtElevation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RYScaffold(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    topBarTonalElevation: Dp = 0.dp,
    containerTonalElevation: Dp = 0.dp,
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        containerColor =
            MaterialTheme.colorScheme.surfaceColorAtElevation(
                containerTonalElevation,
                color = containerColor,
            ),
        topBar = {
            if (topBar != null) topBar()
            else if (navigationIcon != null || actions != null) {
                TopAppBar(
                    title = {},
                    navigationIcon = { navigationIcon?.invoke() },
                    actions = { actions?.invoke(this) },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceColorAtElevation(
                                    topBarTonalElevation
                                )
                        ),
                )
            }
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
            ) {
                content()
            }
        },
        bottomBar = { bottomBar?.invoke() },
        floatingActionButton = { floatingActionButton?.invoke() },
        floatingActionButtonPosition = floatingActionButtonPosition,
    )
}
