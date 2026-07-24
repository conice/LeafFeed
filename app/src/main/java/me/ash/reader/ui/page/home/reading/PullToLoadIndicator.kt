package me.ash.reader.ui.page.home.reading

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.ash.reader.ui.page.home.reading.PullToLoadState.Status.Idle
import me.ash.reader.ui.page.home.reading.PullToLoadState.Status.PulledDown
import me.ash.reader.ui.page.home.reading.PullToLoadState.Status.PulledUp
import me.ash.reader.ui.page.home.reading.PullToLoadState.Status.PullingDown
import me.ash.reader.ui.page.home.reading.PullToLoadState.Status.PullingUp
import kotlin.math.abs

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.PullToLoadIndicator(
    modifier: Modifier = Modifier,
    state: PullToLoadState,
    canLoadPrevious: Boolean = true,
    canLoadNext: Boolean = true
) {
    val hapticFeedback = LocalHapticFeedback.current
    val motionScheme = MaterialTheme.motionScheme
    val status = state.status
    var isPullingUp by remember { mutableStateOf(false) }

    LaunchedEffect(status) {
        when (status) {
            PullingUp, PulledUp -> isPullingUp = true
            PullingDown, PulledDown -> isPullingUp = false
            Idle -> Unit
        }
    }

    LaunchedEffect(status) {
        when {
            canLoadPrevious && status == PulledDown -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }

            canLoadNext && status == PulledUp -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }

            else -> {}
        }
    }

    val fraction = state.offsetFraction
    val absFraction = abs(fraction)

    val imageVector = when (status) {
        PulledDown -> Icons.Rounded.KeyboardArrowUp
        PulledUp -> Icons.Rounded.KeyboardArrowDown
        else -> null
    }

    val alignment =
        when (status) {
            PullingUp, PulledUp -> Alignment.BottomCenter
            PullingDown, PulledDown -> Alignment.TopCenter
            Idle -> if (isPullingUp) Alignment.BottomCenter else Alignment.TopCenter
        }

    val visible = remember(status, canLoadPrevious, canLoadNext) {
        when (status) {
            Idle -> {
                false
            }

            PullingUp, PulledUp -> {
                canLoadNext
            }

            PulledDown, PullingDown -> {
                canLoadPrevious
            }
        }
    }

    AnimatedVisibility(
        visible = visible && !state.isSettled,
        modifier = modifier
            .align(alignment)
            .padding(vertical = 80.dp),
        enter = fadeIn(motionScheme.fastEffectsSpec()) +
            scaleIn(motionScheme.fastSpatialSpec(), initialScale = .5f),
        exit = fadeOut(motionScheme.defaultEffectsSpec()) +
            scaleOut(motionScheme.defaultSpatialSpec(), targetScale = .5f),
    ) {
        Surface(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = 0,
                        y = (fraction * PullToLoadDefaults.ContentOffsetMultiple * .5f).dp.roundToPx()
                    )
                }
                .width(36.dp),
            color = MaterialTheme.colorScheme.primaryFixed,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.align(Alignment.Center)) {
                AnimatedContent(
                    targetState = imageVector, modifier = Modifier.align(
                        Alignment.CenterHorizontally
                    ), transitionSpec = {
                        fadeIn(animationSpec = motionScheme.slowEffectsSpec())
                            .togetherWith(
                                fadeOut(
                                    animationSpec = motionScheme.fastEffectsSpec()
                                )
                            )
                    }, label = "pullToLoadDirection",
                    contentAlignment = Alignment.Center
                ) {
                    if (it != null) {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryFixedVariant,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .padding(vertical = (2 * absFraction).dp)
                                .size(32.dp)
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .width(36.dp)
                                .height((12 * absFraction).dp)
                        )
                    }
                }

            }
        }
    }

}
