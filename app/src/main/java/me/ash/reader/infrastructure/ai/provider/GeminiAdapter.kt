package me.ash.reader.infrastructure.ai.provider

import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.ash.reader.domain.model.ai.AiAuthType
import me.ash.reader.domain.model.ai.AiProvider
import me.ash.reader.domain.model.ai.AiRequest
import me.ash.reader.domain.model.ai.AiStreamEvent
import me.ash.reader.domain.model.ai.AiUsage
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.executeAsync

class GeminiAdapter(
    private val client: OkHttpClient,
) : AiProviderAdapter {
    override val provider: AiProvider = AiProvider.GEMINI
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(
        request: AiRequest,
        emit: suspend (AiStreamEvent) -> Unit,
    ): String {
        emit(AiStreamEvent.Started(provider, request.model.modelId))
        val payload = buildJsonObject {
            put(
                "systemInstruction",
                buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", request.systemInstruction) }) })
                },
            )
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("parts", buildJsonArray { add(buildJsonObject { put("text", request.userInput) }) })
                        }
                    )
                },
            )
            put(
                "generationConfig",
                buildJsonObject {
                    put("maxOutputTokens", request.options.maxOutputTokens)
                    request.options.temperature?.let { put("temperature", it) }
                },
            )
        }
        val operation = if (request.options.stream) "streamGenerateContent" else "generateContent"
        val normalizedModelId = request.model.modelId.removePrefix("models/")
        val suffix = "/models/${Uri.encode(normalizedModelId)}:$operation"
        var endpoint = AiHttpSupport.endpoint(request.connection.baseUrl, suffix)
        if (request.options.stream) endpoint = AiHttpSupport.appendQuery(endpoint, "alt", "sse")
        if (request.connection.authType == AiAuthType.API_KEY_QUERY) {
            endpoint = AiHttpSupport.applyQueryAuth(endpoint, request, "key")
        }
        val httpRequest = Request.Builder()
            .url(endpoint)
            .post(AiHttpSupport.jsonBody(payload.toString()))
            .header("Accept", if (request.options.stream) "text/event-stream" else "application/json")
            .header("Cache-Control", "no-store")
            .apply { AiHttpSupport.applyAuth(request, "x-goog-api-key") }
            .build()
        return AiHttpSupport.client(client, request.options.timeoutSeconds)
            .newCall(httpRequest)
            .executeAsync()
            .use { response ->
                if (!response.isSuccessful) AiHttpSupport.httpError(response, provider)
                val body = response.body ?: AiHttpSupport.invalidResponse(provider, "missing body")
                val contentType = response.header("content-type").orEmpty()
                if (request.options.stream && contentType.contains("text/event-stream", ignoreCase = true)) {
                    readStream(body.source(), emit)
                } else {
                    readComplete(body.string(), emit)
                }
            }
    }

    private suspend fun readStream(
        source: okio.BufferedSource,
        emit: suspend (AiStreamEvent) -> Unit,
    ): String {
        val output = StringBuilder()
        var usage: AiUsage? = null
        var finishReason: String? = null
        var refusalReason: String? = null
        readAiSse(source) { _, data ->
            if (data == "[DONE]") return@readAiSse
            val root = parseObject(data)
            root["error"]?.let(::throwProviderError)
            appendCandidate(root, output, emit)
            usage = usage ?: parseUsage(root)
            refusalReason = refusalReason ?: blockedReason(root)
            finishReason = finishReason ?: parseFinishReason(root)
        }
        if (output.isEmpty()) refusalReason?.let {
            AiHttpSupport.contentRefused(provider, it)
        }
        if (output.isEmpty()) AiHttpSupport.emptyResponse(provider)
        usage?.let { emit(AiStreamEvent.Usage(it)) }
        emit(AiStreamEvent.Completed(finishReason, usage))
        return output.toString()
    }

    private suspend fun readComplete(
        body: String,
        emit: suspend (AiStreamEvent) -> Unit,
    ): String {
        val root = parseObject(body)
        root["error"]?.let(::throwProviderError)
        val output = StringBuilder()
        appendCandidate(root, output, emit)
        if (output.isEmpty()) blockedReason(root)?.let {
            AiHttpSupport.contentRefused(provider, it)
        }
        if (output.isEmpty()) AiHttpSupport.emptyResponse(provider)
        val usage = parseUsage(root)
        usage?.let { emit(AiStreamEvent.Usage(it)) }
        emit(
            AiStreamEvent.Completed(
                finishReason = parseFinishReason(root),
                usage = usage,
            )
        )
        return output.toString()
    }

    private suspend fun appendCandidate(
        root: JsonObject,
        output: StringBuilder,
        emit: suspend (AiStreamEvent) -> Unit,
    ): Boolean {
        val parts = firstCandidate(root)
            ?.get("content")
            ?.let { it as? JsonObject }
            ?.get("parts") as? JsonArray
            ?: return false
        var appended = false
        parts.forEach { part ->
            val text = (part as? JsonObject)?.string("text")
            if (!text.isNullOrEmpty()) {
                output.append(text)
                emit(AiStreamEvent.TextDelta(text))
                appended = true
            }
        }
        return appended
    }

    private fun parseObject(value: String): JsonObject = runCatching {
        json.parseToJsonElement(value).jsonObject
    }.getOrElse { AiHttpSupport.invalidResponse(provider, value) }

    private fun parseUsage(root: JsonObject): AiUsage? {
        val metadata = root["usageMetadata"] as? JsonObject ?: return null
        val input = metadata.string("promptTokenCount")?.toLongOrNull()
        val output = metadata.string("candidatesTokenCount")?.toLongOrNull()
        val total = metadata.string("totalTokenCount")?.toLongOrNull()
        return if (input == null && output == null && total == null) null else AiUsage(input, output, total)
    }

    private fun blockedReason(root: JsonObject): String? {
        val promptBlock = (root["promptFeedback"] as? JsonObject)
            ?.string("blockReason")
        val finishReason = parseFinishReason(root)?.takeIf { it in REFUSAL_FINISH_REASONS }
        return promptBlock ?: finishReason
    }

    private fun firstCandidate(root: JsonObject): JsonObject? =
        (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject

    private fun parseFinishReason(root: JsonObject): String? =
        firstCandidate(root)?.string("finishReason")

    private fun throwProviderError(element: kotlinx.serialization.json.JsonElement): Nothing {
        val error = element as? JsonObject
        AiHttpSupport.providerError(
            provider = provider,
            message = error?.string("message")
                ?: "Gemini request failed",
            providerCode = error?.string("status"),
            detail = element.toString(),
        )
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private companion object {
        val REFUSAL_FINISH_REASONS = setOf("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT")
    }
}
