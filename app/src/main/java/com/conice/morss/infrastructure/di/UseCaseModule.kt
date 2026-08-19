package com.conice.morss.infrastructure.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import com.conice.morss.application.data.ArticlePagingListUseCase
import com.conice.morss.application.data.AutomationRepository
import com.conice.morss.infrastructure.sync.DiffMapHolder
import com.conice.morss.application.data.FilterStateUseCase
import com.conice.morss.application.data.GroupWithFeedsListUseCase
import com.conice.morss.application.service.AccountService
import com.conice.morss.application.service.RssService
import com.conice.morss.infrastructure.android.AndroidStringsHelper
import com.conice.morss.infrastructure.preference.SettingsProvider

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun providesArticlePagingList(
        rssService: RssService,
        androidStringsHelper: AndroidStringsHelper,
        @ApplicationScope applicationScope: CoroutineScope,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
        settingsProvider: SettingsProvider,
        filterStateUseCase: FilterStateUseCase,
        accountService: AccountService,
        automationRepository: AutomationRepository,
    ): ArticlePagingListUseCase {
        return ArticlePagingListUseCase(
            rssService,
            androidStringsHelper,
            applicationScope,
            ioDispatcher,
            settingsProvider,
            filterStateUseCase,
            accountService,
            automationRepository,
        )
    }

    @Provides
    @Singleton
    fun providesGroupWithFeedsList(
        @ApplicationScope applicationScope: CoroutineScope,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
        settingsProvider: SettingsProvider,
        rssService: RssService,
        filterStateUseCase: FilterStateUseCase,
        diffMapHolder: DiffMapHolder,
        accountService: AccountService,
    ): GroupWithFeedsListUseCase {
        return GroupWithFeedsListUseCase(
            applicationScope = applicationScope,
            ioDispatcher = ioDispatcher,
            settingsProvider = settingsProvider,
            rssService = rssService,
            filterStateUseCase = filterStateUseCase,
            diffMapHolder = diffMapHolder,
            accountService = accountService,
        )
    }
}
