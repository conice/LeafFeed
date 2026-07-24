package me.ash.reader.ui.interaction

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import me.ash.reader.ui.theme.MotionTokens

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.expressivePressFeedback(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedAlpha: Float = .7f,
    pressedScale: Float = MotionTokens.PressedScale,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha by
        animateFloatAsState(
            targetValue = if (enabled && isPressed) pressedAlpha else if (enabled) 1f else .5f,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "pressAlpha",
        )
    val scale by
        animateFloatAsState(
            targetValue = if (enabled && isPressed) pressedScale else 1f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "pressScale",
        )

    return graphicsLayer {
        this.alpha = alpha
        scaleX = scale
        scaleY = scale
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.alphaIndicationClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    return clickable(
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            indication = null,
            interactionSource = interactionSource,
            onClick = onClick,
        )
        .expressivePressFeedback(
            interactionSource = interactionSource,
            enabled = enabled,
            pressedAlpha = .5f,
            pressedScale = 1f,
        )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.alphaIndicationSelectable(
    selected: Boolean,
    enabled: Boolean = true,
    role: Role? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    return selectable(
            selected = selected,
            enabled = enabled,
            role = role,
            indication = null,
            interactionSource = interactionSource,
            onClick = onClick,
        )
        .expressivePressFeedback(
            interactionSource = interactionSource,
            enabled = enabled,
            pressedAlpha = .5f,
            pressedScale = 1f,
        )
}
