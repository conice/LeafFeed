package me.ash.reader.infrastructure.ai

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AiSummaryCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `returns a cached successful result`() = runBlocking {
        val cache = cache()
        val calls = AtomicInteger()

        val first = cache.getOrPut(1, "request") {
            calls.incrementAndGet()
            "summary"
        }
        val second = cache.getOrPut(1, "request") {
            calls.incrementAndGet()
            "different"
        }

        assertFalse(first.fromCache)
        assertTrue(second.fromCache)
        assertEquals("summary", second.content)
        assertEquals(1, calls.get())
    }

    @Test
    fun `different fingerprints and forced refresh generate new results`() = runBlocking {
        val cache = cache()
        val calls = AtomicInteger()

        cache.getOrPut(1, "request-a") { "summary-${calls.incrementAndGet()}" }
        cache.getOrPut(1, "request-b") { "summary-${calls.incrementAndGet()}" }
        val refreshed = cache.getOrPut(1, "request-a", forceRefresh = true) {
            "summary-${calls.incrementAndGet()}"
        }

        assertFalse(refreshed.fromCache)
        assertEquals("summary-3", refreshed.content)
        assertEquals(3, calls.get())
    }

    @Test
    fun `does not cache failed or empty production`() = runBlocking {
        val cache = cache()

        assertTrue(runCatching {
            cache.getOrPut(1, "failed") { error("failed") }
        }.isFailure)
        assertTrue(runCatching {
            cache.getOrPut(1, "empty") { "" }
        }.isFailure)

        assertEquals("recovered", cache.getOrPut(1, "failed") { "recovered" }.content)
        assertEquals("non-empty", cache.getOrPut(1, "empty") { "non-empty" }.content)
    }

    @Test
    fun `returns a successful summary when the cache cannot be written`() = runBlocking {
        val invalidCacheDirectory = temporaryFolder.newFile("not-a-directory")
        val cache = AiSummaryCache(invalidCacheDirectory, Dispatchers.IO)

        val result = cache.getOrPut(1, "request") { "summary" }

        assertFalse(result.fromCache)
        assertEquals("summary", result.content)
    }

    @Test
    fun `combines concurrent requests for the same fingerprint`() = runBlocking {
        val cache = cache()
        val calls = AtomicInteger()

        val results = coroutineScope {
            List(8) {
                async {
                    cache.getOrPut(1, "request") {
                        calls.incrementAndGet()
                        delay(50)
                        "summary"
                    }.content
                }
            }.awaitAll()
        }

        assertEquals(List(8) { "summary" }, results)
        assertEquals(1, calls.get())
    }

    @Test
    fun `removes a damaged entry and regenerates it`() = runBlocking {
        val root = temporaryFolder.newFolder("cache")
        val accountDirectory = root.resolve("1").apply { mkdirs() }
        accountDirectory.resolve("${sha256("request")}.json").writeText("not json")
        val cache = AiSummaryCache(root, Dispatchers.IO)

        val result = cache.getOrPut(1, "request") { "summary" }

        assertFalse(result.fromCache)
        assertEquals("summary", result.content)
    }

    @Test
    fun `keeps account caches isolated and clears only the selected account`() = runBlocking {
        val cache = cache()
        cache.getOrPut(1, "request") { "account-one" }
        cache.getOrPut(2, "request") { "account-two" }

        assertTrue(cache.clearAccount(1))

        assertFalse(cache.getOrPut(1, "request") { "new-account-one" }.fromCache)
        assertTrue(cache.getOrPut(2, "request") { "unused" }.fromCache)
    }

    private fun cache(): AiSummaryCache =
        AiSummaryCache(temporaryFolder.newFolder(), Dispatchers.IO)

    @OptIn(ExperimentalStdlibApi::class)
    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .toHexString()
}
