package com.conice.morss.domain.repository

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import com.conice.morss.domain.model.article.Article
import com.conice.morss.domain.model.article.ArticleContentFetchCandidate
import com.conice.morss.domain.model.article.ArticleMeta
import com.conice.morss.domain.model.article.ArticleWithFeed
import com.conice.morss.domain.model.article.ArchivedArticleCleanupCandidate
import com.conice.morss.domain.model.feed.Feed
import com.conice.morss.domain.model.feed.FeedWithArticle
import java.util.Date

@Dao
interface ArticleDao {

    @Query("UPDATE article SET lastOpenedAt = :openedAt WHERE id = :articleId")
    suspend fun updateLastOpenedAt(articleId: String, openedAt: Date)

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM article
            INNER JOIN feed ON feed.id = article.feedId
            WHERE article.accountId = :accountId
                AND feed.accountId = :accountId
                AND (:groupId IS NULL OR feed.groupId = :groupId)
                AND (:feedId IS NULL OR article.feedId = :feedId)
                AND ((:audioOnly = 1 AND audioUrl IS NOT NULL)
                    OR (:audioOnly = 0 AND audioUrl IS NULL))
        )
        """
    )
    suspend fun hasContent(
        accountId: Int,
        groupId: String?,
        feedId: String?,
        audioOnly: Boolean,
    ): Boolean

    @Query("SELECT id FROM article WHERE accountId = :accountId AND id IN (:ids)")
    suspend fun queryExistingIds(accountId: Int, ids: List<String>): List<String>

    @Transaction
    @Query("SELECT * FROM article WHERE accountId = :accountId AND id IN (:articleIds)")
    suspend fun queryWithFeedsByIds(
        accountId: Int,
        articleIds: List<String>,
    ): List<ArticleWithFeed>

    @Query(
        """
        UPDATE article SET isStarred = :isStarred 
        WHERE accountId = :accountId
        AND id in (:ids)
        """
    )
    fun markAsStarredByIdSet(
        accountId: Int,
        ids: Set<String>,
        isStarred: Boolean,
    ): Int

    @Query(
        """
        UPDATE article SET isUnread = :isUnread 
        WHERE accountId = :accountId
        AND id in (:ids)
        """
    )
    fun markAsReadByIdSet(
        accountId: Int,
        ids: Set<String>,
        isUnread: Boolean,
    ): Int

    @Query(
        """
        SELECT count(1)
        FROM article
        WHERE feedId = :feedId
        AND (isStarred = 1 OR isReadLater = 1)
        AND accountId = :accountId
        """
    )
    fun countSavedByFeedId(
        accountId: Int,
        feedId: String,
    ): Int

    @Query(
        """
        SELECT count(1)
        FROM article AS a
        LEFT JOIN feed AS b ON b.id = a.feedId
        LEFT JOIN `group` AS c ON c.id = b.groupId
        WHERE c.id = :groupId
        AND (a.isStarred = 1 OR a.isReadLater = 1)
        AND a.accountId = :accountId
        """
    )
    fun countSavedByGroupId(
        accountId: Int,
        groupId: String,
    ): Int


    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        INNER JOIN article_fts ON article_fts.articleId = article.id
        WHERE accountId = :accountId 
        AND feedId IN (
            SELECT id FROM feed WHERE groupId = :groupId
        )
        AND isUnread = :isUnread
        AND article_fts MATCH :text
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN date END ASC,
            CASE WHEN :sortAscending = 0 THEN date END DESC
        """
    )
    fun searchArticleByGroupIdWhenIsUnread(
        accountId: Int,
        text: String,
        groupId: String,
        isUnread: Boolean,
        sortAscending: Boolean = false
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        INNER JOIN article_fts ON article_fts.articleId = article.id
        WHERE accountId = :accountId 
        AND feedId IN (
            SELECT id FROM feed WHERE groupId = :groupId
        )
        AND isStarred = :isStarred
        AND article_fts MATCH :text
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN date END ASC,
            CASE WHEN :sortAscending = 0 THEN date END DESC
        """
    )
    fun searchArticleByGroupIdWhenIsStarred(
        accountId: Int,
        text: String,
        groupId: String,
        isStarred: Boolean,
        sortAscending: Boolean = false
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        INNER JOIN article_fts ON article_fts.articleId = article.id
        WHERE accountId = :accountId 
        AND feedId IN (
            SELECT id FROM feed WHERE groupId = :groupId
        )
        AND article_fts MATCH :text
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN date END ASC,
            CASE WHEN :sortAscending = 0 THEN date END DESC
        """
    )
    fun searchArticleByGroupIdWhenAll(
        accountId: Int, text: String, groupId: String, sortAscending: Boolean = false
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        INNER JOIN article_fts ON article_fts.articleId = article.id
        WHERE accountId = :accountId 
        AND feedId = :feedId
        AND isUnread = :isUnread
        AND article_fts MATCH :text
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN date END ASC,
            CASE WHEN :sortAscending = 0 THEN date END DESC
        """
    )
    fun searchArticleByFeedIdWhenIsUnread(
        accountId: Int,
        text: String,
        feedId: String,
        isUnread: Boolean,
        sortAscending: Boolean = false
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        INNER JOIN article_fts ON article_fts.articleId = article.id
        WHERE accountId = :accountId 
        AND feedId = :feedId
        AND isStarred = :isStarred
        AND article_fts MATCH :text
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN date END ASC,
            CASE WHEN :sortAscending = 0 THEN date END DESC
        """
    )
    fun searchArticleByFeedIdWhenIsStarred(
        accountId: Int,
        text: String,
        feedId: String,
        isStarred: Boolean,
        sortAscending: Boolean = false
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        INNER JOIN article_fts ON article_fts.articleId = article.id
        WHERE accountId = :accountId 
        AND feedId = :feedId 
        AND article_fts MATCH :text
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN date END ASC,
            CASE WHEN :sortAscending = 0 THEN date END DESC
        """
    )
    fun searchArticleByFeedIdWhenAll(
        accountId: Int, text: String, feedId: String, sortAscending: Boolean = false
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        INNER JOIN article_fts ON article_fts.articleId = article.id
        WHERE accountId = :accountId 
        AND isUnread = :isUnread
        AND article_fts MATCH :text
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN date END ASC,
            CASE WHEN :sortAscending = 0 THEN date END DESC
        """
    )
    fun searchArticleWhenIsUnread(
        accountId: Int, text: String, isUnread: Boolean, sortAscending: Boolean = false
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        INNER JOIN article_fts ON article_fts.articleId = article.id
        WHERE accountId = :accountId 
        AND isStarred = :isStarred
        AND article_fts MATCH :text
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN date END ASC,
            CASE WHEN :sortAscending = 0 THEN date END DESC
        """
    )
    fun searchArticleWhenIsStarred(
        accountId: Int, text: String, isStarred: Boolean, sortAscending: Boolean = false
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        INNER JOIN article_fts ON article_fts.articleId = article.id
        WHERE accountId = :accountId 
        AND article_fts MATCH :text
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN date END ASC,
            CASE WHEN :sortAscending = 0 THEN date END DESC
        """
    )
    fun searchArticleWhenAll(
        accountId: Int, text: String, sortAscending: Boolean = false
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT article.* FROM article
        INNER JOIN article_fts ON article_fts.articleId = article.id
        WHERE article.accountId = :accountId
        AND article.isReadLater = 1
        AND (:groupId IS NULL OR article.feedId IN (
            SELECT id FROM feed
            WHERE accountId = :accountId AND groupId = :groupId
        ))
        AND (:feedId IS NULL OR article.feedId = :feedId)
        AND article_fts MATCH :text
        ORDER BY article.date DESC
        """
    )
    fun searchArticleWhenIsReadLater(
        accountId: Int,
        text: String,
        groupId: String?,
        feedId: String?,
    ): PagingSource<Int, ArticleWithFeed>


    @Query(
        """
        SELECT id, feedId, link FROM article
        WHERE accountId = :accountId
        AND COALESCE(updateAt, date) < :before
        AND isUnread = 0
        AND isStarred = 0
        AND isReadLater = 0
        """
    )
    suspend fun queryArchivedArticleCleanupCandidates(
        accountId: Int,
        before: Date,
    ): List<ArchivedArticleCleanupCandidate>

    @Query("DELETE FROM article WHERE id IN (:articleIds)")
    suspend fun deleteByIds(articleIds: List<String>): Int

    @Transaction
    suspend fun cleanArchivedArticlesBefore(
        accountId: Int,
        before: Date,
    ): List<ArchivedArticleCleanupCandidate> {
        val candidates = queryArchivedArticleCleanupCandidates(accountId, before)
        candidates.chunked(500).forEach { deleteByIds(it.map { candidate -> candidate.id }) }
        return candidates
    }

    @Transaction
    @Query(
        """
        UPDATE article SET isUnread = :isUnread 
        WHERE accountId = :accountId
        AND date < :before
        AND isUnread != :isUnread
        """
    )
    suspend fun markAllAsRead(
        accountId: Int,
        isUnread: Boolean,
        before: Date,
    )

    @Transaction
    @Query(
        """
        UPDATE article SET isUnread = :isUnread 
        WHERE feedId IN (
            SELECT id FROM feed 
            WHERE groupId = :groupId
        )
        AND accountId = :accountId
        AND isUnread != :isUnread
        AND date < :before
        """
    )
    suspend fun markAllAsReadByGroupId(
        accountId: Int,
        groupId: String,
        isUnread: Boolean,
        before: Date,
    )

    @Transaction
    @Query(
        """
        UPDATE article SET isUnread = :isUnread 
        WHERE feedId = :feedId
        AND accountId = :accountId
        AND isUnread != :isUnread
        AND date < :before
        """
    )
    suspend fun markAllAsReadByFeedId(
        accountId: Int,
        feedId: String,
        isUnread: Boolean,
        before: Date,
    )

    @Transaction
    @Query(
        """
        UPDATE article SET isUnread = :isUnread 
        WHERE id = :articleId
        AND accountId = :accountId
        """
    )
    suspend fun markAsReadByArticleId(
        accountId: Int,
        articleId: String,
        isUnread: Boolean,
    )

    @Query(
        """
        UPDATE article SET isStarred = :isStarred 
        WHERE id = :articleId
        AND accountId = :accountId
        """
    )
    suspend fun markAsStarredByArticleId(
        accountId: Int,
        articleId: String,
        isStarred: Boolean,
    )

    @Query(
        """
        UPDATE article SET isReadLater = :isReadLater
        WHERE id = :articleId
        AND accountId = :accountId
        """
    )
    suspend fun markAsReadLaterByArticleId(
        accountId: Int,
        articleId: String,
        isReadLater: Boolean,
    )

    @Query(
        """
        UPDATE article SET isReadLater = 0
        WHERE accountId = :accountId
        AND isReadLater = 1
        AND isUnread = 0
        AND (:feedId IS NULL OR feedId = :feedId)
        AND (:groupId IS NULL OR feedId IN (
            SELECT id FROM feed
            WHERE accountId = :accountId AND groupId = :groupId
        ))
        """
    )
    suspend fun clearReadArticlesFromReadLater(
        accountId: Int,
        groupId: String?,
        feedId: String?,
    ): Int

    @Query(
        """
        DELETE FROM article
        WHERE accountId = :accountId
        AND feedId = :feedId
        AND (isStarred = :includeStarred OR :includeStarred = 1)
        """
    )
    suspend fun deleteByFeedId(accountId: Int, feedId: String, includeStarred: Boolean = false)

    @Query(
        """
        DELETE FROM article
        WHERE id IN (
            SELECT a.id FROM article AS a, feed AS b, `group` AS c
            WHERE a.accountId = :accountId
            AND a.feedId = b.id
            AND b.groupId = c.id
            AND c.id = :groupId
            AND (a.isStarred = :includeStarred OR :includeStarred = 1)
        )
        """
    )
    suspend fun deleteByGroupId(accountId: Int, groupId: String, includeStarred: Boolean = false)

    @Query(
        """
        DELETE FROM article
        WHERE accountId = :accountId
        """
    )
    suspend fun deleteByAccountId(accountId: Int)


    @Transaction
    @Query(
        """
        SELECT feedId, COUNT(*) AS important
        FROM article
        WHERE isUnread = :isUnread
        AND accountId = :accountId
        AND ((:audioOnly = 1 AND audioUrl IS NOT NULL) OR (:audioOnly = 0 AND audioUrl IS NULL))
        GROUP BY feedId
        """
    )
    fun queryImportantCountWhenIsUnread(
        accountId: Int,
        isUnread: Boolean,
        audioOnly: Boolean,
    ): Flow<Map<@MapColumn("feedId") String, @MapColumn("important") Int>>

    @Transaction
    @Query(
        """
        SELECT feedId, COUNT(*) AS important
        FROM article
        WHERE isStarred = :isStarred
        AND accountId = :accountId
        AND ((:audioOnly = 1 AND audioUrl IS NOT NULL) OR (:audioOnly = 0 AND audioUrl IS NULL))
        GROUP BY feedId
        """
    )
    fun queryImportantCountWhenIsStarred(
        accountId: Int,
        isStarred: Boolean,
        audioOnly: Boolean,
    ): Flow<Map<@MapColumn("feedId") String, @MapColumn("important") Int>>

    @Transaction
    @Query(
        """
        SELECT feedId, COUNT(*) AS important
        FROM article
        WHERE isReadLater = 1
        AND accountId = :accountId
        GROUP BY feedId
        """
    )
    fun queryImportantCountWhenIsReadLater(
        accountId: Int,
    ): Flow<Map<@MapColumn("feedId") String, @MapColumn("important") Int>>

    @Transaction
    @Query(
        """
        SELECT feedId, COUNT(*) AS important
        FROM article
        WHERE accountId = :accountId
        AND ((:audioOnly = 1 AND audioUrl IS NOT NULL) OR (:audioOnly = 0 AND audioUrl IS NULL))
        GROUP BY feedId
        """
    )
    fun queryImportantCountWhenIsAll(accountId: Int, audioOnly: Boolean):
            Flow<Map<@MapColumn("feedId") String, @MapColumn("important") Int>>

    @Transaction
    @Query(
        """
        SELECT feedId, COUNT(*) AS important
        FROM article
        WHERE accountId = :accountId
        AND date >= :start
        AND date < :endExclusive
        GROUP BY feedId
        """
    )
    fun queryArticleCountByFeedInDateRange(
        accountId: Int,
        start: Date,
        endExclusive: Date,
    ): Flow<Map<@MapColumn("feedId") String, @MapColumn("important") Int>>

    data class IntakeFeedStats(
        val feedId: String,
        val currentReceived: Int,
        val previousReceived: Int,
        val currentOpened: Int,
        val previousOpened: Int,
        val clearedWithoutOpening: Int,
        val saved: Int,
        val currentPending: Int,
        val unreadBacklog: Int,
    )

    @Query(
        """SELECT feedId,
            SUM(CASE WHEN date >= :currentStart AND date < :currentEndExclusive
                THEN 1 ELSE 0 END) AS currentReceived,
            SUM(CASE WHEN date >= :previousStart AND date < :currentStart
                THEN 1 ELSE 0 END) AS previousReceived,
            SUM(CASE WHEN lastOpenedAt >= :currentStart AND lastOpenedAt < :currentEndExclusive
                THEN 1 ELSE 0 END) AS currentOpened,
            SUM(CASE WHEN lastOpenedAt >= :previousStart AND lastOpenedAt < :currentStart
                THEN 1 ELSE 0 END) AS previousOpened,
            SUM(CASE WHEN date >= :currentStart AND date < :currentEndExclusive
                AND isUnread = 0 AND lastOpenedAt IS NULL
                THEN 1 ELSE 0 END) AS clearedWithoutOpening,
            SUM(CASE WHEN date >= :currentStart AND date < :currentEndExclusive
                AND (isStarred = 1 OR isReadLater = 1)
                THEN 1 ELSE 0 END) AS saved,
            SUM(CASE WHEN date >= :currentStart AND date < :currentEndExclusive
                AND isUnread = 1
                THEN 1 ELSE 0 END) AS currentPending,
            SUM(CASE WHEN isUnread = 1 THEN 1 ELSE 0 END) AS unreadBacklog
        FROM article
        WHERE accountId = :accountId
        GROUP BY feedId"""
    )
    fun queryIntakeFeedStats(
        accountId: Int,
        previousStart: Date,
        currentStart: Date,
        currentEndExclusive: Date,
    ): Flow<List<IntakeFeedStats>>

    @Transaction
    @Query(
        """
        SELECT * FROM article 
        WHERE accountId = :accountId
        ORDER BY date DESC
        """
    )
    fun queryArticleWithFeedWhenIsAll(
        accountId: Int
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE accountId = :accountId
        AND isReadLater = 1
        AND (:groupId IS NULL OR feedId IN (
            SELECT id FROM feed
            WHERE accountId = :accountId AND groupId = :groupId
        ))
        AND (:feedId IS NULL OR feedId = :feedId)
        ORDER BY date DESC
        """
    )
    fun queryArticleWithFeedWhenIsReadLater(
        accountId: Int,
        groupId: String?,
        feedId: String?,
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE isStarred = :isStarred 
        AND accountId = :accountId
        ORDER BY date DESC
        """
    )
    fun queryArticleWithFeedWhenIsStarred(
        accountId: Int, isStarred: Boolean
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT * FROM article 
        WHERE isUnread = :isUnread 
        AND accountId = :accountId
        ORDER BY date DESC
        """
    )
    fun queryArticleWithFeedWhenIsUnread(
        accountId: Int, isUnread: Boolean
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE isUnread = :isUnread
        AND accountId = :accountId
        ORDER BY date ASC
        """
    )
    fun queryArticleWithFeedWhenIsUnreadAscending(
        accountId: Int, isUnread: Boolean
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT a.id, a.date, a.title, a.author, a.rawDescription, 
        a.shortDescription, a.fullContent, a.img, a.link, a.feedId, 
        a.accountId, a.isUnread, a.isStarred, a.isReadLater, a.updateAt, a.lastOpenedAt,
        a.audioUrl, a.audioMimeType, a.audioLength, a.durationSeconds, a.episodeGuid,
        a.seasonNumber, a.episodeNumber, a.transcriptUrl, a.isExplicit,
        a.playbackPositionMs, a.isPlayed, a.downloadedPath
        FROM article AS a
        LEFT JOIN feed AS b ON b.id = a.feedId
        LEFT JOIN `group` AS c ON c.id = b.groupId
        WHERE c.id = :groupId
        AND a.accountId = :accountId
        ORDER BY a.date DESC
        """
    )
    fun queryArticleWithFeedByGroupIdWhenIsAll(
        accountId: Int, groupId: String
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT a.id, a.date, a.title, a.author, a.rawDescription, 
        a.shortDescription, a.fullContent, a.img, a.link, a.feedId, 
        a.accountId, a.isUnread, a.isStarred, a.isReadLater, a.updateAt, a.lastOpenedAt,
        a.audioUrl, a.audioMimeType, a.audioLength, a.durationSeconds, a.episodeGuid,
        a.seasonNumber, a.episodeNumber, a.transcriptUrl, a.isExplicit,
        a.playbackPositionMs, a.isPlayed, a.downloadedPath
        FROM article AS a
        LEFT JOIN feed AS b ON b.id = a.feedId
        LEFT JOIN `group` AS c ON c.id = b.groupId
        WHERE c.id = :groupId
        AND a.isStarred = :isStarred
        AND a.accountId = :accountId
        ORDER BY a.date DESC
        """
    )
    fun queryArticleWithFeedByGroupIdWhenIsStarred(
        accountId: Int, groupId: String, isStarred: Boolean
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT a.id, a.date, a.title, a.author, a.rawDescription, 
        a.shortDescription, a.fullContent, a.img, a.link, a.feedId, 
        a.accountId, a.isUnread, a.isStarred, a.isReadLater, a.updateAt, a.lastOpenedAt,
        a.audioUrl, a.audioMimeType, a.audioLength, a.durationSeconds, a.episodeGuid,
        a.seasonNumber, a.episodeNumber, a.transcriptUrl, a.isExplicit,
        a.playbackPositionMs, a.isPlayed, a.downloadedPath
        FROM article AS a
        LEFT JOIN feed AS b ON b.id = a.feedId
        LEFT JOIN `group` AS c ON c.id = b.groupId
        WHERE c.id = :groupId
        AND a.isUnread = :isUnread
        AND a.accountId = :accountId
        ORDER BY a.date DESC
        """
    )
    fun queryArticleWithFeedByGroupIdWhenIsUnread(
        accountId: Int, groupId: String, isUnread: Boolean
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT a.id, a.date, a.title, a.author, a.rawDescription,
        a.shortDescription, a.fullContent, a.img, a.link, a.feedId,
        a.accountId, a.isUnread, a.isStarred, a.isReadLater, a.updateAt, a.lastOpenedAt,
        a.audioUrl, a.audioMimeType, a.audioLength, a.durationSeconds, a.episodeGuid,
        a.seasonNumber, a.episodeNumber, a.transcriptUrl, a.isExplicit,
        a.playbackPositionMs, a.isPlayed, a.downloadedPath
        FROM article AS a
        LEFT JOIN feed AS b ON b.id = a.feedId
        LEFT JOIN `group` AS c ON c.id = b.groupId
        WHERE c.id = :groupId
        AND a.isUnread = :isUnread
        AND a.accountId = :accountId
        ORDER BY a.date ASC
        """
    )
    fun queryArticleWithFeedByGroupIdWhenIsUnreadAscending(
        accountId: Int, groupId: String, isUnread: Boolean
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE feedId = :feedId
        AND accountId = :accountId
        ORDER BY date DESC
        """
    )
    fun queryArticleWithFeedByFeedIdWhenIsAll(
        accountId: Int, feedId: String
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT * from article 
        WHERE feedId = :feedId 
        AND isStarred = :isStarred
        AND accountId = :accountId
        ORDER BY date DESC
        """
    )
    fun queryArticleWithFeedByFeedIdWhenIsStarred(
        accountId: Int, feedId: String, isStarred: Boolean
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT * FROM article 
        WHERE feedId = :feedId 
        AND isUnread = :isUnread
        AND accountId = :accountId
        ORDER BY date DESC
        """
    )
    fun queryArticleWithFeedByFeedIdWhenIsUnread(
        accountId: Int, feedId: String, isUnread: Boolean
    ): PagingSource<Int, ArticleWithFeed>

    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE feedId = :feedId
        AND isUnread = :isUnread
        AND accountId = :accountId
        ORDER BY date ASC
        """
    )
    fun queryArticleWithFeedByFeedIdWhenIsUnreadAscending(
        accountId: Int, feedId: String, isUnread: Boolean
    ): PagingSource<Int, ArticleWithFeed>


    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT a.id, a.date, a.title, a.author, a.rawDescription, 
        a.shortDescription, a.fullContent, a.img, a.link, a.feedId, 
        a.accountId, a.isUnread, a.isStarred, a.isReadLater, a.updateAt, a.lastOpenedAt,
        a.audioUrl, a.audioMimeType, a.audioLength, a.durationSeconds, a.episodeGuid,
        a.seasonNumber, a.episodeNumber, a.transcriptUrl, a.isExplicit,
        a.playbackPositionMs, a.isPlayed, a.downloadedPath
        FROM article AS a LEFT JOIN feed AS b 
        ON a.feedId = b.id
        WHERE a.feedId = :feedId 
        AND a.accountId = :accountId
        ORDER BY date DESC LIMIT 1
        """
    )
    suspend fun queryLatestByFeedId(accountId: Int, feedId: String): Article?


    @Query(
        """
        SELECT * from article 
        WHERE link in (:linkList)
        AND feedId = :feedId
        AND accountId = :accountId
        """
    )
    suspend fun queryArticlesByLinks(
        linkList: List<String>,
        feedId: String,
        accountId: Int,
    ): List<Article>

    @Query(
        """
        SELECT a.id, a.title, a.link
        FROM article AS a
        LEFT JOIN feed AS f ON a.feedId = f.id
        WHERE f.accountId = :accountId
        AND f.isFullContent = 1
        AND a.isUnread = 1
        ORDER BY a.date DESC
        LIMIT :limit
        """
    )
    suspend fun queryUnreadFullContentArticles(
        accountId: Int,
        limit: Int,
    ): List<ArticleContentFetchCandidate>

    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE id = :id
        """
    )
    suspend fun queryById(id: String): ArticleWithFeed?


    @Transaction
    @Query(
        """
        SELECT id, isUnread, isStarred FROM article
        WHERE accountId = :accountId
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN date END ASC,
            CASE WHEN :sortAscending = 0 THEN date END DESC
        """
    )
    fun queryMetadataAll(
        accountId: Int, sortAscending: Boolean = false
    ): List<ArticleMeta>

    @Transaction
    @Query(
        """
        SELECT id, isUnread, isStarred FROM article
        WHERE accountId = :accountId
        AND isUnread = :isUnread
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN date END ASC,
            CASE WHEN :sortAscending = 0 THEN date END DESC
        """
    )
    fun queryMetadataAll(
        accountId: Int, isUnread: Boolean, sortAscending: Boolean = false
    ): List<ArticleMeta>

    @Transaction
    @Query(
        """
        SELECT id, isUnread, isStarred FROM article
        WHERE accountId = :accountId
        AND date < :before
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN date END ASC,
            CASE WHEN :sortAscending = 0 THEN date END DESC
        """
    )
    fun queryMetadataAll(
        accountId: Int, before: Date, sortAscending: Boolean = false
    ): List<ArticleMeta>

    @Transaction
    @Query(
        """
        SELECT id, isUnread, isStarred FROM article
        WHERE accountId = :accountId
        AND isUnread = :isUnread
        AND date < :before
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN date END ASC,
            CASE WHEN :sortAscending = 0 THEN date END DESC
        """
    )
    fun queryMetadataAll(
        accountId: Int, isUnread: Boolean, before: Date, sortAscending: Boolean = false
    ): List<ArticleMeta>

    @Transaction
    @Query(
        """
        SELECT id, isUnread, isStarred FROM article
        WHERE accountId = :accountId
        AND feedId = :feedId
        AND isUnread = :isUnread
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN date END ASC,
            CASE WHEN :sortAscending = 0 THEN date END DESC
        """
    )
    fun queryMetadataByFeedId(
        accountId: Int, feedId: String, isUnread: Boolean, sortAscending: Boolean = false
    ): List<ArticleMeta>

    @Transaction
    @Query(
        """
        SELECT id, isUnread, isStarred FROM article
        WHERE accountId = :accountId
        AND feedId = :feedId
        AND isUnread = :isUnread
        AND date < :before
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN date END ASC,
            CASE WHEN :sortAscending = 0 THEN date END DESC
        """
    )
    fun queryMetadataByFeedId(
        accountId: Int,
        feedId: String,
        isUnread: Boolean,
        before: Date,
        sortAscending: Boolean = false
    ): List<ArticleMeta>

    @Transaction
    @Query(
        """
        SELECT a.id, a.isUnread, a.isStarred 
        FROM article AS a
        LEFT JOIN feed AS b ON b.id = a.feedId
        LEFT JOIN `group` AS c ON c.id = b.groupId
        WHERE c.id = :groupId
        AND a.accountId = :accountId
        AND a.isUnread = :isUnread
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN a.date END ASC,
            CASE WHEN :sortAscending = 0 THEN a.date END DESC
        """
    )
    fun queryMetadataByGroupIdWhenIsUnread(
        accountId: Int, groupId: String, isUnread: Boolean, sortAscending: Boolean = false
    ): List<ArticleMeta>

    @Transaction
    @Query(
        """
        SELECT a.id, a.isUnread, a.isStarred 
        FROM article AS a
        LEFT JOIN feed AS b ON b.id = a.feedId
        LEFT JOIN `group` AS c ON c.id = b.groupId
        WHERE c.id = :groupId
        AND a.accountId = :accountId
        AND a.isUnread = :isUnread
        AND a.date < :before
        ORDER BY
            CASE WHEN :sortAscending = 1 THEN a.date END ASC,
            CASE WHEN :sortAscending = 0 THEN a.date END DESC
        """
    )
    fun queryMetadataByGroupIdWhenIsUnread(
        accountId: Int,
        groupId: String,
        isUnread: Boolean,
        before: Date,
        sortAscending: Boolean = false
    ): List<ArticleMeta>


    /**
     * query the latest unread articles from account with id, limit count
     */
    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE accountId = :accountId
        AND isUnread = 1
        ORDER BY date desc
        LIMIT :limit
        """
    )
    fun queryLatestUnreadArticles(accountId: Int, limit: Int = 15): Flow<List<ArticleWithFeed>>


    /**
     * query the latest unread articles from feed with id, limit count
     */
    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE feedId = :feedId
        AND isUnread = 1
        ORDER BY date desc
        LIMIT :limit
        """
    )
    fun queryLatestUnreadArticlesFromFeed(feedId: String, limit: Int = 15): Flow<List<ArticleWithFeed>>


    /**
     * query the latest unread articles from group with id, limit count
     */
    @Transaction
    @Query(
        """
        SELECT a.* FROM article AS a
        LEFT JOIN feed AS f ON a.feedId = f.id
        WHERE f.groupId = :groupId
        AND a.isUnread = 1
        ORDER BY a.date DESC
        LIMIT :limit
        """
    )
    fun queryLatestUnreadArticlesFromGroup(groupId: String, limit: Int = 15): Flow<List<ArticleWithFeed>>


    /**
     * query the latest unread articles from account with id, limit count
     */
    @Transaction
    @Query(
        """
        SELECT * FROM article
        WHERE accountId = :accountId
        AND isUnread = 1
        ORDER BY date desc
        LIMIT :limit
        """
    )
    fun queryLatestUnreadArticleFlow(accountId: Int, limit: Int): Flow<List<ArticleWithFeed>>

    @Upsert
    suspend fun insert(vararg article: Article)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOnConflictIgnore(vararg article: Article)

    @Insert
    suspend fun insertList(articles: List<Article>)

    @Delete
    suspend fun delete(vararg article: Article)

    @Update
    suspend fun update(vararg article: Article)

    @Transaction
    suspend fun insertListIfNotExist(articles: List<Article>, feed: Feed): List<Article> {
        if (articles.isEmpty()) return articles

        val existingArticles = queryArticlesByLinks(
            linkList = articles.map { it.link },
            feedId = feed.id,
            accountId = feed.accountId
        ).associateBy { it.link }

        return articles.filterNot { existingArticles.containsKey(it.link) }.also { insertList(it) }
    }

    /**
     * Inserts one refresh batch in a single outer transaction.
     *
     * Room dispatches table invalidations after the batch commits, so article lists and unread
     * counts refresh once per batch instead of once per feed.
     */
    @Transaction
    suspend fun insertFeedArticlesIfNotExist(
        feedsWithArticles: List<FeedWithArticle>,
    ): List<FeedWithArticle> {
        return feedsWithArticles.map { feedWithArticles ->
            val insertedArticles =
                insertListIfNotExist(
                    articles = feedWithArticles.articles,
                    feed = feedWithArticles.feed,
                )
            feedWithArticles.copy(
                articles = insertedArticles,
            )
        }
    }
}
