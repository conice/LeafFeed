package com.conice.morss.ui.component.webview

import org.jsoup.Jsoup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewContentSanitizerTest {
    @Test
    fun `removes scripts nested pages event handlers and active urls`() {
        val sanitized =
            sanitizeWebViewContent(
                """
                <p onclick="steal()">Article</p>
                <script>steal()</script>
                <a href="java&#x09;script:steal()">Unsafe</a>
                <a href="data:text/html,&lt;script&gt;steal()&lt;/script&gt;">Unsafe data</a>
                <iframe src="https://example.com/active-page"></iframe>
                <iframe src="https://www.youtube.com/embed/video-id?feature=oembed"></iframe>
                <meta http-equiv="refresh" content="0; https://example.com">
                <img src="https://example.com/image.jpg" onerror="steal()">
                """.trimIndent()
            )

        assertFalse(sanitized.contains("<script", ignoreCase = true))
        assertTrue(sanitized.contains("https://www.youtube.com/embed/video-id"))
        assertFalse(sanitized.contains("https://example.com/active-page"))
        assertFalse(sanitized.contains("<meta", ignoreCase = true))
        assertFalse(sanitized.contains("onclick", ignoreCase = true))
        assertFalse(sanitized.contains("onerror", ignoreCase = true))
        assertFalse(sanitized.contains("javascript:", ignoreCase = true))
        assertTrue(sanitized.contains("https://example.com/image.jpg"))
        assertTrue(Jsoup.parseBodyFragment(sanitized).select("a[href]").isEmpty())
    }

    @Test
    fun `keeps trusted video frames and resolves relative sources`() {
        val sanitized =
            sanitizeWebViewContent(
                """
                <iframe src="//www.youtube-nocookie.com/embed/video-id"></iframe>
                <iframe src="https://player.bilibili.com/player.html?aid=729663986"></iframe>
                """.trimIndent(),
                "https://example.com/articles/story",
            )

        assertTrue(sanitized.contains("youtube-nocookie.com/embed/video-id"))
        assertTrue(sanitized.contains("player.bilibili.com/player.html"))
    }

    @Test
    fun `keeps form text but removes controls and submissions`() {
        val sanitized =
            sanitizeWebViewContent(
                """
                <form action="https://example.com/submit">
                    <p>Question</p>
                    <input name="answer">
                    <button>Send</button>
                </form>
                """.trimIndent()
            )

        assertTrue(sanitized.contains("Question"))
        assertFalse(sanitized.contains("<form", ignoreCase = true))
        assertFalse(sanitized.contains("<input", ignoreCase = true))
        assertFalse(sanitized.contains("<button", ignoreCase = true))
    }
}
