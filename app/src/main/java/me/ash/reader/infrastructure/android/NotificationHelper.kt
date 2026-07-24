package me.ash.reader.infrastructure.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
import me.ash.reader.R
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.data.ArticleRuleRepository
import me.ash.reader.domain.model.article.RuleScope
import me.ash.reader.domain.model.article.RuleType
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.feed.FeedWithArticle
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.ui.page.common.ExtraName
import me.ash.reader.ui.page.common.NotificationGroupName
import timber.log.Timber

class NotificationHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val coroutineScope: CoroutineScope,
    private val settingsProvider: SettingsProvider,
    private val articleRuleRepository: ArticleRuleRepository,
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
            val rules = articleRuleRepository.currentRules(feed.accountId)
            val includeDescription =
                settingsProvider.get(FeaturePreferenceKeys.ruleMatchDescription) != false
            val filterRulesEnabled =
                settingsProvider.get(FeaturePreferenceKeys.filterRulesEnabled) != false
            val highlightRulesEnabled =
                settingsProvider.get(FeaturePreferenceKeys.highlightRulesEnabled) != false
            fun Article.matches(type: RuleType): Boolean {
                val text =
                    if (includeDescription) "$title\n$rawDescription" else title
                return rules.any { rule ->
                    rule.type == type &&
                        when (rule.scope) {
                            RuleScope.GLOBAL -> true
                            RuleScope.GROUP -> rule.scopeId == feed.groupId
                            RuleScope.FEED -> rule.scopeId == feed.id
                        } && rule.matches(text)
                }
            }

            val selectedArticles = articles.asSequence()
                .filter {
                    settingsProvider.get(FeaturePreferenceKeys.notificationPodcastEpisodes) !=
                        false || it.audioUrl == null
                }
                .filter {
                    settingsProvider.get(FeaturePreferenceKeys.notificationExcludeFiltered) !=
                        true || !filterRulesEnabled || !it.matches(RuleType.FILTER)
                }
                .filter {
                    settingsProvider.get(FeaturePreferenceKeys.notificationHighlightsOnly) !=
                        true || highlightRulesEnabled && it.matches(RuleType.HIGHLIGHT)
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

            notificationManager.notify(
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
                notificationManager.notify(
                    Random().nextInt() + article.id.hashCode(),
                    builder.build(),
                )
            }
        }
    }

    fun notify(feedWithArticle: FeedWithArticle) {
        notify(feedWithArticle.feed, feedWithArticle.articles)
    }
}
