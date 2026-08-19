package com.conice.morss.ui.page.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conice.morss.BuildConfig
import com.conice.morss.R
import com.conice.morss.infrastructure.android.getCurrentVersion
import com.conice.morss.infrastructure.preference.FeatureSettings
import com.conice.morss.infrastructure.preference.LocalNewVersionNumber
import com.conice.morss.infrastructure.preference.LocalSettings
import com.conice.morss.infrastructure.preference.LocalSkipVersionNumber
import com.conice.morss.infrastructure.preference.SkipVersionNumberPreference
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.toFeatureSettings
import com.conice.morss.ui.component.base.Banner
import com.conice.morss.ui.component.base.DisplayText
import com.conice.morss.ui.component.base.FeedbackIconButton
import com.conice.morss.ui.component.base.RYScaffold
import com.conice.morss.ui.page.settings.accounts.AccountViewModel
import com.conice.morss.ui.page.settings.ai.AiSettingsViewModel
import com.conice.morss.ui.page.settings.features.CacheSettingsViewModel
import com.conice.morss.ui.page.settings.tips.UpdateDialog
import com.conice.morss.ui.page.settings.tips.UpdateViewModel
import com.conice.morss.ui.theme.LayoutTokens
import com.conice.morss.ui.theme.palette.onLight
import kotlinx.coroutines.flow.map

@Composable
fun SettingsPage(
    updateViewModel: UpdateViewModel = hiltViewModel(),
    accountViewModel: AccountViewModel = hiltViewModel(),
    aiViewModel: AiSettingsViewModel = hiltViewModel(),
    cacheViewModel: CacheSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    navigateToAccounts: () -> Unit,
    navigateToColorAndStyle: (String?) -> Unit,
    navigateToInteraction: (String?) -> Unit,
    navigateToTipsAndSupport: () -> Unit,
    navigateToReadingOptions: (String?) -> Unit,
    navigateToPodcastSettings: (String?) -> Unit,
    navigateToDataPrivacySettings: (String?) -> Unit,
    navigateToAiSettings: (String?) -> Unit,
    navigateToNotificationSettings: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = LocalSettings.current
    val newVersion = LocalNewVersionNumber.current
    val skipVersion = LocalSkipVersionNumber.current
    val currentVersion = remember(context) { context.getCurrentVersion() }
    val accounts by accountViewModel.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val aiState by aiViewModel.state.collectAsStateWithLifecycle()
    val cacheUsage by cacheViewModel.usage.collectAsStateWithLifecycle()
    val featureSettingsFlow = remember(context) {
        context.dataStore.data.map { it.toFeatureSettings() }
    }
    val features by featureSettingsFlow.collectAsStateWithLifecycle(initialValue = FeatureSettings())
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(searchVisible) {
        if (searchVisible) focusRequester.requestFocus()
    }

    val accountsTitle = stringResource(R.string.settings_accounts_title)
    val readingTitle = stringResource(R.string.settings_reading_title)
    val aiTitle = stringResource(R.string.ai_settings)
    val podcastTitle = stringResource(R.string.settings_podcast_title)
    val notificationsTitle = stringResource(R.string.settings_notifications_title)
    val appearanceTitle = stringResource(R.string.appearance)
    val interactionTitle = stringResource(R.string.settings_general_title)
    val privacyTitle = stringResource(R.string.settings_privacy_title)
    val supportTitle = stringResource(R.string.settings_help_about_title)

    val accountsSummary = pluralStringResource(
        R.plurals.settings_accounts_count,
        accounts.size,
        accounts.size,
    )
    val readingSummary = if (features.markReadOnOpen) {
        stringResource(R.string.settings_reading_summary_opened)
    } else {
        stringResource(R.string.settings_reading_summary_manual)
    }
    val selectedAiModel = aiState.models.firstOrNull {
        it.id == aiState.articleBinding?.primaryModelId
    }?.displayName
    val aiSummary = when {
        !aiState.initialized -> stringResource(R.string.loading)
        aiState.connections.isEmpty() -> stringResource(R.string.settings_ai_not_configured)
        selectedAiModel != null -> selectedAiModel
        else -> pluralStringResource(
            R.plurals.settings_ai_connections_count,
            aiState.connections.size,
            aiState.connections.size,
        )
    }
    val podcastSummary = when {
        !features.podcastAutoDownload -> stringResource(R.string.settings_podcast_downloads_off)
        features.podcastWifiOnly -> stringResource(R.string.settings_podcast_downloads_wifi)
        else -> stringResource(R.string.settings_podcast_downloads_any_network)
    }
    val notificationsSummary = stringResource(
        if (features.notificationsEnabled) R.string.settings_notifications_enabled
        else R.string.settings_notifications_disabled,
    )
    val appearanceSummary = stringResource(
        R.string.settings_summary_pair,
        settings.darkTheme.toDesc(context),
        settings.basicFonts.toDesc(context),
    )
    val interactionSummary = stringResource(
        R.string.settings_starts_on,
        settings.initialPage.toDesc(context),
    )
    val storageBytes = cacheUsage.databaseBytes + cacheUsage.readerBytes +
        cacheUsage.aiBytes + cacheUsage.temporaryBytes
    val dataSummary = stringResource(R.string.settings_storage_used, formatSettingsBytes(storageBytes))

    val readingStatusPath = settingsPath(readingTitle, stringResource(R.string.settings_reading_status_section))
    val readingManagementPath = settingsPath(readingTitle, stringResource(R.string.settings_management_section))
    val aiConnectionsPath = settingsPath(aiTitle, stringResource(R.string.settings_ai_connections_section))
    val aiRoutingPath = settingsPath(aiTitle, stringResource(R.string.settings_ai_routing_section))
    val aiRequestPath = settingsPath(aiTitle, stringResource(R.string.settings_ai_request_section))
    val aiPrivacyPath = settingsPath(aiTitle, stringResource(R.string.settings_ai_privacy_section))
    val podcastPlaybackPath = settingsPath(podcastTitle, stringResource(R.string.settings_podcast_playback_section))
    val podcastDownloadsPath = settingsPath(podcastTitle, stringResource(R.string.settings_podcast_downloads_section))
    val podcastMetadataPath = settingsPath(podcastTitle, stringResource(R.string.settings_podcast_transcript_section))
    val notificationArticlesPath = settingsPath(notificationsTitle, stringResource(R.string.settings_notifications_articles_section))
    val notificationContentPath = settingsPath(notificationsTitle, stringResource(R.string.settings_notifications_content_section))
    val notificationSystemPath = settingsPath(notificationsTitle, stringResource(R.string.settings_notifications_system_section))
    val appearanceMainPath = settingsPath(appearanceTitle, stringResource(R.string.appearance))
    val appearanceStylePath = settingsPath(appearanceTitle, stringResource(R.string.style))
    val interactionStartPath = settingsPath(interactionTitle, stringResource(R.string.on_start))
    val interactionNavigationPath = settingsPath(interactionTitle, stringResource(R.string.navigation_section))
    val interactionFeedsPath = settingsPath(interactionTitle, stringResource(R.string.feeds_page))
    val interactionArticlesPath = settingsPath(interactionTitle, stringResource(R.string.article_list))
    val interactionReadingPath = settingsPath(interactionTitle, stringResource(R.string.reading_page))
    val interactionLinksPath = settingsPath(interactionTitle, stringResource(R.string.external_links))
    val interactionSharePath = settingsPath(interactionTitle, stringResource(R.string.share))
    val interactionSystemPath = settingsPath(interactionTitle, stringResource(R.string.settings_system_section))
    val dataSyncPath = settingsPath(privacyTitle, stringResource(R.string.settings_data_sync_section))
    val dataCachePath = settingsPath(privacyTitle, stringResource(R.string.settings_data_cache_section))
    val dataBackupPath = settingsPath(privacyTitle, stringResource(R.string.settings_data_backup_section))
    val dataDiagnosticsPath = settingsPath(privacyTitle, stringResource(R.string.settings_data_diagnostics_section))

    val searchEntries = listOf(
        SettingsSearchEntry(accountsTitle, accountsTitle, accountsSummary, listOf("sync"), SettingsDestination.Accounts),
        SettingsSearchEntry(stringResource(R.string.settings_mark_read_when_opened), readingStatusPath, enabledText(features.markReadOnOpen), destination = SettingsDestination.Reading, targetSetting = SettingKeys.ReadingMarkOpened),
        SettingsSearchEntry(stringResource(R.string.settings_mark_read_at_end), readingStatusPath, enabledText(features.markReadAtEnd), destination = SettingsDestination.Reading, targetSetting = SettingKeys.ReadingMarkEnd),
        SettingsSearchEntry(stringResource(R.string.settings_prefer_full_content), readingStatusPath, enabledText(features.preferFullContent), listOf("parse", "offline"), SettingsDestination.Reading, SettingKeys.ReadingFullContent),
        SettingsSearchEntry(title = stringResource(R.string.settings_tags_notes), path = readingManagementPath, destination = SettingsDestination.Reading, targetSetting = SettingKeys.ReadingTagsNotes),
        SettingsSearchEntry(title = stringResource(R.string.settings_automations), path = readingManagementPath, destination = SettingsDestination.Reading, targetSetting = SettingKeys.ReadingAutomations),
        SettingsSearchEntry(stringResource(R.string.settings_ai_connections_section), aiConnectionsPath, aiSummary, listOf("api", "Responses", "Gemini", "Anthropic"), SettingsDestination.Ai, SettingKeys.AiConnections),
        SettingsSearchEntry(stringResource(R.string.settings_ai_models_section), aiConnectionsPath, selectedAiModel, listOf("model"), SettingsDestination.Ai, SettingKeys.AiModels),
        SettingsSearchEntry(stringResource(R.string.settings_ai_routing_section), aiRoutingPath, selectedAiModel, destination = SettingsDestination.Ai, targetSetting = SettingKeys.AiRouting),
        SettingsSearchEntry(stringResource(R.string.settings_ai_prompts_section), aiRoutingPath, keywords = listOf("template"), destination = SettingsDestination.Ai, targetSetting = SettingKeys.AiPrompts),
        SettingsSearchEntry(stringResource(R.string.settings_ai_streaming), aiRequestPath, enabledText(features.aiStreamingEnabled), destination = SettingsDestination.Ai, targetSetting = SettingKeys.AiStreaming),
        SettingsSearchEntry(stringResource(R.string.settings_ai_timeout), aiRequestPath, stringResource(R.string.settings_minutes, features.aiTimeoutSeconds / 60), destination = SettingsDestination.Ai, targetSetting = SettingKeys.AiTimeout),
        SettingsSearchEntry(stringResource(R.string.settings_ai_content_scope), aiPrivacyPath, aiContentScopeText(features.aiContentScope), listOf("privacy", "sent"), SettingsDestination.Ai, SettingKeys.AiContentScope),
        SettingsSearchEntry(stringResource(R.string.settings_ai_include_link), aiPrivacyPath, enabledText(features.aiIncludeArticleLink), listOf("privacy", "url"), SettingsDestination.Ai, SettingKeys.AiIncludeLink),
        SettingsSearchEntry(stringResource(R.string.settings_podcast_default_speed), podcastPlaybackPath, "${features.podcastDefaultSpeed}x", destination = SettingsDestination.Podcast, targetSetting = SettingKeys.PodcastSpeed),
        SettingsSearchEntry(stringResource(R.string.settings_podcast_rewind), podcastPlaybackPath, stringResource(R.string.settings_seconds, features.podcastRewindSeconds), destination = SettingsDestination.Podcast, targetSetting = SettingKeys.PodcastRewind),
        SettingsSearchEntry(stringResource(R.string.settings_podcast_forward), podcastPlaybackPath, stringResource(R.string.settings_seconds, features.podcastForwardSeconds), destination = SettingsDestination.Podcast, targetSetting = SettingKeys.PodcastForward),
        SettingsSearchEntry(stringResource(R.string.settings_podcast_auto_download), podcastDownloadsPath, enabledText(features.podcastAutoDownload), destination = SettingsDestination.Podcast, targetSetting = SettingKeys.PodcastAutoDownload),
        SettingsSearchEntry(stringResource(R.string.settings_podcast_wifi_only), podcastDownloadsPath, enabledText(features.podcastWifiOnly), destination = SettingsDestination.Podcast, targetSetting = SettingKeys.PodcastWifiOnly),
        SettingsSearchEntry(stringResource(R.string.settings_podcast_cache_limit), podcastDownloadsPath, stringResource(R.string.settings_megabytes, features.podcastCacheMb), destination = SettingsDestination.Podcast, targetSetting = SettingKeys.PodcastCache),
        SettingsSearchEntry(stringResource(R.string.settings_podcast_transcript_auto), podcastMetadataPath, enabledText(features.podcastAutoTranscript), destination = SettingsDestination.Podcast, targetSetting = SettingKeys.PodcastTranscript),
        SettingsSearchEntry(stringResource(R.string.settings_notifications_enable_articles), notificationArticlesPath, enabledText(features.notificationsEnabled), destination = SettingsDestination.Notifications, targetSetting = SettingKeys.NotificationsEnabled),
        SettingsSearchEntry(stringResource(R.string.settings_notifications_articles_per_feed), notificationArticlesPath, features.notificationMaxArticles.toString(), destination = SettingsDestination.Notifications, targetSetting = SettingKeys.NotificationsMaxArticles),
        SettingsSearchEntry(stringResource(R.string.settings_notifications_open_article), notificationArticlesPath, enabledText(features.notificationOpenArticle), destination = SettingsDestination.Notifications, targetSetting = SettingKeys.NotificationsOpenArticle),
        SettingsSearchEntry(stringResource(R.string.settings_notifications_podcasts), notificationContentPath, enabledText(features.notificationPodcastEpisodes), destination = SettingsDestination.Notifications, targetSetting = SettingKeys.NotificationsPodcasts),
        SettingsSearchEntry(stringResource(R.string.settings_notifications_system), notificationSystemPath, keywords = listOf("sound", "vibration", "importance"), destination = SettingsDestination.Notifications, targetSetting = SettingKeys.NotificationsSystem),
        SettingsSearchEntry(stringResource(R.string.dark_theme), appearanceMainPath, settings.darkTheme.toDesc(context), destination = SettingsDestination.Appearance, targetSetting = SettingKeys.AppearanceDarkTheme),
        SettingsSearchEntry(stringResource(R.string.basic_fonts), appearanceMainPath, settings.basicFonts.toDesc(context), listOf("font"), SettingsDestination.Appearance, SettingKeys.AppearanceFonts),
        SettingsSearchEntry(title = stringResource(R.string.feeds_page), path = appearanceStylePath, destination = SettingsDestination.Appearance, targetSetting = SettingKeys.AppearanceFeeds),
        SettingsSearchEntry(title = stringResource(R.string.flow_page), path = appearanceStylePath, destination = SettingsDestination.Appearance, targetSetting = SettingKeys.AppearanceFlow),
        SettingsSearchEntry(
            title = stringResource(R.string.reading_page),
            path = appearanceStylePath,
            keywords = listOf("font size", "images", "video"),
            destination = SettingsDestination.Appearance,
            targetSetting = SettingKeys.AppearanceReading,
        ),
        SettingsSearchEntry(stringResource(R.string.initial_page), interactionStartPath, settings.initialPage.toDesc(context), destination = SettingsDestination.Interaction, targetSetting = SettingKeys.InteractionInitialPage),
        SettingsSearchEntry(stringResource(R.string.initial_filter), interactionStartPath, settings.initialFilter.toDesc(context), destination = SettingsDestination.Interaction, targetSetting = SettingKeys.InteractionInitialFilter),
        SettingsSearchEntry(stringResource(R.string.navigation_actions_title), interactionNavigationPath, destination = SettingsDestination.Interaction, targetSetting = SettingKeys.InteractionNavigation),
        SettingsSearchEntry(stringResource(R.string.hide_empty_groups), interactionFeedsPath, enabledText(settings.hideEmptyGroups.value), destination = SettingsDestination.Interaction, targetSetting = SettingKeys.InteractionHideEmpty),
        SettingsSearchEntry(stringResource(R.string.swipe_to_start), interactionArticlesPath, settings.swipeStartAction.desc, listOf("gesture"), SettingsDestination.Interaction, SettingKeys.InteractionSwipeStart),
        SettingsSearchEntry(stringResource(R.string.swipe_to_end), interactionArticlesPath, settings.swipeEndAction.desc, listOf("gesture"), SettingsDestination.Interaction, SettingKeys.InteractionSwipeEnd),
        SettingsSearchEntry(stringResource(R.string.sort_unread_articles), interactionArticlesPath, settings.flowSortUnreadArticles.description(), destination = SettingsDestination.Interaction, targetSetting = SettingKeys.InteractionSortUnread),
        SettingsSearchEntry(stringResource(R.string.mark_as_read_on_scroll), interactionArticlesPath, enabledText(settings.markAsReadOnScroll.value), destination = SettingsDestination.Interaction, targetSetting = SettingKeys.InteractionMarkScroll),
        SettingsSearchEntry(stringResource(R.string.pull_from_bottom), interactionArticlesPath, settings.pullToSwitchFeed.description(), destination = SettingsDestination.Interaction, targetSetting = SettingKeys.InteractionPullFeed),
        SettingsSearchEntry(stringResource(R.string.pull_to_switch_article), interactionReadingPath, enabledText(settings.pullToSwitchArticle.value), destination = SettingsDestination.Interaction, targetSetting = SettingKeys.InteractionPullArticle),
        SettingsSearchEntry(stringResource(R.string.initial_open_app), interactionLinksPath, settings.openLink.toDesc(context), destination = SettingsDestination.Interaction, targetSetting = SettingKeys.InteractionOpenLinks),
        SettingsSearchEntry(stringResource(R.string.open_link_specific_browser), interactionLinksPath, settings.openLinkSpecificBrowser.toDesc(context), destination = SettingsDestination.Interaction, targetSetting = SettingKeys.InteractionBrowser),
        SettingsSearchEntry(stringResource(R.string.shared_content), interactionSharePath, settings.sharedContent.toDesc(context), destination = SettingsDestination.Interaction, targetSetting = SettingKeys.InteractionShare),
        SettingsSearchEntry(stringResource(R.string.languages), interactionSystemPath, settings.languages.toDesc(), destination = SettingsDestination.Interaction, targetSetting = SettingKeys.InteractionLanguages),
        SettingsSearchEntry(stringResource(R.string.settings_data_duplicate_detection), dataSyncPath, duplicateDetectionText(features.deduplicationMode), destination = SettingsDestination.Data, targetSetting = SettingKeys.DataDuplicateDetection),
        SettingsSearchEntry(stringResource(R.string.settings_data_sync_full_content), dataSyncPath, enabledText(features.syncFullContent), destination = SettingsDestination.Data, targetSetting = SettingKeys.DataSyncFullContent),
        SettingsSearchEntry(stringResource(R.string.settings_data_confirm_cleanup), dataSyncPath, enabledText(features.cleanupConfirmation), destination = SettingsDestination.Data, targetSetting = SettingKeys.DataCleanupConfirmation),
        SettingsSearchEntry(stringResource(R.string.settings_data_database_storage), dataCachePath, formatSettingsBytes(cacheUsage.databaseBytes), destination = SettingsDestination.Data, targetSetting = SettingKeys.DataDatabaseStorage),
        SettingsSearchEntry(stringResource(R.string.settings_data_clear_temporary), dataCachePath, formatSettingsBytes(cacheUsage.temporaryBytes), destination = SettingsDestination.Data, targetSetting = SettingKeys.DataClearTemporary),
        SettingsSearchEntry(stringResource(R.string.settings_data_clear_article_cache), dataCachePath, formatSettingsBytes(cacheUsage.readerBytes), destination = SettingsDestination.Data, targetSetting = SettingKeys.DataClearArticle),
        SettingsSearchEntry(stringResource(R.string.settings_data_cleanup_articles), dataCachePath, keywords = listOf("retention", "old read"), destination = SettingsDestination.Data, targetSetting = SettingKeys.DataCleanArticles),
        SettingsSearchEntry(stringResource(R.string.settings_data_optimize), dataCachePath, keywords = listOf("database", "vacuum"), destination = SettingsDestination.Data, targetSetting = SettingKeys.DataOptimize),
        SettingsSearchEntry(stringResource(R.string.settings_data_backup), dataBackupPath, keywords = listOf("export", "import", "migration"), destination = SettingsDestination.Data, targetSetting = SettingKeys.DataBackup),
        SettingsSearchEntry(stringResource(R.string.settings_data_diagnostics), dataDiagnosticsPath, keywords = listOf("worker", "errors", "repair"), destination = SettingsDestination.Data, targetSetting = SettingKeys.DataDiagnostics),
        SettingsSearchEntry(supportTitle, supportTitle, BuildConfig.VERSION_NAME, listOf("about", "license", "sponsor", "version"), SettingsDestination.Support),
    )
    val results = searchEntries.searchSettings(searchQuery)

    fun openDestination(destination: SettingsDestination, target: String? = null) {
        keyboardController?.hide()
        when (destination) {
            SettingsDestination.Accounts -> navigateToAccounts()
            SettingsDestination.Reading -> navigateToReadingOptions(target)
            SettingsDestination.Ai -> navigateToAiSettings(target)
            SettingsDestination.Podcast -> navigateToPodcastSettings(target)
            SettingsDestination.Notifications -> navigateToNotificationSettings(target)
            SettingsDestination.Appearance -> navigateToColorAndStyle(target)
            SettingsDestination.Interaction -> navigateToInteraction(target)
            SettingsDestination.Data -> navigateToDataPrivacySettings(target)
            SettingsDestination.Support -> navigateToTipsAndSupport()
        }
    }

    RYScaffold(
        containerColor =
            MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        actions = {
            FeedbackIconButton(
                imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                contentDescription =
                    stringResource(if (searchVisible) R.string.close else R.string.search),
                tint = MaterialTheme.colorScheme.onSurface,
            ) {
                searchVisible = !searchVisible
                if (!searchVisible) {
                    searchQuery = ""
                    keyboardController?.hide()
                }
            }
        },
        content = {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 840.dp)
                        .fillMaxWidth(),
                    state = listState,
                ) {
                    item { DisplayText(text = stringResource(R.string.settings), desc = "") }
                    if (searchVisible) {
                        item {
                            OutlinedTextField(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = LayoutTokens.PageHorizontalPadding)
                                    .focusRequester(focusRequester),
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text(stringResource(R.string.settings_search)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                            )
                            Spacer(modifier = Modifier.height(LayoutTokens.ContentGap))
                        }
                    }
                    if (!searchVisible && newVersion.whetherNeedUpdate(currentVersion, skipVersion)) {
                        item {
                            Banner(
                                modifier = Modifier.zIndex(1f),
                                title = stringResource(R.string.get_new_updates),
                                desc = stringResource(
                                    R.string.get_new_updates_desc,
                                    newVersion.toString(),
                                ),
                                icon = Icons.Outlined.AutoAwesome,
                                action = {
                                    FeedbackIconButton(
                                        modifier = Modifier.size(20.dp),
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.skip_this_version),
                                        onClick = {
                                            SkipVersionNumberPreference.put(
                                                context,
                                                scope,
                                                newVersion.toString(),
                                            )
                                        },
                                    )
                                },
                                onClick = updateViewModel::showDialog,
                            )
                            Spacer(modifier = Modifier.height(LayoutTokens.ContentGap))
                        }
                    }

                    if (searchVisible && searchQuery.isNotBlank()) {
                        item { SettingsSectionTitle(stringResource(R.string.settings_search_results)) }
                        if (results.isEmpty()) {
                            item {
                                Text(
                                    modifier = Modifier.padding(
                                        horizontal = LayoutTokens.PageHorizontalPadding,
                                        vertical = LayoutTokens.SectionSpacing,
                                    ),
                                    text = stringResource(
                                        R.string.settings_no_search_results,
                                        searchQuery.trim(),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        } else {
                            items(results.size, key = { index ->
                                "${results[index].destination}:${results[index].targetSetting}:${results[index].title}"
                            }) { index ->
                                val result = results[index]
                                SettingItem(
                                    title = result.title,
                                    desc = result.currentValue?.let {
                                        stringResource(R.string.settings_search_result_value, result.path, it)
                                    } ?: result.path,
                                    type = SettingItemType.Navigation,
                                    onClick = {
                                        openDestination(result.destination, result.targetSetting)
                                    },
                                )
                            }
                        }
                    } else {
                        item { SettingsSectionTitle(stringResource(R.string.settings_content_services_section)) }
                        item {
                            SettingDestinationItem(accountsTitle, accountsSummary, Icons.Outlined.AccountCircle) {
                                openDestination(SettingsDestination.Accounts)
                            }
                            SettingDestinationItem(readingTitle, readingSummary, Icons.Outlined.MenuBook) {
                                openDestination(SettingsDestination.Reading)
                            }
                            SettingDestinationItem(aiTitle, aiSummary, Icons.Outlined.AutoAwesome) {
                                openDestination(SettingsDestination.Ai)
                            }
                            SettingDestinationItem(podcastTitle, podcastSummary, Icons.Outlined.Podcasts) {
                                openDestination(SettingsDestination.Podcast)
                            }
                            SettingDestinationItem(notificationsTitle, notificationsSummary, Icons.Outlined.Notifications) {
                                openDestination(SettingsDestination.Notifications)
                            }
                        }
                        item { SettingsSectionTitle(stringResource(R.string.settings_experience_section)) }
                        item {
                            SettingDestinationItem(appearanceTitle, appearanceSummary, Icons.Outlined.Palette) {
                                openDestination(SettingsDestination.Appearance)
                            }
                            SettingDestinationItem(interactionTitle, interactionSummary, Icons.Outlined.TouchApp) {
                                openDestination(SettingsDestination.Interaction)
                            }
                        }
                        item { SettingsSectionTitle(stringResource(R.string.settings_data_app_section)) }
                        item {
                            SettingDestinationItem(privacyTitle, dataSummary, Icons.Outlined.PrivacyTip) {
                                openDestination(SettingsDestination.Data)
                            }
                            SettingDestinationItem(supportTitle, BuildConfig.VERSION_NAME, Icons.Outlined.TipsAndUpdates) {
                                openDestination(SettingsDestination.Support)
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(LayoutTokens.SectionSpacing))
                        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }
        },
    )

    UpdateDialog()
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        modifier = Modifier.padding(
            start = LayoutTokens.PageHorizontalPadding,
            top = LayoutTokens.SectionSpacing,
            end = LayoutTokens.PageHorizontalPadding,
            bottom = LayoutTokens.SectionLabelVerticalPadding,
        ),
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun settingsPath(parent: String, section: String): String =
    stringResource(R.string.settings_search_path, parent, section)

@Composable
private fun SettingDestinationItem(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    SelectableSettingGroupItem(
        title = title,
        desc = desc,
        icon = icon,
        onClick = onClick,
    )
}

@Composable
private fun enabledText(enabled: Boolean): String =
    stringResource(if (enabled) R.string.enabled else R.string.disabled)

@Composable
private fun aiContentScopeText(scope: Int): String = stringResource(
    when (scope) {
        0 -> R.string.settings_ai_content_title_only
        1 -> R.string.settings_ai_content_title_description
        else -> R.string.settings_ai_content_displayed
    },
)

@Composable
private fun duplicateDetectionText(mode: Int): String = stringResource(
    when (mode) {
        0 -> R.string.settings_data_duplicate_id
        1 -> R.string.settings_data_duplicate_link
        else -> R.string.settings_data_duplicate_link_title_date
    },
)

internal fun formatSettingsBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    bytes < 1024L * 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    else -> "${bytes / (1024L * 1024L * 1024L)} GB"
}
