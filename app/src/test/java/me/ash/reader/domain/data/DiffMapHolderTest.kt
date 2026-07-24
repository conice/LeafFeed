package me.ash.reader.domain.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DiffMapHolderTest {
    @Test
    fun partitionsReadAndUnreadUpdates() {
        val batch =
            mapOf(
                    "read-1" to diff("read-1", isUnread = false),
                    "read-2" to diff("read-2", isUnread = false),
                    "unread" to diff("unread", isUnread = true),
                )
                .toDiffBatch()

        assertEquals(setOf("read-1", "read-2"), batch.markAsReadArticleIds)
        assertEquals(setOf("unread"), batch.markAsUnreadArticleIds)
    }

    @Test
    fun removesOnlyDiffsThatHaveNotChangedSinceSnapshot() {
        val appliedRead = diff("read", isUnread = false)
        val appliedUnread = diff("unread", isUnread = true)
        val current =
            mutableMapOf(
                "read" to appliedRead,
                "unread" to appliedUnread.copy(isUnread = false),
                "new" to diff("new", isUnread = false),
            )

        current.removeAppliedDiffs(
            mapOf(
                "read" to appliedRead,
                "unread" to appliedUnread,
            )
        )

        assertEquals(
            mapOf(
                "unread" to appliedUnread.copy(isUnread = false),
                "new" to diff("new", isUnread = false),
            ),
            current,
        )
    }

    private fun diff(articleId: String, isUnread: Boolean) =
        Diff(
            isUnread = isUnread,
            articleId = articleId,
            feedId = "feed",
        )
}
