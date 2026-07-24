package me.ash.reader.domain.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.ash.reader.domain.model.article.ArticleRule
import me.ash.reader.domain.model.article.RuleScope
import me.ash.reader.domain.model.article.RuleType
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys
import me.ash.reader.infrastructure.preference.SettingsProvider

@Singleton
class ArticleRuleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val feedDao: FeedDao,
    private val groupDao: GroupDao,
    private val settingsProvider: SettingsProvider,
) {
    private val key = stringPreferencesKey("article_rules")
    private val json = Json { ignoreUnknownKeys = true }
    private val prettyJson = Json { prettyPrint = true; encodeDefaults = false }

    val rules: Flow<List<ArticleRule>> =
        context.dataStore.data
            .map { preferences ->
                preferences[key]?.let {
                    runCatching { json.decodeFromString<List<ArticleRule>>(it) }.getOrNull()
                } ?: emptyList()
            }
            // The DataStore also contains unrelated settings. Do not rebuild article pagers when
            // one of those settings changes while the serialized rule list stays the same.
            .distinctUntilChanged()

    suspend fun currentRules(accountId: Int): List<ArticleRule> =
        rules.first().filter { it.accountId == accountId }

    suspend fun add(
        accountId: Int,
        scope: RuleScope,
        scopeId: String,
        type: RuleType,
        pattern: String,
        isRegex: Boolean,
        caseSensitive: Boolean,
    ) {
        update { current ->
            val rule = ArticleRule(
                UUID.randomUUID().toString(),
                accountId,
                scope,
                scopeId,
                type,
                pattern.trim(),
                isRegex,
                caseSensitive,
            )
            if (current.any { it.signature == rule.signature }) current else current + rule
        }
    }

    suspend fun delete(id: String) = update { rules -> rules.filterNot { it.id == id } }

    suspend fun edit(
        id: String,
        scope: RuleScope,
        scopeId: String,
        pattern: String,
        isRegex: Boolean,
        caseSensitive: Boolean,
    ) = update { current ->
        val existing = current.firstOrNull { it.id == id } ?: return@update current
        val edited = existing.copy(
            scope = scope,
            scopeId = scopeId,
            pattern = pattern.trim(),
            isRegex = isRegex,
            caseSensitive = caseSensitive,
        )
        if (current.any { it.id != id && it.signature == edited.signature }) {
            current
        } else {
            current.map { if (it.id == id) edited else it }
        }
    }

    suspend fun reorder(orderedIds: List<String>) = update { current ->
        val ids = orderedIds.toSet()
        val replacements = orderedIds.mapNotNull { id -> current.firstOrNull { it.id == id } }.iterator()
        current.map { rule ->
            if (rule.id in ids && replacements.hasNext()) replacements.next() else rule
        }
    }

    suspend fun export(accountId: Int, types: Set<RuleType>): String {
        val feeds = feedDao.queryAll(accountId).associateBy { it.id }
        val groups = groupDao.queryAll(accountId).associateBy { it.id }
        val selectedRules = rules.first().filter { it.accountId == accountId && it.type in types }
        val globalRules = selectedRules.filter { it.scope == RuleScope.GLOBAL }

        val exportedFeeds = selectedRules.filter { it.scope == RuleScope.FEED }
            .groupBy { it.scopeId }
            .mapNotNull { (feedId, scopedRules) ->
                val feed = feeds[feedId] ?: return@mapNotNull null
                ExportedRuleScope(
                    name = feed.name,
                    url = feed.url,
                    filterRules = scopedRules.toDefinitions(RuleType.FILTER),
                    highlightRules = scopedRules.toDefinitions(RuleType.HIGHLIGHT),
                ).takeIf { it.hasRules }
            }.sortedBy { it.name.lowercase() }

        val exportedGroups = selectedRules.filter { it.scope == RuleScope.GROUP }
            .groupBy { it.scopeId }
            .mapNotNull { (groupId, scopedRules) ->
                val group = groups[groupId] ?: return@mapNotNull null
                ExportedRuleScope(
                    name = group.name,
                    filterRules = scopedRules.toDefinitions(RuleType.FILTER),
                    highlightRules = scopedRules.toDefinitions(RuleType.HIGHLIGHT),
                ).takeIf { it.hasRules }
            }.sortedBy { it.name.lowercase() }

        return prettyJson.encodeToString(
            ArticleRuleExportV2(
                version = 4,
                feeds = exportedFeeds,
                groups = exportedGroups,
                globalFilterRules = globalRules.toDefinitions(RuleType.FILTER),
                globalHighlightRules = globalRules.toDefinitions(RuleType.HIGHLIGHT),
            )
        )
    }

    private fun List<ArticleRule>.toDefinitions(type: RuleType): List<ExportedRuleDefinition> =
        filter { it.type == type }
            .map {
                ExportedRuleDefinition(
                    pattern = it.pattern,
                    regex = it.isRegex,
                    caseSensitive = it.caseSensitive,
                )
            }
            .sortedBy { it.pattern.lowercase() }

    private fun parseExport(content: String): List<LegacyExportedArticleRule> {
        val current = runCatching { json.decodeFromString<ArticleRuleExportV2>(content) }.getOrNull()
        if (current != null && current.version in 2..4) {
            return buildList {
                current.feeds.filter { it.hasRules && !it.url.isNullOrBlank() }.forEach { scope ->
                    addAll(scope.toLegacy(RuleScope.FEED, scope.url!!))
                }
                current.groups.filter { it.hasRules }.forEach { scope ->
                    addAll(scope.toLegacy(RuleScope.GROUP, scope.name))
                }
                addAll(current.globalFilterRules.map {
                    LegacyExportedArticleRule(
                        RuleScope.GLOBAL,
                        "",
                        RuleType.FILTER,
                        it.pattern,
                        it.regex,
                        it.caseSensitive,
                    )
                })
                addAll(current.globalHighlightRules.map {
                    LegacyExportedArticleRule(
                        RuleScope.GLOBAL,
                        "",
                        RuleType.HIGHLIGHT,
                        it.pattern,
                        it.regex,
                        it.caseSensitive,
                    )
                })
            }
        }
        return json.decodeFromString<ArticleRuleExportV1>(content).entries
    }

    suspend fun importRules(accountId: Int, content: String, types: Set<RuleType>): Int {
        val exported = parseExport(content)
        val feedsByUrl = feedDao.queryAll(accountId).associateBy { it.url }
        val groupsByName = groupDao.queryAll(accountId).associateBy { it.name }
        val imported = exported.filter { it.type in types }.mapNotNull { entry ->
            val scopeId = when (entry.scope) {
                RuleScope.FEED -> feedsByUrl[entry.scopeKey]?.id
                RuleScope.GROUP -> groupsByName[entry.scopeKey]?.id
                RuleScope.GLOBAL -> ""
            } ?: return@mapNotNull null
            ArticleRule(
                UUID.randomUUID().toString(),
                accountId,
                entry.scope,
                scopeId,
                entry.type,
                entry.pattern,
                entry.isRegex,
                entry.caseSensitive,
            )
        }
        var addedCount = 0
        update { current ->
            val conflictMode =
                settingsProvider.get(FeaturePreferenceKeys.ruleConflictMode) ?: 0
            val mutable = current.toMutableList()
            imported.forEach { candidate ->
                val conflicts = mutable.filter { it.conflictKey == candidate.conflictKey }
                when (conflictMode) {
                    1 -> if (conflicts.isEmpty()) {
                        mutable += candidate
                        addedCount += 1
                    }
                    2 -> {
                        mutable += candidate
                        addedCount += 1
                    }
                    else -> {
                        mutable.removeAll { it.conflictKey == candidate.conflictKey }
                        mutable += candidate
                        addedCount += 1
                    }
                }
            }
            mutable
        }
        return addedCount
    }

    private suspend fun update(transform: (List<ArticleRule>) -> List<ArticleRule>) {
        context.dataStore.edit { preferences ->
            val current = preferences[key]
                ?.let { runCatching { json.decodeFromString<List<ArticleRule>>(it) }.getOrNull() }
                ?: emptyList()
            preferences[key] = json.encodeToString(transform(current))
        }
    }
}

@Serializable
private data class ArticleRuleExportV2(
    val version: Int,
    val feeds: List<ExportedRuleScope> = emptyList(),
    val groups: List<ExportedRuleScope> = emptyList(),
    val globalFilterRules: List<ExportedRuleDefinition> = emptyList(),
    val globalHighlightRules: List<ExportedRuleDefinition> = emptyList(),
)

@Serializable
private data class ExportedRuleScope(
    val name: String,
    val url: String? = null,
    val filterRules: List<ExportedRuleDefinition> = emptyList(),
    val highlightRules: List<ExportedRuleDefinition> = emptyList(),
) {
    val hasRules: Boolean get() = filterRules.isNotEmpty() || highlightRules.isNotEmpty()

    fun toLegacy(scope: RuleScope, scopeKey: String): List<LegacyExportedArticleRule> =
        filterRules.map {
            LegacyExportedArticleRule(
                scope,
                scopeKey,
                RuleType.FILTER,
                it.pattern,
                it.regex,
                it.caseSensitive,
            )
        } + highlightRules.map {
            LegacyExportedArticleRule(
                scope,
                scopeKey,
                RuleType.HIGHLIGHT,
                it.pattern,
                it.regex,
                it.caseSensitive,
            )
        }
}

@Serializable
private data class ExportedRuleDefinition(
    val pattern: String,
    val regex: Boolean = false,
    val caseSensitive: Boolean = false,
)

@Serializable
private data class ArticleRuleExportV1(val version: Int = 1, val entries: List<LegacyExportedArticleRule>)

@Serializable
private data class LegacyExportedArticleRule(
    val scope: RuleScope,
    val scopeKey: String,
    val type: RuleType,
    val pattern: String,
    val isRegex: Boolean,
    val caseSensitive: Boolean = false,
)

private val ArticleRule.signature: List<Any>
    get() = listOf(accountId, scope, scopeId, type, pattern, isRegex, caseSensitive)

private val ArticleRule.conflictKey: List<Any>
    get() = listOf(accountId, scope, scopeId, type, pattern)
