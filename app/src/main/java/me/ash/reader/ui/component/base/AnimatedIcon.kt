package me.ash.reader.ui.component.base

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
        scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = .8f)
    val exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
        scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = .8f)

    AnimatedContent(
        targetState = imageVector,
        modifier = modifier,
        contentKey = { it.name },
        transitionSpec = { enter togetherWith exit },
        label = "animatedIcon",
    ) { targetVector ->
        Icon(
            imageVector = targetVector,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}
