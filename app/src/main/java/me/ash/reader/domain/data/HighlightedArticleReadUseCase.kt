package me.ash.reader.domain.data

import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import me.ash.reader.domain.model.article.ArticleFilterCandidate
import me.ash.reader.domain.model.article.ArticleHighlightMatcher
import me.ash.reader.domain.model.article.ArticleRule
import me.ash.reader.domain.model.article.RuleType
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RssService

class HighlightedArticleReadUseCase
@Inject
constructor(
    private val accountService: AccountService,
    private val articleRuleRepository: ArticleRuleRepository,
    private val articleDao: ArticleDao,
    private val rssService: RssService,
) {
    suspend operator fun invoke(filterState: FilterState, before: Date?) {
        val account = accountService.currentAccountFlow.first { it?.id != null } ?: return
        val groupId = filterState.group?.id
        val candidates =
            articleDao.queryUnreadHighlightCandidates(
                accountId = account.id ?: return,
                groupId = groupId,
                feedId = filterState.feed?.id.takeIf { groupId == null },
                before = before ?: Date(Long.MAX_VALUE),
            )
        val articleIds =
            matchingHighlightArticleIds(
                articles = candidates,
                rules = articleRuleRepository.rules.first(),
                selectedRuleId = filterState.highlightRuleId,
            )
        if (articleIds.isEmpty()) return

        rssService.get(account.type.id).run {
            batchMarkAsRead(articleIds = articleIds, isUnread = false)
            syncReadStatus(articleIds = articleIds, isUnread = false)
        }
    }
}

internal fun matchingHighlightArticleIds(
    articles: List<ArticleFilterCandidate>,
    rules: List<ArticleRule>,
    selectedRuleId: String?,
): Set<String> {
    val highlightRules = rules.filter { it.type == RuleType.HIGHLIGHT }
    val selectedRule = selectedRuleId?.let { id -> highlightRules.firstOrNull { it.id == id } }
    if (selectedRuleId != null && selectedRule == null) return emptySet()
    val matcher =
        ArticleHighlightMatcher.from(
            if (selectedRule == null) highlightRules else listOf(selectedRule),
        )

    return articles.mapNotNullTo(mutableSetOf()) { article ->
        article.articleId.takeIf { matcher.matches(article) }
    }
}
