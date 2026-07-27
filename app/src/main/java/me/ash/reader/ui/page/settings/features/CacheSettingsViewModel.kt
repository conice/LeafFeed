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
import me.ash.reader.domain.data.SyncLogger
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.rss.ReaderCacheHelper
import me.ash.reader.infrastructure.ai.AiSummaryCache
import me.ash.reader.domain.service.AccountService
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
                    }.size
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
                    val databaseFiles = databaseFiles()
                    val requiredBytes = databaseFiles.sumOf { it.length() }
                    val databaseDirectory = context.getDatabasePath("Reader").parentFile
                        ?: error("Database directory is unavailable")
                    check(StatFs(databaseDirectory.path).availableBytes > requiredBytes) {
                        "Insufficient free space to optimize databases"
                    }
                    listOf(database, collectionDatabase).forEach { roomDatabase ->
                        val sqliteDatabase = roomDatabase.openHelper.writableDatabase
                        sqliteDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                            cursor.moveToFirst()
                        }
                        sqliteDatabase.execSQL("VACUUM")
                    }
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
)
