package me.ash.reader.domain.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.article.AutomationActionEntity
import me.ash.reader.domain.model.article.AutomationActionType
import me.ash.reader.domain.model.article.AutomationConditionEntity
import me.ash.reader.domain.model.article.AutomationConditionGroupEntity
import me.ash.reader.domain.model.article.AutomationConditionDraft
import me.ash.reader.domain.model.article.AutomationDraft
import me.ash.reader.domain.model.article.AutomationExecutionEntity
import me.ash.reader.domain.model.article.AutomationExecutionSummary
import me.ash.reader.domain.model.article.AutomationField
import me.ash.reader.domain.model.article.AutomationRule
import me.ash.reader.domain.model.article.AutomationRuleEntity
import me.ash.reader.domain.model.article.AutomationOperator
import me.ash.reader.domain.model.article.AutomationScope
import me.ash.reader.domain.model.article.toDomain
import me.ash.reader.domain.repository.AutomationDao
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.ui.ext.dataStore

@Singleton
class AutomationRepository @Inject constructor(
    private val dao: AutomationDao,
    @ApplicationContext context: Context,
    @ApplicationScope applicationScope: CoroutineScope,
) {
    init {
        applicationScope.launch {
            context.dataStore.edit { preferences ->
                preferences.remove(stringPreferencesKey("article_rules"))
                preferences.remove(booleanPreferencesKey("rules_filter_enabled"))
                preferences.remove(booleanPreferencesKey("rules_highlight_enabled"))
                preferences.remove(booleanPreferencesKey("rules_match_description"))
                preferences.remove(intPreferencesKey("rules_failure_mode"))
                preferences.remove(intPreferencesKey("rules_conflict_mode"))
                preferences.remove(booleanPreferencesKey("reading_show_highlight_matches"))
                preferences.remove(booleanPreferencesKey("notifications_highlights_only"))
                preferences.remove(booleanPreferencesKey("notifications_exclude_filtered"))
            }
        }
    }

    fun observeRules(accountId: Int): Flow<List<AutomationRule>> =
        dao.observeRules(accountId).map { bundles -> bundles.map { it.toDomain() } }

    suspend fun queryRules(accountId: Int): List<AutomationRule> =
        dao.queryRules(accountId).map { it.toDomain() }

    fun observeRecentExecutions(accountId: Int): Flow<List<AutomationExecutionSummary>> =
        dao.observeRecentExecutions(accountId)

    suspend fun save(accountId: Int, draft: AutomationDraft) {
        require(draft.name.isNotBlank()) { "Automation name cannot be blank" }
        require(draft.groups.isNotEmpty() && draft.groups.all { it.isNotEmpty() }) {
            "Automation must contain at least one condition in every group"
        }
        require(draft.scope == AutomationScope.GLOBAL || draft.scopeId.isNotBlank()) {
            "A group or feed scope must have a target"
        }
        require(draft.groups.flatten().all { it.isValid() }) {
            "Automation contains an invalid condition"
        }
        require(draft.actions.isNotEmpty()) { "Automation must contain at least one action" }
        require(
            AutomationActionType.MARK_READ !in draft.actions ||
                AutomationActionType.MARK_UNREAD !in draft.actions
        ) { "Mark read and mark unread cannot be combined" }
        val ruleId = draft.id ?: UUID.randomUUID().toString()
        val existing = dao.queryRule(ruleId)?.toDomain()
        require(existing == null || existing.accountId == accountId) {
            "Automation belongs to another account"
        }
        val position = existing?.position ?: dao.maxPosition(accountId) + 1
        val groups = draft.groups.mapIndexed { index, _ ->
            AutomationConditionGroupEntity(ruleId = ruleId, position = index)
        }
        val conditions = groups.flatMapIndexed { groupIndex, group ->
            draft.groups[groupIndex].mapIndexed { index, condition ->
                AutomationConditionEntity(
                    groupId = group.id,
                    position = index,
                    field = condition.field.name,
                    operator = condition.operator.name,
                    value = condition.value,
                    caseSensitive = condition.caseSensitive,
                )
            }
        }
        val actions = draft.actions.sortedBy { it.ordinal }.mapIndexed { index, type ->
            AutomationActionEntity(ruleId = ruleId, position = index, type = type.name)
        }
        dao.replaceRule(
            rule =
                AutomationRuleEntity(
                    id = ruleId,
                    accountId = accountId,
                    name = draft.name.trim(),
                    enabled = draft.enabled,
                    position = position,
                    scope = draft.scope.name,
                    scopeId = draft.scopeId,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                ),
            groups = groups,
            conditions = conditions,
            actions = actions,
        )
    }

    suspend fun setEnabled(ruleId: String, enabled: Boolean) = dao.setEnabled(ruleId, enabled)

    suspend fun delete(ruleId: String) = dao.deleteRule(ruleId)

    suspend fun record(execution: AutomationExecutionEntity) = dao.upsertExecution(execution)

    suspend fun claim(execution: AutomationExecutionEntity): Boolean =
        dao.claimExecution(
            execution,
            staleBefore = System.currentTimeMillis() - STALE_EXECUTION_MILLIS,
        )

    suspend fun release(articleId: String, ruleId: String, actionType: String) =
        dao.deleteRunningExecution(articleId, ruleId, actionType)

    internal fun isValid(draft: AutomationDraft): Boolean =
        draft.name.isNotBlank() &&
            draft.groups.isNotEmpty() && draft.groups.all { it.isNotEmpty() } &&
            (draft.scope == AutomationScope.GLOBAL || draft.scopeId.isNotBlank()) &&
            draft.groups.flatten().all { it.isValid() } &&
            draft.actions.isNotEmpty() &&
            (AutomationActionType.MARK_READ !in draft.actions ||
                AutomationActionType.MARK_UNREAD !in draft.actions)

    private companion object {
        const val STALE_EXECUTION_MILLIS = 10 * 60 * 1000L
    }
}

private fun AutomationConditionDraft.isValid(): Boolean =
    value.isNotBlank() &&
        operator in field.validOperators() &&
        (!field.isBoolean() || value.toBooleanStrictOrNull() != null) &&
        (!field.isNumeric() || value.toDoubleOrNull() != null) &&
        (operator != AutomationOperator.REGEX || runCatching { Regex(value) }.isSuccess)

private fun AutomationField.isBoolean(): Boolean = this in setOf(
    AutomationField.HAS_AUDIO,
    AutomationField.HAS_VIDEO,
    AutomationField.IS_UNREAD,
    AutomationField.IS_STARRED,
    AutomationField.IS_READ_LATER,
)

private fun AutomationField.isNumeric(): Boolean =
    this == AutomationField.MEDIA_SIZE || this == AutomationField.MEDIA_DURATION

private fun AutomationField.validOperators(): Set<AutomationOperator> = when {
    isBoolean() -> setOf(AutomationOperator.EQUALS, AutomationOperator.NOT_EQUALS)
    isNumeric() -> setOf(
        AutomationOperator.EQUALS,
        AutomationOperator.NOT_EQUALS,
        AutomationOperator.GREATER_THAN,
        AutomationOperator.LESS_THAN,
    )
    else -> setOf(
        AutomationOperator.CONTAINS,
        AutomationOperator.NOT_CONTAINS,
        AutomationOperator.EQUALS,
        AutomationOperator.NOT_EQUALS,
        AutomationOperator.REGEX,
    )
}
