package com.conice.morss.ui.component

import org.commonmark.node.Link
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiSummaryDialogTest {
    @Test
    fun findsLegacyLeadingArticleNumber() {
        assertEquals(
            2,
            findAiSummaryArticleNumber(
                line = "2. Article title",
                articleTitles = listOf("First", "Article title"),
                articleCount = 2,
            ),
        )
    }

    @Test
    fun findsTitleFirstArticleNumber() {
        assertEquals(
            2,
            findAiSummaryArticleNumber(
                line = "Article title · 2",
                articleTitles = listOf("First", "Article title"),
                articleCount = 2,
            ),
        )
    }

    @Test
    fun findsTranslatedTitleByTrailingArticleNumber() {
        assertEquals(
            2,
            findAiSummaryArticleNumber(
                line = "翻译后的文章标题 · 2",
                articleTitles = listOf("First", "Original article title"),
                articleCount = 2,
            ),
        )
    }

    @Test
    fun keepsCompatibilityWithTitleAndIndexWithoutSeparator() {
        assertEquals(
            2,
            findAiSummaryArticleNumber(
                line = "Article title2",
                articleTitles = listOf("First", "Article title"),
                articleCount = 2,
            ),
        )
    }

    @Test
    fun findsMarkdownListArticleNumber() {
        assertEquals(
            2,
            findAiSummaryArticleNumber(
                line = "- [2] Article title",
                articleTitles = listOf("First", "Article title"),
                articleCount = 2,
            ),
        )
    }

    @Test
    fun findsTitleInCustomPromptOutput() {
        assertEquals(
            2,
            findAiSummaryArticleNumber(
                line = "[Recommended] Article title",
                articleTitles = listOf("First", "Article title"),
                articleCount = 2,
            ),
        )
    }

    @Test
    fun doesNotMatchAnAmbiguousTitle() {
        assertNull(
            findAiSummaryArticleNumber(
                line = "Article title roundup",
                articleTitles = listOf("Article", "Article title"),
                articleCount = 2,
            ),
        )
    }

    @Test
    fun titleFirstFormatWinsWhenTitleStartsWithANumber() {
        assertEquals(
            2,
            findAiSummaryArticleNumber(
                line = "1. Article title · 2",
                articleTitles = listOf("First", "1. Article title"),
                articleCount = 2,
            ),
        )
    }

    @Test
    fun ignoresUnknownSummaryLine() {
        assertNull(
            findAiSummaryArticleNumber(
                line = "**Topic**",
                articleTitles = listOf("Article title"),
                articleCount = 1,
            ),
        )
    }

    @Test
    fun extractsPlainTextFromMarkdownForArticleMatching() {
        val document = parseAiMarkdown("- **Article title** · 2")
        val paragraph = document.firstChild.firstChild.firstChild

        assertEquals("Article title · 2", aiMarkdownPlainText(paragraph))
    }

    @Test
    fun appendsStreamingCursorToLastMarkdownLine() {
        val document = parseAiMarkdown("### Category\n\n- First title\n- Second title")

        assertEquals(true, appendAiMarkdownCursor(document))
        assertEquals("Second title ▍", aiMarkdownPlainText(document.lastChild.lastChild))
    }

    @Test
    fun leavesBlockOnlyMarkdownCursorForFallbackRendering() {
        val document = parseAiMarkdown("```\ncode\n```")

        assertEquals(false, appendAiMarkdownCursor(document))
    }

    @Test
    fun restoresOrderedListMarkerForArticleMatching() {
        val document = parseAiMarkdown("2. **Article title**")
        val listItem = document.firstChild.firstChild

        assertEquals("2. Article title", aiMarkdownMatchText(listItem, 2))
    }

    @Test
    fun onlyAllowsWebLinksFromAiMarkdown() {
        assertEquals(true, isSafeAiMarkdownUrl("https://example.com/article"))
        assertEquals(true, isSafeAiMarkdownUrl("HTTP://example.com"))
        assertEquals(false, isSafeAiMarkdownUrl("intent://open/settings"))
        assertEquals(false, isSafeAiMarkdownUrl("file:///data/local/file"))
        assertEquals(false, isSafeAiMarkdownUrl("/relative/path"))
    }

    @Test
    fun resolvesMarkdownTitleLinkToLocalArticle() {
        val document = parseAiMarkdown("[Article title](https://example.com/article)")
        val link = document.firstChild.firstChild as Link

        assertEquals(
            "article-2",
            findAiMarkdownArticleId(
                link = link,
                articleIds = listOf("article-1", "article-2"),
                articleTitles = listOf("First", "Article title"),
            ),
        )
    }

    @Test
    fun leavesUnmatchedMarkdownLinkExternal() {
        val document = parseAiMarkdown("[Source website](https://example.com)")
        val link = document.firstChild.firstChild as Link

        assertNull(
            findAiMarkdownArticleId(
                link = link,
                articleIds = listOf("article-1"),
                articleTitles = listOf("Article title"),
            ),
        )
    }
}
