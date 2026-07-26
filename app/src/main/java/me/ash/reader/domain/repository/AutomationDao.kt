package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.ash.reader.domain.model.article.AutomationActionEntity
import me.ash.reader.domain.model.article.AutomationConditionEntity
import me.ash.reader.domain.model.article.AutomationConditionGroupEntity
import me.ash.reader.domain.model.article.AutomationExecutionEntity
import me.ash.reader.domain.model.article.AutomationExecutionSummary
import me.ash.reader.domain.model.article.AutomationRuleBundle
import me.ash.reader.domain.model.article.AutomationRuleEntity

@Dao
interface AutomationDao {
    @Transaction
    @Query("SELECT * FROM automation_rule WHERE accountId = :accountId ORDER BY position, createdAt")
    fun observeRules(accountId: Int): Flow<List<AutomationRuleBundle>>

    @Transaction
    @Query("SELECT * FROM automation_rule WHERE accountId = :accountId ORDER BY position, createdAt")
    suspend fun queryRules(accountId: Int): List<AutomationRuleBundle>

    @Transaction
    @Query("SELECT * FROM automation_rule WHERE id = :ruleId LIMIT 1")
    suspend fun queryRule(ruleId: String): AutomationRuleBundle?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: AutomationRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<AutomationConditionGroupEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConditions(conditions: List<AutomationConditionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActions(actions: List<AutomationActionEntity>)

    @Query("DELETE FROM automation_condition_group WHERE ruleId = :ruleId")
    suspend fun deleteGroups(ruleId: String)

    @Query("DELETE FROM automation_action WHERE ruleId = :ruleId")
    suspend fun deleteActions(ruleId: String)

    @Query("DELETE FROM automation_execution WHERE ruleId = :ruleId")
    suspend fun deleteExecutions(ruleId: String)

    @Query("DELETE FROM automation_rule WHERE id = :ruleId")
    suspend fun deleteRule(ruleId: String)

    @Query("UPDATE automation_rule SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :ruleId")
    suspend fun setEnabled(ruleId: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COALESCE(MAX(position), -1) FROM automation_rule WHERE accountId = :accountId")
    suspend fun maxPosition(accountId: Int): Int

    @Transaction
    suspend fun replaceRule(
        rule: AutomationRuleEntity,
        groups: List<AutomationConditionGroupEntity>,
        conditions: List<AutomationConditionEntity>,
        actions: List<AutomationActionEntity>,
    ) {
        upsertRule(rule)
        deleteGroups(rule.id)
        deleteActions(rule.id)
        deleteExecutions(rule.id)
        insertGroups(groups)
        insertConditions(conditions)
        insertActions(actions)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExecution(execution: AutomationExecutionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExecution(execution: AutomationExecutionEntity): Long

    @Query(
        """
        DELETE FROM automation_execution
        WHERE articleId = :articleId AND ruleId = :ruleId AND actionType = :actionType
            AND status = 'RUNNING' AND executedAt < :staleBefore
        """
    )
    suspend fun deleteStaleExecution(
        articleId: String,
        ruleId: String,
        actionType: String,
        staleBefore: Long,
    )

    @Query(
        """
        DELETE FROM automation_execution
        WHERE articleId = :articleId AND ruleId = :ruleId AND actionType = :actionType
            AND status = 'RUNNING'
        """
    )
    suspend fun deleteRunningExecution(articleId: String, ruleId: String, actionType: String)

    @Transaction
    suspend fun claimExecution(execution: AutomationExecutionEntity, staleBefore: Long): Boolean {
        deleteStaleExecution(
            execution.articleId,
            execution.ruleId,
            execution.actionType,
            staleBefore,
        )
        return insertExecution(execution) != -1L
    }

    @Query(
        """
        SELECT e.*, r.name AS ruleName
        FROM automation_execution AS e
        INNER JOIN automation_rule AS r ON r.id = e.ruleId
        WHERE r.accountId = :accountId
        ORDER BY e.executedAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentExecutions(accountId: Int, limit: Int = 100): Flow<List<AutomationExecutionSummary>>
}
