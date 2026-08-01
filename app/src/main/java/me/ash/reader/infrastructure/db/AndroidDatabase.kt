package me.ash.reader.infrastructure.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.ash.reader.domain.model.account.*
import me.ash.reader.domain.model.account.security.DESUtils
import me.ash.reader.domain.model.article.ArchivedArticle
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.article.ArticleSearchEntity
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.repository.AccountDao
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.infrastructure.preference.*
import me.ash.reader.ui.ext.toInt
import java.util.*

@Database(
    entities = [
        Account::class,
        Feed::class,
        Article::class,
        ArticleSearchEntity::class,
        Group::class,
        ArchivedArticle::class,
    ],
    version = 12,
    autoMigrations = [
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 5, to = 7),
        AutoMigration(from = 6, to = 7),
    ]
)
@TypeConverters(
    AndroidDatabase.DateConverters::class,
    AccountTypeConverters::class,
    SyncIntervalConverters::class,
    SyncOnStartConverters::class,
    SyncOnlyOnWiFiConverters::class,
    SyncOnlyWhenChargingConverters::class,
    KeepArchivedConverters::class,
    SyncBlockListConverters::class,
)
abstract class AndroidDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao
    abstract fun groupDao(): GroupDao

    companion object {

        @Volatile private var instance: AndroidDatabase? = null

        fun getInstance(context: Context): AndroidDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AndroidDatabase::class.java,
                    "Reader"
                ).addMigrations(*allMigrations)
                    .addCallback(ARTICLE_SEARCH_CALLBACK)
                    .build().also {
                        instance = it
                    }
            }
        }
    }

    class DateConverters {

        @TypeConverter
        fun toDate(dateLong: Long?): Date? {
            return dateLong?.let { Date(it) }
        }

        @TypeConverter
        fun fromDate(date: Date?): Long? {
            return date?.time
        }
    }
}

val allMigrations = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
)

@Suppress("ClassName")
object MIGRATION_11_12 : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        dropArticleSearchTriggers(db)
        db.execSQL("DROP TABLE IF EXISTS `article_fts`")
        createArticleSearchTable(db)
        db.execSQL(
            "INSERT INTO `article_fts` (`articleId`, `title`, `shortDescription`) " +
                "SELECT `id`, `title`, `shortDescription` FROM `article`"
        )
        createArticleSearchTriggers(db)

        // fullContent has been unused for years; discard legacy duplicate article bodies.
        db.execSQL("UPDATE `article` SET `fullContent` = NULL WHERE `fullContent` IS NOT NULL")
        db.execSQL("DROP INDEX IF EXISTS `index_article_accountId`")

        db.execSQL("ALTER TABLE `archived_article` RENAME TO `archived_article_old`")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `archived_article` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`feedId` TEXT NOT NULL, `link` TEXT NOT NULL, `archivedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`feedId`) REFERENCES `feed`(`id`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE)"
        )
        db.execSQL(
            "INSERT INTO `archived_article` (`feedId`, `link`, `archivedAt`) " +
                "SELECT `feedId`, `link`, ${System.currentTimeMillis()} " +
                "FROM `archived_article_old` GROUP BY `feedId`, `link`"
        )
        db.execSQL("DROP TABLE `archived_article_old`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_archived_article_feedId` " +
                "ON `archived_article` (`feedId`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_archived_article_feedId_link` " +
                "ON `archived_article` (`feedId`, `link`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_archived_article_archivedAt` " +
                "ON `archived_article` (`archivedAt`)"
        )
    }
}

@Suppress("ClassName")
object MIGRATION_10_11 : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        createLegacyArticleSearchTable(db)
        db.execSQL(
            "INSERT INTO `article_fts` (`articleId`, `title`, `shortDescription`, " +
                "`rawDescription`) SELECT `id`, `title`, `shortDescription`, " +
                "`rawDescription` FROM `article`"
        )
        createLegacyArticleSearchTriggers(db)
    }
}

private fun createLegacyArticleSearchTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        "CREATE VIRTUAL TABLE IF NOT EXISTS `article_fts` USING FTS4(" +
            "`articleId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
            "`shortDescription` TEXT NOT NULL, `rawDescription` TEXT NOT NULL)"
    )
}

private fun createLegacyArticleSearchTriggers(db: SupportSQLiteDatabase) {
    db.execSQL(
        "CREATE TRIGGER IF NOT EXISTS `article_fts_after_insert` AFTER INSERT ON `article` " +
            "BEGIN INSERT INTO `article_fts` (`articleId`, `title`, `shortDescription`, " +
            "`rawDescription`) VALUES (new.`id`, new.`title`, new.`shortDescription`, " +
            "new.`rawDescription`); END"
    )
    db.execSQL(
        "CREATE TRIGGER IF NOT EXISTS `article_fts_after_delete` AFTER DELETE ON `article` " +
            "BEGIN DELETE FROM `article_fts` WHERE `articleId` = old.`id`; END"
    )
    db.execSQL(
        "CREATE TRIGGER IF NOT EXISTS `article_fts_after_update` AFTER UPDATE OF " +
            "`id`, `title`, `shortDescription`, `rawDescription` ON `article` " +
            "BEGIN DELETE FROM `article_fts` WHERE `articleId` = old.`id`; " +
            "INSERT INTO `article_fts` (`articleId`, `title`, `shortDescription`, " +
            "`rawDescription`) VALUES (new.`id`, new.`title`, new.`shortDescription`, " +
            "new.`rawDescription`); END"
    )
}

private fun createArticleSearchTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        "CREATE VIRTUAL TABLE IF NOT EXISTS `article_fts` USING FTS4(" +
            "`articleId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
            "`shortDescription` TEXT NOT NULL)"
    )
}

private val ARTICLE_SEARCH_CALLBACK = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        createArticleSearchTriggers(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        createArticleSearchTriggers(db)
    }
}

private fun createArticleSearchTriggers(db: SupportSQLiteDatabase) {
    db.execSQL(
        "CREATE TRIGGER IF NOT EXISTS `article_fts_after_insert` AFTER INSERT ON `article` " +
            "BEGIN INSERT INTO `article_fts` (`articleId`, `title`, `shortDescription`) " +
            "VALUES (new.`id`, new.`title`, new.`shortDescription`); END"
    )
    db.execSQL(
        "CREATE TRIGGER IF NOT EXISTS `article_fts_after_delete` AFTER DELETE ON `article` " +
            "BEGIN DELETE FROM `article_fts` WHERE `articleId` = old.`id`; END"
    )
    db.execSQL(
        "CREATE TRIGGER IF NOT EXISTS `article_fts_after_update` AFTER UPDATE OF " +
            "`id`, `title`, `shortDescription` ON `article` " +
            "BEGIN DELETE FROM `article_fts` WHERE `articleId` = old.`id`; " +
            "INSERT INTO `article_fts` (`articleId`, `title`, `shortDescription`) " +
            "VALUES (new.`id`, new.`title`, new.`shortDescription`); END"
    )
}

private fun dropArticleSearchTriggers(db: SupportSQLiteDatabase) {
    db.execSQL("DROP TRIGGER IF EXISTS `article_fts_after_insert`")
    db.execSQL("DROP TRIGGER IF EXISTS `article_fts_after_delete`")
    db.execSQL("DROP TRIGGER IF EXISTS `article_fts_after_update`")
}

@Suppress("ClassName")
object MIGRATION_9_10 : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE article ADD COLUMN lastOpenedAt INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_article_accountId_lastOpenedAt " +
                "ON article(accountId, lastOpenedAt)"
        )
    }
}

@Suppress("ClassName")
object MIGRATION_8_9 : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_archived_article_feedId " +
                "ON archived_article(feedId)"
        )
    }
}

@Suppress("ClassName")
object MIGRATION_7_8 : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE article ADD COLUMN audioUrl TEXT")
        db.execSQL("ALTER TABLE article ADD COLUMN audioMimeType TEXT")
        db.execSQL("ALTER TABLE article ADD COLUMN audioLength INTEGER")
        db.execSQL("ALTER TABLE article ADD COLUMN durationSeconds INTEGER")
        db.execSQL("ALTER TABLE article ADD COLUMN episodeGuid TEXT")
        db.execSQL("ALTER TABLE article ADD COLUMN seasonNumber INTEGER")
        db.execSQL("ALTER TABLE article ADD COLUMN episodeNumber INTEGER")
        db.execSQL("ALTER TABLE article ADD COLUMN transcriptUrl TEXT")
        db.execSQL("ALTER TABLE article ADD COLUMN isExplicit INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE article ADD COLUMN playbackPositionMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE article ADD COLUMN isPlayed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE article ADD COLUMN downloadedPath TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_article_accountId_date " +
                "ON article(accountId, date)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_article_accountId_isUnread_date " +
                "ON article(accountId, isUnread, date)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_article_accountId_isStarred_date " +
                "ON article(accountId, isStarred, date)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_article_accountId_isUnread_feedId_date " +
                "ON article(accountId, isUnread, feedId, date)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_article_accountId_isStarred_feedId_date " +
                "ON article(accountId, isStarred, feedId, date)"
        )
    }
}

@Suppress("ClassName")
object MIGRATION_8_7 : Migration(8, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS index_article_accountId_date")
        db.execSQL("DROP INDEX IF EXISTS index_article_accountId_isUnread_date")
        db.execSQL("DROP INDEX IF EXISTS index_article_accountId_isStarred_date")
        db.execSQL("DROP INDEX IF EXISTS index_article_accountId_isUnread_feedId_date")
        db.execSQL("DROP INDEX IF EXISTS index_article_accountId_isStarred_feedId_date")
    }
}

@Suppress("ClassName")
object MIGRATION_1_2 : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE article ADD COLUMN img TEXT DEFAULT NULL
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_2_3 : Migration(2, 3) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE article ADD COLUMN updateAt INTEGER DEFAULT ${System.currentTimeMillis()}
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncInterval INTEGER NOT NULL DEFAULT ${SyncIntervalPreference.default.value}
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncOnStart INTEGER NOT NULL DEFAULT ${SyncOnStartPreference.default.value.toInt()}
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncOnlyOnWiFi INTEGER NOT NULL DEFAULT ${SyncOnlyOnWiFiPreference.default.value.toInt()}
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncOnlyWhenCharging INTEGER NOT NULL DEFAULT ${SyncOnlyWhenChargingPreference.default.value.toInt()}
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE account ADD COLUMN keepArchived INTEGER NOT NULL DEFAULT ${KeepArchivedPreference.default.value}
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE account ADD COLUMN syncBlockList TEXT NOT NULL DEFAULT ''
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_3_4 : Migration(3, 4) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE account ADD COLUMN securityKey TEXT DEFAULT '${DESUtils.empty}'
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_4_5 : Migration(4, 5) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE account ADD COLUMN lastArticleId TEXT DEFAULT NULL
            """.trimIndent()
        )
    }
}
