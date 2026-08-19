package com.conice.morss.domain.repository

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.conice.morss.domain.model.article.AutomationCandidate

@Dao
interface AutomationArticleDao {
    @Query(
        """
        SELECT article.id AS articleId, article.accountId AS accountId,
            article.title AS title, article.rawDescription AS description,
            article.author AS author, article.link AS articleUrl, article.feedId AS feedId,
            feed.name AS feedName, feed.url AS feedUrl, feed.groupId AS groupId,
            article.isUnread AS isUnread, article.isStarred AS isStarred,
            article.isReadLater AS isReadLater, article.audioUrl AS audioUrl,
            article.audioLength AS mediaSize, article.durationSeconds AS mediaDuration
        FROM article INNER JOIN feed ON feed.id = article.feedId
        WHERE article.accountId = :accountId AND feed.accountId = :accountId
        ORDER BY article.date DESC LIMIT :limit
        """
    )
    fun queryAutomationCandidates(
        accountId: Int,
        limit: Int = 1000,
    ): Flow<List<AutomationCandidate>>
}
