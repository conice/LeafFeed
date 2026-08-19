package com.conice.morss.application.data

import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import com.conice.morss.infrastructure.android.AndroidStringsHelper
import com.conice.morss.domain.model.article.ArticleWithFeed

/**
 * Provide paginated and inserted separator data types for article list view.
 *
 * @see com.conice.morss.ui.page.home.flow.ArticleList
 */
sealed class ArticleFlowItem {

    /**
     * The [Article] item.
     *
     * @see com.conice.morss.ui.page.home.flow.ArticleItem
     */
    class Article(
        val articleWithFeed: ArticleWithFeed,
        val highlightRanges: List<IntRange> = emptyList(),
    ) : ArticleFlowItem()

    /**
     * The feed publication date separator between [Article] items.
     *
     * @see com.conice.morss.ui.page.home.flow.StickyHeader
     */
    class Date(val date: String, val showSpacer: Boolean) : ArticleFlowItem()
}

/**
 * Mapping [ArticleWithFeed] list to [ArticleFlowItem] list.
 */
fun PagingData<ArticleWithFeed>.mapPagingFlowItem(
    androidStringsHelper: AndroidStringsHelper,
    matcher: AutomationMatcher = AutomationMatcher(emptyList()),
): PagingData<ArticleFlowItem> =
    mapPagingArticleItems(
        androidStringsHelper = androidStringsHelper,
        matcher = matcher,
    )
        .insertDateSeparators(androidStringsHelper)

/**
 * Maps articles without inserting date separators, allowing callers to filter articles first.
 */
fun PagingData<ArticleWithFeed>.mapPagingArticleItems(
    androidStringsHelper: AndroidStringsHelper,
    matcher: AutomationMatcher = AutomationMatcher(emptyList()),
): PagingData<ArticleFlowItem.Article> =
    map {
        ArticleFlowItem.Article(
            articleWithFeed = it.apply {
                article.dateString = androidStringsHelper.formatAsString(
                    date = article.date,
                    onlyHourMinute = true,
                )
            },
            highlightRanges = matcher.highlightRanges(it),
        )
    }

/** Inserts date separators for the articles that remain in this paging stream. */
fun PagingData<ArticleFlowItem.Article>.insertDateSeparators(
    androidStringsHelper: AndroidStringsHelper,
): PagingData<ArticleFlowItem> =
    insertSeparators { before, after ->
        val beforeDate =
            androidStringsHelper.formatAsString(before?.articleWithFeed?.article?.date)
        val afterDate =
            androidStringsHelper.formatAsString(after?.articleWithFeed?.article?.date)
        if (beforeDate != afterDate) {
            afterDate?.let { ArticleFlowItem.Date(it, beforeDate != null) }
        } else {
            null
        }
    }
