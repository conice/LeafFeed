package me.ash.reader.ui.page.settings.features

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseCompactionPolicyTest {
    @Test
    fun `small amounts of free pages do not trigger compaction`() {
        assertFalse(
            shouldAutoCompactDatabase(
                databaseBytes = 100L * 1024L * 1024L,
                reclaimableBytes = 15L * 1024L * 1024L,
            )
        )
    }

    @Test
    fun `large but low percentage fragmentation does not trigger compaction`() {
        assertFalse(
            shouldAutoCompactDatabase(
                databaseBytes = 200L * 1024L * 1024L,
                reclaimableBytes = 20L * 1024L * 1024L,
            )
        )
    }

    @Test
    fun `material fragmentation triggers compaction`() {
        assertTrue(
            shouldAutoCompactDatabase(
                databaseBytes = 100L * 1024L * 1024L,
                reclaimableBytes = 16L * 1024L * 1024L,
            )
        )
    }
}
