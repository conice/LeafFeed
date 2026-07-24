package me.ash.reader.domain.data

import me.ash.reader.domain.model.article.ArticleFilterCandidate
import me.ash.reader.domain.model.article.ArticleRule
import me.ash.reader.domain.model.article.RuleScope
import me.ash.reader.domain.model.article.RuleType
import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightedArticleReadUseCaseTest {
    @Test
    fun allHighlightsMatchOnlyApplicableHighlightRules() {
        val articles =
            listOf(
                article(id = "global", title = "global match"),
                article(id = "group", title = "group match"),
                article(id = "wrong-group", groupId = "group-2", title = "group match"),
                article(id = "filter", title = "filter match"),
            )
        val rules =
            listOf(
                rule(id = "global-rule", pattern = "global"),
                rule(
                    id = "group-rule",
                    pattern = "group",
                    scope = RuleScope.GROUP,
                    scopeId = "group-1",
                ),
                rule(id = "filter-rule", pattern = "filter", type = RuleType.FILTER),
            )

        assertEquals(
            setOf("global", "group"),
            matchingHighlightArticleIds(articles, rules, selectedRuleId = null),
        )
    }

    @Test
    fun selectedHighlightMatchesOnlyThatRule() {
        val articles =
            listOf(
                article(id = "first", title = "first second"),
                article(id = "second", title = "second"),
            )
        val rules =
            listOf(
                rule(id = "first-rule", pattern = "first"),
                rule(id = "second-rule", pattern = "second"),
            )

        assertEquals(
            setOf("first"),
            matchingHighlightArticleIds(articles, rules, selectedRuleId = "first-rule"),
        )
    }

    @Test
    fun highlightedArticleCountsOnlyIncludeMatchingFeedsAndSelectedRule() {
        val articles =
            listOf(
                article(id = "one", feedId = "feed-1", title = "alpha"),
                article(id = "two", feedId = "feed-2", title = "beta"),
                article(id = "three", feedId = "feed-1", title = "alpha", isUnread = false),
            )
        val rules =
            listOf(
                rule(id = "alpha-rule", pattern = "alpha"),
                rule(id = "beta-rule", pattern = "beta"),
            )

        assertEquals(
            mapOf("feed-1" to 1),
            highlightedArticleCounts(articles, rules, "alpha-rule", true),
        )
        assertEquals(
            mapOf("feed-1" to 1, "feed-2" to 1),
            highlightedArticleCounts(articles, rules, null, true),
        )
        assertEquals(
            mapOf("feed-1" to 2, "feed-2" to 1),
            highlightedArticleCounts(articles, rules, null, false),
        )
    }

    @Test
    fun missingSelectedHighlightDoesNotMatchEverything() {
        assertEquals(
            emptySet<String>(),
            matchingHighlightArticleIds(
                articles = listOf(article(title = "match")),
                rules = listOf(rule(id = "rule", pattern = "match")),
                selectedRuleId = "deleted-rule",
            ),
        )
    }

    private fun article(
        id: String = "article-1",
        accountId: Int = 1,
        feedId: String = "feed-1",
        groupId: String = "group-1",
        isUnread: Boolean = true,
        title: String,
    ) =
        ArticleFilterCandidate(
            articleId = id,
            accountId = accountId,
            title = title,
            rawDescription = "",
            feedId = feedId,
            groupId = groupId,
            isUnread = isUnread,
        )

    private fun rule(
        id: String,
        pattern: String,
        accountId: Int = 1,
        scope: RuleScope = RuleScope.GLOBAL,
        scopeId: String = "",
        type: RuleType = RuleType.HIGHLIGHT,
    ) =
        ArticleRule(
            id = id,
            accountId = accountId,
            scope = scope,
            scopeId = scopeId,
            type = type,
            pattern = pattern,
        )
}
