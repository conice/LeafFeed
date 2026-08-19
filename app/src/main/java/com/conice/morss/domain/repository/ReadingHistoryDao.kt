package com.conice.morss.domain.repository

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.conice.morss.domain.model.article.ArticleWithFeed

@Dao
interface ReadingHistoryDao {
    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        INNER JOIN feed ON feed.id = article.feedId
        WHERE article.accountId = :accountId
            AND feed.accountId = :accountId
            AND article.lastOpenedAt IS NOT NULL
            AND (:groupId IS NULL OR feed.groupId = :groupId)
            AND (:feedId IS NULL OR article.feedId = :feedId)
            AND ((:audioOnly = 1 AND article.audioUrl IS NOT NULL)
                OR (:audioOnly = 0 AND article.audioUrl IS NULL))
        ORDER BY article.lastOpenedAt DESC
        """
    )
    fun queryReadingHistory(
        accountId: Int,
        groupId: String?,
        feedId: String?,
        audioOnly: Boolean,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        INNER JOIN article_fts ON article_fts.articleId = article.id
        INNER JOIN feed ON feed.id = article.feedId
        WHERE article.accountId = :accountId
            AND feed.accountId = :accountId
            AND article.lastOpenedAt IS NOT NULL
            AND (:groupId IS NULL OR feed.groupId = :groupId)
            AND (:feedId IS NULL OR article.feedId = :feedId)
            AND ((:audioOnly = 1 AND article.audioUrl IS NOT NULL)
                OR (:audioOnly = 0 AND article.audioUrl IS NULL))
            AND article_fts MATCH :text
        ORDER BY article.lastOpenedAt DESC
        """
    )
    fun searchReadingHistory(
        accountId: Int,
        text: String,
        groupId: String?,
        feedId: String?,
        audioOnly: Boolean,
    ): PagingSource<Int, ArticleWithFeed>
}
