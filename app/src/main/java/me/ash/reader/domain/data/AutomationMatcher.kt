package me.ash.reader.domain.data

import java.net.URI
import me.ash.reader.domain.model.article.AutomationActionType
import me.ash.reader.domain.model.article.AutomationCandidate
import me.ash.reader.domain.model.article.AutomationCondition
import me.ash.reader.domain.model.article.AutomationField
import me.ash.reader.domain.model.article.AutomationOperator
import me.ash.reader.domain.model.article.AutomationRule
import me.ash.reader.domain.model.article.ArticleWithFeed

class AutomationMatcher(private val rules: List<AutomationRule>) {
    private val regexes: Map<String, Regex?> = rules
        .asSequence()
        .flatMap { it.groups.asSequence() }
        .flatMap { it.conditions.asSequence() }
        .filter { it.operator == AutomationOperator.REGEX }
        .associate { condition ->
            condition.id to runCatching {
                if (condition.caseSensitive) Regex(condition.value)
                else Regex(condition.value, RegexOption.IGNORE_CASE)
            }.getOrNull()
        }

    fun matching(candidate: AutomationCandidate): List<AutomationRule> =
        rules.filter { it.enabled && it.appliesTo(candidate) && it.matches(candidate, regexes) }

    fun isFiltered(article: ArticleWithFeed): Boolean =
        matching(article.toAutomationCandidate()).any { AutomationActionType.FILTER in it.actions }

    fun highlightRanges(article: ArticleWithFeed): List<IntRange> {
        val candidate = article.toAutomationCandidate()
        return matching(candidate)
            .filter { AutomationActionType.HIGHLIGHT in it.actions }
            .flatMap { rule ->
                val ranges = rule.groups.flatMap { group ->
                    group.conditions
                        .filter { it.field == AutomationField.TITLE }
                        .flatMap { it.ranges(candidate.title, regexes[it.id]) }
                }
                ranges.ifEmpty {
                    if (candidate.title.isEmpty()) emptyList() else listOf(candidate.title.indices)
                }
            }
            .distinct()
    }
}

private fun AutomationRule.appliesTo(candidate: AutomationCandidate): Boolean =
    accountId == candidate.accountId && when (scope) {
        me.ash.reader.domain.model.article.AutomationScope.GLOBAL -> true
        me.ash.reader.domain.model.article.AutomationScope.GROUP -> candidate.groupId in scopeIds
        me.ash.reader.domain.model.article.AutomationScope.FEED -> candidate.feedId in scopeIds
    }

private fun AutomationRule.matches(candidate: AutomationCandidate, regexes: Map<String, Regex?>): Boolean =
    groups.isNotEmpty() && groups.any { group ->
        group.conditions.isNotEmpty() && group.conditions.all { it.matches(candidate, regexes[it.id]) }
    }

private fun AutomationCondition.matches(candidate: AutomationCandidate, regex: Regex?): Boolean {
    val actual = when (field) {
        AutomationField.FEED_NAME -> candidate.feedName
        AutomationField.GROUP_ID -> candidate.groupId
        AutomationField.FEED_URL -> candidate.feedUrl
        AutomationField.SITE_URL -> runCatching { URI(candidate.feedUrl).host.orEmpty() }.getOrDefault("")
        AutomationField.TITLE -> candidate.title
        AutomationField.DESCRIPTION -> candidate.description
        AutomationField.AUTHOR -> candidate.author.orEmpty()
        AutomationField.ARTICLE_URL -> candidate.articleUrl
        AutomationField.HAS_AUDIO -> (candidate.audioUrl != null).toString()
        AutomationField.HAS_VIDEO -> containsVideo("${candidate.description}\n${candidate.articleUrl}").toString()
        AutomationField.MEDIA_SIZE -> candidate.mediaSize?.toString().orEmpty()
        AutomationField.MEDIA_DURATION -> candidate.mediaDuration?.toString().orEmpty()
        AutomationField.IS_UNREAD -> candidate.isUnread.toString()
        AutomationField.IS_STARRED -> candidate.isStarred.toString()
        AutomationField.IS_READ_LATER -> candidate.isReadLater.toString()
    }
    return compare(actual, regex, field == AutomationField.MEDIA_SIZE || field == AutomationField.MEDIA_DURATION)
}

private fun AutomationCondition.compare(actual: String, regex: Regex?, numeric: Boolean): Boolean = when (operator) {
    AutomationOperator.CONTAINS -> actual.contains(value, ignoreCase = !caseSensitive)
    AutomationOperator.NOT_CONTAINS -> !actual.contains(value, ignoreCase = !caseSensitive)
    AutomationOperator.EQUALS -> if (numeric) numericEquals(actual, value)
        else actual.equals(value, ignoreCase = !caseSensitive)
    AutomationOperator.NOT_EQUALS -> if (numeric) !numericEquals(actual, value)
        else !actual.equals(value, ignoreCase = !caseSensitive)
    AutomationOperator.GREATER_THAN -> actual.toDoubleOrNull()?.let { left -> value.toDoubleOrNull()?.let { left > it } } == true
    AutomationOperator.LESS_THAN -> actual.toDoubleOrNull()?.let { left -> value.toDoubleOrNull()?.let { left < it } } == true
    AutomationOperator.REGEX -> regex?.containsMatchIn(actual) == true
}

private fun numericEquals(left: String, right: String): Boolean =
    left.toDoubleOrNull()?.let { value -> right.toDoubleOrNull()?.let { value == it } } == true

private fun AutomationCondition.ranges(text: String, regex: Regex?): List<IntRange> = when (operator) {
    AutomationOperator.CONTAINS, AutomationOperator.EQUALS -> {
        if (value.isBlank()) emptyList() else buildList {
            var start = 0
            while (start < text.length) {
                val index = text.indexOf(value, start, ignoreCase = !caseSensitive)
                if (index < 0) break
                add(index until index + value.length)
                start = index + value.length
            }
        }
    }
    AutomationOperator.REGEX -> regex?.findAll(text)?.map { it.range }?.toList().orEmpty()
    else -> emptyList()
}

private fun ArticleWithFeed.toAutomationCandidate(): AutomationCandidate =
    AutomationCandidate(
        articleId = article.id,
        accountId = article.accountId,
        title = article.title,
        description = article.rawDescription,
        author = article.author,
        articleUrl = article.link,
        feedId = feed.id,
        feedName = feed.name,
        feedUrl = feed.url,
        groupId = feed.groupId,
        isUnread = article.isUnread,
        isStarred = article.isStarred,
        isReadLater = article.isReadLater,
        audioUrl = article.audioUrl,
        mediaSize = article.audioLength,
        mediaDuration = article.durationSeconds,
    )

private fun containsVideo(content: String): Boolean {
    val normalized = content.lowercase()
    return "<video" in normalized || "youtube.com" in normalized || "youtu.be" in normalized || "vimeo.com" in normalized
}
