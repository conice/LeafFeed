package me.ash.reader.infrastructure.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import me.ash.reader.application.data.ArticlePagingListUseCase
import me.ash.reader.application.data.AutomationRepository
import me.ash.reader.infrastructure.sync.DiffMapHolder
import me.ash.reader.application.data.FilterStateUseCase
import me.ash.reader.application.data.GroupWithFeedsListUseCase
import me.ash.reader.application.service.AccountService
import me.ash.reader.application.service.RssService
import me.ash.reader.infrastructure.android.AndroidStringsHelper
import me.ash.reader.infrastructure.preference.SettingsProvider

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
