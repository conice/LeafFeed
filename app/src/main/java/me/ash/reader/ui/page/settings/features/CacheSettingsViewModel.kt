package me.ash.reader.ui.page.settings.features

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.ash.reader.application.data.SyncLogger
import me.ash.reader.application.service.RssService
import me.ash.reader.infrastructure.rss.ReaderCacheHelper
import me.ash.reader.infrastructure.ai.AiSummaryCache
import me.ash.reader.application.service.AccountService
import me.ash.reader.infrastructure.db.AndroidDatabase
import me.ash.reader.infrastructure.db.ArticleCollectionDatabase
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.exception.runSuspendCatching
import okhttp3.OkHttpClient

@HiltViewModel
class CacheSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val readerCache: ReaderCacheHelper,
    private val aiSummaryCache: AiSummaryCache,
    private val accountService: AccountService,
    private val rssService: RssService,
    private val syncLogger: SyncLogger,
    private val imageLoader: ImageLoader,
    private val okHttpClient: OkHttpClient,
    private val database: AndroidDatabase,
    private val collectionDatabase: ArticleCollectionDatabase,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _usage = MutableStateFlow(CacheUsageState())
    val usage: StateFlow<CacheUsageState> = _usage.asStateFlow()
    private val _operationInProgress = MutableStateFlow(false)
    val operationInProgress: StateFlow<Boolean> = _operationInProgress.asStateFlow()

    init { refreshUsage() }

    fun refreshUsage() {
        viewModelScope.launch {
            val reader = readerCache.usage()
            val ai = aiSummaryCache.usage(accountService.getCurrentAccountId())
            val storage = withContext(ioDispatcher) {
                val temporaryDirectories = temporaryDirectories()
                CacheUsageState(
                    readerFiles = reader.files,
                    readerBytes = reader.bytes,
                    aiFiles = ai.files,
                    aiBytes = ai.bytes,
                    temporaryFiles = temporaryDirectories.sumOf(::fileCount),
                    temporaryBytes = temporaryDirectories.sumOf(::fileSize),
                    databaseBytes = databaseFiles().sumOf { it.length() },
                    reclaimableDatabaseBytes = reclaimableDatabaseBytes(),
                )
            }
            _usage.value = storage
        }
    }

    fun clearReaderCache(onComplete: (Boolean) -> Unit) {
        launchStorageOperation {
            val result = readerCache.clearCache()
            refreshUsage()
            onComplete(result)
        }
    }

    fun clearAiSummaryCache(onComplete: (Boolean) -> Unit) {
        launchStorageOperation {
            val result = aiSummaryCache.clearAccount(accountService.getCurrentAccountId())
            refreshUsage()
            onComplete(result)
        }
    }

    fun clearTemporaryCache(onComplete: (Boolean) -> Unit) {
        launchStorageOperation {
            val result = withContext(ioDispatcher) {
                runSuspendCatching {
                    imageLoader.diskCache?.clear()
                    okHttpClient.cache?.evictAll()
                    syncLogger.clear()
                }
            }
            refreshUsage()
            onComplete(result.isSuccess)
        }
    }

    fun cleanOldReadArticles(onComplete: (Result<Int>) -> Unit) {
        launchStorageOperation {
            val result = withContext(ioDispatcher) {
                runSuspendCatching {
                    rssService.get().clearKeepArchivedArticles().also { articles ->
                        articles.forEach { readerCache.deleteCacheFor(it.id) }
                    }.size.also {
                        compactDatabases(force = false)
                    }
                }
            }
            refreshUsage()
            onComplete(result)
        }
    }

    fun optimizeDatabases(onComplete: (Result<Unit>) -> Unit) {
        launchStorageOperation {
            val result = withContext(ioDispatcher) {
                runSuspendCatching {
                    compactDatabases(force = true)
                }
            }
            refreshUsage()
            onComplete(result)
        }
    }

    private fun launchStorageOperation(block: suspend () -> Unit) {
        if (_operationInProgress.value) return
        _operationInProgress.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    private fun temporaryDirectories() = listOf(
        context.cacheDir.resolve("images"),
        context.cacheDir.resolve("http"),
        context.cacheDir.resolve("logs"),
    )

    private fun databaseFiles() = listOf("Reader", "ReaderCollections").flatMap { name ->
        val databaseFile = context.getDatabasePath(name)
        databaseFile.parentFile?.listFiles().orEmpty().filter { file ->
            file.name == name || file.name.startsWith("$name-")
        }
    }

    private fun reclaimableDatabaseBytes(): Long =
        listOf(database, collectionDatabase).sumOf { roomDatabase ->
            val sqliteDatabase = roomDatabase.openHelper.writableDatabase
            pragmaLong(sqliteDatabase, "freelist_count") * pragmaLong(sqliteDatabase, "page_size")
        }

    private fun compactDatabases(force: Boolean) {
        val databaseDirectory = context.getDatabasePath("Reader").parentFile
            ?: error("Database directory is unavailable")
        listOf(database, collectionDatabase).forEach { roomDatabase ->
            val sqliteDatabase = roomDatabase.openHelper.writableDatabase
            sqliteDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            val pageSize = pragmaLong(sqliteDatabase, "page_size")
            val pageCount = pragmaLong(sqliteDatabase, "page_count")
            val reclaimableBytes = pragmaLong(sqliteDatabase, "freelist_count") * pageSize
            val databaseBytes = pageCount * pageSize
            val worthCompacting = shouldAutoCompactDatabase(
                databaseBytes = databaseBytes,
                reclaimableBytes = reclaimableBytes,
            )
            val hasVacuumSpace = StatFs(databaseDirectory.path).availableBytes > databaseBytes
            if (force) {
                check(hasVacuumSpace) {
                    "Insufficient free space to optimize databases"
                }
                sqliteDatabase.execSQL("VACUUM")
            } else if (worthCompacting && hasVacuumSpace) {
                sqliteDatabase.execSQL("VACUUM")
            }
        }
    }

    private fun pragmaLong(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        name: String,
    ): Long = database.query("PRAGMA $name").use { cursor ->
        check(cursor.moveToFirst()) { "Unable to read PRAGMA $name" }
        cursor.getLong(0)
    }

    private fun fileCount(directory: java.io.File): Int =
        directory.takeIf { it.exists() }?.walkTopDown()?.count { it.isFile } ?: 0

    private fun fileSize(directory: java.io.File): Long =
        directory.takeIf { it.exists() }?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() }
            ?: 0L
}

data class CacheUsageState(
    val readerFiles: Int = 0,
    val readerBytes: Long = 0,
    val aiFiles: Int = 0,
    val aiBytes: Long = 0,
    val temporaryFiles: Int = 0,
    val temporaryBytes: Long = 0,
    val databaseBytes: Long = 0,
    val reclaimableDatabaseBytes: Long = 0,
)

private const val AUTO_VACUUM_MIN_BYTES = 16L * 1024L * 1024L
private const val AUTO_VACUUM_MIN_PERCENT = 15L

internal fun shouldAutoCompactDatabase(databaseBytes: Long, reclaimableBytes: Long): Boolean =
    reclaimableBytes >= AUTO_VACUUM_MIN_BYTES &&
        reclaimableBytes * 100L >= databaseBytes * AUTO_VACUUM_MIN_PERCENT
