package com.conice.morss.infrastructure.ai.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import com.conice.morss.domain.model.ai.AiProvider
import com.conice.morss.domain.model.ai.AiRequest
import com.conice.morss.domain.model.ai.AiStreamEvent
import com.conice.morss.domain.model.ai.AiUsage
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.executeAsync

class AnthropicAdapter(
    private val client: OkHttpClient,
) : AiProviderAdapter {
    override val provider: AiProvider = AiProvider.ANTHROPIC
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(
        request: AiRequest,
        emit: suspend (AiStreamEvent) -> Unit,
    ): String {
        emit(AiStreamEvent.Started(provider, request.model.modelId))
        val payload = buildJsonObject {
            put("model", request.model.modelId)
            put("max_tokens", request.options.maxOutputTokens)
            put("system", request.systemInstruction)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                buildJsonArray {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", request.userInput)
                                    })
                                },
                            )
                        }
                    )
                },
            )
            put("stream", request.options.stream)
            request.options.temperature?.let { put("temperature", it) }
        }
        var endpoint = AiHttpSupport.endpoint(request.connection.baseUrl, "/messages")
        endpoint = AiHttpSupport.applyQueryAuth(endpoint, request, "api_key")
        val httpRequest = Request.Builder()
            .url(endpoint)
            .post(AiHttpSupport.jsonBody(payload.toString()))
            .header("Accept", if (request.options.stream) "text/event-stream" else "application/json")
            .header("anthropic-version", "2023-06-01")
            .header("Cache-Control", "no-store")
            .apply { AiHttpSupport.apply { applyAuth(request, "x-api-key") } }
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
        readAiSse(source) { eventName, data ->
            if (data == "[DONE]") return@readAiSse
            val root = parseObject(data)
            if (eventName == "error" || root.string("type") == "error") {
                val error = root["error"] as? JsonObject
                AiHttpSupport.providerError(
                    provider = provider,
                    providerCode = error?.string("type"),
                    message = error?.string("message")
                        ?: "Anthropic request failed",
                    detail = root.toString(),
                )
            }
            when (eventName ?: root.string("type")) {
                "content_block_delta" -> {
                    val text = (root["delta"] as? JsonObject)?.string("text")
                    if (!text.isNullOrEmpty()) {
                        output.append(text)
                        emit(AiStreamEvent.TextDelta(text))
                    }
                }
                "message_start" -> {
                    val messageUsage = (root["message"] as? JsonObject)
                        ?.get("usage") as? JsonObject
                    usage = mergeUsage(usage, messageUsage?.let(::parseUsage))
                }
                "message_delta" -> {
                    val delta = root["delta"] as? JsonObject
                    finishReason = finishReason
                        ?: delta?.string("stop_reason")
                    usage = mergeUsage(usage, (root["usage"] as? JsonObject)?.let(::parseUsage))
                }
            }
        }
        if (output.isEmpty() && finishReason == "refusal") {
            AiHttpSupport.contentRefused(provider)
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
        if (root.string("type") == "error") {
            val error = root["error"] as? JsonObject
            AiHttpSupport.providerError(
                provider = provider,
                message = error?.string("message")
                    ?: "Anthropic request failed",
                providerCode = error?.string("type"),
                detail = body,
            )
        }
        val output = extractText(root)
        if (output.isBlank() && root.string("stop_reason") == "refusal") {
            AiHttpSupport.contentRefused(provider)
        }
        if (output.isBlank()) AiHttpSupport.emptyResponse(provider)
        emit(AiStreamEvent.TextDelta(output))
        val usage = (root["usage"] as? JsonObject)?.let(::parseUsage)
        usage?.let { emit(AiStreamEvent.Usage(it)) }
        emit(
            AiStreamEvent.Completed(
                finishReason = root.string("stop_reason"),
                usage = usage,
            )
        )
        return output
    }

    private fun parseObject(value: String): JsonObject = runCatching {
        json.parseToJsonElement(value).jsonObject
    }.getOrElse { AiHttpSupport.invalidResponse(provider, value) }

    private fun extractText(root: JsonObject): String {
        val content = root["content"] as? JsonArray ?: return ""
        return content.mapNotNull { block ->
            (block as? JsonObject)?.takeIf {
                it.string("type") == "text"
            }?.string("text")
        }.joinToString("")
    }

    private fun parseUsage(value: JsonObject): AiUsage? {
        val input = value.string("input_tokens")?.toLongOrNull()
        val output = value.string("output_tokens")?.toLongOrNull()
        val total = if (input != null || output != null) {
            (input ?: 0L) + (output ?: 0L)
        } else null
        return if (input == null && output == null) null else AiUsage(input, output, total)
    }

    private fun mergeUsage(current: AiUsage?, update: AiUsage?): AiUsage? {
        if (current == null) return update
        if (update == null) return current
        val input = update.inputTokens ?: current.inputTokens
        val output = update.outputTokens ?: current.outputTokens
        val total = if (input != null || output != null) {
            (input ?: 0L) + (output ?: 0L)
        } else {
            update.totalTokens ?: current.totalTokens
        }
        return AiUsage(input, output, total)
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull
}
