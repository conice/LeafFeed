package me.ash.reader.domain.model.article

import java.util.Date
import me.ash.reader.domain.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleRuleTest {
    @Test
    fun keywordMatchingAndRangesRespectCaseSensitivity() {
        val insensitive = rule(pattern = "leaf")
        val sensitive = rule(pattern = "leaf", caseSensitive = true)

        assertTrue(insensitive.matches("Leaf Feed"))
        assertEquals(listOf(0..3), insensitive.ranges("Leaf Feed"))
        assertFalse(sensitive.matches("Leaf Feed"))
        assertEquals(emptyList<IntRange>(), sensitive.ranges("Leaf Feed"))
        assertEquals(listOf(0..3), sensitive.ranges("leaf Feed"))
    }

    @Test
    fun regexMatchingAndRangesRespectCaseSensitivity() {
        val insensitive = rule(pattern = "l.af", isRegex = true)
        val sensitive = rule(pattern = "l.af", isRegex = true, caseSensitive = true)

        assertTrue(insensitive.matches("Leaf Feed"))
        assertEquals(listOf(0..3), insensitive.ranges("Leaf Feed"))
        assertFalse(sensitive.matches("Leaf Feed"))
        assertEquals(emptyList<IntRange>(), sensitive.ranges("Leaf Feed"))
    }

    @Test
    fun compiledHighlightMatcherIndexesScopesAndIgnoresInvalidRegex() {
        val article =
            article(
                title = "Leaf Feed",
                accountId = 1,
                feedId = "feed-1",
                groupId = "group-1",
            )
        val rules =
            listOf(
                rule(pattern = "leaf"),
                rule(pattern = "Feed", scope = RuleScope.GROUP, scopeId = "group-1"),
                rule(pattern = "Feed", scope = RuleScope.FEED, scopeId = "feed-2"),
                rule(pattern = "[", isRegex = true),
                rule(pattern = "Leaf", accountId = 2),
            )

        assertEquals(
            listOf(0..3, 5..7),
            ArticleHighlightMatcher.from(rules).ranges(article),
        )
    }

    @Test
    fun diagnosticsExplainApplicableRuleMatchesWithoutChangingState() {
        val matchingGlobal = rule(pattern = "Leaf")
        val matchingGroup = rule(pattern = "Feed", scope = RuleScope.GROUP, scopeId = "group-1")
        val nonMatchingFeed = rule(pattern = "Android", scope = RuleScope.FEED, scopeId = "feed-1")
        val otherFeed = rule(pattern = "Leaf", scope = RuleScope.FEED, scopeId = "feed-2")

        val diagnostics = listOf(matchingGlobal, matchingGroup, nonMatchingFeed, otherFeed).diagnose(
            accountId = 1,
            groupId = "group-1",
            feedId = "feed-1",
            title = "Leaf Feed",
        )

        assertEquals(listOf(matchingGlobal, matchingGroup, nonMatchingFeed), diagnostics.map { it.rule })
        assertEquals(listOf(true, true, false), diagnostics.map { it.matched })
    }

    @Test
    fun diagnosticsCanExplainDescriptionMatches() {
        val descriptionRule = rule(pattern = "diagnostic keyword")

        val diagnostics = listOf(descriptionRule).diagnose(
            accountId = 1,
            groupId = "group-1",
            feedId = "feed-1",
            title = "Leaf Feed",
            description = "Contains a diagnostic keyword in the summary",
        )

        assertTrue(diagnostics.single().matched)
    }

    private fun rule(
        pattern: String,
        isRegex: Boolean = false,
        caseSensitive: Boolean = false,
        accountId: Int = 1,
        scope: RuleScope = RuleScope.GLOBAL,
        scopeId: String = "",
    ) = ArticleRule(
        id = pattern,
        accountId = accountId,
        scope = scope,
        scopeId = scopeId,
        type = RuleType.HIGHLIGHT,
        pattern = pattern,
        isRegex = isRegex,
        caseSensitive = caseSensitive,
    )

    private fun article(
        title: String,
        accountId: Int,
        feedId: String,
        groupId: String,
    ) =
        ArticleWithFeed(
            article =
                Article(
                    id = "article-1",
                    date = Date(0),
                    title = title,
                    rawDescription = "",
                    shortDescription = "",
                    link = "https://example.com/article",
                    feedId = feedId,
                    accountId = accountId,
                ),
            feed =
                Feed(
                    id = feedId,
                    name = "Feed",
                    url = "https://example.com/feed",
                    groupId = groupId,
                    accountId = accountId,
                ),
        )
}
