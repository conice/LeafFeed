package me.ash.reader.ui.page.nav3

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.delay
import me.ash.reader.ui.motion.materialSharedAxisXIn
import me.ash.reader.ui.motion.materialSharedAxisXOut
import me.ash.reader.ui.theme.MotionTokens
import me.ash.reader.ui.page.adaptive.ArticleData
import me.ash.reader.ui.page.adaptive.ArticleListReaderPage
import me.ash.reader.ui.page.adaptive.ArticleListReaderViewModel
import me.ash.reader.ui.page.home.feeds.FeedsPage
import me.ash.reader.ui.page.home.feeds.subscribe.SubscribeViewModel
import me.ash.reader.ui.page.home.report.SubscriptionReportPage
import me.ash.reader.ui.page.nav3.key.Route
import me.ash.reader.ui.page.nav3.key.Route.ReadingSource
import me.ash.reader.ui.page.settings.SettingsPage
import me.ash.reader.ui.page.settings.ai.AiSettingsPage
import me.ash.reader.ui.page.settings.features.DataPrivacySettingsPage
import me.ash.reader.ui.page.settings.features.NotificationSettingsPage
import me.ash.reader.ui.page.settings.features.PodcastSettingsPage
import me.ash.reader.ui.page.settings.features.ReadingOptionsPage
import me.ash.reader.ui.page.settings.features.PodcastLibraryPage
import me.ash.reader.ui.page.settings.features.CollectionManagerPage
import me.ash.reader.ui.page.settings.features.RuleManagerPage
import me.ash.reader.ui.page.settings.accounts.AccountDetailsPage
import me.ash.reader.ui.page.settings.accounts.AccountViewModel
import me.ash.reader.ui.page.settings.accounts.AccountsPage
import me.ash.reader.ui.page.settings.accounts.AddAccountsPage
import me.ash.reader.ui.page.settings.color.ColorAndStylePage
import me.ash.reader.ui.page.settings.color.DarkThemePage
import me.ash.reader.ui.page.settings.color.feeds.FeedsPageStylePage
import me.ash.reader.ui.page.settings.color.flow.FlowPageStylePage
import me.ash.reader.ui.page.settings.color.reading.BoldCharactersPage
import me.ash.reader.ui.page.settings.color.reading.ReadingImagePage
import me.ash.reader.ui.page.settings.color.reading.ReadingStylePage
import me.ash.reader.ui.page.settings.color.reading.ReadingTextPage
import me.ash.reader.ui.page.settings.color.reading.ReadingTitlePage
import me.ash.reader.ui.page.settings.color.reading.ReadingVideoPage
import me.ash.reader.ui.page.settings.interaction.InteractionPage
import me.ash.reader.ui.page.settings.languages.LanguagesPage
import me.ash.reader.ui.page.settings.tips.LicenseListPage
import me.ash.reader.ui.page.settings.tips.TipsAndSupportPage
import me.ash.reader.ui.page.settings.troubleshooting.TroubleshootingPage
import me.ash.reader.ui.page.settings.troubleshooting.SyncStatusPage
import me.ash.reader.ui.page.startup.StartupPage
import me.ash.reader.infrastructure.audio.PodcastPlayer
import me.ash.reader.ui.page.home.reading.PodcastMiniPlayer

private const val INITIAL_OFFSET_FACTOR = MotionTokens.ContentOffsetFactor

@OptIn(
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
)
@Composable
fun AppEntry(backStack: NavBackStack<NavKey>, podcastPlayer: PodcastPlayer) {
    val subscribeViewModel = hiltViewModel<SubscribeViewModel>()
    val miniPlayerBottomPadding =
        when (backStack.lastOrNull()) {
            Route.Feeds, is Route.Reading -> 76.dp
            else -> 16.dp
        }

    val onBack: () -> Unit = {
        if (backStack.size == 1) backStack[0] = Route.Feeds else backStack.removeLastOrNull()
    }

    val scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())

    SharedTransitionLayout {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            NavDisplay(
                modifier = Modifier.fillMaxSize(),
                backStack = backStack,
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                transitionSpec = {
                    materialSharedAxisXIn(
                        initialOffsetX = { (it * INITIAL_OFFSET_FACTOR).toInt() }
                    ) togetherWith
                        materialSharedAxisXOut(
                            targetOffsetX = { -(it * INITIAL_OFFSET_FACTOR).toInt() }
                        )
                },
                popTransitionSpec = {
                    materialSharedAxisXIn(
                        initialOffsetX = { -(it * INITIAL_OFFSET_FACTOR).toInt() }
                    ) togetherWith
                        materialSharedAxisXOut(targetOffsetX = { (it * INITIAL_OFFSET_FACTOR).toInt() })
                },
                predictivePopTransitionSpec = {
                    materialSharedAxisXIn(
                        initialOffsetX = { -(it * INITIAL_OFFSET_FACTOR).toInt() }
                    ) togetherWith
                        materialSharedAxisXOut(targetOffsetX = { (it * INITIAL_OFFSET_FACTOR).toInt() })
                },
                onBack = { backStack.removeLastOrNull() },
                entryProvider = { key ->
                when (key) {
                    Route.Feeds -> {
                        NavEntry(key) {
                            FeedsPage(
                                subscribeViewModel = subscribeViewModel,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                navigateToSettings = { backStack.add(Route.Settings) },
                                navigateToSubscriptionReport = {
                                    backStack.add(Route.SubscriptionReport)
                                },
                                navigationToFlow = { backStack.add(Route.Reading(null)) },
                                navigateToAccountList = { backStack.add(Route.Accounts) },
                                navigateToAccountDetail = {
                                    backStack.add(Route.AccountDetails(it))
                                },
                            )
                        }
                    }
                    Route.SubscriptionReport ->
                        NavEntry(key) {
                            SubscriptionReportPage(
                                onBack = onBack,
                                onOpenReading = { backStack.add(Route.Reading(null)) },
                            )
                        }
                    is Route.Reading -> {
                        NavEntry(key) {
                            val key = rememberSaveable(saver = Route.Reading.Saver) { key }
                            val navigator =
                                rememberListDetailPaneScaffoldNavigator<ArticleData>(
                                    scaffoldDirective = scaffoldDirective,
                                    isDestinationHistoryAware = false,
                                )

                            LaunchedEffect(key) {
                                if (key.articleId != null) {
                                    delay(50L)
                                    navigator.navigateTo(
                                        ListDetailPaneScaffoldRole.Detail,
                                        ArticleData(key.articleId),
                                    )
                                }
                            }

                            val viewModel = hiltViewModel<ArticleListReaderViewModel>()

                            ArticleListReaderPage(
                                scaffoldDirective = scaffoldDirective,
                                navigator = navigator,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                                viewModel = viewModel,
                                directArticleBack = key.source == ReadingSource.AiSummary,
                                onBack = onBack,
                                onNavigateToStylePage = { backStack.add(Route.ReadingPageStyle) },
                            )
                        }
                    }
                    //                    is Route.Reading -> {
                    //                        NavEntry(key) {
                    //                            val articleId = key.articleId
                    //
                    //                            val readingViewModel: ReadingViewModel =
                    //                                hiltViewModel<
                    //                                    ReadingViewModel,
                    //                                    ReadingViewModel.ReadingViewModelFactory,
                    //                                > { factory ->
                    //                                    factory.create(articleId.toString(), null)
                    //                                }
                    //
                    //                            ReadingPage(
                    //                                readingViewModel = readingViewModel,
                    //                                onBack = onBack,
                    //                                onNavigateToStylePage = {
                    // backStack.add(Route.ReadingPageStyle) },
                    //                            )
                    //                        }
                    //                    }
                    Route.Startup -> {
                        NavEntry(key) {
                            StartupPage(onNavigateToFeeds = { backStack.add(Route.Feeds) })
                        }
                    }
                    Route.Settings ->
                        NavEntry(key) {
                            SettingsPage(
                                onBack = onBack,
                                navigateToAccounts = { backStack.add(Route.Accounts) },
                                navigateToColorAndStyle = { backStack.add(Route.ColorAndStyle) },
                                navigateToInteraction = { backStack.add(Route.Interaction) },
                                navigateToLanguages = { backStack.add(Route.Languages) },
                                navigateToTroubleshooting = {
                                    backStack.add(Route.Troubleshooting)
                                },
                                navigateToTipsAndSupport = { backStack.add(Route.TipsAndSupport) },
                                navigateToAiSettings = { backStack.add(Route.AiSettings) },
                                navigateToReadingOptions = { backStack.add(Route.ReadingOptions) },
                                navigateToPodcastSettings = { backStack.add(Route.PodcastSettings) },
                                navigateToNotificationSettings = { backStack.add(Route.NotificationSettings) },
                                navigateToDataPrivacySettings = { backStack.add(Route.DataPrivacySettings) },
                            )
                        }
                    Route.AiSettings -> NavEntry(key) { AiSettingsPage(onBack = onBack) }
                    Route.ReadingOptions -> NavEntry(key) {
                        ReadingOptionsPage(
                            onBack = onBack,
                            navigateToCollections = { backStack.add(Route.CollectionManager) },
                            navigateToRules = { backStack.add(Route.RuleManager) },
                        )
                    }
                    Route.PodcastSettings -> NavEntry(key) {
                        PodcastSettingsPage(
                            onBack = onBack,
                            navigateToLibrary = { backStack.add(Route.PodcastLibrary) },
                        )
                    }
                    Route.NotificationSettings -> NavEntry(key) { NotificationSettingsPage(onBack = onBack) }
                    Route.DataPrivacySettings -> NavEntry(key) {
                        DataPrivacySettingsPage(
                            onBack = onBack,
                            navigateToDataTools = { backStack.add(Route.Troubleshooting) },
                            navigateToSyncStatus = { backStack.add(Route.SyncStatus) },
                        )
                    }
                    Route.SyncStatus -> NavEntry(key) {
                        SyncStatusPage(
                            onBack = onBack,
                            navigateToLogs = { backStack.add(Route.Troubleshooting) },
                        )
                    }
                    Route.PodcastLibrary -> NavEntry(key) {
                        PodcastLibraryPage(
                            onBack = onBack,
                            onOpenArticle = {
                                backStack.add(Route.Reading(it, ReadingSource.Flow))
                            },
                        )
                    }
                    Route.CollectionManager -> NavEntry(key) {
                        CollectionManagerPage(
                            onBack = onBack,
                            onOpenFlow = { backStack.add(Route.Reading(null)) },
                            onOpenArticle = {
                                backStack.add(Route.Reading(it, ReadingSource.Flow))
                            },
                        )
                    }
                    Route.RuleManager -> NavEntry(key) { RuleManagerPage(onBack = onBack) }
                    Route.Accounts ->
                        NavEntry(key) {
                            AccountsPage(
                                onBack = onBack,
                                navigateToAddAccount = { backStack.add(Route.AddAccounts) },
                                navigateToAccountDetails = {
                                    backStack.add(Route.AccountDetails(it))
                                },
                            )
                        }
                    is Route.AccountDetails ->
                        NavEntry(key) {
                            AccountDetailsPage(
                                viewModel =
                                    hiltViewModel<AccountViewModel>().also {
                                        it.initData(key.accountId)
                                    },
                                onBack = onBack,
                                navigateToFeeds = { backStack.add(Route.Feeds) },
                            )
                        }
                    Route.AddAccounts ->
                        NavEntry(key) {
                            AddAccountsPage(
                                onBack = onBack,
                                navigateToAccountDetails = {
                                    backStack.add(Route.AccountDetails(it))
                                },
                            )
                        }
                    Route.ColorAndStyle ->
                        NavEntry(key) {
                            ColorAndStylePage(
                                onBack = onBack,
                                navigateToDarkTheme = { backStack.add(Route.DarkTheme) },
                                navigateToFeedsPageStyle = { backStack.add(Route.FeedsPageStyle) },
                                navigateToFlowPageStyle = { backStack.add(Route.FlowPageStyle) },
                                navigateToReadingPageStyle = {
                                    backStack.add(Route.ReadingPageStyle)
                                },
                            )
                        }
                    Route.DarkTheme -> NavEntry(key) { DarkThemePage(onBack = onBack) }
                    Route.FeedsPageStyle -> NavEntry(key) { FeedsPageStylePage(onBack = onBack) }
                    Route.FlowPageStyle -> NavEntry(key) { FlowPageStylePage(onBack = onBack) }
                    Route.ReadingPageStyle ->
                        NavEntry(key) {
                            ReadingStylePage(
                                onBack = onBack,
                                navigateToReadingBoldCharacters = {
                                    backStack.add(Route.ReadingBoldCharacters)
                                },
                                navigateToReadingPageTitle = {
                                    backStack.add(Route.ReadingPageTitle)
                                },
                                navigateToReadingPageText = {
                                    backStack.add(Route.ReadingPageText)
                                },
                                navigateToReadingPageImage = {
                                    backStack.add(Route.ReadingPageImage)
                                },
                                navigateToReadingPageVideo = {
                                    backStack.add(Route.ReadingPageVideo)
                                },
                            )
                        }
                    Route.ReadingBoldCharacters ->
                        NavEntry(key) { BoldCharactersPage(onBack = onBack) }
                    Route.ReadingPageTitle -> NavEntry(key) { ReadingTitlePage(onBack = onBack) }
                    Route.ReadingPageText -> NavEntry(key) { ReadingTextPage(onBack = onBack) }
                    Route.ReadingPageImage -> NavEntry(key) { ReadingImagePage(onBack = onBack) }
                    Route.ReadingPageVideo -> NavEntry(key) { ReadingVideoPage(onBack = onBack) }
                    Route.Interaction -> NavEntry(key) { InteractionPage(onBack = onBack) }
                    Route.Languages -> NavEntry(key) { LanguagesPage(onBack = onBack) }
                    Route.Troubleshooting -> NavEntry(key) { TroubleshootingPage(onBack = onBack) }
                    Route.TipsAndSupport ->
                        NavEntry(key) {
                            TipsAndSupportPage(
                                onBack = onBack,
                                navigateToLicenseList = { backStack.add(Route.LicenseList) },
                            )
                        }
                    Route.LicenseList -> NavEntry(key) { LicenseListPage(onBack = onBack) }
                    else -> NavEntry(key) { throw Exception("Unknown destination") }
                }
                },
            )
            PodcastMiniPlayer(
                podcastPlayer,
                onNavigateToArticle = { articleId -> backStack.add(Route.Reading(articleId)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(WindowInsets.navigationBars.asPaddingValues())
                    .padding(end = 16.dp, start = 16.dp, bottom = miniPlayerBottomPadding),
            )
        }
    }
}
