@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.page.home.reading

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.infrastructure.audio.PodcastPlayer
import me.ash.reader.infrastructure.audio.PodcastPlaybackSpeeds
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.component.base.RYAsyncImage
import me.ash.reader.ui.theme.LayoutTokens
import me.ash.reader.ui.theme.ShapeTokens
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastMiniPlayer(
    player: PodcastPlayer,
    modifier: Modifier = Modifier,
    onNavigateToArticle: (String) -> Unit = {},
) {
    val state = player.state.collectAsStateValue()
    val context = LocalContext.current
    val intervals by context.dataStore.data.map { preferences ->
        Pair(
            preferences[FeaturePreferenceKeys.podcastRewindSeconds] ?: 10,
            preferences[FeaturePreferenceKeys.podcastForwardSeconds] ?: 30,
        )
    }.collectAsStateWithLifecycle(initialValue = Pair(10, 30))
    var expanded by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showStopConfirmation by remember { mutableStateOf(false) }
    val motionScheme = MaterialTheme.motionScheme
    LaunchedEffect(state.articleId) {
        if (state.articleId == null) {
            expanded = false
            collapsed = false
            showQueue = false
            showSleepTimer = false
            showStopConfirmation = false
        }
    }
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "podcast progress",
    )

    AnimatedVisibility(
        visible = state.articleId != null,
        enter =
            fadeIn(motionScheme.slowEffectsSpec()) +
                slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = motionScheme.defaultSpatialSpec(),
                ),
        exit =
            fadeOut(motionScheme.fastEffectsSpec()) +
                slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = motionScheme.fastSpatialSpec(),
                ),
        modifier = modifier,
    ) {
        if (collapsed) {
            Surface(
                tonalElevation = 5.dp,
                shape = ShapeTokens.Surface,
                modifier = Modifier.size(52.dp).clickable { collapsed = false },
            ) {
                Box(Modifier.fillMaxSize()) {
                    state.artworkUri?.let { artwork ->
                        RYAsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            data = artwork,
                            contentScale = ContentScale.Crop,
                            contentDescription = stringResource(R.string.podcast_episode_artwork),
                        )
                    } ?: Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalIconButton(
                        onClick = if (state.errorMessage != null) player::retry else player::toggle,
                        modifier = Modifier.align(Alignment.Center).size(40.dp),
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(if (state.errorMessage != null) R.string.retry else R.string.podcast_play_pause),
                        )
                    }
                }
            }
        } else {
            Surface(
                tonalElevation = 5.dp,
                shape = ShapeTokens.Surface,
                modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
            ) {
                Box(Modifier.fillMaxWidth().clickable { expanded = true }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(56.dp)) {
                            state.artworkUri?.let { artwork ->
                                RYAsyncImage(
                                    modifier = Modifier.fillMaxSize(),
                                    data = artwork,
                                    contentScale = ContentScale.Crop,
                                    contentDescription = stringResource(R.string.podcast_episode_artwork),
                                )
                            } ?: Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.Center),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(
                                state.title.ifBlank { stringResource(R.string.podcast_default_title) },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                when {
                                    state.errorMessage != null -> state.errorMessage!!
                                    state.playbackState == androidx.media3.common.Player.STATE_BUFFERING -> stringResource(R.string.podcast_buffering)
                                    else -> listOfNotNull(
                                        state.artist,
                                        "${formatPodcastTime(state.positionMs)} / ${formatPodcastTime(state.durationMs)}",
                                    ).joinToString(" · ")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (state.errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FilledTonalIconButton(
                            onClick = if (state.errorMessage != null) player::retry else player::toggle,
                            modifier = Modifier.size(44.dp),
                        ) {
                            if (state.playbackState == androidx.media3.common.Player.STATE_BUFFERING) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Crossfade(targetState = state.isPlaying, animationSpec = motionScheme.fastEffectsSpec(), label = "podcast playback icon") { isPlaying ->
                                    Icon(
                                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = stringResource(if (state.errorMessage != null) R.string.retry else R.string.podcast_play_pause),
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { collapsed = true }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.podcast_collapse))
                        }
                    }
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    )
                }
            }
        }
    }

    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }) {
            var dragged by remember(state.articleId) { mutableFloatStateOf(Float.NaN) }
            val shown = if (dragged.isNaN()) state.positionMs else dragged.toLong()
            Column(
                Modifier.fillMaxWidth().padding(
                    horizontal = LayoutTokens.PageHorizontalPadding,
                    vertical = LayoutTokens.ContentGap,
                )
            ) {
                state.artworkUri?.let { artwork ->
                    RYAsyncImage(
                        modifier = Modifier.align(Alignment.CenterHorizontally).size(160.dp),
                        data = artwork,
                        contentScale = ContentScale.Crop,
                        contentDescription = stringResource(R.string.podcast_episode_artwork),
                    )
                }
                Text(
                    state.title.ifBlank { stringResource(R.string.podcast_default_title) },
                    modifier = Modifier.clickable {
                        state.articleId?.let(onNavigateToArticle)
                        expanded = false
                    },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                state.artist?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Slider(
                    value = shown.coerceIn(0L, state.durationMs.coerceAtLeast(1L)).toFloat(),
                    valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
                    onValueChange = { dragged = it },
                    onValueChangeFinished = {
                        if (!dragged.isNaN()) player.seekTo(dragged.toLong())
                        dragged = Float.NaN
                    },
                )
                Text("${formatPodcastTime(shown)} / ${formatPodcastTime(state.durationMs)}", style = MaterialTheme.typography.labelMedium)
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = player::skipPrevious, enabled = state.positionMs > 0L || state.queueIndex > 0) { Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.podcast_restart_previous)) }
                    IconButton(onClick = { player.seekBy(-intervals.first * 1_000L) }) { Icon(Icons.Rounded.Replay, stringResource(R.string.podcast_back_seconds, intervals.first)) }
                    IconButton(onClick = player::toggle) { Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(R.string.podcast_play_pause)) }
                    IconButton(onClick = { player.seekBy(intervals.second * 1_000L) }) { Icon(Icons.Rounded.Forward, stringResource(R.string.podcast_forward_seconds, intervals.second)) }
                    IconButton(onClick = player::skipNext, enabled = state.queueIndex < state.queueSize - 1) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.podcast_next_episode)) }
                }
                TextButton(onClick = {
                    val speeds = PodcastPlaybackSpeeds
                    player.setSpeed(speeds[(speeds.indexOf(state.speed).coerceAtLeast(0) + 1) % speeds.size])
                }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("${state.speed}x") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { showQueue = !showQueue }) {
                        Icon(Icons.Rounded.QueueMusic, contentDescription = null)
                        Text(stringResource(R.string.podcast_queue_count, state.queueSize), Modifier.padding(start = 6.dp))
                    }
                    TextButton(onClick = { showSleepTimer = true }) {
                        Icon(Icons.Rounded.Bedtime, contentDescription = null)
                        Text(
                            state.sleepTimerRemainingMs?.let {
                                stringResource(R.string.podcast_minutes_short, it / 60_000L + 1)
                            } ?: stringResource(R.string.podcast_sleep_timer),
                            Modifier.padding(start = 6.dp),
                        )
                    }
                }
                TextButton(
                    onClick = { showStopConfirmation = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Text(stringResource(R.string.podcast_stop_and_clear), Modifier.padding(start = 6.dp))
                }
                if (showQueue) {
                    HorizontalDivider()
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        itemsIndexed(state.queue, key = { index, item -> "${item.id}:$index" }) { index, item ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    item.title,
                                    Modifier.weight(1f).clickable { player.playQueueItem(index) },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (index == state.queueIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                IconButton(
                                    enabled = index > 0,
                                    onClick = { player.moveInQueue(index, index - 1) },
                                ) { Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.podcast_move_earlier)) }
                                IconButton(
                                    enabled = index < state.queue.lastIndex,
                                    onClick = { player.moveInQueue(index, index + 1) },
                                ) { Icon(Icons.Rounded.ArrowDownward, stringResource(R.string.podcast_move_later)) }
                                IconButton(
                                    enabled = state.queueSize > 1,
                                    onClick = { player.removeFromQueue(index) },
                                ) { Icon(Icons.Rounded.Close, stringResource(R.string.podcast_remove_from_queue)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSleepTimer) {
        AlertDialog(
            onDismissRequest = { showSleepTimer = false },
            title = { Text(stringResource(R.string.podcast_sleep_timer)) },
            text = {
                Column {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        TextButton(onClick = { player.setSleepTimer(minutes); showSleepTimer = false }) {
                            Text(stringResource(R.string.podcast_minutes, minutes))
                        }
                    }
                    if (state.sleepTimerRemainingMs != null) {
                        TextButton(onClick = { player.setSleepTimer(null); showSleepTimer = false }) {
                            Text(stringResource(R.string.podcast_turn_off_timer))
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            title = { Text(stringResource(R.string.podcast_stop_and_clear)) },
            text = { Text(stringResource(R.string.podcast_stop_and_clear_confirmation)) },
            dismissButton = {
                TextButton(onClick = { showStopConfirmation = false }) { Text(stringResource(R.string.cancel)) }
            },
            confirmButton = {
                TextButton(onClick = { showStopConfirmation = false; expanded = false; player.close() }) {
                    Text(stringResource(R.string.podcast_stop_and_clear))
                }
            },
        )
    }
}

private fun formatPodcastTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
