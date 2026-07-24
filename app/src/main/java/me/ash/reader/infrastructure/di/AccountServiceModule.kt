package me.ash.reader.infrastructure.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import me.ash.reader.domain.repository.AccountDao
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.ArticleCollectionDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.ai.AiSummaryCache
import me.ash.reader.infrastructure.preference.SettingsProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AccountServiceModule {
    @Provides
    @Singleton
    fun provideAccountService(
        @ApplicationContext context: Context,
        accountDao: AccountDao,
        groupDao: GroupDao,
        feedDao: FeedDao,
        articleDao: ArticleDao,
        articleCollectionDao: ArticleCollectionDao,
        aiSummaryCache: AiSummaryCache,
        @ApplicationScope coroutineScope: CoroutineScope,
        settingsProvider: SettingsProvider,
    ): AccountService {
        return AccountService(
            context = context,
            accountDao = accountDao,
            groupDao = groupDao,
            feedDao = feedDao,
            articleDao = articleDao,
            articleCollectionDao = articleCollectionDao,
            aiSummaryCache = aiSummaryCache,
            coroutineScope = coroutineScope,
            settingsProvider = settingsProvider,
        )
    }
}
