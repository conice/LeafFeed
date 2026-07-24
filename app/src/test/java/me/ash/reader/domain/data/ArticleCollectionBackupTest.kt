package me.ash.reader.domain.data

import kotlinx.serialization.json.Json
import me.ash.reader.domain.model.article.ArticleNote
import me.ash.reader.domain.model.article.ArticleTagCrossRef
import me.ash.reader.domain.model.article.ArticleTagLabel
import me.ash.reader.domain.model.article.SavedSearch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleCollectionBackupTest {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun `v2 backup detects changed content`() {
        val backup = ArticleCollectionBackup(
            tags = listOf(ArticleTagLabel("tag", 1, "Saved", null)),
            tagRefs = listOf(ArticleTagCrossRef("article", "tag")),
            notes = listOf(ArticleNote("note", "article", 1, "quote", "note", 1, 1)),
            savedSearches = listOf(SavedSearch("search", 1, "Saved", "kotlin", 0)),
        ).withIntegrityHash(json)

        assertTrue(backup.hasValidIntegrityHash(json))
        assertFalse(backup.copy(notes = emptyList()).hasValidIntegrityHash(json))
    }

    @Test
    fun `v1 backup remains import compatible`() {
        val legacy = ArticleCollectionBackup(
            format = null,
            version = 1,
            integritySha256 = null,
        )
        assertTrue(legacy.version in 1..COLLECTION_BACKUP_VERSION)
        assertTrue(legacy.integritySha256 == null)
    }
}
