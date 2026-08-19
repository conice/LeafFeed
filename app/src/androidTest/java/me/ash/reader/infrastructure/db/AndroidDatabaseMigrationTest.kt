package me.ash.reader.infrastructure.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AndroidDatabase::class.java,
        )

    @Test
    fun migrateVersion7To12PreservesArticlesAndBuildsSearchAndHistoryIndexes() {
        helper.createDatabase(TEST_DATABASE, 7).apply {
            execSQL("INSERT INTO `group` (`id`, `name`, `accountId`) VALUES ('group-1', 'Group', 1)")
            execSQL(
                "INSERT INTO `feed` (`id`, `name`, `url`, `groupId`, `accountId`, " +
                    "`isNotification`, `isFullContent`, `isBrowser`) " +
                    "VALUES ('feed-1', 'Feed', 'https://example.com/feed', 'group-1', 1, 0, 0, 0)"
            )
            execSQL(
                "INSERT INTO `article` (`id`, `date`, `title`, `rawDescription`, " +
                    "`shortDescription`, `fullContent`, `link`, `feedId`, `accountId`, " +
                    "`isUnread`, `isStarred`, `isReadLater`) VALUES " +
                    "('article-1', 1, 'Migration needle', 'body', 'summary', 'legacy body', " +
                    "'https://example.com/article', 'feed-1', 1, 1, 0, 0)"
            )
            execSQL(
                "INSERT INTO `archived_article` (`feedId`, `link`) " +
                    "VALUES ('feed-1', 'https://example.com/archived')"
            )
            close()
        }

        val database =
            helper.runMigrationsAndValidate(
                TEST_DATABASE,
                12,
                true,
                *allMigrations,
            )
        helper.closeWhenFinished(database)

        database.query(
            "SELECT `fullContent`, `playbackPositionMs`, `isPlayed`, `lastOpenedAt` " +
                "FROM `article` WHERE `id` = 'article-1'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertEquals(0L, cursor.getLong(1))
            assertEquals(0, cursor.getInt(2))
            assertTrue(cursor.isNull(3))
        }
        database.query(
            "SELECT `articleId` FROM `article_fts` WHERE `article_fts` MATCH 'needle'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("article-1", cursor.getString(0))
        }
        database.query("PRAGMA index_list(`article`)").use { cursor ->
            val names = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue("Missing reading-history index: $names", names.contains("index_article_accountId_lastOpenedAt"))
        }
        database.query(
            "SELECT `archivedAt` FROM `archived_article` " +
                "WHERE `feedId` = 'feed-1' AND `link` = 'https://example.com/archived'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getLong(0) > 0L)
        }
    }

    private companion object {
        const val TEST_DATABASE = "android-database-migration-test"
    }
}
