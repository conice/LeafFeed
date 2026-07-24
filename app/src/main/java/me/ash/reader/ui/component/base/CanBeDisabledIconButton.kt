package me.ash.reader.ui.component.base

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CanBeDisabledIconButton(
    modifier: Modifier = Modifier,
    disabled: Boolean,
    imageVector: ImageVector? = null,
    icon: @Composable () -> Unit = {},
    size: Dp = 24.dp,
    contentDescription: String?,
    tint: Color = LocalContentColor.current,
    onClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    ExpressiveIconButton(
        modifier = modifier,
        enabled = !disabled,
        interactionSource = interactionSource,
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint,
            disabledContentColor = MaterialTheme.colorScheme.outline
        )
    ) {
        if (imageVector != null) {
            AnimatedIcon(
                modifier = Modifier.size(size),
                imageVector = imageVector,
                contentDescription = contentDescription,
            )
        } else {
            icon()
        }
    }
}
