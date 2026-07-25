package me.ash.reader.domain.data

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import me.ash.reader.domain.model.article.ArticleFilterCandidate
import me.ash.reader.domain.model.article.ArticleHighlightMatcher
import me.ash.reader.domain.model.article.ArticleRule
import me.ash.reader.domain.model.article.RuleType
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.infrastructure.di.DefaultDispatcher

class HighlightedArticleCountUseCase @Inject constructor(
    private val articleDao: ArticleDao,
    private val articleRuleRepository: ArticleRuleRepository,
    private val diffMapHolder: DiffMapHolder,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    fun invoke(
        accountId: Int,
        ruleId: String?,
        highlightUnreadOnly: Boolean,
    ): Flow<HighlightedArticleCounts> =
        combine(
            articleDao.queryHighlightCandidates(accountId),
            articleRuleRepository.rules,
            diffMapHolder.diffMapSnapshotFlow,
        ) { candidates, rules, diffs ->
            highlightedArticleCountsByContent(
                candidates,
                rules,
                ruleId,
                highlightUnreadOnly,
                diffs,
            )
        }
            // A synchronization can invalidate the article table again while a large rule set is
            // still being evaluated. Only the newest snapshot is useful to the feed drawer.
            .conflate()
            .flowOn(defaultDispatcher)
}

data class HighlightedArticleCounts(
    val articles: Map<String, Int>,
    val audio: Map<String, Int>,
)

internal fun highlightedArticleCountsByContent(
    articles: List<ArticleFilterCandidate>,
    rules: List<ArticleRule>,
    selectedRuleId: String?,
    highlightUnreadOnly: Boolean,
    diffs: Map<String, Diff> = emptyMap(),
): HighlightedArticleCounts {
    val highlightRules = rules.filter { it.type == RuleType.HIGHLIGHT }
    val selectedRule =
        selectedRuleId?.let { id -> highlightRules.firstOrNull { it.id == id } }
            ?: if (selectedRuleId != null) {
                return HighlightedArticleCounts(emptyMap(), emptyMap())
            } else {
                null
            }
    val matcher =
        ArticleHighlightMatcher.from(
            if (selectedRule == null) highlightRules else listOf(selectedRule),
        )
    val articleCounts = mutableMapOf<String, Int>()
    val audioCounts = mutableMapOf<String, Int>()

    articles.forEach { article ->
        if (highlightUnreadOnly && !(diffs[article.articleId]?.isUnread ?: article.isUnread)) {
            return@forEach
        }
        if (!matcher.matches(article)) return@forEach
        val counts = if (article.audioUrl == null) articleCounts else audioCounts
        counts[article.feedId] = (counts[article.feedId] ?: 0) + 1
    }
    return HighlightedArticleCounts(articleCounts, audioCounts)
}

internal fun highlightedArticleCounts(
    articles: List<ArticleFilterCandidate>,
    rules: List<ArticleRule>,
    selectedRuleId: String?,
    highlightUnreadOnly: Boolean,
    diffs: Map<String, Diff> = emptyMap(),
    contentType: ArticleContentType = ArticleContentType.ARTICLE,
): Map<String, Int> =
    highlightedArticleCountsByContent(
        articles = articles,
        rules = rules,
        selectedRuleId = selectedRuleId,
        highlightUnreadOnly = highlightUnreadOnly,
        diffs = diffs,
    ).let { counts ->
        when (contentType) {
            ArticleContentType.ARTICLE -> counts.articles
            ArticleContentType.AUDIO -> counts.audio
        }
    }
