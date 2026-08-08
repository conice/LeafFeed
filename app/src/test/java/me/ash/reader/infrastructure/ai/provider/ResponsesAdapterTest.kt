package me.ash.reader.infrastructure.ai.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.ash.reader.domain.model.ai.AiAuthType
import me.ash.reader.domain.model.ai.AiConnection
import me.ash.reader.domain.model.ai.AiGenerationOptions
import me.ash.reader.domain.model.ai.AiModelProfile
import me.ash.reader.domain.model.ai.AiProvider
import me.ash.reader.domain.model.ai.AiRequest
import me.ash.reader.domain.model.ai.AiTask
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ResponsesAdapterTest {
    @Test
    fun buildsStructuredSystemAndUserInput() {
        val payload = ResponsesAdapter(OkHttpClient()).buildPayload(
            AiRequest(
                task = AiTask.ARTICLE_SUMMARY,
                systemInstruction = "Summarize faithfully.",
                userInput = "Title: Example\n\nArticle body",
                model = AiModelProfile(
                    connectionId = "connection",
                    modelId = "example-model",
                ),
                connection = AiConnection(
                    id = "connection",
                    name = "Example",
                    provider = AiProvider.RESPONSES,
                    baseUrl = "https://example.com/v1",
                    authType = AiAuthType.BEARER,
                ),
                apiKey = "secret",
                options = AiGenerationOptions(
                    stream = true,
                    timeoutSeconds = 60,
                    maxOutputTokens = 2_048,
                ),
            )
        )

        assertFalse(payload.containsKey("instructions"))
        val input = payload["input"] as JsonArray
        assertEquals(2, input.size)
        assertMessage(input[0] as JsonObject, "system", "Summarize faithfully.")
        assertMessage(input[1] as JsonObject, "user", "Title: Example\n\nArticle body")
    }

    private fun assertMessage(message: JsonObject, role: String, content: String) {
        assertEquals(role, message["role"]?.jsonPrimitive?.content)
        assertEquals(content, message["content"]?.jsonPrimitive?.content)
    }
}
