package me.ash.reader.domain.model.article

import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import me.ash.reader.infrastructure.android.AndroidStringsHelper

/**
 * Provide paginated and inserted separator data types for article list view.
 *
 * @see me.ash.reader.ui.page.home.flow.ArticleList
 */
sealed class ArticleFlowItem {

    /**
     * The [Article] item.
     *
     * @see me.ash.reader.ui.page.home.flow.ArticleItem
     */
    class Article(
        val articleWithFeed: ArticleWithFeed,
        val highlightRanges: List<IntRange> = emptyList(),
    ) : ArticleFlowItem()

    /**
     * The feed publication date separator between [Article] items.
     *
     * @see me.ash.reader.ui.page.home.flow.StickyHeader
     */
    class Date(val date: String, val showSpacer: Boolean) : ArticleFlowItem()
}

/**
 * Mapping [ArticleWithFeed] list to [ArticleFlowItem] list.
 */
fun PagingData<ArticleWithFeed>.mapPagingFlowItem(
    androidStringsHelper: AndroidStringsHelper,
    rules: List<ArticleRule> = emptyList(),
): PagingData<ArticleFlowItem> =
    mapPagingArticleItems(
            androidStringsHelper = androidStringsHelper,
            matcher = ArticleHighlightMatcher.from(rules),
        )
        .insertDateSeparators(androidStringsHelper)

/**
 * Maps articles without inserting date separators, allowing callers to filter articles first.
 */
fun PagingData<ArticleWithFeed>.mapPagingArticleItems(
    androidStringsHelper: AndroidStringsHelper,
    rules: List<ArticleRule> = emptyList(),
): PagingData<ArticleFlowItem.Article> =
    mapPagingArticleItems(
        androidStringsHelper = androidStringsHelper,
        matcher = ArticleHighlightMatcher.from(rules),
    )

/** Maps articles using a matcher compiled once for the lifetime of this paging stream. */
internal fun PagingData<ArticleWithFeed>.mapPagingArticleItems(
    androidStringsHelper: AndroidStringsHelper,
    matcher: ArticleHighlightMatcher,
): PagingData<ArticleFlowItem.Article> =
    map {
        ArticleFlowItem.Article(it.apply {
            article.dateString = androidStringsHelper.formatAsString(
                date = article.date,
                onlyHourMinute = true
            )
        }, matcher.ranges(it))
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
