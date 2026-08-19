package com.conice.morss.infrastructure.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmapOrNull
import coil.imageLoader
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Random
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.conice.morss.R
import com.conice.morss.domain.model.article.Article
import com.conice.morss.domain.model.feed.Feed
import com.conice.morss.domain.model.feed.FeedWithArticle
import com.conice.morss.infrastructure.di.ApplicationScope
import com.conice.morss.infrastructure.di.IODispatcher
import com.conice.morss.infrastructure.preference.FeaturePreferenceKeys
import com.conice.morss.infrastructure.preference.SettingsProvider
import com.conice.morss.ui.page.common.ExtraName
import com.conice.morss.ui.page.common.NotificationGroupName
import timber.log.Timber

class NotificationHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val coroutineScope: CoroutineScope,
    private val settingsProvider: SettingsProvider,
) {
    private companion object {
        const val MAX_ARTICLE_NOTIFICATIONS = 5
    }

    private val notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(context).apply {
            createNotificationChannel(
                NotificationChannel(
                    NotificationGroupName.ARTICLE_UPDATE,
                    NotificationGroupName.ARTICLE_UPDATE,
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }

    fun notify(feed: Feed, articles: List<Article>) {
        if (settingsProvider.get(FeaturePreferenceKeys.notificationsEnabled) == false) return
        if (!notificationManager.areNotificationsEnabled()) return
        if (articles.isEmpty()) return
        if (!feed.isNotification) return
        coroutineScope.launch {
            val selectedArticles = articles.asSequence()
                .filter {
                    settingsProvider.get(FeaturePreferenceKeys.notificationPodcastEpisodes) !=
                        false || it.audioUrl == null
                }
                .toList()
            if (selectedArticles.isEmpty()) return@launch

            Timber.d("notify ${feed.name} for ${selectedArticles.size} articles")

            val favIcon =
                withContext(ioDispatcher) {
                    feed.icon?.let { icon ->
                        context.imageLoader
                            .execute(ImageRequest.Builder(context).data(icon).build())
                            .drawable
                            ?.toBitmapOrNull()
                    }
                }

            postNotification(
                feed.id.hashCode(),
                NotificationCompat.Builder(context, NotificationGroupName.ARTICLE_UPDATE)
                    .setContentTitle(feed.name)
                    .setContentText(
                        context.resources.getQuantityText(
                            R.plurals.unread_desc,
                            selectedArticles.size,
                        )
                    )
                    .setSmallIcon(R.drawable.ic_notification)
                    .setStyle(NotificationCompat.InboxStyle().setSummaryText(feed.name))
                    .setGroup(feed.id)
                    .setGroupSummary(true)
                    .build(),
            )

            val maxArticles = settingsProvider
                .get(FeaturePreferenceKeys.notificationMaxArticles)
                ?.coerceIn(1, 20) ?: MAX_ARTICLE_NOTIFICATIONS
            selectedArticles.takeLast(maxArticles).asReversed().forEach { article ->
                val builder =
                    NotificationCompat.Builder(context, NotificationGroupName.ARTICLE_UPDATE)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setSubText(feed.name)
                        .setAutoCancel(true)
                        .setContentTitle(article.title)
                        .setContentText(article.shortDescription)
                        .setLargeIcon(favIcon)
                        .setContentIntent(
                            PendingIntent.getActivity(
                                context,
                                Random().nextInt() + article.id.hashCode(),
                                Intent(context, MainActivity::class.java).apply {
                                    flags =
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    if (
                                        settingsProvider.get(
                                            FeaturePreferenceKeys.notificationOpenArticle
                                        ) != false
                                    ) {
                                        putExtra(ExtraName.ARTICLE_ID, article.id)
                                    }
                                },
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                            )
                        )
                        .setGroup(feed.id)
                postNotification(
                    Random().nextInt() + article.id.hashCode(),
                    builder.build(),
                )
            }
        }
    }

    fun notify(feedWithArticle: FeedWithArticle) {
        notify(feedWithArticle.feed, feedWithArticle.articles)
    }

    suspend fun notifyAutomation(feed: Feed, article: Article, ruleId: String, ruleName: String) {
        if (!notificationManager.areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(ExtraName.ARTICLE_ID, article.id)
        }
        postNotification(
            ("automation:${article.id}:$ruleId").hashCode(),
            NotificationCompat.Builder(context, NotificationGroupName.ARTICLE_UPDATE)
                .setSmallIcon(R.drawable.ic_notification)
                .setSubText(ruleName)
                .setContentTitle(article.title)
                .setContentText(feed.name)
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        ("automation:${article.id}:$ruleId").hashCode(),
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                .build(),
        )
    }

    private fun postNotification(notificationId: Int, notification: Notification) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            notificationManager.notify(notificationId, notification)
        } catch (exception: SecurityException) {
            Timber.w(exception, "Unable to post notification $notificationId")
        }
    }
}
