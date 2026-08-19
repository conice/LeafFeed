package com.conice.morss.infrastructure.ai.provider

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import com.conice.morss.domain.model.ai.AiAuthType
import com.conice.morss.domain.model.ai.AiError
import com.conice.morss.domain.model.ai.AiFailureKind
import com.conice.morss.domain.model.ai.AiProvider
import com.conice.morss.domain.model.ai.AiProviderException
import com.conice.morss.domain.model.ai.AiRequest
import com.conice.morss.domain.model.ai.AiStreamEvent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource

interface AiProviderAdapter {
    val provider: AiProvider

    suspend fun execute(
        request: AiRequest,
        emit: suspend (AiStreamEvent) -> Unit,
    ): String
}

class AiProviderRegistry(
    private val adapters: List<AiProviderAdapter>,
) {
    fun adapter(provider: AiProvider): AiProviderAdapter =
        adapters.firstOrNull { it.provider == provider }
            ?: error("No AI adapter registered for ${provider.name}")
}

internal object AiHttpSupport {
    private val json = Json { ignoreUnknownKeys = true }

    fun client(base: OkHttpClient, timeoutSeconds: Int): OkHttpClient =
        base.newBuilder()
            .readTimeout(timeoutSeconds.coerceIn(30, 900).toLong(), TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds.coerceIn(30, 900).toLong(), TimeUnit.SECONDS)
            .build()

    fun jsonBody(value: String) =
        value.toRequestBody("application/json; charset=utf-8".toMediaType())

    fun endpoint(baseUrl: String, suffix: String): String {
        val base = baseUrl.trim().trimEnd('/')
        require(base.isNotBlank()) { "AI base URL cannot be blank" }
        return if (base.endsWith(suffix)) base else "$base$suffix"
    }

    fun Request.Builder.applyAuth(request: AiRequest, apiKeyHeader: String) {
        val key = request.apiKey?.takeIf { it.isNotBlank() } ?: return
        when (request.connection.authType) {
            AiAuthType.NONE -> Unit
            AiAuthType.BEARER -> header("Authorization", "Bearer $key")
            AiAuthType.API_KEY_HEADER -> header(apiKeyHeader, key)
            AiAuthType.API_KEY_QUERY -> Unit
        }
    }

    fun appendQuery(url: String, name: String, value: String): String =
        url + (if (url.contains('?')) "&" else "?") +
            "${java.net.URLEncoder.encode(name, Charsets.UTF_8.name())}=" +
            java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    fun applyQueryAuth(url: String, request: AiRequest, parameterName: String): String {
        if (request.connection.authType != AiAuthType.API_KEY_QUERY) return url
        val key = request.apiKey?.takeIf(String::isNotBlank) ?: return url
        return appendQuery(url, parameterName, key)
    }

    fun httpError(response: Response, provider: AiProvider): Nothing {
        val body = response.body?.string().orEmpty()
        val (message, providerCode) = parseError(body)
        val lowerMessage = message.lowercase()
        val kind = when {
            response.code == 404 && ("model" in lowerMessage || "does not exist" in lowerMessage) ->
                AiFailureKind.MODEL_NOT_FOUND
            response.code == 429 && ("quota" in lowerMessage || "billing" in lowerMessage) ->
                AiFailureKind.QUOTA_EXCEEDED
            response.code == 401 || response.code == 403 -> AiFailureKind.AUTHENTICATION
            response.code == 404 -> AiFailureKind.ENDPOINT_NOT_FOUND
            response.code == 408 -> AiFailureKind.TIMEOUT
            response.code == 429 -> AiFailureKind.RATE_LIMITED
            response.code in 400..499 -> AiFailureKind.REQUEST_REJECTED
            response.code in 500..599 -> AiFailureKind.SERVICE_UNAVAILABLE
            else -> AiFailureKind.UNKNOWN
        }
        throw AiProviderException(
            AiError(
                kind = kind,
                provider = provider,
                statusCode = response.code,
                providerCode = providerCode,
                requestId = response.header("x-request-id")
                    ?: response.header("request-id")
                    ?: response.header("x-goog-request-id")
                    ?: response.header("anthropic-request-id"),
                retryAfterSeconds = response.header("retry-after")?.toLongOrNull(),
                retryable = kind == AiFailureKind.RATE_LIMITED ||
                    kind == AiFailureKind.TIMEOUT ||
                    kind == AiFailureKind.SERVICE_UNAVAILABLE,
                message = message.ifBlank { "AI request failed (${response.code})" },
                detail = redact(body).take(2_000).ifBlank { null },
            )
        )
    }

    fun invalidResponse(provider: AiProvider, detail: String): Nothing =
        throw AiProviderException(
            AiError(
                kind = AiFailureKind.INVALID_RESPONSE,
                provider = provider,
                message = "AI returned an invalid response",
                detail = redact(detail).take(2_000),
            )
        )

    fun emptyResponse(provider: AiProvider): Nothing =
        throw AiProviderException(
            AiError(
                kind = AiFailureKind.EMPTY_RESPONSE,
                provider = provider,
                message = "AI returned an empty response",
            )
        )

    fun contentRefused(provider: AiProvider, detail: String? = null): Nothing =
        throw AiProviderException(
            AiError(
                kind = AiFailureKind.CONTENT_REFUSED,
                provider = provider,
                message = "AI declined to produce a response",
                detail = detail?.let(::redact)?.take(2_000),
            )
        )

    fun providerError(
        provider: AiProvider,
        message: String,
        providerCode: String? = null,
        retryable: Boolean = false,
        detail: String? = null,
    ): Nothing = throw AiProviderException(
        AiError(
            kind = message.lowercase().let { lower ->
                when {
                    "quota" in lower || "billing" in lower -> AiFailureKind.QUOTA_EXCEEDED
                    "rate" in lower || "too many requests" in lower -> AiFailureKind.RATE_LIMITED
                    "timeout" in lower || "timed out" in lower -> AiFailureKind.TIMEOUT
                    else -> AiFailureKind.REQUEST_REJECTED
                }
            },
            provider = provider,
            providerCode = providerCode,
            retryable = retryable || message.lowercase().let { lower ->
                "quota" in lower || "billing" in lower || "rate" in lower ||
                    "too many requests" in lower || "timeout" in lower || "timed out" in lower
            },
            message = message.ifBlank { "AI request failed" },
            detail = detail?.let(::redact)?.take(2_000),
        )
    )

    fun parseError(body: String): Pair<String, String?> = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val error = root["error"]
        val errorObject = error as? JsonObject
        val message = when {
            errorObject?.get("message") is JsonPrimitive ->
                (errorObject["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            error is JsonPrimitive -> error.contentOrNull.orEmpty()
            root["message"] is JsonPrimitive ->
                (root["message"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            else -> ""
        }
        val code = (errorObject?.get("code") as? JsonPrimitive)?.contentOrNull
            ?: (errorObject?.get("type") as? JsonPrimitive)?.contentOrNull
            ?: (root["type"] as? JsonPrimitive)?.contentOrNull
        message to code
    }.getOrDefault("" to null)

    fun redact(value: String): String = value
        .replace(Regex("(?i)(\\b(?:authorization|x-api-key|api[_-]?key|key)\\s*[=:]\\s*)[^\\s,;&]+")) {
            "${it.groupValues[1]}***"
        }
        .replace(Regex("\\b(?:sk-ant-|sk-|AIza)[A-Za-z0-9._-]{6,}\\b"), "***")
}

internal suspend fun readAiSse(
    source: BufferedSource,
    onEvent: suspend (eventName: String?, data: String) -> Unit,
) {
    var eventName: String? = null
    val data = StringBuilder()

    suspend fun consume() {
        if (data.isEmpty()) {
            eventName = null
            return
        }
        onEvent(eventName, data.toString())
        data.clear()
        eventName = null
    }

    while (true) {
        val line = source.readUtf8Line() ?: break
        when {
            line.isEmpty() -> consume()
            line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
            line.startsWith("data:") -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").trimStart())
            }
            line.startsWith(":") || line.startsWith("id:") || line.startsWith("retry:") -> Unit
        }
    }
    consume()
}
