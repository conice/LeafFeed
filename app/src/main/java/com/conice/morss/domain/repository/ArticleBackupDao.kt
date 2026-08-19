package com.conice.morss.domain.repository

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import com.conice.morss.domain.model.article.Article
import com.conice.morss.domain.model.article.ArticleBackupIdentityRow
import com.conice.morss.domain.model.article.ArticleReadingStateRow
import com.conice.morss.domain.model.article.ArticleReadingStateUpdate

@Dao
interface ArticleBackupDao {
    @Query(
        """
        SELECT article.id AS articleId, feed.url AS feedUrl, article.link AS articleLink
        FROM article INNER JOIN feed ON feed.id = article.feedId
        WHERE article.accountId = :accountId AND feed.accountId = :accountId
            AND article.id IN (:articleIds)
        """
    )
    suspend fun queryBackupIdentities(
        accountId: Int,
        articleIds: List<String>,
    ): List<ArticleBackupIdentityRow>

    @Query(
        """
        SELECT article.id AS articleId, feed.url AS feedUrl, article.link AS articleLink
        FROM article INNER JOIN feed ON feed.id = article.feedId
        WHERE article.accountId = :accountId AND feed.accountId = :accountId
            AND article.link IN (:articleLinks)
        """
    )
    suspend fun queryBackupIdentitiesByLinks(
        accountId: Int,
        articleLinks: List<String>,
    ): List<ArticleBackupIdentityRow>

    @Query(
        """
        SELECT article.id AS articleId, feed.url AS feedUrl, article.link AS articleLink,
            article.isUnread, article.isStarred, article.isReadLater, article.lastOpenedAt,
            article.playbackPositionMs, article.isPlayed
        FROM article INNER JOIN feed ON feed.id = article.feedId
        WHERE article.accountId = :accountId AND feed.accountId = :accountId
            AND (article.isUnread = 0 OR article.isStarred = 1 OR article.isReadLater = 1
                OR article.lastOpenedAt IS NOT NULL OR article.playbackPositionMs > 0
                OR article.isPlayed = 1)
        """
    )
    suspend fun queryReadingStatesForBackup(accountId: Int): List<ArticleReadingStateRow>

    @Update(entity = Article::class)
    suspend fun restoreReadingStates(states: List<ArticleReadingStateUpdate>)
}
