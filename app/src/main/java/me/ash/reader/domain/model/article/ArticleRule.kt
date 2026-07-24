package me.ash.reader.domain.model.article

import kotlinx.serialization.Serializable

@Serializable
data class ArticleRule(
    val id: String,
    val accountId: Int,
    val scope: RuleScope,
    val scopeId: String,
    val type: RuleType,
    val pattern: String,
    val isRegex: Boolean = false,
    val caseSensitive: Boolean = false,
) {
    fun matches(text: String): Boolean =
        if (isRegex) {
            runCatching {
                val regex = if (caseSensitive) Regex(pattern) else Regex(pattern, RegexOption.IGNORE_CASE)
                regex.containsMatchIn(text)
            }.getOrDefault(false)
        } else {
            text.contains(pattern, ignoreCase = !caseSensitive)
        }

    fun ranges(text: String): List<IntRange> =
        if (isRegex) {
            runCatching {
                val regex = if (caseSensitive) Regex(pattern) else Regex(pattern, RegexOption.IGNORE_CASE)
                regex.findAll(text).map { it.range }.toList()
            }.getOrDefault(emptyList())
        } else {
            if (pattern.isBlank()) emptyList() else buildList {
                var start = 0
                while (start < text.length) {
                    val index = text.indexOf(pattern, start, ignoreCase = !caseSensitive)
                    if (index < 0) break
                    add(index until index + pattern.length)
                    start = index + pattern.length
                }
            }
        }
}

@Serializable enum class RuleScope { FEED, GROUP, GLOBAL }
@Serializable enum class RuleType { FILTER, HIGHLIGHT }

fun List<ArticleRule>.forArticle(article: ArticleWithFeed): List<ArticleRule> = filter {
    it.accountId == article.article.accountId &&
        ((it.scope == RuleScope.FEED && it.scopeId == article.feed.id) ||
            (it.scope == RuleScope.GROUP && it.scopeId == article.feed.groupId) ||
            it.scope == RuleScope.GLOBAL)
}

/**
 * Returns the rules that would affect an article without mutating article state. This is used by
 * diagnostics and the rule preview UI so users can understand why an article was filtered or
 * highlighted.
 */
data class ArticleRuleDiagnostic(
    val rule: ArticleRule,
    val matched: Boolean,
)

fun List<ArticleRule>.diagnose(
    accountId: Int,
    groupId: String,
    feedId: String,
    title: String,
    description: String = "",
): List<ArticleRuleDiagnostic> = asSequence()
    .filter { rule ->
        rule.accountId == accountId && when (rule.scope) {
            RuleScope.GLOBAL -> true
            RuleScope.GROUP -> rule.scopeId == groupId
            RuleScope.FEED -> rule.scopeId == feedId
        }
    }
    .map { rule ->
        ArticleRuleDiagnostic(
            rule = rule,
            matched = rule.matches(if (description.isBlank()) title else "$title\n$description"),
        )
    }
    .toList()

/**
 * Account/scope-indexed highlight rules with regular expressions compiled once.
 *
 * A paging stream can map hundreds of articles. Keeping this work out of the per-article path
 * avoids repeatedly walking every account's rules and repeatedly compiling the same regex.
 */
internal class ArticleHighlightMatcher private constructor(
    private val globalRules: Map<Int, List<CompiledHighlightRule>>,
    private val groupRules: Map<Int, Map<String, List<CompiledHighlightRule>>>,
    private val feedRules: Map<Int, Map<String, List<CompiledHighlightRule>>>,
) {
    fun ranges(article: ArticleWithFeed): List<IntRange> =
        ranges(
            accountId = article.article.accountId,
            groupId = article.feed.groupId,
            feedId = article.feed.id,
            title = article.article.title,
        )

    fun matches(article: ArticleFilterCandidate): Boolean =
        applicableRules(
                accountId = article.accountId,
                groupId = article.groupId,
                feedId = article.feedId,
            )
            .any { it.matches(article.title) }

    private fun ranges(
        accountId: Int,
        groupId: String,
        feedId: String,
        title: String,
    ): List<IntRange> {
        val applicable = applicableRules(accountId, groupId, feedId)
        if (applicable.isEmpty()) return emptyList()

        return applicable.flatMap { it.ranges(title) }.distinct()
    }

    private fun applicableRules(
        accountId: Int,
        groupId: String,
        feedId: String,
    ): List<CompiledHighlightRule> = buildList {
        globalRules[accountId]?.let { addAll(it) }
        groupRules[accountId]?.get(groupId)?.let { addAll(it) }
        feedRules[accountId]?.get(feedId)?.let { addAll(it) }
    }

    companion object {
        fun from(rules: List<ArticleRule>): ArticleHighlightMatcher {
            val globalRules = mutableMapOf<Int, MutableList<CompiledHighlightRule>>()
            val groupRules =
                mutableMapOf<Int, MutableMap<String, MutableList<CompiledHighlightRule>>>()
            val feedRules =
                mutableMapOf<Int, MutableMap<String, MutableList<CompiledHighlightRule>>>()

            fun MutableMap<Int, MutableMap<String, MutableList<CompiledHighlightRule>>>.add(
                rule: ArticleRule,
                compiled: CompiledHighlightRule,
            ) {
                getOrPut(rule.accountId) { mutableMapOf() }
                    .getOrPut(rule.scopeId) { mutableListOf() }
                    .add(compiled)
            }

            rules.forEach { rule ->
                if (rule.type != RuleType.HIGHLIGHT) return@forEach
                val compiled = CompiledHighlightRule.from(rule) ?: return@forEach
                when (rule.scope) {
                    RuleScope.GLOBAL ->
                        globalRules.getOrPut(rule.accountId) { mutableListOf() }.add(compiled)
                    RuleScope.GROUP -> groupRules.add(rule, compiled)
                    RuleScope.FEED -> feedRules.add(rule, compiled)
                }
            }
            return ArticleHighlightMatcher(globalRules, groupRules, feedRules)
        }
    }
}

private class CompiledHighlightRule(
    private val pattern: String,
    private val regex: Regex?,
    private val isRegex: Boolean,
    private val caseSensitive: Boolean,
) {
    fun matches(text: String): Boolean =
        if (isRegex) {
            regex?.containsMatchIn(text) == true
        } else {
            text.contains(pattern, ignoreCase = !caseSensitive)
        }

    fun ranges(text: String): List<IntRange> =
        if (isRegex) {
            regex?.findAll(text)?.map { it.range }?.toList().orEmpty()
        } else if (pattern.isBlank()) {
            emptyList()
        } else {
            buildList {
                var start = 0
                while (start < text.length) {
                    val index = text.indexOf(pattern, start, ignoreCase = !caseSensitive)
                    if (index < 0) break
                    add(index until index + pattern.length)
                    start = index + pattern.length
                }
            }
        }

    companion object {
        fun from(rule: ArticleRule): CompiledHighlightRule? {
            val regex =
                if (rule.isRegex) {
                    runCatching {
                        if (rule.caseSensitive) Regex(rule.pattern)
                        else Regex(rule.pattern, RegexOption.IGNORE_CASE)
                    }.getOrNull() ?: return null
                } else {
                    null
                }
            return CompiledHighlightRule(
                pattern = rule.pattern,
                regex = regex,
                isRegex = rule.isRegex,
                caseSensitive = rule.caseSensitive,
            )
        }
    }
}
