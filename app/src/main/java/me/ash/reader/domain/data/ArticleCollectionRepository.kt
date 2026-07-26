package me.ash.reader.domain.data

import java.security.MessageDigest
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.ash.reader.domain.model.article.ArticleNote
import me.ash.reader.domain.model.article.ArticleBackupIdentityRow
import me.ash.reader.domain.model.article.ArticleReadingStateUpdate
import me.ash.reader.domain.model.article.ArticleTagCrossRef
import me.ash.reader.domain.model.article.ArticleTagLabel
import me.ash.reader.domain.model.article.ArticleTagGroup
import me.ash.reader.domain.model.article.AutomationActionType
import me.ash.reader.domain.model.article.AutomationConditionDraft
import me.ash.reader.domain.model.article.AutomationDraft
import me.ash.reader.domain.model.article.AutomationField
import me.ash.reader.domain.model.article.AutomationOperator
import me.ash.reader.domain.model.article.AutomationScope
import me.ash.reader.domain.model.article.SavedSearch
import me.ash.reader.domain.repository.ArticleCollectionDao
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.ui.ext.dollarLast
import me.ash.reader.ui.ext.getDefaultGroupId
import me.ash.reader.ui.ext.spacerDollar

class ArticleCollectionRepository
@Inject
constructor(
    private val dao: ArticleCollectionDao,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val groupDao: GroupDao,
    private val accountService: AccountService,
    private val automationRepository: AutomationRepository,
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
        val tags = dao.queryTags(accountId)
        val tagRefs = dao.queryTagRefs(accountId)
        val notes = dao.queryNotesByAccount(accountId)
        val savedSearches = dao.querySavedSearches(accountId)
        val readingStateRows = articleDao.queryReadingStatesForBackup(accountId)
        val stateIdentities =
            readingStateRows.map {
                ArticleBackupIdentityRow(it.articleId, it.feedUrl, it.articleLink)
            }
        val referencedArticleIds =
            buildSet {
                tagRefs.forEach { add(it.articleId) }
                notes.forEach { add(it.articleId) }
            }
        val otherIdentities =
            referencedArticleIds
                .minus(stateIdentities.mapTo(mutableSetOf()) { it.articleId })
                .chunked(ARTICLE_QUERY_CHUNK_SIZE)
                .flatMap { articleDao.queryBackupIdentities(accountId, it) }
        val articleIdentities =
            (stateIdentities + otherIdentities)
                .distinctBy { it.articleId }
                .map {
                    BackupArticleIdentity(
                        articleId = it.articleId,
                        sourceArticleId = it.articleId.dollarLast(),
                        feedUrl = it.feedUrl,
                        articleLink = it.articleLink,
                    )
                }
        val groupsById = groupDao.queryAll(accountId).associateBy { it.id }
        val feedsById = feedDao.queryAll(accountId).associateBy { it.id }
        val automations = automationRepository.queryRules(accountId)
        val backup =
            ArticleCollectionBackup(
                tags = tags,
                tagRefs = tagRefs,
                notes = notes,
                savedSearches = savedSearches,
                articles = articleIdentities,
                readingStates =
                    readingStateRows.map {
                        ArticleReadingStateBackup(
                            articleId = it.articleId,
                            isUnread = it.isUnread,
                            isStarred = it.isStarred,
                            isReadLater = it.isReadLater,
                            lastOpenedAt = it.lastOpenedAt?.time,
                            playbackPositionMs = it.playbackPositionMs,
                            isPlayed = it.isPlayed,
                        )
                    },
                savedSearchScopes =
                    savedSearches.map { search ->
                        SavedSearchScopeBackup(
                            searchId = search.id,
                            groupName = search.groupId?.let { groupsById[it]?.name },
                            isDefaultGroup = search.groupId == accountId.getDefaultGroupId(),
                            feedUrl = search.feedId?.let { feedsById[it]?.url },
                        )
                    },
                automations = automations.map { rule ->
                    AutomationBackup(
                        name = rule.name,
                        enabled = rule.enabled,
                        scope = rule.scope.name,
                        groupName = rule.scopeId.takeIf { rule.scope == AutomationScope.GROUP }?.let { groupsById[it]?.name },
                        feedUrl = rule.scopeId.takeIf { rule.scope == AutomationScope.FEED }?.let { feedsById[it]?.url },
                        conditionGroups = rule.groups.map { group ->
                            group.conditions.map { condition ->
                                AutomationConditionBackup(
                                    field = condition.field.name,
                                    operator = condition.operator.name,
                                    value = condition.value,
                                    caseSensitive = condition.caseSensitive,
                                )
                            }
                        },
                        actions = rule.actions.map { it.name },
                    )
                },
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
        val backupArticleIds =
            buildSet {
                backup.tagRefs.forEach { add(it.articleId) }
                backup.notes.forEach { add(it.articleId) }
                backup.readingStates.forEach { add(it.articleId) }
            }
        val candidateArticleIds =
            backupArticleIds +
                backup.articles.mapNotNull { identity ->
                    identity.sourceArticleId?.let { accountId spacerDollar it }
                }
        val currentById =
            candidateArticleIds
                .chunked(ARTICLE_QUERY_CHUNK_SIZE)
                .flatMap { articleDao.queryBackupIdentities(accountId, it) }
                .associateBy { it.articleId }
        val backupIdentitiesById = backup.articles.associateBy { it.articleId }
        val backupLinks = backup.articles.map { it.articleLink }.filter { it.isNotBlank() }.distinct()
        val currentByPortableIdentity =
            backupLinks
                .chunked(ARTICLE_QUERY_CHUNK_SIZE)
                .flatMap { articleDao.queryBackupIdentitiesByLinks(accountId, it) }
                .associateBy { it.portableKey() }
        fun resolveArticleId(sourceArticleId: String): String? =
            currentById[sourceArticleId]?.articleId
                ?: backupIdentitiesById[sourceArticleId]?.let { identity ->
                    identity.sourceArticleId
                        ?.let { accountId spacerDollar it }
                        ?.let(currentById::get)
                        ?.articleId
                        ?: identity
                            .portableKey()
                            .let(currentByPortableIdentity::get)
                            ?.articleId
                }

        val existingTags = dao.queryTags(accountId)
        val existingTagsByName = existingTags.associateBy { it.name }
        val importedToLocalTagId = mutableMapOf<String, String>()
        val tags = backup.tags.map { imported ->
            val existing = existingTagsByName[imported.name]
            val local = if (existing != null) {
                existing.copy(color = imported.color ?: existing.color)
            } else {
                imported.copy(
                    id = portableImportedId("tag", accountId, imported.id),
                    accountId = accountId,
                )
            }
            importedToLocalTagId[imported.id] = local.id
            local
        }.distinctBy { it.id }
        val refs = backup.tagRefs.mapNotNull { ref ->
            val tagId = importedToLocalTagId[ref.tagId] ?: return@mapNotNull null
            val articleId = resolveArticleId(ref.articleId) ?: return@mapNotNull null
            ref.copy(articleId = articleId, tagId = tagId)
        }.distinctBy { it.articleId to it.tagId }
        val existingNoteIds = dao.queryNotesByAccount(accountId).mapTo(mutableSetOf()) { it.id }
        val notes = backup.notes.mapNotNull { imported ->
            val articleId = resolveArticleId(imported.articleId) ?: return@mapNotNull null
            imported.copy(
                id =
                    imported.id.takeIf { it in existingNoteIds }
                        ?: portableImportedId("note", accountId, imported.id),
                articleId = articleId,
                accountId = accountId,
            )
        }
        val groupsById = groupDao.queryAll(accountId).associateBy { it.id }
        val groupsByName = groupsById.values.associateBy { it.name }
        val feedsById = feedDao.queryAll(accountId).associateBy { it.id }
        val feedsByUrl = feedsById.values.associateBy { it.url }
        val existingAutomationsByName = automationRepository.queryRules(accountId).associateBy { it.name }
        val scopesBySearchId = backup.savedSearchScopes.associateBy { it.searchId }
        val existingSearchIds =
            dao.querySavedSearches(accountId).mapTo(mutableSetOf()) { it.id }
        val searches = backup.savedSearches.mapNotNull { imported ->
            val scope = scopesBySearchId[imported.id]
            val groupId =
                when {
                    imported.groupId == null -> null
                    scope?.isDefaultGroup == true ->
                        accountId.getDefaultGroupId().takeIf(groupsById::containsKey)
                    scope?.groupName != null -> groupsByName[scope.groupName]?.id
                    else -> imported.groupId.takeIf(groupsById::containsKey)
                }
            if (imported.groupId != null && groupId == null) return@mapNotNull null
            val feedId =
                when {
                    imported.feedId == null -> null
                    scope?.feedUrl != null -> feedsByUrl[scope.feedUrl]?.id
                    else -> imported.feedId.takeIf(feedsById::containsKey)
                }
            if (imported.feedId != null && feedId == null) return@mapNotNull null
            imported.copy(
                id =
                    imported.id.takeIf { it in existingSearchIds }
                        ?: portableImportedId("search", accountId, imported.id),
                accountId = accountId,
                groupId = groupId,
                feedId = feedId,
            )
        }
        val readingStates =
            backup.readingStates.mapNotNull { state ->
                val articleId = resolveArticleId(state.articleId) ?: return@mapNotNull null
                ArticleReadingStateUpdate(
                    articleId = articleId,
                    isUnread = state.isUnread,
                    isStarred = state.isStarred,
                    isReadLater = state.isReadLater,
                    lastOpenedAt = state.lastOpenedAt?.let(::Date),
                    playbackPositionMs = state.playbackPositionMs.coerceAtLeast(0L),
                    isPlayed = state.isPlayed,
                )
            }.distinctBy { it.articleId }
        val automationDrafts = backup.automations.mapNotNull { imported ->
            val scope = parseAutomationEnum<AutomationScope>(imported.scope) ?: return@mapNotNull null
            val scopeId = when (scope) {
                AutomationScope.GLOBAL -> ""
                AutomationScope.GROUP -> imported.groupName?.let { groupsByName[it]?.id } ?: return@mapNotNull null
                AutomationScope.FEED -> imported.feedUrl?.let { feedsByUrl[it]?.id } ?: return@mapNotNull null
            }
            val groups = imported.conditionGroups.map { group ->
                group.mapNotNull { condition ->
                    val field = parseAutomationEnum<AutomationField>(condition.field)
                    val operator = parseAutomationEnum<AutomationOperator>(condition.operator)
                    if (field == null || operator == null) null else AutomationConditionDraft(
                        field = field,
                        operator = operator,
                        value = condition.value,
                        caseSensitive = condition.caseSensitive,
                    )
                }
            }
            val actions = imported.actions.mapNotNull { parseAutomationEnum<AutomationActionType>(it) }.toSet()
            if (imported.name.isBlank() || groups.isEmpty() || groups.any { it.isEmpty() } || actions.isEmpty()) {
                return@mapNotNull null
            }
            AutomationDraft(
                id = existingAutomationsByName[imported.name]?.id,
                name = imported.name,
                enabled = imported.enabled,
                scope = scope,
                scopeId = scopeId,
                groups = groups,
                actions = actions,
            ).takeIf(automationRepository::isValid)
        }
        dao.importCollections(tags, refs, notes, searches)
        articleDao.restoreReadingStates(readingStates)
        automationDrafts.forEach { automationRepository.save(accountId, it) }
        val skipped =
            (backup.tagRefs.size - refs.size) +
                (backup.notes.size - notes.size) +
                (backup.savedSearches.size - searches.size) +
                (backup.readingStates.size - readingStates.size) +
                (backup.automations.size - automationDrafts.size)
        return ArticleCollectionImportResult(
            tags = tags.size,
            tagRefs = refs.size,
            notes = notes.size,
            savedSearches = searches.size,
            readingStates = readingStates.size,
            automations = automationDrafts.size,
            skipped = skipped,
        )
    }

    private companion object {
        const val MAX_BACKUP_CHARS = 50 * 1024 * 1024
        const val ARTICLE_QUERY_CHUNK_SIZE = 500
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
    val articles: List<BackupArticleIdentity> = emptyList(),
    val readingStates: List<ArticleReadingStateBackup> = emptyList(),
    val savedSearchScopes: List<SavedSearchScopeBackup> = emptyList(),
    val automations: List<AutomationBackup> = emptyList(),
)

@Serializable
data class AutomationBackup(
    val name: String,
    val enabled: Boolean = true,
    val scope: String,
    val groupName: String? = null,
    val feedUrl: String? = null,
    val conditionGroups: List<List<AutomationConditionBackup>> = emptyList(),
    val actions: List<String> = emptyList(),
)

@Serializable
data class AutomationConditionBackup(
    val field: String,
    val operator: String,
    val value: String,
    val caseSensitive: Boolean = false,
)

@Serializable
data class BackupArticleIdentity(
    val articleId: String,
    val feedUrl: String,
    val articleLink: String,
    val sourceArticleId: String? = null,
)

@Serializable
data class ArticleReadingStateBackup(
    val articleId: String,
    val isUnread: Boolean = true,
    val isStarred: Boolean = false,
    val isReadLater: Boolean = false,
    val lastOpenedAt: Long? = null,
    val playbackPositionMs: Long = 0L,
    val isPlayed: Boolean = false,
)

@Serializable
data class SavedSearchScopeBackup(
    val searchId: String,
    val groupName: String? = null,
    val isDefaultGroup: Boolean = false,
    val feedUrl: String? = null,
)

internal const val COLLECTION_BACKUP_FORMAT = "leaffeed.collections"
internal const val COLLECTION_BACKUP_VERSION = 4
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
    require(articles.size <= MAX_COLLECTION_ENTRIES) {
        "Too many article identities in reading data backup"
    }
    require(readingStates.size <= MAX_COLLECTION_ENTRIES) {
        "Too many reading states in reading data backup"
    }
    require(savedSearchScopes.size <= MAX_COLLECTION_ENTRIES) {
        "Too many saved search scopes in reading data backup"
    }
    require(automations.size <= MAX_COLLECTION_ENTRIES) {
        "Too many automations in reading data backup"
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
    require(articles.all { it.articleId.isNotBlank() }) {
        "Reading data backup contains an invalid article identity"
    }
    require(readingStates.all { it.articleId.isNotBlank() && it.playbackPositionMs >= 0L }) {
        "Reading data backup contains an invalid reading state"
    }
    require(savedSearchScopes.all { it.searchId.isNotBlank() }) {
        "Reading data backup contains an invalid saved search scope"
    }
    require(automations.all { automation ->
        automation.name.isNotBlank() && automation.conditionGroups.isNotEmpty() &&
            automation.conditionGroups.all { it.isNotEmpty() } && automation.actions.isNotEmpty()
    }) { "Reading data backup contains an invalid automation" }
}

private inline fun <reified T : Enum<T>> parseAutomationEnum(value: String): T? =
    enumValues<T>().firstOrNull { it.name == value }

private fun BackupArticleIdentity.portableKey(): Pair<String, String> = feedUrl to articleLink

private fun ArticleBackupIdentityRow.portableKey(): Pair<String, String> = feedUrl to articleLink

private fun portableImportedId(kind: String, accountId: Int, sourceId: String): String =
    UUID.nameUUIDFromBytes("LeafFeed:$kind:$accountId:$sourceId".toByteArray(Charsets.UTF_8))
        .toString()

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
    val readingStates: Int = 0,
    val automations: Int = 0,
    val skipped: Int = 0,
)
