package me.ash.reader.ui.page.home.reading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.domain.model.article.Article
import me.ash.reader.infrastructure.audio.PodcastPlayer
import me.ash.reader.infrastructure.audio.PodcastPlaybackSpeeds
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.collectAsStateValue
import kotlinx.coroutines.flow.map

@Composable
fun PodcastControls(
    episode: Article,
    player: PodcastPlayer,
    artist: String?,
    artwork: String?,
    onDownload: () -> Unit,
    onTranscript: (() -> Unit)?,
    onTogglePlayed: (Boolean) -> Unit = {},
    downloadProgress: Int? = null,
    modifier: Modifier = Modifier,
) {
    val state = player.state.collectAsStateValue()
    val context = LocalContext.current
    val settings by context.dataStore.data.map { preferences ->
        Triple(
            preferences[FeaturePreferenceKeys.podcastRewindSeconds] ?: 10,
            preferences[FeaturePreferenceKeys.podcastForwardSeconds] ?: 30,
            preferences[FeaturePreferenceKeys.podcastDefaultSpeed] ?: 1f,
        )
    }.collectAsStateWithLifecycle(initialValue = Triple(10, 30, 1f))
    val showEpisodeMetadata by context.dataStore.data
        .map { it[FeaturePreferenceKeys.podcastShowEpisodeMetadata] ?: true }
        .collectAsStateWithLifecycle(initialValue = true)
    val isCurrent = state.articleId == episode.id
    val position = if (isCurrent) state.positionMs else episode.playbackPositionMs
    val duration = (if (isCurrent) state.durationMs else episode.durationSeconds?.times(1000L)) ?: 0L
    var draggedPosition by remember(episode.id) { mutableFloatStateOf(Float.NaN) }
    val displayedPosition = if (draggedPosition.isNaN()) position else draggedPosition.toLong()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(episode.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showEpisodeMetadata) {
                val metadata = buildList {
                    episode.seasonNumber?.let { add(stringResource(R.string.podcast_season, it)) }
                    episode.episodeNumber?.let {
                        add(stringResource(R.string.podcast_episode, it))
                    }
                    if (episode.isExplicit) add(stringResource(R.string.podcast_explicit))
                }.joinToString(" · ")
                if (metadata.isNotBlank()) {
                    Text(metadata, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isCurrent && state.errorMessage != null) {
                Text(state.errorMessage!!, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            } else if (isCurrent && state.playbackState == androidx.media3.common.Player.STATE_BUFFERING) {
                Text(
                    stringResource(R.string.podcast_buffering),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Slider(
                value = displayedPosition.coerceIn(0L, duration.coerceAtLeast(1L)).toFloat(),
                onValueChange = { if (isCurrent) draggedPosition = it },
                onValueChangeFinished = {
                    if (isCurrent && !draggedPosition.isNaN()) player.seekTo(draggedPosition.toLong())
                    draggedPosition = Float.NaN
                },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${formatTime(displayedPosition)} / ${formatTime(duration)}", style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = onDownload) {
                    Icon(
                        when {
                            downloadProgress != null -> Icons.Rounded.Close
                            episode.downloadedPath == null -> Icons.Rounded.Download
                            else -> Icons.Rounded.Check
                        },
                        contentDescription =
                            stringResource(
                                when {
                                    downloadProgress != null -> R.string.podcast_cancel_download
                                    episode.downloadedPath != null ->
                                        R.string.podcast_remove_download
                                    else -> R.string.podcast_download_episode
                                }
                            ),
                    )
                }
                downloadProgress?.let {
                    Text("$it%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { onTogglePlayed(!episode.isPlayed) }) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription =
                            stringResource(
                                if (episode.isPlayed) R.string.podcast_mark_unplayed
                                else R.string.podcast_mark_played
                            ),
                    )
                }
                IconButton(onClick = { player.enqueue(episode, artist) }) {
                    Icon(
                        Icons.Rounded.PlaylistAdd,
                        contentDescription = stringResource(R.string.podcast_add_to_queue),
                    )
                }
                if (onTranscript != null) {
                    IconButton(onClick = onTranscript) {
                        Icon(
                            Icons.Rounded.Subtitles,
                            contentDescription = stringResource(R.string.podcast_open_transcript),
                        )
                    }
                }
                if (isCurrent && state.queueSize > 1) {
                    IconButton(onClick = player::skipNext) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = stringResource(R.string.podcast_next_episode),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(enabled = isCurrent, onClick = { player.seekBy(-settings.first * 1_000L) }) {
                    Icon(
                        Icons.Rounded.Replay,
                        contentDescription =
                            stringResource(R.string.podcast_back_seconds, settings.first),
                    )
                }
                IconButton(onClick = {
                    if (isCurrent) player.toggle() else player.play(episode, artist = artist, artwork = artwork)
                }) {
                    Icon(
                        if (isCurrent && state.isPlaying) Icons.Rounded.Pause
                        else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.podcast_play_pause),
                    )
                }
                IconButton(enabled = isCurrent, onClick = { player.seekBy(settings.second * 1_000L) }) {
                    Icon(
                        Icons.Rounded.Forward,
                        contentDescription =
                            stringResource(R.string.podcast_forward_seconds, settings.second),
                    )
                }
                TextButton(enabled = isCurrent, onClick = {
                    val speeds = PodcastPlaybackSpeeds
                    val next = speeds[(speeds.indexOf(state.speed).coerceAtLeast(0) + 1) % speeds.size]
                    player.setSpeed(next)
                }) { Text("${if (isCurrent) state.speed else settings.third}x") }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
