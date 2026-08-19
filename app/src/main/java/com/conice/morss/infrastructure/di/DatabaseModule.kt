package com.conice.morss.infrastructure.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.conice.morss.domain.repository.AccountDao
import com.conice.morss.domain.repository.ArticleCollectionDao
import com.conice.morss.domain.repository.ArticleDao
import com.conice.morss.domain.repository.ArticleBackupDao
import com.conice.morss.domain.repository.ArticleSummaryDao
import com.conice.morss.domain.repository.AutomationArticleDao
import com.conice.morss.domain.repository.AutomationDao
import com.conice.morss.domain.repository.FeedDao
import com.conice.morss.domain.repository.GroupDao
import com.conice.morss.domain.repository.PodcastDao
import com.conice.morss.domain.repository.ReadingHistoryDao
import com.conice.morss.infrastructure.db.AndroidDatabase
import com.conice.morss.infrastructure.db.ArticleCollectionDatabase
import com.conice.morss.infrastructure.ai.AiDao
import com.conice.morss.infrastructure.ai.AiDatabase
import javax.inject.Singleton

/**
 * Provides Data Access Objects for database.
 *
 * - [ArticleDao]
 * - [FeedDao]
 * - [GroupDao]
 * - [AccountDao]
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideArticleDao(androidDatabase: AndroidDatabase): ArticleDao =
        androidDatabase.articleDao()

    @Provides @Singleton
    fun provideArticleBackupDao(database: AndroidDatabase): ArticleBackupDao =
        database.articleBackupDao()

    @Provides @Singleton
    fun provideArticleSummaryDao(database: AndroidDatabase): ArticleSummaryDao =
        database.articleSummaryDao()

    @Provides @Singleton
    fun provideAutomationArticleDao(database: AndroidDatabase): AutomationArticleDao =
        database.automationArticleDao()

    @Provides
    @Singleton
    fun provideReadingHistoryDao(androidDatabase: AndroidDatabase): ReadingHistoryDao =
        androidDatabase.readingHistoryDao()

    @Provides
    @Singleton
    fun providePodcastDao(androidDatabase: AndroidDatabase): PodcastDao = androidDatabase.podcastDao()

    @Provides
    @Singleton
    fun provideArticleCollectionDao(database: ArticleCollectionDatabase): ArticleCollectionDao =
        database.articleCollectionDao()

    @Provides
    @Singleton
    fun provideAutomationDao(database: ArticleCollectionDatabase): AutomationDao =
        database.automationDao()

    @Provides
    @Singleton
    fun provideFeedDao(androidDatabase: AndroidDatabase): FeedDao =
        androidDatabase.feedDao()

    @Provides
    @Singleton
    fun provideGroupDao(androidDatabase: AndroidDatabase): GroupDao =
        androidDatabase.groupDao()

    @Provides
    @Singleton
    fun provideAccountDao(androidDatabase: AndroidDatabase): AccountDao =
        androidDatabase.accountDao()

    @Provides
    @Singleton
    fun provideReaderDatabase(@ApplicationContext context: Context): AndroidDatabase =
        AndroidDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideArticleCollectionDatabase(
        @ApplicationContext context: Context
    ): ArticleCollectionDatabase = ArticleCollectionDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideAiDatabase(@ApplicationContext context: Context): AiDatabase =
        AiDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideAiDao(database: AiDatabase): AiDao = database.aiDao()
}
