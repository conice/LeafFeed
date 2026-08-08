package me.ash.reader.infrastructure.ai.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.ash.reader.domain.model.ai.AiProvider
import me.ash.reader.domain.model.ai.AiRequest
import me.ash.reader.domain.model.ai.AiStreamEvent
import me.ash.reader.domain.model.ai.AiUsage
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.executeAsync

class ResponsesAdapter(
    private val client: OkHttpClient,
) : AiProviderAdapter {
    override val provider: AiProvider = AiProvider.RESPONSES
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun execute(
        request: AiRequest,
        emit: suspend (AiStreamEvent) -> Unit,
    ): String {
        emit(AiStreamEvent.Started(provider, request.model.modelId))
        val payload = buildJsonObject {
            put("model", request.model.modelId)
            put("instructions", request.systemInstruction)
            put("input", request.userInput)
            put("stream", request.options.stream)
            put("max_output_tokens", request.options.maxOutputTokens)
            request.options.temperature?.let { put("temperature", it) }
        }
        var endpoint = AiHttpSupport.endpoint(request.connection.baseUrl, "/responses")
        endpoint = AiHttpSupport.applyQueryAuth(endpoint, request, "api_key")
        val httpRequest = Request.Builder()
            .url(endpoint)
            .post(AiHttpSupport.jsonBody(payload.toString()))
            .header("Accept", if (request.options.stream) "text/event-stream" else "application/json")
            .header("Cache-Control", "no-store")
            .apply { AiHttpSupport.applyAuth(request, "x-api-key") }
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
        val refusal = StringBuilder()
        var usage: AiUsage? = null
        var finishReason: String? = null
        readAiSse(source) { eventName, data ->
            if (data == "[DONE]") return@readAiSse
            val root = parseObject(data)
            root["error"]?.let { errorElement ->
                AiHttpSupport.providerError(
                    provider = provider,
                    message = errorMessage(errorElement),
                    detail = errorElement.toString(),
                )
            }
            val effectiveEvent = eventName ?: root.string("type")
            if (effectiveEvent == "response.failed") {
                val response = root["response"] as? JsonObject
                val error = response?.get("error")
                AiHttpSupport.providerError(
                    provider = provider,
                    message = error?.let(::errorMessage) ?: "Responses request did not complete",
                    detail = response?.toString() ?: root.toString(),
                )
            }
            val textDelta = when (effectiveEvent) {
                "response.output_text.delta" ->
                    root.string("delta") ?: root.string("text")
                else -> null
            }
            if (!textDelta.isNullOrEmpty()) {
                output.append(textDelta)
                emit(AiStreamEvent.TextDelta(textDelta))
            }
            if (effectiveEvent == "response.refusal.delta") {
                root.string("delta")?.let(refusal::append)
            }
            val response = root["response"] as? JsonObject
            usage = usage ?: parseUsage(response ?: root)
            finishReason = finishReason ?: response
                ?.string("status")
                ?: root.string("status")
        }
        if (output.isEmpty() && refusal.isNotEmpty()) {
            AiHttpSupport.contentRefused(provider, refusal.toString())
        }
        if (output.isEmpty()) AiHttpSupport.emptyResponse(provider)
        usage?.let { emit(AiStreamEvent.Usage(it)) }
        emit(AiStreamEvent.Completed(finishReason = finishReason, usage = usage))
        return output.toString()
    }

    private suspend fun readComplete(
        body: String,
        emit: suspend (AiStreamEvent) -> Unit,
    ): String {
        val root = parseObject(body)
        root["error"]?.let {
            AiHttpSupport.providerError(provider, errorMessage(it), detail = it.toString())
        }
        val output = extractOutputText(root)
        if (output.isBlank()) {
            extractRefusal(root)?.let { AiHttpSupport.contentRefused(provider, it) }
        }
        if (output.isBlank()) AiHttpSupport.emptyResponse(provider)
        emit(AiStreamEvent.TextDelta(output))
        val usage = parseUsage(root)
        usage?.let { emit(AiStreamEvent.Usage(it)) }
        emit(
            AiStreamEvent.Completed(
                finishReason = root.string("status"),
                usage = usage,
            )
        )
        return output
    }

    private fun parseObject(value: String): JsonObject = runCatching {
        json.parseToJsonElement(value).jsonObject
    }.getOrElse { AiHttpSupport.invalidResponse(provider, value) }

    private fun extractOutputText(root: JsonObject): String {
        root.string("output_text")?.let { return it }
        val output = root["output"] as? JsonArray ?: return ""
        return output.flatMap { item ->
            val content = (item as? JsonObject)?.get("content") as? JsonArray ?: return@flatMap emptyList()
            content.mapNotNull { part ->
                (part as? JsonObject)?.string("text")
            }
        }.joinToString("")
    }

    private fun extractRefusal(root: JsonObject): String? {
        val output = root["output"] as? JsonArray ?: return null
        return output.asSequence().mapNotNull { item ->
            (item as? JsonObject)?.get("content") as? JsonArray
        }.flatten().mapNotNull { part ->
            (part as? JsonObject)?.string("refusal")
        }.joinToString("").takeIf(String::isNotBlank)
    }

    private fun parseUsage(root: JsonObject): AiUsage? {
        val value = root["usage"] as? JsonObject ?: return null
        val input = value.long("input_tokens") ?: value.long("prompt_tokens")
        val output = value.long("output_tokens") ?: value.long("completion_tokens")
        val total = value.long("total_tokens")
        return if (input == null && output == null && total == null) null else AiUsage(input, output, total)
    }

    private fun JsonObject.long(name: String): Long? =
        string(name)?.toLongOrNull()

    private fun errorMessage(element: JsonElement): String =
        (element as? JsonObject)?.string("message")
            ?: (element as? JsonPrimitive)?.contentOrNull
            ?: "Responses request failed"

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull
}
