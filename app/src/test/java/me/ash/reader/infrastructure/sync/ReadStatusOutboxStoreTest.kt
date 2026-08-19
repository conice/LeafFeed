package me.ash.reader.infrastructure.sync

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReadStatusOutboxStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `round trips local and remote pending changes`() {
        val directory = temporaryFolder.newFolder("outbox")
        val store = ReadStatusOutboxStore(directory)
        val snapshot =
            ReadStatusOutboxSnapshot(
                localDiffs = mapOf("local" to diff("local", isUnread = false)),
                pendingRemoteDiffs = mapOf("remote" to diff("remote", isUnread = true)),
            )

        store.write(snapshot)

        assertEquals(snapshot, store.read())
    }

    @Test
    fun `reads legacy local diff map`() {
        val directory = temporaryFolder.newFolder("legacy")
        File(directory, "read_status_outbox.json").writeText(
            """{"article":{"isUnread":false,"articleId":"article","feedId":"feed"}}"""
        )

        assertEquals(
            ReadStatusOutboxSnapshot(
                localDiffs = mapOf("article" to diff("article", isUnread = false))
            ),
            ReadStatusOutboxStore(directory).read(),
        )
    }

    @Test
    fun `removes persisted state when the outbox becomes empty`() {
        val directory = temporaryFolder.newFolder("empty")
        val store = ReadStatusOutboxStore(directory)
        store.write(
            ReadStatusOutboxSnapshot(localDiffs = mapOf("article" to diff("article", false)))
        )

        store.write(ReadStatusOutboxSnapshot())

        assertFalse(File(directory, "read_status_outbox.json").exists())
    }

    @Test
    fun `retries a failed remote change until it succeeds`() = runBlocking {
        val pending = mutableMapOf("article" to diff("article", isUnread = false))
        var attempts = 0
        val delays = mutableListOf<Long>()

        drainReadStatusOutbox(
            pendingSnapshot = { pending.toMap() },
            sync = {
                attempts++
                if (attempts < 3) emptySet() else setOf("article")
            },
            removeSynced = { _, synced -> pending.keys.removeAll(synced) },
            persist = {},
            pause = { delays += it },
        )

        assertEquals(3, attempts)
        assertEquals(listOf(2_000L, 4_000L), delays)
        assertEquals(emptyMap<String, Diff>(), pending)
    }

    @Test
    fun `does not remove a newer change after an older change is acknowledged`() = runBlocking {
        val original = diff("article", isUnread = false)
        val newer = diff("article", isUnread = true)
        val pending = mutableMapOf("article" to original)

        try {
            drainReadStatusOutbox(
                pendingSnapshot = { pending.toMap() },
                sync = {
                    pending["article"] = newer
                    setOf("article")
                },
                removeSynced = { attempted, synced ->
                    synced.forEach { articleId ->
                        if (pending[articleId] == attempted[articleId]) pending.remove(articleId)
                    }
                },
                persist = {},
                pause = { throw CancellationException("stop after first attempt") },
            )
        } catch (_: CancellationException) {
            // Expected: the assertion checks the state after the first in-flight acknowledgement.
        }

        assertEquals(newer, pending["article"])
    }

    private fun diff(articleId: String, isUnread: Boolean) =
        Diff(isUnread = isUnread, articleId = articleId, feedId = "feed")
}
