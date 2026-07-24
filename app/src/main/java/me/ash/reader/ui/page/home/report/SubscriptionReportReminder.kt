package me.ash.reader.ui.page.home.report

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import me.ash.reader.R
import me.ash.reader.infrastructure.android.MainActivity

object SubscriptionReportReminder {
    private const val WORK_NAME = "subscription-report-reminder"
    fun isEnabled(workManager: WorkManager): Boolean =
        workManager.getWorkInfosForUniqueWork(WORK_NAME).get().any { !it.state.isFinished }

    fun setEnabled(workManager: WorkManager, enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SubscriptionReportReminderWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(7, TimeUnit.DAYS)
                .build(),
        )
    }
}

@HiltWorker
class SubscriptionReportReminderWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val compatManager = androidx.core.app.NotificationManagerCompat.from(applicationContext)
        if (!compatManager.areNotificationsEnabled()) return Result.success()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, applicationContext.getString(R.string.subscription_report), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val intent = PendingIntent.getActivity(
            applicationContext, 4401, Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_OPEN_REPORT, true)
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        NotificationManagerCompatCompat.notify(applicationContext, intent)
        return Result.success()
    }

    private companion object {
        const val CHANNEL = "subscription-report"
        const val EXTRA_OPEN_REPORT = "subscription.report.open"
    }
}

private object NotificationManagerCompatCompat {
    fun notify(context: Context, intent: PendingIntent) {
        androidx.core.app.NotificationManagerCompat.from(context).notify(
            4401,
            NotificationCompat.Builder(context, "subscription-report")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.subscription_report))
                .setContentText(context.getString(R.string.report_reminder_text))
                .setAutoCancel(true)
                .setContentIntent(intent)
                .build(),
        )
    }
}
