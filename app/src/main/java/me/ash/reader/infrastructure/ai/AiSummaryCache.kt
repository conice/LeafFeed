package me.ash.reader.infrastructure.ai

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.cache.CacheUsage

class AiSummaryCache internal constructor(
    private val cacheDir: File,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getOrPut(
        accountId: Int,
        fingerprint: String,
        forceRefresh: Boolean = false,
        produce: suspend () -> String,
    ): AiSummaryCacheResult {
        val key = sha256(fingerprint)
        val lock = locks.getOrPut("$accountId/$key") { Mutex() }
        return lock.withLock {
            if (!forceRefresh) {
                read(accountId, key)?.let {
                    return@withLock AiSummaryCacheResult(content = it, fromCache = true)
                }
            }

            val content = produce().takeIf { it.isNotBlank() }
                ?: error("AI returned an empty response")
            try {
                write(accountId, key, content)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
            AiSummaryCacheResult(content = content, fromCache = false)
        }
    }

    suspend fun clearAccount(accountId: Int): Boolean = withContext(ioDispatcher) {
        runCatching { cacheDir.resolve(accountId.toString()).deleteRecursively() }
            .getOrDefault(false)
    }

    suspend fun usage(accountId: Int): CacheUsage = withContext(ioDispatcher) {
        val directory = cacheDir.resolve(accountId.toString())
        CacheUsage(
            files = directory.walkTopDown().count { it.isFile && it.extension == "json" },
            bytes = directory.walkTopDown()
                .filter { it.isFile && it.extension == "json" }
                .sumOf { it.length() },
        )
    }

    private suspend fun read(accountId: Int, key: String): String? = withContext(ioDispatcher) {
        val file = cacheFile(accountId, key)
        if (!file.isFile) return@withContext null
        val entry = runCatching { json.decodeFromString<CacheEntry>(file.readText()) }.getOrNull()
        if (entry == null || entry.version != CACHE_FORMAT_VERSION || entry.content.isBlank()) {
            file.delete()
            return@withContext null
        }
        file.setLastModified(System.currentTimeMillis())
        entry.content
    }

    private suspend fun write(accountId: Int, key: String, content: String) {
        withContext(ioDispatcher) {
            val directory = cacheDir.resolve(accountId.toString()).apply { mkdirs() }
            val target = directory.resolve("$key.json")
            val temporary = directory.resolve("$key.tmp")
            temporary.writeText(
                json.encodeToString(
                    CacheEntry(
                        version = CACHE_FORMAT_VERSION,
                        createdAt = System.currentTimeMillis(),
                        content = content,
                    )
                )
            )
            if (!temporary.renameTo(target)) {
                target.delete()
                check(temporary.renameTo(target)) { "Unable to write AI summary cache" }
            }
            trim()
        }
    }

    private fun trim() {
        val now = System.currentTimeMillis()
        val files = cacheDir.walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .toList()
        files.filter { now - it.lastModified() > MAX_AGE_MILLIS }.forEach(File::delete)

        val retained = files.filter(File::exists).sortedByDescending(File::lastModified)
        var retainedBytes = 0L
        retained.forEachIndexed { index, file ->
            retainedBytes += file.length()
            if (index >= MAX_ENTRIES || retainedBytes > MAX_BYTES) file.delete()
        }
        cacheDir.walkBottomUp()
            .filter { it.isDirectory && it != cacheDir }
            .forEach { directory -> directory.delete() }
    }

    private fun cacheFile(accountId: Int, key: String): File =
        cacheDir.resolve(accountId.toString()).resolve("$key.json")

    @OptIn(ExperimentalStdlibApi::class)
    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).toHexString()

    @Serializable
    private data class CacheEntry(
        val version: Int,
        val createdAt: Long,
        val content: String,
    )

    private companion object {
        const val CACHE_FORMAT_VERSION = 1
        const val MAX_ENTRIES = 500
        const val MAX_BYTES = 20L * 1024L * 1024L
        const val MAX_AGE_MILLIS = 60L * 24L * 60L * 60L * 1_000L
    }
}

data class AiSummaryCacheResult(
    val content: String,
    val fromCache: Boolean,
)
