package me.ash.reader.infrastructure.audio

import android.content.Context
import android.os.Environment
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys
import me.ash.reader.infrastructure.preference.SettingsProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Singleton
class PodcastDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val articleDao: ArticleDao,
    private val workManager: WorkManager,
    private val settingsProvider: SettingsProvider,
) {
    val downloadDirectory: File
        get() = context.externalMediaDirs
            .filterNotNull()
            .firstOrNull { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
            ?.resolve(PODCAST_DIRECTORY)
            ?: legacyDownloadDirectory

    private val legacyDownloadDirectory: File
        get() = context.filesDir.resolve(PODCAST_DIRECTORY)

    fun enqueue(article: Article, wifiOnly: Boolean): Result<Unit> = runCatching {
        requireNotNull(article.audioUrl) { "Episode has no audio" }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<PodcastDownloadWorker>()
            .setInputData(Data.Builder().putString(PodcastDownloadWorker.ARTICLE_ID, article.id).build())
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork(workName(article.id), ExistingWorkPolicy.KEEP, request)
        Unit
    }

    fun cancel(articleId: String) = workManager.cancelUniqueWork(workName(articleId))

    fun observe(articleId: String): Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(workName(articleId))

    suspend fun download(
        article: Article,
        onProgress: suspend (Int) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        val url = article.audioUrl ?: return@withContext Result.failure(IllegalArgumentException("Episode has no audio"))
        runCatching {
            val directory = downloadDirectory.also {
                check(it.isDirectory || it.mkdirs()) { "Unable to create download directory" }
            }
            val extension = url.substringAfterLast('.', "mp3").substringBefore('?').take(5).ifBlank { "mp3" }
            val file = File(directory, "${article.id.hashCode()}.$extension")
            val temporary = File(directory, ".${file.name}.download")
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                val body = response.body
                val totalBytes = body.contentLength()
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        var lastProgress = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (totalBytes > 0) {
                                val progress = (copied * 100 / totalBytes).toInt()
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }
            }
            check(temporary.renameTo(file)) { "Unable to finalize download" }
            articleDao.updateDownloadedPath(article.id, file.absolutePath)
            pruneCache(directory)
            file
        }
    }

    suspend fun remove(article: Article): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            article.downloadedPath?.let(::File)?.takeIf(File::exists)?.delete()
            articleDao.updateDownloadedPath(article.id, null)
        }
    }

    suspend fun clearAll(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            setOf(downloadDirectory, legacyDownloadDirectory).forEach { directory ->
                directory.listFiles().orEmpty().filter { it.isFile }.forEach { deleteAndClear(it) }
                directory.deleteRecursively()
            }
            Unit
        }
    }

    private suspend fun pruneCache(directory: File) {
        val maxBytes = (settingsProvider.get(FeaturePreferenceKeys.podcastCacheMb) ?: 512)
            .coerceAtLeast(64).toLong() * 1024L * 1024L
        val retentionDays = settingsProvider.get(FeaturePreferenceKeys.podcastRetentionDays) ?: 30
        val cutoff = if (retentionDays > 0) System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong()) else Long.MIN_VALUE
        val files = directory.listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") }
        files.filter { it.lastModified() < cutoff }.forEach { deleteAndClear(it) }
        var total = directory.listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") }.sumOf { it.length() }
        directory.listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") }
            .sortedBy { it.lastModified() }
            .forEach { file ->
                if (total <= maxBytes) return@forEach
                total -= file.length()
                deleteAndClear(file)
            }
    }

    private suspend fun deleteAndClear(file: File) {
        val id = articleDao.queryIdByDownloadedPath(file.absolutePath)
        file.delete()
        if (id != null) articleDao.updateDownloadedPath(id, null)
    }

    private fun workName(articleId: String) = "podcast-download-${articleId.hashCode()}"

    private companion object {
        const val PODCAST_DIRECTORY = "podcasts"
    }
}
