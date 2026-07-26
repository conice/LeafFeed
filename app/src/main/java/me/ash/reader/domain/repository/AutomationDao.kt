package me.ash.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import me.ash.reader.domain.model.article.AutomationActionClaimEntity
import me.ash.reader.domain.model.article.AutomationActionEntity
import me.ash.reader.domain.model.article.AutomationConditionEntity
import me.ash.reader.domain.model.article.AutomationConditionGroupEntity
import me.ash.reader.domain.model.article.AutomationExecutionEntity
import me.ash.reader.domain.model.article.AutomationExecutionRecord
import me.ash.reader.domain.model.article.AutomationExecutionStatus
import me.ash.reader.domain.model.article.AutomationRuleBundle
import me.ash.reader.domain.model.article.AutomationRuleEntity
import me.ash.reader.domain.model.article.AutomationScopeTargetEntity

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

    @Query("SELECT EXISTS(SELECT 1 FROM automation_rule WHERE id = :ruleId)")
    suspend fun ruleExists(ruleId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRule(rule: AutomationRuleEntity): Long

    @Update
    suspend fun updateRule(rule: AutomationRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<AutomationConditionGroupEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConditions(conditions: List<AutomationConditionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActions(actions: List<AutomationActionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScopeTargets(targets: List<AutomationScopeTargetEntity>)

    @Query("DELETE FROM automation_condition_group WHERE ruleId = :ruleId")
    suspend fun deleteGroups(ruleId: String)

    @Query("DELETE FROM automation_action WHERE ruleId = :ruleId")
    suspend fun deleteActions(ruleId: String)

    @Query("DELETE FROM automation_scope_target WHERE ruleId = :ruleId")
    suspend fun deleteScopeTargets(ruleId: String)

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
        targets: List<AutomationScopeTargetEntity>,
    ) {
        if (insertRule(rule) == -1L) updateRule(rule)
        deleteGroups(rule.id)
        deleteActions(rule.id)
        deleteScopeTargets(rule.id)
        insertGroups(groups)
        insertConditions(conditions)
        insertActions(actions)
        insertScopeTargets(targets)
    }

    @Query(
        """SELECT * FROM automation_action_claim
            WHERE articleId = :articleId AND ruleId = :ruleId AND actionType = :actionType"""
    )
    suspend fun queryClaim(
        articleId: String,
        ruleId: String,
        actionType: String,
    ): AutomationActionClaimEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClaim(claim: AutomationActionClaimEntity)

    @Insert
    suspend fun insertExecution(execution: AutomationExecutionEntity)

    @Query(
        """
        UPDATE automation_execution
        SET status = 'INTERRUPTED', completedAt = :completedAt,
            message = 'Previous run was interrupted'
        WHERE articleId = :articleId AND ruleId = :ruleId AND actionType = :actionType
            AND status = 'RUNNING'
        """
    )
    suspend fun interruptRunningExecutions(
        articleId: String,
        ruleId: String,
        actionType: String,
        completedAt: Long,
    )

    @Query(
        """
        DELETE FROM automation_action_claim
        WHERE articleId = :articleId AND ruleId = :ruleId AND actionType = :actionType
            AND status = 'RUNNING'
        """
    )
    suspend fun deleteRunningClaim(articleId: String, ruleId: String, actionType: String)

    @Query(
        """UPDATE automation_execution SET status = :status, completedAt = :completedAt,
            message = :message WHERE id = :executionId"""
    )
    suspend fun updateExecution(
        executionId: String,
        status: String,
        completedAt: Long,
        message: String?,
    )

    @Transaction
    suspend fun claimExecution(
        articleId: String,
        ruleId: String,
        actionType: String,
        startedAt: Long,
        staleBefore: Long,
        maxAttempts: Int,
    ): AutomationExecutionEntity? {
        if (!ruleExists(ruleId)) return null
        val existing = queryClaim(articleId, ruleId, actionType)
        val staleRunning = existing?.status == AutomationExecutionStatus.RUNNING.name &&
            existing.updatedAt < staleBefore
        val retryableFailure = existing?.status == AutomationExecutionStatus.FAILED.name &&
            existing.attemptCount < maxAttempts &&
            existing.nextRetryAt?.let { it <= startedAt } == true
        if (existing != null && !staleRunning && !retryableFailure) return null
        if (staleRunning) {
            interruptRunningExecutions(articleId, ruleId, actionType, startedAt)
            if (existing.attemptCount >= maxAttempts) {
                upsertClaim(
                    existing.copy(
                        status = AutomationExecutionStatus.FAILED.name,
                        updatedAt = startedAt,
                        nextRetryAt = null,
                        lastError = "Maximum attempts reached after an interrupted run",
                    )
                )
                return null
            }
        }
        val attempt = (existing?.attemptCount ?: 0) + 1
        upsertClaim(
            AutomationActionClaimEntity(
                articleId = articleId,
                ruleId = ruleId,
                actionType = actionType,
                status = AutomationExecutionStatus.RUNNING.name,
                attemptCount = attempt,
                updatedAt = startedAt,
            )
        )
        val execution =
            AutomationExecutionEntity(
                id = UUID.randomUUID().toString(),
                articleId = articleId,
                ruleId = ruleId,
                actionType = actionType,
                status = AutomationExecutionStatus.RUNNING.name,
                attempt = attempt,
                startedAt = startedAt,
            )
        insertExecution(execution)
        return execution
    }

    @Transaction
    suspend fun completeExecution(
        execution: AutomationExecutionEntity,
        status: AutomationExecutionStatus,
        completedAt: Long,
        message: String?,
        nextRetryAt: Long?,
    ) {
        updateExecution(execution.id, status.name, completedAt, message)
        if (ruleExists(execution.ruleId)) {
            upsertClaim(
                AutomationActionClaimEntity(
                    articleId = execution.articleId,
                    ruleId = execution.ruleId,
                    actionType = execution.actionType,
                    status = status.name,
                    attemptCount = execution.attempt,
                    updatedAt = completedAt,
                    nextRetryAt = nextRetryAt,
                    lastError = message,
                )
            )
        }
    }

    @Transaction
    suspend fun interruptAndRelease(execution: AutomationExecutionEntity, completedAt: Long) {
        updateExecution(
            execution.id,
            AutomationExecutionStatus.INTERRUPTED.name,
            completedAt,
            "Execution was cancelled",
        )
        deleteRunningClaim(execution.articleId, execution.ruleId, execution.actionType)
    }

    @Query(
        """UPDATE automation_execution SET status = 'INTERRUPTED', completedAt = :recoveredAt,
            message = 'Previous run timed out'
            WHERE status = 'RUNNING' AND startedAt < :staleBefore AND ruleId IN
                (SELECT id FROM automation_rule WHERE accountId = :accountId)"""
    )
    suspend fun interruptStaleHistory(accountId: Int, staleBefore: Long, recoveredAt: Long)

    @Query(
        """UPDATE automation_action_claim SET status = 'FAILED', updatedAt = :recoveredAt,
            nextRetryAt = :recoveredAt, lastError = 'Previous run timed out'
            WHERE status = 'RUNNING' AND updatedAt < :staleBefore AND ruleId IN
                (SELECT id FROM automation_rule WHERE accountId = :accountId)"""
    )
    suspend fun releaseStaleClaims(accountId: Int, staleBefore: Long, recoveredAt: Long)

    @Transaction
    suspend fun recoverStaleExecutions(accountId: Int, staleBefore: Long, recoveredAt: Long) {
        interruptStaleHistory(accountId, staleBefore, recoveredAt)
        releaseStaleClaims(accountId, staleBefore, recoveredAt)
    }

    @Query(
        """
        SELECT e.*, r.name AS ruleName
        FROM automation_execution AS e
        INNER JOIN automation_rule AS r ON r.id = e.ruleId
        WHERE r.accountId = :accountId
        ORDER BY e.startedAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentExecutions(accountId: Int, limit: Int = 100): Flow<List<AutomationExecutionRecord>>

    @Query(
        """DELETE FROM automation_execution WHERE status != 'RUNNING' AND id IN (
            SELECT e.id FROM automation_execution AS e
            INNER JOIN automation_rule AS r ON r.id = e.ruleId
            WHERE r.accountId = :accountId AND e.status != 'RUNNING'
            ORDER BY e.startedAt DESC LIMIT -1 OFFSET :keepCount
        )"""
    )
    suspend fun trimExecutionHistory(accountId: Int, keepCount: Int)

    @Query(
        """DELETE FROM automation_execution WHERE status != 'RUNNING' AND ruleId IN
            (SELECT id FROM automation_rule WHERE accountId = :accountId)"""
    )
    suspend fun clearExecutionHistory(accountId: Int)
}
