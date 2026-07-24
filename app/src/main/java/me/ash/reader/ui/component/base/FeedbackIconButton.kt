package me.ash.reader.ui.component.base

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedbackIconButton(
    modifier: Modifier = Modifier.size(22.dp),
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color = LocalContentColor.current,
    enabled: Boolean = true,
    showBadge: Boolean = false,
    isHaptic: Boolean? = true,
    isSound: Boolean? = true,
    onClick: () -> Unit = {},
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }

    BadgedBox(
        badge = {
            AnimatedVisibility(
                visible = showBadge,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()),
            ) {
                Badge(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape),
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            }
        }
    ) {
        ExpressiveIconButton(
            enabled = enabled,
            interactionSource = interactionSource,
            onClick = {
                if (isHaptic == true) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (isSound == true) view.playSoundEffect(SoundEffectConstants.CLICK)
                onClick()
            },
        ) {
            AnimatedIcon(
                modifier = modifier,
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = tint,
            )
        }
    }
}
