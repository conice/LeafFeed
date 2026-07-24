package me.ash.reader.infrastructure.audio

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class PodcastTranscriptCue(val startMs: Long?, val text: String)

@Singleton
class PodcastTranscriptRepository @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun load(url: String): Result<List<PodcastTranscriptCue>> = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "Transcript failed: HTTP ${response.code}" }
                parse(response.body.string())
            }
        }
    }

    internal fun parse(content: String): List<PodcastTranscriptCue> {
        val trimmed = content.trim()
        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> parseJson(trimmed)
            trimmed.startsWith("WEBVTT") || trimmed.contains(" --> ") -> parseTimedText(trimmed)
            else -> trimmed.lines().filter(String::isNotBlank).map { PodcastTranscriptCue(null, it.trim()) }
        }
    }

    private fun parseJson(content: String): List<PodcastTranscriptCue> {
        val array = if (content.startsWith("[")) JSONArray(content) else {
            val root = JSONObject(content)
            root.optJSONArray("segments") ?: root.optJSONArray("transcript") ?: JSONArray()
        }
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val text = item.optString("body").ifBlank { item.optString("text") }.trim()
            if (text.isBlank()) return@mapNotNull null
            val seconds = when {
                item.has("startTime") -> item.optDouble("startTime", -1.0)
                item.has("start") -> item.optDouble("start", -1.0)
                else -> -1.0
            }
            PodcastTranscriptCue(seconds.takeIf { it >= 0 }?.times(1_000)?.toLong(), text)
        }
    }

    private fun parseTimedText(content: String): List<PodcastTranscriptCue> {
        val lines = content.lines()
        val cues = mutableListOf<PodcastTranscriptCue>()
        var index = 0
        while (index < lines.size) {
            val timingIndex = when {
                lines[index].contains(" --> ") -> index
                index + 1 < lines.size && lines[index + 1].contains(" --> ") -> index + 1
                else -> { index++; continue }
            }
            val start = parseTimestamp(lines[timingIndex].substringBefore(" --> ").trim())
            index = timingIndex + 1
            val text = buildList {
                while (index < lines.size && lines[index].isNotBlank()) add(lines[index++].trim())
            }.joinToString(" ").replace(Regex("<[^>]+>"), "")
            if (text.isNotBlank()) cues += PodcastTranscriptCue(start, text)
        }
        return cues
    }

    private fun parseTimestamp(value: String): Long? {
        val parts = value.replace(',', '.').split(':')
        return runCatching {
            val seconds = parts.last().toDouble()
            val minutes = parts.getOrNull(parts.lastIndex - 1)?.toLong() ?: 0L
            val hours = parts.getOrNull(parts.lastIndex - 2)?.toLong() ?: 0L
            ((hours * 3_600 + minutes * 60) * 1_000 + seconds * 1_000).toLong()
        }.getOrNull()
    }
}
