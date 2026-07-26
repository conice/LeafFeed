package me.ash.reader.domain.model.article

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

enum class AutomationScope { GLOBAL, GROUP, FEED }

enum class AutomationField {
    FEED_NAME,
    GROUP_ID,
    FEED_URL,
    SITE_URL,
    TITLE,
    DESCRIPTION,
    AUTHOR,
    ARTICLE_URL,
    HAS_AUDIO,
    HAS_VIDEO,
    MEDIA_SIZE,
    MEDIA_DURATION,
    IS_UNREAD,
    IS_STARRED,
    IS_READ_LATER,
}

enum class AutomationOperator { CONTAINS, NOT_CONTAINS, EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN, REGEX }

enum class AutomationActionType {
    FILTER,
    HIGHLIGHT,
    STAR,
    READ_LATER,
    MARK_READ,
    MARK_UNREAD,
    NOTIFY,
    DOWNLOAD_PODCAST,
    FETCH_FULL_CONTENT,
}

enum class AutomationExecutionStatus { RUNNING, SUCCEEDED, FAILED }

@Entity(tableName = "automation_rule", indices = [Index("accountId"), Index(value = ["accountId", "position"])])
data class AutomationRuleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val accountId: Int,
    val name: String,
    val enabled: Boolean = true,
    val position: Int = 0,
    val scope: String = AutomationScope.GLOBAL.name,
    val scopeId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "automation_condition_group",
    foreignKeys = [
        ForeignKey(
            entity = AutomationRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("ruleId"), Index(value = ["ruleId", "position"])],
)
data class AutomationConditionGroupEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val ruleId: String,
    val position: Int = 0,
)

@Entity(
    tableName = "automation_condition",
    foreignKeys = [
        ForeignKey(
            entity = AutomationConditionGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("groupId"), Index(value = ["groupId", "position"])],
)
data class AutomationConditionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val position: Int = 0,
    val field: String,
    val operator: String,
    val value: String,
    val caseSensitive: Boolean = false,
)

@Entity(
    tableName = "automation_action",
    foreignKeys = [
        ForeignKey(
            entity = AutomationRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("ruleId"), Index(value = ["ruleId", "position"])],
)
data class AutomationActionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val ruleId: String,
    val position: Int = 0,
    val type: String,
)

@Entity(
    tableName = "automation_execution",
    primaryKeys = ["articleId", "ruleId", "actionType"],
    foreignKeys = [
        ForeignKey(
            entity = AutomationRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("ruleId"), Index("executedAt")],
)
data class AutomationExecutionEntity(
    val articleId: String,
    val ruleId: String,
    val actionType: String,
    val status: String,
    val executedAt: Long = System.currentTimeMillis(),
    val message: String? = null,
)

data class AutomationGroupWithConditions(
    @androidx.room.Embedded val group: AutomationConditionGroupEntity,
    @Relation(parentColumn = "id", entityColumn = "groupId")
    val conditions: List<AutomationConditionEntity>,
)

data class AutomationRuleBundle(
    @androidx.room.Embedded val rule: AutomationRuleEntity,
    @Relation(parentColumn = "id", entityColumn = "ruleId", entity = AutomationConditionGroupEntity::class)
    val groups: List<AutomationGroupWithConditions>,
    @Relation(parentColumn = "id", entityColumn = "ruleId")
    val actions: List<AutomationActionEntity>,
)

data class AutomationCondition(
    val id: String,
    val field: AutomationField,
    val operator: AutomationOperator,
    val value: String,
    val caseSensitive: Boolean,
)

data class AutomationConditionGroup(val id: String, val conditions: List<AutomationCondition>)

data class AutomationRule(
    val id: String,
    val accountId: Int,
    val name: String,
    val enabled: Boolean,
    val position: Int,
    val scope: AutomationScope,
    val scopeId: String,
    val createdAt: Long,
    val groups: List<AutomationConditionGroup>,
    val actions: List<AutomationActionType>,
)

data class AutomationDraft(
    val id: String? = null,
    val name: String,
    val enabled: Boolean = true,
    val scope: AutomationScope = AutomationScope.GLOBAL,
    val scopeId: String = "",
    val groups: List<List<AutomationConditionDraft>>,
    val actions: Set<AutomationActionType>,
)

data class AutomationConditionDraft(
    val field: AutomationField,
    val operator: AutomationOperator,
    val value: String,
    val caseSensitive: Boolean = false,
)

data class AutomationExecutionRecord(
    @androidx.room.Embedded val execution: AutomationExecutionEntity,
    val ruleName: String,
)

data class AutomationExecutionSummary(
    val execution: AutomationExecutionEntity,
    val ruleName: String,
    val articleTitle: String?,
    val feedName: String?,
)

data class AutomationCandidate(
    val articleId: String,
    val accountId: Int,
    val title: String,
    val description: String,
    val author: String?,
    val articleUrl: String,
    val feedId: String,
    val feedName: String,
    val feedUrl: String,
    val groupId: String,
    val isUnread: Boolean,
    val isStarred: Boolean,
    val isReadLater: Boolean,
    val audioUrl: String?,
    val mediaSize: Long?,
    val mediaDuration: Long?,
)

internal fun AutomationRuleBundle.toDomain(): AutomationRule =
    AutomationRule(
        id = rule.id,
        accountId = rule.accountId,
        name = rule.name,
        enabled = rule.enabled,
        position = rule.position,
        scope = enumValueOrDefault(rule.scope, AutomationScope.GLOBAL),
        scopeId = rule.scopeId,
        createdAt = rule.createdAt,
        groups =
            groups.sortedBy { it.group.position }.map { bundle ->
                AutomationConditionGroup(
                    id = bundle.group.id,
                    conditions =
                        bundle.conditions.sortedBy { it.position }.mapNotNull { condition ->
                            val field = enumValueOrNull<AutomationField>(condition.field)
                            val operator = enumValueOrNull<AutomationOperator>(condition.operator)
                            if (field == null || operator == null) null
                            else AutomationCondition(
                                id = condition.id,
                                field = field,
                                operator = operator,
                                value = condition.value,
                                caseSensitive = condition.caseSensitive,
                            )
                        },
                )
            },
        actions =
            actions.sortedBy { it.position }
                .mapNotNull { enumValueOrNull<AutomationActionType>(it.type) },
    )

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    enumValues<T>().firstOrNull { it.name == value }

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValueOrNull<T>(value) ?: default
