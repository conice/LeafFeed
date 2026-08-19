package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Query
import me.ash.reader.domain.model.article.Article

@Dao
interface ArticleSummaryDao {
    @Query(
        """
        SELECT article.* FROM article
        INNER JOIN feed ON feed.id = article.feedId
        WHERE article.accountId = :accountId AND feed.accountId = :accountId
            AND (:groupId IS NULL OR feed.groupId = :groupId)
            AND (:feedId IS NULL OR article.feedId = :feedId)
            AND (:unreadOnly = 0 OR article.isUnread = 1)
        ORDER BY article.date DESC LIMIT :limit
        """
    )
    suspend fun queryLatestArticlesForSummary(
        accountId: Int,
        groupId: String?,
        feedId: String?,
        unreadOnly: Boolean,
        limit: Int,
    ): List<Article>
}
