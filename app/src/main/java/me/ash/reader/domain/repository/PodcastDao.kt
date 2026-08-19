package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.ash.reader.domain.model.article.ArticleWithFeed

@Dao
interface PodcastDao {
    @Query("UPDATE article SET playbackPositionMs = :positionMs, isPlayed = CASE WHEN :isPlayed THEN 1 ELSE isPlayed END WHERE id = :articleId")
    suspend fun updatePlayback(articleId: String, positionMs: Long, isPlayed: Boolean)

    @Query("UPDATE article SET isPlayed = :isPlayed WHERE id = :articleId")
    suspend fun updatePlayedStatus(articleId: String, isPlayed: Boolean)

    @Query("UPDATE article SET downloadedPath = :path WHERE id = :articleId")
    suspend fun updateDownloadedPath(articleId: String, path: String?)

    @Query("SELECT id FROM article WHERE downloadedPath = :path LIMIT 1")
    suspend fun queryIdByDownloadedPath(path: String): String?

    @Transaction
    @Query(
        """SELECT * FROM article
        WHERE audioUrl IS NOT NULL AND accountId = :accountId
        AND (:unplayedOnly = 0 OR isPlayed = 0)
        AND (:downloadedOnly = 0 OR downloadedPath IS NOT NULL)
        ORDER BY date DESC"""
    )
    fun observePodcastEpisodes(
        accountId: Int,
        unplayedOnly: Boolean = false,
        downloadedOnly: Boolean = false,
    ): Flow<List<ArticleWithFeed>>
}
