package me.ash.reader.ui.component.base

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import me.ash.reader.ui.interaction.expressivePressFeedback

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveIconButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.expressivePressFeedback(
                interactionSource = interactionSource,
                enabled = enabled,
                pressedAlpha = .72f,
                pressedScale = .88f,
            ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
