package com.conice.morss.domain.model.ai

import java.util.UUID

/**
 * Native provider protocols supported by the AI subsystem.
 *
 * These values describe wire protocols, rather than marketing names.  Keeping the protocol in the
 * model prevents the execution layer from guessing an endpoint shape from a URL.
 */
enum class AiProvider {
    RESPONSES,
    GEMINI,
    ANTHROPIC,
}

enum class AiAuthType {
    NONE,
    BEARER,
    API_KEY_HEADER,
    API_KEY_QUERY,
}

enum class AiTask {
    TITLE_SUMMARY,
    ARTICLE_SUMMARY,
}

enum class AiOutputMode {
    TEXT,
    MARKDOWN,
}

data class AiConnection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val provider: AiProvider,
    val baseUrl: String,
    val authType: AiAuthType,
    val secretRef: String? = null,
    val enabled: Boolean = true,
    val revision: Long = 0L,
)

data class AiModelProfile(
    val id: String = UUID.randomUUID().toString(),
    val connectionId: String,
    val modelId: String,
    val displayName: String = modelId,
    val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
    val temperature: Double? = null,
    val enabled: Boolean = true,
    val revision: Long = 0L,
)

data class AiPrompt(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val task: AiTask,
    val systemTemplate: String,
    val userTemplate: String,
    val itemTemplate: String = "{title} · {index}",
    val outputMode: AiOutputMode = AiOutputMode.MARKDOWN,
    val builtIn: Boolean = false,
    val revision: Long = 1L,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class AiTaskBinding(
    val task: AiTask,
    val promptId: String,
    val primaryModelId: String = "",
    val fallbackModelIds: List<String> = emptyList(),
    val articleCount: Int = DEFAULT_ARTICLE_COUNT,
)

data class AiGenerationOptions(
    val stream: Boolean,
    val timeoutSeconds: Int,
    val maxOutputTokens: Int,
    val temperature: Double? = null,
)

data class AiArticleInput(
    val articleId: String,
    val title: String,
    val content: String,
    val link: String? = null,
)

data class AiTitleItem(
    val id: String,
    val title: String,
)

data class AiPromptContext(
    val task: AiTask,
    val title: String? = null,
    val content: String? = null,
    val link: String? = null,
    val index: Int? = null,
    val items: List<AiTitleItem> = emptyList(),
)

data class AiRequest(
    val task: AiTask,
    val systemInstruction: String,
    val userInput: String,
    val model: AiModelProfile,
    val connection: AiConnection,
    val apiKey: String?,
    val options: AiGenerationOptions,
)

data class AiResolvedModel(
    val model: AiModelProfile,
    val connection: AiConnection,
    val apiKey: String?,
)

data class AiUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
)

sealed interface AiStreamEvent {
    data class Started(
        val provider: AiProvider,
        val modelId: String,
    ) : AiStreamEvent

    data class TextDelta(val text: String) : AiStreamEvent

    data class Usage(val usage: AiUsage) : AiStreamEvent

    data class Completed(
        val finishReason: String? = null,
        val usage: AiUsage? = null,
    ) : AiStreamEvent
}

enum class AiFailureKind {
    NOT_CONFIGURED,
    INVALID_CONFIGURATION,
    AUTHENTICATION,
    ENDPOINT_NOT_FOUND,
    MODEL_NOT_FOUND,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    REQUEST_REJECTED,
    SERVICE_UNAVAILABLE,
    NETWORK,
    TIMEOUT,
    EMPTY_RESPONSE,
    INVALID_RESPONSE,
    CONTENT_REFUSED,
    UNKNOWN,
}

data class AiError(
    val kind: AiFailureKind,
    val provider: AiProvider? = null,
    val statusCode: Int? = null,
    val providerCode: String? = null,
    val requestId: String? = null,
    val retryAfterSeconds: Long? = null,
    val retryable: Boolean = false,
    val message: String,
    val detail: String? = null,
)

class AiProviderException(val error: AiError) : IllegalStateException(error.message)

const val DEFAULT_MAX_OUTPUT_TOKENS = 2_048
const val DEFAULT_ARTICLE_COUNT = 30
