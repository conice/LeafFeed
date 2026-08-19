package com.conice.morss.infrastructure.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import com.conice.morss.domain.repository.AccountDao
import com.conice.morss.domain.repository.ArticleDao
import com.conice.morss.domain.repository.ArticleCollectionDao
import com.conice.morss.domain.repository.FeedDao
import com.conice.morss.domain.repository.GroupDao
import com.conice.morss.application.service.AccountService
import com.conice.morss.application.service.RssService
import com.conice.morss.infrastructure.ai.AiSummaryCache
import com.conice.morss.infrastructure.preference.SettingsProvider
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
