package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.ash.reader.domain.model.article.ArticleNote
import me.ash.reader.domain.model.article.ArticleTagCrossRef
import me.ash.reader.domain.model.article.ArticleTagLabel
import me.ash.reader.domain.model.article.ArticleTagGroup
import me.ash.reader.domain.model.article.SavedSearch

@Dao
interface ArticleCollectionDao {
    @Query("SELECT * FROM article_tag_label WHERE accountId = :accountId ORDER BY name")
    fun observeTags(accountId: Int): Flow<List<ArticleTagLabel>>

    @Query(
        """
        SELECT label.*, COUNT(ref.articleId) AS articleCount
        FROM article_tag_label AS label
        LEFT JOIN article_tag_cross_ref AS ref ON ref.tagId = label.id
        WHERE label.accountId = :accountId
        GROUP BY label.id
        ORDER BY label.name COLLATE NOCASE
        """
    )
    fun observeTagGroups(accountId: Int): Flow<List<ArticleTagGroup>>

    @Query("SELECT * FROM article_tag_label WHERE accountId = :accountId ORDER BY name")
    suspend fun queryTags(accountId: Int): List<ArticleTagLabel>

    @Query("SELECT * FROM article_tag_label WHERE accountId = :accountId AND name = :name LIMIT 1")
    suspend fun queryTagByName(accountId: Int, name: String): ArticleTagLabel?

    @Query(
        """
        SELECT label.* FROM article_tag_label AS label
        INNER JOIN article_tag_cross_ref AS ref ON ref.tagId = label.id
        WHERE ref.articleId = :articleId
        ORDER BY label.name
        """
    )
    fun observeTagsForArticle(articleId: String): Flow<List<ArticleTagLabel>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTag(tag: ArticleTagLabel)

    @Update
    suspend fun updateTag(tag: ArticleTagLabel)

    @Delete
    suspend fun deleteTag(tag: ArticleTagLabel)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToArticle(ref: ArticleTagCrossRef)

    @Query(
        """
        SELECT ref.* FROM article_tag_cross_ref AS ref
        INNER JOIN article_tag_label AS label ON label.id = ref.tagId
        WHERE label.accountId = :accountId
        """
    )
    suspend fun queryTagRefs(accountId: Int): List<ArticleTagCrossRef>

    @Query("DELETE FROM article_tag_cross_ref WHERE articleId = :articleId AND tagId = :tagId")
    suspend fun removeTagFromArticle(articleId: String, tagId: String)

    @Query("DELETE FROM article_tag_cross_ref WHERE tagId = :tagId")
    suspend fun removeTagRefs(tagId: String)

    @Query("SELECT articleId FROM article_tag_cross_ref WHERE tagId = :tagId ORDER BY createdAt DESC")
    suspend fun queryArticleIdsForTag(tagId: String): List<String>

    @Query("SELECT * FROM article_note WHERE articleId = :articleId ORDER BY createdAt")
    fun observeNotes(articleId: String): Flow<List<ArticleNote>>

    @Query("SELECT * FROM article_note WHERE articleId = :articleId ORDER BY createdAt")
    suspend fun queryNotes(articleId: String): List<ArticleNote>

    @Query("SELECT * FROM article_note WHERE accountId = :accountId ORDER BY createdAt")
    suspend fun queryNotesByAccount(accountId: Int): List<ArticleNote>

    @Query("SELECT * FROM article_note WHERE accountId = :accountId ORDER BY updatedAt DESC")
    fun observeNotesByAccount(accountId: Int): Flow<List<ArticleNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: ArticleNote)

    @Delete
    suspend fun deleteNote(note: ArticleNote)

    @Query("SELECT * FROM saved_search WHERE accountId = :accountId ORDER BY createdAt")
    fun observeSavedSearches(accountId: Int): Flow<List<SavedSearch>>

    @Query("SELECT * FROM saved_search WHERE accountId = :accountId ORDER BY createdAt")
    suspend fun querySavedSearches(accountId: Int): List<SavedSearch>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTags(tags: List<ArticleTagLabel>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagRefs(refs: List<ArticleTagCrossRef>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotes(notes: List<ArticleNote>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedSearches(searches: List<SavedSearch>)

    @Transaction
    suspend fun deleteTagWithRefs(tag: ArticleTagLabel) {
        removeTagRefs(tag.id)
        deleteTag(tag)
    }

    @Transaction
    suspend fun importCollections(
        tags: List<ArticleTagLabel>,
        refs: List<ArticleTagCrossRef>,
        notes: List<ArticleNote>,
        searches: List<SavedSearch>,
    ) {
        upsertTags(tags)
        insertTagRefs(refs)
        upsertNotes(notes)
        upsertSavedSearches(searches)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedSearch(search: SavedSearch)

    @Delete
    suspend fun deleteSavedSearch(search: SavedSearch)

    @Query("DELETE FROM article_tag_label WHERE accountId = :accountId")
    suspend fun deleteTagsByAccount(accountId: Int)

    @Query(
        """
        DELETE FROM article_tag_cross_ref
        WHERE tagId IN (SELECT id FROM article_tag_label WHERE accountId = :accountId)
        """
    )
    suspend fun deleteTagRefsByAccount(accountId: Int)

    @Query("DELETE FROM article_note WHERE accountId = :accountId")
    suspend fun deleteNotesByAccount(accountId: Int)

    @Query("DELETE FROM saved_search WHERE accountId = :accountId")
    suspend fun deleteSavedSearchesByAccount(accountId: Int)

    @Transaction
    suspend fun deleteByAccount(accountId: Int) {
        deleteTagRefsByAccount(accountId)
        deleteTagsByAccount(accountId)
        deleteNotesByAccount(accountId)
        deleteSavedSearchesByAccount(accountId)
    }
}
