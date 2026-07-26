package me.ash.reader.infrastructure.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.ash.reader.domain.model.article.AutomationActionEntity
import me.ash.reader.domain.model.article.AutomationActionClaimEntity
import me.ash.reader.domain.model.article.AutomationConditionEntity
import me.ash.reader.domain.model.article.AutomationConditionGroupEntity
import me.ash.reader.domain.model.article.AutomationExecutionEntity
import me.ash.reader.domain.model.article.AutomationRuleEntity
import me.ash.reader.domain.model.article.AutomationScopeTargetEntity
import me.ash.reader.domain.model.article.ArticleNote
import me.ash.reader.domain.model.article.ArticleTagCrossRef
import me.ash.reader.domain.model.article.ArticleTagLabel
import me.ash.reader.domain.model.article.SavedSearch
import me.ash.reader.domain.repository.ArticleCollectionDao
import me.ash.reader.domain.repository.AutomationDao

@Database(
    entities = [
        ArticleTagLabel::class,
        ArticleTagCrossRef::class,
        ArticleNote::class,
        SavedSearch::class,
        AutomationRuleEntity::class,
        AutomationConditionGroupEntity::class,
        AutomationConditionEntity::class,
        AutomationActionEntity::class,
        AutomationActionClaimEntity::class,
        AutomationExecutionEntity::class,
        AutomationScopeTargetEntity::class,
    ],
    version = 4,
)
abstract class ArticleCollectionDatabase : RoomDatabase() {
    abstract fun articleCollectionDao(): ArticleCollectionDao
    abstract fun automationDao(): AutomationDao

    companion object {
        @Volatile private var instance: ArticleCollectionDatabase? = null

        fun getInstance(context: Context): ArticleCollectionDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ArticleCollectionDatabase::class.java,
                    "ReaderCollections",
                ).addMigrations(
                    MIGRATION_COLLECTIONS_1_2,
                    MIGRATION_COLLECTIONS_2_3,
                    MIGRATION_COLLECTIONS_3_4,
                ).build().also { instance = it }
            }
    }
}

private object MIGRATION_COLLECTIONS_3_4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `automation_scope_target` (`ruleId` TEXT NOT NULL, `targetId` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`ruleId`, `targetId`), FOREIGN KEY(`ruleId`) REFERENCES `automation_rule`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_scope_target_ruleId` ON `automation_scope_target` (`ruleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_scope_target_ruleId_position` ON `automation_scope_target` (`ruleId`, `position`)")
        db.execSQL(
            """INSERT OR IGNORE INTO `automation_scope_target` (`ruleId`, `targetId`, `position`)
                SELECT `id`, `scopeId`, 0 FROM `automation_rule`
                WHERE `scope` != 'GLOBAL' AND `scopeId` != ''"""
        )
    }
}

private object MIGRATION_COLLECTIONS_2_3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_automation_execution_ruleId`")
        db.execSQL("DROP INDEX IF EXISTS `index_automation_execution_executedAt`")
        db.execSQL("ALTER TABLE `automation_execution` RENAME TO `automation_execution_old`")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `automation_action_claim` (`articleId` TEXT NOT NULL, `ruleId` TEXT NOT NULL, `actionType` TEXT NOT NULL, `status` TEXT NOT NULL, `attemptCount` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `nextRetryAt` INTEGER, `lastError` TEXT, PRIMARY KEY(`articleId`, `ruleId`, `actionType`), FOREIGN KEY(`ruleId`) REFERENCES `automation_rule`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_action_claim_ruleId` ON `automation_action_claim` (`ruleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_action_claim_updatedAt` ON `automation_action_claim` (`updatedAt`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `automation_execution` (`id` TEXT NOT NULL, `articleId` TEXT NOT NULL, `ruleId` TEXT NOT NULL, `actionType` TEXT NOT NULL, `status` TEXT NOT NULL, `attempt` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, `completedAt` INTEGER, `message` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`ruleId`) REFERENCES `automation_rule`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_execution_ruleId` ON `automation_execution` (`ruleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_execution_startedAt` ON `automation_execution` (`startedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_execution_status` ON `automation_execution` (`status`)")
        db.execSQL(
            """INSERT INTO `automation_action_claim` (`articleId`, `ruleId`, `actionType`, `status`, `attemptCount`, `updatedAt`, `nextRetryAt`, `lastError`) SELECT `articleId`, `ruleId`, `actionType`, `status`, 1, `executedAt`, CASE WHEN `status` = 'FAILED' THEN 0 ELSE NULL END, `message` FROM `automation_execution_old`"""
        )
        db.execSQL(
            """INSERT INTO `automation_execution` (`id`, `articleId`, `ruleId`, `actionType`, `status`, `attempt`, `startedAt`, `completedAt`, `message`) SELECT lower(hex(randomblob(16))), `articleId`, `ruleId`, `actionType`, `status`, 1, `executedAt`, CASE WHEN `status` = 'RUNNING' THEN NULL ELSE `executedAt` END, `message` FROM `automation_execution_old`"""
        )
        db.execSQL("DROP TABLE `automation_execution_old`")
    }
}

private object MIGRATION_COLLECTIONS_1_2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `automation_rule` (`id` TEXT NOT NULL, `accountId` INTEGER NOT NULL, `name` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `position` INTEGER NOT NULL, `scope` TEXT NOT NULL, `scopeId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_rule_accountId` ON `automation_rule` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_rule_accountId_position` ON `automation_rule` (`accountId`, `position`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `automation_condition_group` (`id` TEXT NOT NULL, `ruleId` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`ruleId`) REFERENCES `automation_rule`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_condition_group_ruleId` ON `automation_condition_group` (`ruleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_condition_group_ruleId_position` ON `automation_condition_group` (`ruleId`, `position`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `automation_condition` (`id` TEXT NOT NULL, `groupId` TEXT NOT NULL, `position` INTEGER NOT NULL, `field` TEXT NOT NULL, `operator` TEXT NOT NULL, `value` TEXT NOT NULL, `caseSensitive` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`groupId`) REFERENCES `automation_condition_group`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_condition_groupId` ON `automation_condition` (`groupId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_condition_groupId_position` ON `automation_condition` (`groupId`, `position`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `automation_action` (`id` TEXT NOT NULL, `ruleId` TEXT NOT NULL, `position` INTEGER NOT NULL, `type` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`ruleId`) REFERENCES `automation_rule`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_action_ruleId` ON `automation_action` (`ruleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_action_ruleId_position` ON `automation_action` (`ruleId`, `position`)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `automation_execution` (`articleId` TEXT NOT NULL, `ruleId` TEXT NOT NULL, `actionType` TEXT NOT NULL, `status` TEXT NOT NULL, `executedAt` INTEGER NOT NULL, `message` TEXT, PRIMARY KEY(`articleId`, `ruleId`, `actionType`), FOREIGN KEY(`ruleId`) REFERENCES `automation_rule`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_execution_ruleId` ON `automation_execution` (`ruleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_automation_execution_executedAt` ON `automation_execution` (`executedAt`)")
    }
}
