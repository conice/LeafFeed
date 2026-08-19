package com.conice.morss.infrastructure.widget

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetManager.Companion.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.conice.morss.ui.widget.ArticleCardWidget
import com.conice.morss.ui.widget.ArticleCardWidgetReceiver
import com.conice.morss.ui.widget.ArticleListWidget
import com.conice.morss.ui.widget.ArticleListWidgetReceiver

@HiltWorker
class WidgetUpdateWorker
@AssistedInject
constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    var haveSetPreviews = false

    override suspend fun doWork(): Result {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            generatePreviews()
        }

        ArticleListWidget().updateAll(applicationContext)
        ArticleCardWidget().updateAll(applicationContext)
        return Result.success()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun generatePreviews() {
        if (haveSetPreviews) return
        val glanceManager = GlanceAppWidgetManager(context)
        val list = glanceManager.setWidgetPreviews(ArticleCardWidgetReceiver::class) == SET_WIDGET_PREVIEWS_RESULT_SUCCESS
        val card = glanceManager.setWidgetPreviews(ArticleListWidgetReceiver::class) == SET_WIDGET_PREVIEWS_RESULT_SUCCESS
        haveSetPreviews = list and card
    }

    companion object {
        private const val WORK_NAME_PERIODIC = "WidgetUpdateWorker"
        private const val WORK_NAME_ONETIME = "WidgetUpdateWorker-OneTime"

        fun enqueueOneTimeWork(workManager: WorkManager) =
            workManager.enqueueUniqueWork(
                WORK_NAME_ONETIME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build(),
            )

        fun cancelPeriodicWork(workManager: WorkManager) =
            workManager.cancelUniqueWork(WORK_NAME_PERIODIC)
    }
}
