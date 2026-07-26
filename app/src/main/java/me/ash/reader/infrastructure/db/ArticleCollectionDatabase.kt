package me.ash.reader.infrastructure.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.ash.reader.domain.model.article.AutomationActionEntity
import me.ash.reader.domain.model.article.AutomationConditionEntity
import me.ash.reader.domain.model.article.AutomationConditionGroupEntity
import me.ash.reader.domain.model.article.AutomationExecutionEntity
import me.ash.reader.domain.model.article.AutomationRuleEntity
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
        AutomationExecutionEntity::class,
    ],
    version = 2,
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
                ).addMigrations(MIGRATION_COLLECTIONS_1_2).build().also { instance = it }
            }
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
