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
    fun `v4 backup detects changed reading data`() {
        val backup = ArticleCollectionBackup(
            tags = listOf(ArticleTagLabel("tag", 1, "Saved", null)),
            tagRefs = listOf(ArticleTagCrossRef("article", "tag")),
            notes = listOf(ArticleNote("note", "article", 1, "quote", "note", 1, 1)),
            savedSearches = listOf(SavedSearch("search", 1, "Saved", "kotlin", 0)),
            articles =
                listOf(
                    BackupArticleIdentity(
                        articleId = "article",
                        feedUrl = "https://example.com/feed.xml",
                        articleLink = "https://example.com/article",
                        sourceArticleId = "source-article",
                    )
                ),
            readingStates =
                listOf(
                    ArticleReadingStateBackup(
                        articleId = "article",
                        isUnread = false,
                        isStarred = true,
                        isReadLater = true,
                        lastOpenedAt = 100L,
                        playbackPositionMs = 200L,
                        isPlayed = true,
                    )
                ),
            savedSearchScopes =
                listOf(
                    SavedSearchScopeBackup(
                        searchId = "search",
                        feedUrl = "https://example.com/feed.xml",
                    )
                ),
            automations = listOf(
                AutomationBackup(
                    name = "Filter promotions",
                    scope = "GLOBAL",
                    conditionGroups = listOf(
                        listOf(AutomationConditionBackup("TITLE", "CONTAINS", "Promotion"))
                    ),
                    actions = listOf("FILTER"),
                )
            ),
        ).withIntegrityHash(json)

        assertTrue(backup.hasValidIntegrityHash(json))
        assertFalse(
            backup
                .copy(
                    readingStates =
                        backup.readingStates.map { it.copy(playbackPositionMs = 201L) }
                )
                .hasValidIntegrityHash(json)
        )
        assertFalse(
            backup.copy(automations = backup.automations.map { it.copy(enabled = false) })
                .hasValidIntegrityHash(json)
        )
    }

    @Test
    fun `v2 backup remains integrity compatible`() {
        val backup =
            ArticleCollectionBackup(
                    version = 2,
                    notes = listOf(ArticleNote("note", "article", 1, "quote", "note", 1, 1)),
                )
                .withIntegrityHash(json)

        assertTrue(backup.version in 1..COLLECTION_BACKUP_VERSION)
        assertTrue(backup.hasValidIntegrityHash(json))
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
