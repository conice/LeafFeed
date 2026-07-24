package me.ash.reader.domain.data

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import me.ash.reader.domain.model.article.ArticleFilterCandidate
import me.ash.reader.domain.model.article.ArticleHighlightMatcher
import me.ash.reader.domain.model.article.ArticleRule
import me.ash.reader.domain.model.article.RuleType
import me.ash.reader.domain.repository.ArticleDao

class HighlightedArticleCountUseCase @Inject constructor(
    private val articleDao: ArticleDao,
    private val articleRuleRepository: ArticleRuleRepository,
    private val diffMapHolder: DiffMapHolder,
) {
    fun invoke(
        accountId: Int,
        ruleId: String?,
        highlightUnreadOnly: Boolean,
        contentType: ArticleContentType,
    ): Flow<Map<String, Int>> =
        combine(
            articleDao.queryHighlightCandidates(accountId),
            articleRuleRepository.rules,
            diffMapHolder.diffMapSnapshotFlow,
        ) { candidates, rules, diffs ->
            highlightedArticleCounts(
                candidates,
                rules,
                ruleId,
                highlightUnreadOnly,
                diffs,
                contentType,
            )
        }
}

internal fun highlightedArticleCounts(
    articles: List<ArticleFilterCandidate>,
    rules: List<ArticleRule>,
    selectedRuleId: String?,
    highlightUnreadOnly: Boolean,
    diffs: Map<String, Diff> = emptyMap(),
    contentType: ArticleContentType = ArticleContentType.ARTICLE,
): Map<String, Int> {
    val highlightRules = rules.filter { it.type == RuleType.HIGHLIGHT }
    val selectedRule =
        selectedRuleId?.let { id -> highlightRules.firstOrNull { it.id == id } }
            ?: if (selectedRuleId != null) return emptyMap() else null
    val matcher =
        ArticleHighlightMatcher.from(
            if (selectedRule == null) highlightRules else listOf(selectedRule),
        )
    return articles.asSequence()
        .filter { contentType.includes(it.audioUrl) }
        .filter { !highlightUnreadOnly || (diffs[it.articleId]?.isUnread ?: it.isUnread) }
        .filter { matcher.matches(it) }
        .groupingBy { it.feedId }
        .eachCount()
}
