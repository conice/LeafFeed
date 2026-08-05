package me.ash.reader.ui.component

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
}
