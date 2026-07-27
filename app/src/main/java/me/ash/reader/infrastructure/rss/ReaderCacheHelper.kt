package me.ash.reader.infrastructure.rss

import android.content.Context
import androidx.annotation.CheckResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.article.ArticleContentFetchCandidate
import me.ash.reader.domain.service.AccountService
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.cache.CacheUsage
import me.ash.reader.infrastructure.exception.runSuspendCatching

class ReaderCacheHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val rssHelper: RssHelper,
    private val accountService: AccountService,
) {
    // Full article content is user-requested offline data, so keep it out of Android's evictable
    // cache directory. The legacy location is migrated lazily for existing installations.
    private val offlineDir = context.filesDir.resolve("offline-content")
    private val legacyCacheDir = context.cacheDir.resolve("readability")

    private val currentCacheDir: File
        get() = offlineDir.resolve(accountService.getCurrentAccountId().toString())

    private val currentLegacyCacheDir: File
        get() = legacyCacheDir.resolve(accountService.getCurrentAccountId().toString())

    private fun cacheDir(accountId: Int): File = offlineDir.resolve(accountId.toString())

    private fun accountLegacyCacheDir(accountId: Int): File =
        legacyCacheDir.resolve(accountId.toString())

    @OptIn(ExperimentalStdlibApi::class)
    private fun getFileNameFor(articleId: String): String {
        val bytes = articleId.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.toHexString() + ".html"
    }

    private suspend fun writeContentToCache(
        content: String,
        articleId: String,
        accountId: Int = accountService.getCurrentAccountId(),
    ): Boolean {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                    val directory = cacheDir(accountId).apply {
                        check(isDirectory || mkdirs()) { "Unable to create offline content directory" }
                    }
                    val target = directory.resolve(getFileNameFor(articleId))
                    val temporary = directory.resolve(".${target.name}.tmp")
                    temporary.writeText(content)
                    if (!temporary.renameTo(target)) {
                        target.delete()
                        check(temporary.renameTo(target)) { "Unable to store offline content" }
                    }
                }
                .fold(onSuccess = { true }, onFailure = { false })
        }
    }

    @CheckResult
    suspend fun readFullContent(articleId: String): Result<String> {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                val file = resolveAndMigrate(articleId)
                if (!file.exists()) return@withContext Result.failure(FileNotFoundException())
                file.readText()
            }
        }
    }

    private suspend fun fetchFullContentInternal(
        article: Article,
        accountId: Int = accountService.getCurrentAccountId(),
    ): Result<String> = fetchFullContentInternal(
        articleId = article.id,
        link = article.link,
        title = article.title,
        accountId = accountId,
    )

    private suspend fun fetchFullContentInternal(
        articleId: String,
        link: String,
        title: String,
        accountId: Int,
    ): Result<String> {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                val fullContent = rssHelper.parseFullContent(link, title)
                if (fullContent.isNotBlank()) {
                    writeContentToCache(fullContent, articleId, accountId)
                    fullContent
                } else return@withContext Result.failure(Exception())
            }
        }
    }

    @CheckResult
    suspend fun readOrFetchFullContent(article: Article): Result<String> {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                val result = readFullContent(article.id)
                if (result.isSuccess) return@withContext result
                return@withContext fetchFullContentInternal(article)
            }
        }
    }

    suspend fun checkOrFetchFullContent(
        article: Article,
        accountId: Int = accountService.getCurrentAccountId(),
    ): Boolean = checkOrFetchFullContent(
        articleId = article.id,
        link = article.link,
        title = article.title,
        accountId = accountId,
    )

    suspend fun checkOrFetchFullContent(
        article: ArticleContentFetchCandidate,
        accountId: Int,
    ): Boolean = checkOrFetchFullContent(
        articleId = article.id,
        link = article.link,
        title = article.title,
        accountId = accountId,
    )

    private suspend fun checkOrFetchFullContent(
        articleId: String,
        link: String,
        title: String,
        accountId: Int,
    ): Boolean {
        return withContext(ioDispatcher) {
            val file = resolveAndMigrate(articleId, accountId)
            try {
                if (!file.exists()) {
                    return@withContext fetchFullContentInternal(
                        articleId,
                        link,
                        title,
                        accountId,
                    )
                        .fold(onFailure = { false }, onSuccess = { true })
                } else {
                    return@withContext true
                }
            } catch (_: SecurityException) {
                return@withContext false
            }
        }
    }

    suspend fun deleteCacheFor(
        articleId: String,
        accountId: Int = accountService.getCurrentAccountId(),
    ): Boolean {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                    val file = cacheDir(accountId).resolve(getFileNameFor(articleId))
                    val legacy = accountLegacyCacheDir(accountId).resolve(getFileNameFor(articleId))
                    val deleted = file.delete()
                    val legacyDeleted = legacy.delete()
                    return@runSuspendCatching deleted || legacyDeleted
                }
                .fold(onSuccess = { true }, onFailure = { false })
        }
    }

    suspend fun clearCache(): Boolean {
        return withContext(ioDispatcher) {
            runSuspendCatching {
                    val currentDeleted = currentCacheDir.deleteRecursively()
                    val legacyDeleted = currentLegacyCacheDir.deleteRecursively()
                    return@withContext currentDeleted && legacyDeleted
                }
                .fold(onSuccess = { true }, onFailure = { false })
        }
    }

    suspend fun usage(): CacheUsage = withContext(ioDispatcher) {
        CacheUsage(
            files = sequenceOf(currentCacheDir, currentLegacyCacheDir)
                .flatMap { directory -> directory.walkTopDown() }
                .count { it.isFile },
            bytes = sequenceOf(currentCacheDir, currentLegacyCacheDir)
                .flatMap { directory -> directory.walkTopDown() }
                .filter { it.isFile }
                .sumOf { it.length() },
        )
    }

    private fun resolveAndMigrate(
        articleId: String,
        accountId: Int = accountService.getCurrentAccountId(),
    ): File {
        val fileName = getFileNameFor(articleId)
        val target = cacheDir(accountId).resolve(fileName)
        if (target.isFile) return target
        val legacy = accountLegacyCacheDir(accountId).resolve(fileName)
        if (!legacy.isFile) return target
        cacheDir(accountId).mkdirs()
        if (!legacy.renameTo(target)) legacy.copyTo(target, overwrite = true)
        legacy.delete()
        return target
    }
}
