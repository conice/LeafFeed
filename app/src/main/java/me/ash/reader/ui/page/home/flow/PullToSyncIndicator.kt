package me.ash.reader.ui.page.home.flow

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.ash.reader.ui.component.CompactRefreshIndicatorContent
import me.ash.reader.ui.component.RefreshIndicatorResult
import me.ash.reader.ui.page.home.reading.PullToLoadDefaults
import me.ash.reader.ui.page.home.reading.PullToLoadState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.PullToSyncIndicator(
    pullToLoadState: PullToLoadState,
    modifier: Modifier = Modifier,
    isSyncing: Boolean,
    message: String? = null,
    result: RefreshIndicatorResult? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current

    val animateOffsetFraction = remember { Animatable(if (isSyncing) 1f else 0f) }
    val animateAlpha = remember { Animatable(if (isSyncing) 1f else 0f) }
    val animateScale = remember { Animatable(if (isSyncing) 1f else .2f) }

    val progressFlow = remember(pullToLoadState) { snapshotFlow { pullToLoadState.progress } }

    val offsetFractionFlow =
        remember(pullToLoadState) { snapshotFlow { pullToLoadState.offsetFraction } }

    val showStatus = isSyncing || result != null

    val offsetSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val alphaSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    LaunchedEffect(showStatus, isSyncing) {
        if (showStatus) {
            animateAlpha.snapTo(1f)
            animateScale.snapTo(1f)
            launch {
                animateOffsetFraction.animateTo(0f, offsetSpec)
            }
        } else {
            animateAlpha.animateTo(0f, alphaSpec)
            animateScale.snapTo(0f)
        }
    }

    LaunchedEffect(progressFlow, showStatus) {
        progressFlow.collect { progress ->
            if (!showStatus) {
                animateScale.snapTo(progress.fastCoerceAtMost(1f))
                animateAlpha.snapTo(progress.fastCoerceAtMost(1f))
            }
        }
    }

    LaunchedEffect(offsetFractionFlow, showStatus) {
        offsetFractionFlow.collect {
            if (!showStatus) {
                animateOffsetFraction.snapTo(it.fastCoerceAtMost(3f))
            }
        }
    }

    LaunchedEffect(progressFlow, showStatus) {
        progressFlow.map { it > 1f }.distinctUntilChanged().collect {
            if (it && !showStatus) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }
        }
    }


    val fraction by remember { derivedStateOf { animateOffsetFraction.value } }


    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 72.dp)
            .align(Alignment.TopCenter)
            .offset {
                IntOffset(
                    x = 0,
                    y = (fraction * PullToLoadDefaults.ContentOffsetMultiple).dp.roundToPx()
                )
            }
            .graphicsLayer {
                this.alpha = animateAlpha.value
                this.scaleX = animateScale.value
                this.scaleY = animateScale.value
            }
            .height(48.dp)
            .widthIn(min = 48.dp, max = 280.dp),
        color = MaterialTheme.colorScheme.primaryFixedDim,
        contentColor = MaterialTheme.colorScheme.onPrimaryFixedVariant,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        CompactRefreshIndicatorContent(
            message = message,
            progress = if (isSyncing) null else fraction,
            result = result,
        )
    }
}
