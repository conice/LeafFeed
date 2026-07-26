package me.ash.reader.domain.data

import me.ash.reader.domain.model.article.AutomationActionType
import me.ash.reader.domain.model.article.AutomationCandidate
import me.ash.reader.domain.model.article.AutomationCondition
import me.ash.reader.domain.model.article.AutomationConditionGroup
import me.ash.reader.domain.model.article.AutomationField
import me.ash.reader.domain.model.article.AutomationOperator
import me.ash.reader.domain.model.article.AutomationRule
import me.ash.reader.domain.model.article.AutomationScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationMatcherTest {
    @Test
    fun `conditions use AND while groups use OR`() {
        val rule = rule(
            groups = listOf(
                group(condition(AutomationField.TITLE, AutomationOperator.CONTAINS, "Kotlin")),
                group(
                    condition(AutomationField.AUTHOR, AutomationOperator.EQUALS, "Jane"),
                    condition(AutomationField.IS_UNREAD, AutomationOperator.EQUALS, "true"),
                ),
            )
        )

        assertEquals(listOf(rule), AutomationMatcher(listOf(rule)).matching(candidate(title = "News", author = "Jane")))
        assertTrue(AutomationMatcher(listOf(rule)).matching(candidate(title = "News", author = "John")).isEmpty())
    }

    @Test
    fun `disabled and out of scope rules do not match`() {
        val disabled = rule(enabled = false)
        val otherFeed = rule(scope = AutomationScope.FEED, scopeId = "other")

        assertTrue(AutomationMatcher(listOf(disabled, otherFeed)).matching(candidate()).isEmpty())
    }

    @Test
    fun `invalid regular expressions fail closed`() {
        val rule = rule(groups = listOf(group(condition(AutomationField.TITLE, AutomationOperator.REGEX, "["))))

        assertTrue(AutomationMatcher(listOf(rule)).matching(candidate()).isEmpty())
    }

    private fun rule(
        enabled: Boolean = true,
        scope: AutomationScope = AutomationScope.GLOBAL,
        scopeId: String = "",
        groups: List<AutomationConditionGroup> = listOf(group(condition(AutomationField.TITLE, AutomationOperator.CONTAINS, "Kotlin"))),
    ) = AutomationRule(
        id = "rule",
        accountId = 1,
        name = "Rule",
        enabled = enabled,
        position = 0,
        scope = scope,
        scopeId = scopeId,
        createdAt = 1L,
        groups = groups,
        actions = listOf(AutomationActionType.FILTER),
    )

    private fun group(vararg conditions: AutomationCondition) =
        AutomationConditionGroup("group", conditions.toList())

    private fun condition(field: AutomationField, operator: AutomationOperator, value: String) =
        AutomationCondition("condition", field, operator, value, false)

    private fun candidate(title: String = "Kotlin", author: String? = "John") = AutomationCandidate(
        articleId = "article",
        accountId = 1,
        title = title,
        description = "Description",
        author = author,
        articleUrl = "https://example.com/article",
        feedId = "feed",
        feedName = "Feed",
        feedUrl = "https://example.com/feed.xml",
        groupId = "group",
        isUnread = true,
        isStarred = false,
        isReadLater = false,
        audioUrl = null,
        mediaSize = null,
        mediaDuration = null,
    )
}
