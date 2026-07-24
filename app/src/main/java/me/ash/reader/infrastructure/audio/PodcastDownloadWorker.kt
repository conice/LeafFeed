package me.ash.reader.infrastructure.audio

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.ash.reader.domain.repository.ArticleDao

@HiltWorker
class PodcastDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val articleDao: ArticleDao,
    private val repository: PodcastDownloadRepository,
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        val articleId = inputData.getString(ARTICLE_ID) ?: return Result.failure()
        val article = articleDao.queryById(articleId)?.article ?: return Result.failure()
        val result = repository.download(article) { progress ->
            setProgress(Data.Builder().putInt(PROGRESS, progress).build())
        }
        return if (result.isSuccess) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        const val ARTICLE_ID = "article_id"
        const val PROGRESS = "progress"
    }
}
