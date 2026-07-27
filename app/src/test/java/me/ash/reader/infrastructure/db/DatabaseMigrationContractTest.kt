package me.ash.reader.infrastructure.db

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DatabaseMigrationContractTest {
    @Test
    fun `production migration path only moves forward`() {
        assertTrue(allMigrations.isNotEmpty())
        assertTrue(allMigrations.all { it.startVersion < it.endVersion })
        assertFalse(allMigrations.any { it.startVersion == 8 && it.endVersion == 7 })
    }

    @Test
    fun `archived article migration creates feed index`() {
        val db = mock<SupportSQLiteDatabase>()

        MIGRATION_8_9.migrate(db)

        assertEquals(8, MIGRATION_8_9.startVersion)
        assertEquals(9, MIGRATION_8_9.endVersion)
        assertTrue(allMigrations.contains(MIGRATION_8_9))
        verify(db).execSQL(
            "CREATE INDEX IF NOT EXISTS index_archived_article_feedId " +
                "ON archived_article(feedId)"
        )
    }

    @Test
    fun `reading history migration adds timestamp and index`() {
        val db = mock<SupportSQLiteDatabase>()

        MIGRATION_9_10.migrate(db)

        assertEquals(9, MIGRATION_9_10.startVersion)
        assertEquals(10, MIGRATION_9_10.endVersion)
        assertTrue(allMigrations.contains(MIGRATION_9_10))
        verify(db).execSQL("ALTER TABLE article ADD COLUMN lastOpenedAt INTEGER")
        verify(db).execSQL(
            "CREATE INDEX IF NOT EXISTS index_article_accountId_lastOpenedAt " +
                "ON article(accountId, lastOpenedAt)"
        )
    }

    @Test
    fun `article search migration creates and backfills the FTS index`() {
        val db = mock<SupportSQLiteDatabase>()

        MIGRATION_10_11.migrate(db)

        assertEquals(10, MIGRATION_10_11.startVersion)
        assertEquals(11, MIGRATION_10_11.endVersion)
        assertTrue(allMigrations.contains(MIGRATION_10_11))
        verify(db).execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `article_fts` USING FTS4(" +
                "`articleId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`shortDescription` TEXT NOT NULL, `rawDescription` TEXT NOT NULL)"
        )
        verify(db).execSQL(
            "INSERT INTO `article_fts` (`articleId`, `title`, `shortDescription`, " +
                "`rawDescription`) SELECT `id`, `title`, `shortDescription`, " +
                "`rawDescription` FROM `article`"
        )
    }
}
