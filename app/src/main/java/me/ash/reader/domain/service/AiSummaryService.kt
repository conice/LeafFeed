package me.ash.reader.domain.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.BufferedSource
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.executeAsync
import me.ash.reader.R
import me.ash.reader.infrastructure.ai.AiSummaryCache
import me.ash.reader.infrastructure.preference.AiSettings
import me.ash.reader.infrastructure.preference.AiTaskSettings
import me.ash.reader.infrastructure.preference.toAiSettings
import me.ash.reader.infrastructure.preference.toFeatureSettings
import me.ash.reader.ui.ext.dataStore

@Singleton
class AiSummaryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val cache: AiSummaryCache,
) {
    suspend fun settings(): AiSettings = context.dataStore.data.first().toAiSettings(
        defaultTitlePrompt = context.getString(R.string.ai_default_prompt),
        defaultArticlePrompt = context.getString(R.string.ai_default_article_prompt),
    )

    suspend fun summarizeTitles(
        accountId: Int,
        titles: List<Pair<String, String>>,
        forceRefresh: Boolean = false,
        onUpdate: (String) -> Unit = {},
    ): String {
        require(titles.isNotEmpty()) { "No articles to summarize" }
        val settings = settings()
        val userContent =
            "The input contains exactly ${titles.size} article titles. Summarize exactly these ${titles.size} titles only, once each. Do not infer facts not present in the titles.\n\n" +
                titles.mapIndexed { index, (_, title) -> "${index + 1}. $title" }
                    .joinToString("\n")
        val articleIdentity = titles.joinToString("\u0000") { (id, title) -> "$id\u0001$title" }
        val result = cache.getOrPut(
            accountId = accountId,
            fingerprint = fingerprint(
                "titles",
                "$articleIdentity\u0000$userContent",
                settings.titleSummary,
            ),
            forceRefresh = forceRefresh,
        ) {
            complete(
                userContent,
                settings.titleSummary.prompt,
                settings.titleSummary,
                onUpdate,
            )
        }
        if (result.fromCache) onUpdate(result.content)
        return result.content
    }

    suspend fun summarizeArticle(
        accountId: Int,
        articleId: String,
        title: String,
        content: String,
        link: String? = null,
        forceRefresh: Boolean = false,
        onUpdate: (String) -> Unit = {},
    ): String {
        val settings = settings()
        val featureSettings = context.dataStore.data.first().toFeatureSettings()
        val selectedContent = when (featureSettings.aiContentScope) {
            0 -> ""
            1 -> content.take(2_000)
            else -> content
        }
        val linkLine = if (featureSettings.aiIncludeArticleLink && !link.isNullOrBlank()) {
            "\n\nLink: $link"
        } else ""
        val userContent = "Summarize this article.\n\nTitle: $title\n\n$selectedContent$linkLine"
        val result = cache.getOrPut(
            accountId = accountId,
            fingerprint = fingerprint(
                "article",
                "$articleId\u0000$userContent",
                settings.articleSummary,
            ),
            forceRefresh = forceRefresh,
        ) {
            complete(
                userContent,
                settings.articleSummary.prompt,
                settings.articleSummary,
                onUpdate,
            )
        }
        if (result.fromCache) onUpdate(result.content)
        return result.content
    }

    suspend fun summarizeReport(
        report: String,
        forceRefresh: Boolean = false,
        onUpdate: (String) -> Unit = {},
    ): String {
        val settings = settings()
        val prompt = "You are a concise reading analytics editor. Summarize the supplied subscription report in the user's language. Use only the supplied numbers, identify one useful trend and one concrete cleanup or reading action. Do not invent causes. Keep it under 120 words."
        val result = cache.getOrPut(
            accountId = 0,
            fingerprint = fingerprint("report", report, settings.titleSummary),
            forceRefresh = forceRefresh,
        ) {
            complete("Summarize this subscription report.\n\n$report", prompt, settings.titleSummary, onUpdate)
        }
        if (result.fromCache) onUpdate(result.content)
        return result.content
    }

    private fun fingerprint(task: String, input: String, settings: AiTaskSettings): String =
        listOf(
            CACHE_REQUEST_VERSION,
            task,
            appLanguageTag(),
            completionEndpoint(settings.url),
            settings.model,
            settings.prompt,
            settings.apiKey,
            input,
        ).joinToString("\u0000")

    private suspend fun complete(
        userContent: String,
        systemPrompt: String,
        settings: AiTaskSettings,
        onUpdate: (String) -> Unit,
    ): String {
        require(settings.url.isNotBlank()) { "AI URL is not configured" }
        require(settings.model.isNotBlank()) { "AI model is not configured" }
        val featureSettings = context.dataStore.data.first().toFeatureSettings()
        val endpoint = completionEndpoint(settings.url)
        // The application language is only a fallback. The task prompt follows it and may
        // explicitly select another output language.
        val effectiveSystemPrompt = listOf(
            appLanguageInstruction(),
            systemPrompt.takeIf { it.isNotBlank() },
        ).filterNotNull().joinToString("\n\n")
        val messages = buildJsonArray {
            if (effectiveSystemPrompt.isNotBlank()) add(buildJsonObject {
                put("role", "system"); put("content", effectiveSystemPrompt)
            })
            add(buildJsonObject { put("role", "user"); put("content", userContent) })
        }
        val body = buildJsonObject {
            put("model", settings.model)
            put("messages", messages)
            put("stream", featureSettings.aiStreamingEnabled)
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(endpoint).post(body).apply {
            header("Accept", "text/event-stream")
            if (settings.apiKey.isNotBlank()) header("Authorization", "Bearer ${settings.apiKey}")
        }.build()
        var lastUpdateNanos = 0L
        var lastPublishedContent = ""
        val requestClient = client.newBuilder()
            .readTimeout(featureSettings.aiTimeoutSeconds.coerceIn(30, 900).toLong(), TimeUnit.SECONDS)
            .build()
        val result = requestClient.newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) {
                error("AI request failed (${response.code}): ${response.body.string()}")
            }
            readChatCompletionStream(response.body.source()) { partialContent ->
                val now = System.nanoTime()
                if (
                    lastUpdateNanos == 0L ||
                        partialContent.isNotBlank() && lastPublishedContent.isBlank() ||
                        now - lastUpdateNanos >= STREAM_UPDATE_INTERVAL_NANOS
                ) {
                    lastUpdateNanos = now
                    lastPublishedContent = partialContent
                    onUpdate(partialContent)
                }
            }
        }
        if (result != lastPublishedContent) {
            onUpdate(result)
        }
        return result
    }

    private fun appLanguageTag(): String =
        context.resources.configuration.locales[0].toLanguageTag()

    private fun appLanguageInstruction(): String {
        val locale = context.resources.configuration.locales[0]
        return "Default output language: ${locale.getDisplayLanguage(Locale.ENGLISH)} (${locale.toLanguageTag()}). " +
            "Use this language only when the task prompt does not specify another output language."
    }

    private companion object {
        const val CACHE_REQUEST_VERSION = "2"
        const val STREAM_UPDATE_INTERVAL_NANOS = 50_000_000L

        fun completionEndpoint(url: String): String = url.trimEnd('/').let {
            if (it.contains("/chat/completions")) it else "$it/chat/completions"
        }
    }
}

internal fun readChatCompletionStream(
    source: BufferedSource,
    onUpdate: (String) -> Unit = {},
): String {
    val content = StringBuilder()
    val eventData = mutableListOf<String>()
    val fallbackBody = StringBuilder()
    var receivedStreamEvent = false
    var finished = false

    fun consumeEvent() {
        if (eventData.isEmpty()) return
        receivedStreamEvent = true
        val data = eventData.joinToString("\n")
        eventData.clear()
        if (data == "[DONE]") {
            finished = true
            return
        }
        if (appendChatCompletionContent(data, content)) {
            onUpdate(content.toString())
        }
    }

    while (!finished) {
        val line = source.readUtf8Line() ?: break
        when {
            line.isEmpty() -> consumeEvent()
            line.startsWith("data:") -> eventData += line.removePrefix("data:").removePrefix(" ")
            line.startsWith(":") || line.startsWith("event:") || line.startsWith("id:") -> Unit
            !receivedStreamEvent -> {
                if (fallbackBody.isNotEmpty()) fallbackBody.append('\n')
                fallbackBody.append(line)
            }
        }
    }
    consumeEvent()

    if (!receivedStreamEvent && fallbackBody.isNotBlank()) {
        appendChatCompletionContent(fallbackBody.toString(), content)
    }
    return content.toString().takeIf { it.isNotBlank() }
        ?: error("AI returned an empty response")
}

private fun appendChatCompletionContent(json: String, output: StringBuilder): Boolean {
    val root = Json.parseToJsonElement(json).jsonObject
    root["error"]?.let { errorElement ->
        val message = (errorElement as? JsonObject)?.get("message")
            ?.let { it as? JsonPrimitive }?.contentOrNull
        error(message ?: errorElement.toString())
    }
    val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return false
    val message = choice["delta"] as? JsonObject ?: choice["message"] as? JsonObject
        ?: return false
    val chunk = (message["content"] as? JsonPrimitive)?.contentOrNull ?: return false
    if (chunk.isEmpty()) return false
    output.append(chunk)
    return true
}
