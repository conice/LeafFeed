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
    fun `latest migration creates archived article feed index`() {
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
}
