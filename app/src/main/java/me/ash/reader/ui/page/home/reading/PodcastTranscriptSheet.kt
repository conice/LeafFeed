package me.ash.reader.ui.page.home.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.infrastructure.audio.PodcastTranscriptCue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastTranscriptSheet(
    url: String,
    onDismiss: () -> Unit,
    load: suspend (String) -> Result<List<PodcastTranscriptCue>>,
    onSeek: (Long) -> Unit,
) {
    var cues by remember(url) { mutableStateOf<List<PodcastTranscriptCue>?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    val loadError = stringResource(R.string.podcast_transcript_error)
    LaunchedEffect(url) {
        load(url).fold(
            onSuccess = { cues = it.ifEmpty { emptyList() } },
            onFailure = { error = it.message ?: loadError },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                stringResource(R.string.podcast_transcript),
                style = MaterialTheme.typography.titleLarge,
            )
            when {
                error != null -> Text(
                    error!!,
                    Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                cues == null -> Row(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.podcast_transcript_loading),
                        Modifier.padding(start = 16.dp),
                    )
                }
                cues!!.isEmpty() ->
                    Text(
                        stringResource(R.string.podcast_transcript_empty),
                        Modifier.padding(vertical = 24.dp),
                    )
                else -> LazyColumn {
                    items(cues!!) { cue ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = cue.startMs != null) { cue.startMs?.let(onSeek) }
                                .padding(vertical = 10.dp),
                        ) {
                            cue.startMs?.let {
                                Text(formatCueTime(it), style = MaterialTheme.typography.labelMedium)
                            }
                            Text(cue.text, Modifier.padding(start = if (cue.startMs != null) 12.dp else 0.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun formatCueTime(ms: Long): String {
    val seconds = ms / 1_000L
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
