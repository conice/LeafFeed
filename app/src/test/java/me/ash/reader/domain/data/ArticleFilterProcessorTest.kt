package me.ash.reader.domain.data

import me.ash.reader.domain.model.article.ArticleFilterCandidate
import me.ash.reader.domain.model.article.ArticleRule
import me.ash.reader.domain.model.article.RuleScope
import me.ash.reader.domain.model.article.RuleType
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleFilterProcessorTest {
    @Test
    fun matchesOnlyRulesInArticleScopeAndAccount() {
        val article = article(accountId = 1, feedId = "feed-1", groupId = "group-1")
        val rules =
            listOf(
                rule(accountId = 2, scope = RuleScope.GLOBAL, scopeId = "", pattern = "blocked"),
                rule(accountId = 1, scope = RuleScope.FEED, scopeId = "feed-2", pattern = "blocked"),
                rule(accountId = 1, scope = RuleScope.GROUP, scopeId = "group-1", pattern = "blocked"),
            )

        assertEquals(emptySet<String>(), matchingFilterArticleIds(listOf(article), rules.take(2)))
        assertEquals(setOf(article.articleId), matchingFilterArticleIds(listOf(article), rules))
    }

    @Test
    fun matchesTitleAndDescriptionIgnoringCase() {
        val titleMatch = article(id = "title", title = "BLOCKED title", description = "Safe text")
        val descriptionMatch =
            article(id = "description", title = "Allowed", description = "contains blocked text")
        val noMatch = article(id = "allowed", title = "Allowed", description = "Safe text")
        val rule = rule(pattern = "blocked")

        assertEquals(
            setOf("title", "description"),
            matchingFilterArticleIds(listOf(titleMatch, descriptionMatch, noMatch), listOf(rule)),
        )
    }

    @Test
    fun ignoresDescriptionWhenDescriptionMatchingIsDisabled() {
        val descriptionOnly =
            article(title = "Allowed", description = "contains blocked text")
        val matcher = ArticleFilterMatcher.from(listOf(rule(pattern = "blocked")), false)

        assertEquals(false, matcher.includeDescription)
        assertEquals(emptySet<String>(), matcher.matchingArticleIds(listOf(descriptionOnly)))
    }

    @Test
    fun regexCanMatchAcrossTitleAndDescription() {
        val article = article(title = "The end", description = "Beginning of description")

        assertEquals(
            setOf(article.articleId),
            matchingFilterArticleIds(
                listOf(article),
                listOf(rule(pattern = "end\\nBeginning", isRegex = true)),
            ),
        )
    }

    @Test
    fun caseSensitiveRulesRequireExactCase() {
        val lowerCase = article(id = "lower", title = "blocked", description = "Safe text")
        val upperCase = article(id = "upper", title = "BLOCKED", description = "Safe text")

        assertEquals(
            setOf("lower"),
            matchingFilterArticleIds(
                listOf(lowerCase, upperCase),
                listOf(rule(pattern = "blocked", caseSensitive = true)),
            ),
        )
        assertEquals(
            setOf("upper"),
            matchingFilterArticleIds(
                listOf(lowerCase, upperCase),
                listOf(rule(pattern = "BLOCKED", isRegex = true, caseSensitive = true)),
            ),
        )
    }

    @Test
    fun ignoresInvalidRegexAndHighlightRules() {
        val article = article()
        val rules =
            listOf(
                rule(pattern = "[", isRegex = true),
                rule(pattern = "blocked", type = RuleType.HIGHLIGHT),
            )

        assertEquals(emptySet<String>(), matchingFilterArticleIds(listOf(article), rules))
    }

    private fun article(
        id: String = "article-1",
        accountId: Int = 1,
        feedId: String = "feed-1",
        groupId: String = "group-1",
        title: String = "Title",
        description: String = "blocked description",
    ): ArticleFilterCandidate =
        ArticleFilterCandidate(
            articleId = id,
            accountId = accountId,
            title = title,
            rawDescription = description,
            feedId = feedId,
            groupId = groupId,
        )

    private fun rule(
        accountId: Int = 1,
        scope: RuleScope = RuleScope.GLOBAL,
        scopeId: String = "",
        pattern: String,
        isRegex: Boolean = false,
        caseSensitive: Boolean = false,
        type: RuleType = RuleType.FILTER,
    ) =
        ArticleRule(
            id = "$accountId-$scope-$scopeId-$pattern",
            accountId = accountId,
            scope = scope,
            scopeId = scopeId,
            type = type,
            pattern = pattern,
            isRegex = isRegex,
            caseSensitive = caseSensitive,
        )
}
