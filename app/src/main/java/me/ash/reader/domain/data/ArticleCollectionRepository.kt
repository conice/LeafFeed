package me.ash.reader.domain.data

import java.util.UUID
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.ash.reader.domain.model.article.ArticleNote
import me.ash.reader.domain.model.article.ArticleTagCrossRef
import me.ash.reader.domain.model.article.ArticleTagLabel
import me.ash.reader.domain.model.article.ArticleTagGroup
import me.ash.reader.domain.model.article.SavedSearch
import me.ash.reader.domain.repository.ArticleCollectionDao
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.service.AccountService

class ArticleCollectionRepository
@Inject
constructor(
    private val dao: ArticleCollectionDao,
    private val articleDao: ArticleDao,
    private val accountService: AccountService,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    fun observeTags(): Flow<List<ArticleTagLabel>> =
        dao.observeTags(accountService.getCurrentAccountId())

    fun observeTagGroups(): Flow<List<ArticleTagGroup>> =
        dao.observeTagGroups(accountService.getCurrentAccountId())

    fun observeAllNotes(): Flow<List<ArticleNote>> =
        dao.observeNotesByAccount(accountService.getCurrentAccountId())

    fun observeTagsForArticle(articleId: String): Flow<List<ArticleTagLabel>> =
        dao.observeTagsForArticle(articleId)

    suspend fun createTag(name: String, color: Int? = null): ArticleTagLabel {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Tag name cannot be empty" }
        dao.queryTagByName(accountService.getCurrentAccountId(), normalizedName)?.let { return it }
        val tag =
            ArticleTagLabel(
                id = UUID.randomUUID().toString(),
                accountId = accountService.getCurrentAccountId(),
                name = normalizedName,
                color = color,
            )
        dao.insertTag(tag)
        return tag
    }

    suspend fun addTag(articleId: String, tagId: String) {
        dao.addTagToArticle(ArticleTagCrossRef(articleId = articleId, tagId = tagId))
    }

    suspend fun removeTag(articleId: String, tagId: String) {
        dao.removeTagFromArticle(articleId, tagId)
    }

    suspend fun renameTag(tag: ArticleTagLabel, name: String) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Tag name cannot be empty" }
        val duplicate = dao.queryTagByName(tag.accountId, normalizedName)
        require(duplicate == null || duplicate.id == tag.id) { "A tag with this name already exists" }
        dao.updateTag(tag.copy(name = normalizedName))
    }

    suspend fun updateTag(tag: ArticleTagLabel, name: String, color: Int?) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Tag name cannot be empty" }
        val duplicate = dao.queryTagByName(tag.accountId, normalizedName)
        require(duplicate == null || duplicate.id == tag.id) { "A tag with this name already exists" }
        dao.updateTag(tag.copy(name = normalizedName, color = color))
    }

    suspend fun deleteTag(tag: ArticleTagLabel) = dao.deleteTagWithRefs(tag)

    suspend fun queryArticleIdsForTag(tagId: String): List<String> =
        dao.queryArticleIdsForTag(tagId)

    fun observeNotes(articleId: String): Flow<List<ArticleNote>> = dao.observeNotes(articleId)

    suspend fun queryNotes(articleId: String): List<ArticleNote> = dao.queryNotes(articleId)

    suspend fun deleteNote(note: ArticleNote) = dao.deleteNote(note)

    suspend fun saveNote(
        articleId: String,
        quote: String,
        note: String,
        id: String = UUID.randomUUID().toString(),
        createdAt: Long = System.currentTimeMillis(),
    ): ArticleNote {
        require(quote.isNotBlank() || note.isNotBlank()) { "A note or quote is required" }
        val value =
            ArticleNote(
                id = id,
                articleId = articleId,
                accountId = accountService.getCurrentAccountId(),
                quote = quote.trim(),
                note = note.trim(),
                createdAt = createdAt,
                updatedAt = System.currentTimeMillis(),
            )
        dao.upsertNote(value)
        return value
    }

    fun observeSavedSearches(): Flow<List<SavedSearch>> =
        dao.observeSavedSearches(accountService.getCurrentAccountId())

    suspend fun saveSearch(
        name: String,
        query: String,
        filterIndex: Int,
        groupId: String? = null,
        feedId: String? = null,
        id: String = UUID.randomUUID().toString(),
    ): SavedSearch {
        require(name.isNotBlank()) { "Saved search name cannot be empty" }
        require(query.isNotBlank()) { "Saved search query cannot be empty" }
        val value =
            SavedSearch(
                id = id,
                accountId = accountService.getCurrentAccountId(),
                name = name.trim(),
                query = query.trim(),
                filterIndex = filterIndex,
                groupId = groupId,
                feedId = feedId,
            )
        dao.upsertSavedSearch(value)
        return value
    }

    suspend fun deleteSearch(search: SavedSearch) = dao.deleteSavedSearch(search)

    suspend fun exportBackup(): String {
        val accountId = accountService.getCurrentAccountId()
        val backup =
            ArticleCollectionBackup(
                tags = dao.queryTags(accountId),
                tagRefs = dao.queryTagRefs(accountId),
                notes = dao.queryNotesByAccount(accountId),
                savedSearches = dao.querySavedSearches(accountId),
            )
        return json.encodeToString(backup.withIntegrityHash(json))
    }

    suspend fun importBackup(content: String): ArticleCollectionImportResult {
        require(content.isNotBlank()) { "Reading data backup is empty" }
        require(content.length <= MAX_BACKUP_CHARS) { "Reading data backup is too large" }
        val backup = json.decodeFromString<ArticleCollectionBackup>(content)
        require(backup.version in 1..COLLECTION_BACKUP_VERSION) {
            "Unsupported collection backup version"
        }
        if (backup.version >= 2) {
            require(backup.format == COLLECTION_BACKUP_FORMAT) {
                "Unsupported collection backup format"
            }
            require(backup.hasValidIntegrityHash(json)) {
                "Reading data backup failed its integrity check"
            }
        }
        backup.validate()
        val accountId = accountService.getCurrentAccountId()
        val existingTagsByName = dao.queryTags(accountId).associateBy { it.name }
        val importedToLocalTagId = mutableMapOf<String, String>()
        val tags = backup.tags.map { imported ->
            val existing = existingTagsByName[imported.name]
            val local = if (existing != null) {
                existing.copy(color = imported.color ?: existing.color)
            } else {
                imported.copy(accountId = accountId)
            }
            importedToLocalTagId[imported.id] = local.id
            local
        }.distinctBy { it.id }
        val articleIds = backup.tagRefs.map { it.articleId }.distinct()
        val existingArticleIds = articleIds
            .chunked(500)
            .flatMap { articleDao.queryExistingIds(accountId, it) }
            .toSet()
        val refs = backup.tagRefs.mapNotNull { ref ->
            val tagId = importedToLocalTagId[ref.tagId] ?: return@mapNotNull null
            ref.copy(tagId = tagId).takeIf { it.articleId in existingArticleIds }
        }.distinctBy { it.articleId to it.tagId }
        val noteArticleIds = backup.notes.map { it.articleId }.distinct()
        val existingNoteArticleIds = noteArticleIds
            .chunked(500)
            .flatMap { articleDao.queryExistingIds(accountId, it) }
            .toSet()
        val notes = backup.notes
            .filter { it.articleId in existingNoteArticleIds }
            .map { it.copy(accountId = accountId) }
        val searches = backup.savedSearches.map { it.copy(accountId = accountId) }
        dao.importCollections(tags, refs, notes, searches)
        return ArticleCollectionImportResult(
            tags = tags.size,
            tagRefs = refs.size,
            notes = notes.size,
            savedSearches = searches.size,
        )
    }

    private companion object {
        const val MAX_BACKUP_CHARS = 10 * 1024 * 1024
    }
}

@Serializable
data class ArticleCollectionBackup(
    val format: String? = COLLECTION_BACKUP_FORMAT,
    val version: Int = COLLECTION_BACKUP_VERSION,
    val integritySha256: String? = null,
    val tags: List<ArticleTagLabel> = emptyList(),
    val tagRefs: List<ArticleTagCrossRef> = emptyList(),
    val notes: List<ArticleNote> = emptyList(),
    val savedSearches: List<SavedSearch> = emptyList(),
)

internal const val COLLECTION_BACKUP_FORMAT = "leaffeed.collections"
internal const val COLLECTION_BACKUP_VERSION = 2
private const val MAX_COLLECTION_ENTRIES = 100_000

internal fun ArticleCollectionBackup.withIntegrityHash(json: Json): ArticleCollectionBackup =
    copy(integritySha256 = canonicalSha256(json))

internal fun ArticleCollectionBackup.hasValidIntegrityHash(json: Json): Boolean {
    val expected = integritySha256 ?: return false
    return MessageDigest.isEqual(
        expected.toByteArray(Charsets.US_ASCII),
        canonicalSha256(json).toByteArray(Charsets.US_ASCII),
    )
}

private fun ArticleCollectionBackup.validate() {
    require(tags.size <= MAX_COLLECTION_ENTRIES) { "Too many tags in reading data backup" }
    require(tagRefs.size <= MAX_COLLECTION_ENTRIES) { "Too many tag references in reading data backup" }
    require(notes.size <= MAX_COLLECTION_ENTRIES) { "Too many notes in reading data backup" }
    require(savedSearches.size <= MAX_COLLECTION_ENTRIES) {
        "Too many saved searches in reading data backup"
    }
    require(tags.all { it.id.isNotBlank() && it.name.isNotBlank() }) {
        "Reading data backup contains an invalid tag"
    }
    require(tagRefs.all { it.articleId.isNotBlank() && it.tagId.isNotBlank() }) {
        "Reading data backup contains an invalid tag reference"
    }
    require(notes.all { it.id.isNotBlank() && it.articleId.isNotBlank() }) {
        "Reading data backup contains an invalid note"
    }
    require(savedSearches.all { it.id.isNotBlank() && it.name.isNotBlank() && it.query.isNotBlank() }) {
        "Reading data backup contains an invalid saved search"
    }
}

@OptIn(ExperimentalStdlibApi::class)
private fun ArticleCollectionBackup.canonicalSha256(json: Json): String =
    MessageDigest.getInstance("SHA-256")
        .digest(json.encodeToString(copy(integritySha256 = null)).toByteArray(Charsets.UTF_8))
        .toHexString()

data class ArticleCollectionImportResult(
    val tags: Int,
    val tagRefs: Int,
    val notes: Int,
    val savedSearches: Int,
)
