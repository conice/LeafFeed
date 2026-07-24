package me.ash.reader.domain.service

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AiSummaryStreamTest {
    @Test
    fun `collects content while ignoring reasoning chunks`() {
        val source = Buffer().writeUtf8(
            """
            data: {"choices":[{"delta":{"reasoning_content":"thinking"}}]}

            data: {"choices":[{"delta":{"content":"Hello"}}]}

            data: {"choices":[{"delta":{"content":" world"}}]}

            data: [DONE]

            """.trimIndent()
        )

        assertEquals("Hello world", readChatCompletionStream(source))
    }

    @Test
    fun `publishes cumulative content as stream chunks arrive`() {
        val source = Buffer().writeUtf8(
            """
            data: {"choices":[{"delta":{"content":"Hello"}}]}

            data: {"choices":[{"delta":{"content":" world"}}]}

            data: [DONE]

            """.trimIndent()
        )
        val updates = mutableListOf<String>()

        val result = readChatCompletionStream(source, updates::add)

        assertEquals("Hello world", result)
        assertEquals(listOf("Hello", "Hello world"), updates)
    }

    @Test
    fun `accepts a non-streaming response as fallback`() {
        val source = Buffer().writeUtf8(
            """{"choices":[{"message":{"content":"Summary"}}]}"""
        )

        assertEquals("Summary", readChatCompletionStream(source))
    }

    @Test
    fun `surfaces errors sent in a stream event`() {
        val source = Buffer().writeUtf8(
            """
            data: {"error":{"message":"model unavailable"}}

            """.trimIndent()
        )

        val error = assertThrows(IllegalStateException::class.java) {
            readChatCompletionStream(source)
        }
        assertEquals("model unavailable", error.message)
    }
}
