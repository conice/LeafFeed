package me.ash.reader.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.CompactStatusIndicator(
    visible: Boolean,
    message: String? = null,
    result: RefreshIndicatorResult? = null,
    modifier: Modifier = Modifier,
    topPadding: Dp = 72.dp,
) {
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = topPadding),
        enter = fadeIn(effectsSpec) + scaleIn(spatialSpec, initialScale = 0.2f),
        exit = fadeOut(effectsSpec) + scaleOut(spatialSpec, targetScale = 0.2f),
    ) {
        Surface(
            modifier = Modifier
                .height(48.dp)
                .widthIn(min = 48.dp, max = 280.dp),
            color = MaterialTheme.colorScheme.primaryFixedDim,
            contentColor = MaterialTheme.colorScheme.onPrimaryFixedVariant,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            CompactRefreshIndicatorContent(message = message, result = result)
        }
    }
}
