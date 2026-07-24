package me.ash.reader.ui.page.home.feeds

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.ash.reader.ui.component.CompactRefreshIndicatorContent
import me.ash.reader.ui.component.RefreshIndicatorResult

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.PullToRefreshStatusIndicator(
    state: PullToRefreshState,
    isRunning: Boolean,
    message: String?,
    progress: Float? = null,
    result: RefreshIndicatorResult? = null,
    modifier: Modifier = Modifier,
) {
    val showStatus = isRunning || result != null
    val offset = remember { Animatable(if (showStatus) 12f else -48f) }
    val alpha = remember { Animatable(if (showStatus) 1f else 0f) }
    val scale = remember { Animatable(if (showStatus) 1f else .2f) }
    val pullProgressFlow = remember(state) { snapshotFlow { state.distanceFraction } }

    val offsetSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val alphaSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    LaunchedEffect(showStatus, isRunning) {
        if (showStatus) {
            alpha.snapTo(1f)
            scale.snapTo(1f)
            launch { offset.animateTo(12f, offsetSpec) }
        } else {
            alpha.animateTo(0f, alphaSpec)
            scale.snapTo(0f)
        }
    }

    LaunchedEffect(pullProgressFlow, showStatus) {
        pullProgressFlow.collect { fraction ->
            if (!showStatus) {
                val progress = fraction.coerceIn(0f, 1f)
                offset.snapTo(-48f + progress * 60f)
                alpha.snapTo(progress)
                scale.snapTo(progress.coerceAtLeast(.2f))
            }
        }
    }

    Surface(
        modifier =
            modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(x = 0, y = offset.value.dp.roundToPx()) }
                .graphicsLayer {
                    this.alpha = alpha.value
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .height(48.dp)
                .widthIn(min = 48.dp, max = 280.dp),
        color = MaterialTheme.colorScheme.primaryFixedDim,
        contentColor = MaterialTheme.colorScheme.onPrimaryFixedVariant,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        CompactRefreshIndicatorContent(
            message = if (showStatus) message else null,
            progress = if (isRunning) progress else state.distanceFraction.coerceIn(0f, 1f),
            result = result,
        )
    }
}
