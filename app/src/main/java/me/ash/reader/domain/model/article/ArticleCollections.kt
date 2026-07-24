package me.ash.reader.domain.model.article

import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.Index
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "article_tag_label",
    primaryKeys = ["id"],
    indices = [Index(value = ["accountId", "name"], unique = true)],
)
data class ArticleTagLabel(
    val id: String,
    val accountId: Int,
    val name: String,
    val color: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class ArticleTagGroup(
    @Embedded
    val tag: ArticleTagLabel,
    val articleCount: Long,
)

@Serializable
@Entity(
    tableName = "article_tag_cross_ref",
    primaryKeys = ["articleId", "tagId"],
    indices = [Index("tagId")],
)
data class ArticleTagCrossRef(
    val articleId: String,
    val tagId: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "article_note",
    primaryKeys = ["id"],
    indices = [Index("articleId"), Index("accountId")],
)
data class ArticleNote(
    val id: String,
    val articleId: String,
    val accountId: Int,
    val quote: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "saved_search",
    primaryKeys = ["id"],
    indices = [Index("accountId")],
)
data class SavedSearch(
    val id: String,
    val accountId: Int,
    val name: String,
    val query: String,
    val filterIndex: Int,
    val groupId: String? = null,
    val feedId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
