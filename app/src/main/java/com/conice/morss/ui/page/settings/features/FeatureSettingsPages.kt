package com.conice.morss.ui.page.settings.features

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.conice.morss.R
import com.conice.morss.infrastructure.preference.FeaturePreferenceKeys
import com.conice.morss.infrastructure.audio.PodcastPlaybackSpeeds
import com.conice.morss.infrastructure.preference.FeatureSettings
import com.conice.morss.infrastructure.preference.toFeatureSettings
import com.conice.morss.ui.component.base.DisplayText
import com.conice.morss.ui.component.base.FeedbackIconButton
import com.conice.morss.ui.component.base.RYScaffold
import com.conice.morss.infrastructure.android.showToast
import com.conice.morss.ui.component.base.RYSwitch
import com.conice.morss.ui.component.base.RadioDialog
import com.conice.morss.ui.component.base.RadioDialogOption
import com.conice.morss.ui.component.base.Subtitle
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.ui.page.settings.SettingItem
import com.conice.morss.ui.page.settings.SettingItemType
import com.conice.morss.ui.page.settings.SettingKeys
import com.conice.morss.ui.theme.palette.onLight

@Composable
fun ReadingOptionsPage(
    onBack: () -> Unit,
    targetSetting: String? = null,
    navigateToAiSettings: () -> Unit,
    navigateToCollections: () -> Unit,
    navigateToAutomations: () -> Unit,
) {
    FeatureSettingsPage(title = stringResource(R.string.settings_reading_title), onBack = onBack, targetSetting = targetSetting) { settings, write ->
        section(stringResource(R.string.ai_settings))
        action(
            stringResource(R.string.ai_settings),
            stringResource(R.string.settings_ai_entry_desc),
            type = SettingItemType.Navigation,
            onClick = navigateToAiSettings,
        )

        section(stringResource(R.string.settings_reading_status_section))
        toggle(stringResource(R.string.settings_mark_read_when_opened), settings.markReadOnOpen, settingKey = SettingKeys.ReadingMarkOpened) { write(FeaturePreferenceKeys.markReadOnOpen, it) }
        toggle(stringResource(R.string.settings_mark_read_at_end), settings.markReadAtEnd, settingKey = SettingKeys.ReadingMarkEnd) { write(FeaturePreferenceKeys.markReadAtEnd, it) }
        toggle(stringResource(R.string.settings_prefer_full_content), settings.preferFullContent, settingKey = SettingKeys.ReadingFullContent) { write(FeaturePreferenceKeys.preferFullContent, it) }

        section(stringResource(R.string.settings_management_section))
        action(stringResource(R.string.settings_tags_notes), stringResource(R.string.settings_manage_reading_data), settingKey = SettingKeys.ReadingTagsNotes, type = SettingItemType.Navigation, onClick = navigateToCollections)
        action(stringResource(R.string.settings_automations), stringResource(R.string.settings_automations_desc), settingKey = SettingKeys.ReadingAutomations, type = SettingItemType.Navigation, onClick = navigateToAutomations)
    }
}

@Composable
fun PodcastSettingsPage(
    onBack: () -> Unit,
    targetSetting: String? = null,
    navigateToLibrary: () -> Unit,
    navigateToNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val viewModel: PodcastSettingsViewModel = hiltViewModel()
    var confirmClearDownloads by remember { mutableStateOf(false) }
    FeatureSettingsPage(title = stringResource(R.string.settings_podcast_title), onBack = onBack, targetSetting = targetSetting) { settings, write ->
        section(stringResource(R.string.settings_notifications_title))
        action(
            stringResource(R.string.settings_notification_settings),
            stringResource(R.string.settings_notification_settings_desc),
            settingKey = SettingKeys.PodcastNotifications,
            type = SettingItemType.Navigation,
            onClick = navigateToNotifications,
        )

        section(stringResource(R.string.settings_podcast_playback_section))
        val rewindOptions = listOf(
            stringResource(R.string.settings_seconds, 10),
            stringResource(R.string.settings_seconds, 15),
            stringResource(R.string.settings_seconds, 30),
            stringResource(R.string.settings_seconds, 60),
        )
        val forwardOptions = listOf(
            stringResource(R.string.settings_seconds, 15),
            stringResource(R.string.settings_seconds, 30),
            stringResource(R.string.settings_seconds, 60),
            stringResource(R.string.settings_seconds, 90),
        )
        choice(stringResource(R.string.settings_podcast_default_speed), PodcastPlaybackSpeeds.indexOf(settings.podcastDefaultSpeed).coerceAtLeast(0), PodcastPlaybackSpeeds.map { "${it}x" }, settingKey = SettingKeys.PodcastSpeed) {
            write(FeaturePreferenceKeys.podcastDefaultSpeed, PodcastPlaybackSpeeds[it])
        }
        choice(stringResource(R.string.settings_podcast_rewind), listOf(10, 15, 30, 60).indexOf(settings.podcastRewindSeconds).coerceAtLeast(0), rewindOptions, settingKey = SettingKeys.PodcastRewind) {
            write(FeaturePreferenceKeys.podcastRewindSeconds, listOf(10, 15, 30, 60)[it])
        }
        choice(stringResource(R.string.settings_podcast_forward), listOf(15, 30, 60, 90).indexOf(settings.podcastForwardSeconds).coerceAtLeast(0), forwardOptions, settingKey = SettingKeys.PodcastForward) {
            write(FeaturePreferenceKeys.podcastForwardSeconds, listOf(15, 30, 60, 90)[it])
        }
        toggle(stringResource(R.string.settings_podcast_auto_play), settings.podcastAutoPlayNext, settingKey = SettingKeys.PodcastAutoPlay) { write(FeaturePreferenceKeys.podcastAutoPlayNext, it) }
        toggle(stringResource(R.string.settings_podcast_mark_played), settings.podcastMarkPlayed, settingKey = SettingKeys.PodcastMarkPlayed) { write(FeaturePreferenceKeys.podcastMarkPlayed, it) }
        toggle(stringResource(R.string.settings_podcast_remember_progress), settings.podcastRememberProgress, settingKey = SettingKeys.PodcastRememberProgress) { write(FeaturePreferenceKeys.podcastRememberProgress, it) }

        section(stringResource(R.string.settings_podcast_downloads_section))
        action(stringResource(R.string.settings_podcast_library), stringResource(R.string.settings_podcast_library_desc), settingKey = SettingKeys.PodcastLibrary, type = SettingItemType.Navigation, onClick = navigateToLibrary)
        toggle(stringResource(R.string.settings_podcast_auto_download), settings.podcastAutoDownload, settingKey = SettingKeys.PodcastAutoDownload) { write(FeaturePreferenceKeys.podcastAutoDownload, it) }
        toggle(stringResource(R.string.settings_podcast_wifi_only), settings.podcastWifiOnly, settings.podcastAutoDownload, settingKey = SettingKeys.PodcastWifiOnly) { write(FeaturePreferenceKeys.podcastWifiOnly, it) }
        choice(stringResource(R.string.settings_podcast_download_scope), settings.podcastDownloadScope, listOf(
            stringResource(R.string.settings_podcast_scope_all),
            stringResource(R.string.settings_podcast_scope_starred),
            stringResource(R.string.settings_podcast_scope_read_later),
        ), settings.podcastAutoDownload, settingKey = SettingKeys.PodcastDownloadScope) {
            write(FeaturePreferenceKeys.podcastDownloadScope, it)
        }
        choice(stringResource(R.string.settings_podcast_cache_limit), listOf(256, 512, 1024, 2048).indexOf(settings.podcastCacheMb).coerceAtLeast(1), listOf(
            stringResource(R.string.settings_megabytes, 256),
            stringResource(R.string.settings_megabytes, 512),
            stringResource(R.string.settings_gigabytes, 1),
            stringResource(R.string.settings_gigabytes, 2),
        ), settingKey = SettingKeys.PodcastCache) {
            write(FeaturePreferenceKeys.podcastCacheMb, listOf(256, 512, 1024, 2048)[it])
        }
        choice(stringResource(R.string.settings_podcast_keep_downloads), listOf(7, 30, 90, 0).indexOf(settings.podcastRetentionDays).coerceAtLeast(1), listOf(
            pluralStringResource(R.plurals.days, 7, 7),
            pluralStringResource(R.plurals.days, 30, 30),
            pluralStringResource(R.plurals.days, 90, 90),
            stringResource(R.string.settings_podcast_until_removed),
        ), settingKey = SettingKeys.PodcastRetention) {
            write(FeaturePreferenceKeys.podcastRetentionDays, listOf(7, 30, 90, 0)[it])
        }
        info(stringResource(R.string.settings_podcast_download_location), viewModel.downloadLocation, settingKey = SettingKeys.PodcastDownloadLocation)
        action(stringResource(R.string.settings_podcast_clear_downloads), stringResource(R.string.settings_podcast_clear_downloads_desc), settingKey = SettingKeys.PodcastClearDownloads, type = SettingItemType.Destructive) {
            if (settings.cleanupConfirmation) confirmClearDownloads = true
            else viewModel.clearDownloads { result ->
                context.showToast(resources.getString(if (result.isSuccess) R.string.settings_podcast_downloads_cleared else R.string.settings_podcast_downloads_clear_failed))
            }
        }

        section(stringResource(R.string.settings_podcast_transcript_section))
        toggle(stringResource(R.string.settings_podcast_transcript_auto), settings.podcastAutoTranscript, settingKey = SettingKeys.PodcastTranscript) { write(FeaturePreferenceKeys.podcastAutoTranscript, it) }
        toggle(stringResource(R.string.settings_podcast_metadata), settings.podcastShowEpisodeMetadata, settingKey = SettingKeys.PodcastMetadata) { write(FeaturePreferenceKeys.podcastShowEpisodeMetadata, it) }
    }
    if (confirmClearDownloads) {
        ConfirmationDialog(
            title = stringResource(R.string.settings_podcast_clear_downloads_title),
            text = stringResource(R.string.settings_podcast_clear_downloads_text),
            confirmLabel = stringResource(R.string.settings_action_clear),
            onDismiss = { confirmClearDownloads = false },
            onConfirm = {
                confirmClearDownloads = false
                viewModel.clearDownloads { result ->
                    context.showToast(resources.getString(if (result.isSuccess) R.string.settings_podcast_downloads_cleared else R.string.settings_podcast_downloads_clear_failed))
                }
            },
        )
    }
}

@Composable
fun NotificationSettingsPage(onBack: () -> Unit, targetSetting: String? = null) {
    val context = LocalContext.current
    FeatureSettingsPage(title = stringResource(R.string.settings_notifications_title), onBack = onBack, targetSetting = targetSetting) { settings, write ->
        section(stringResource(R.string.settings_notifications_articles_section))
        toggle(stringResource(R.string.settings_notifications_enable_articles), settings.notificationsEnabled, settingKey = SettingKeys.NotificationsEnabled) { write(FeaturePreferenceKeys.notificationsEnabled, it) }
        choice(stringResource(R.string.settings_notifications_articles_per_feed), listOf(1, 3, 5, 10).indexOf(settings.notificationMaxArticles).coerceAtLeast(2), listOf("1", "3", "5", "10"), settingKey = SettingKeys.NotificationsMaxArticles) {
            write(FeaturePreferenceKeys.notificationMaxArticles, listOf(1, 3, 5, 10)[it])
        }
        toggle(stringResource(R.string.settings_notifications_open_article), settings.notificationOpenArticle, settingKey = SettingKeys.NotificationsOpenArticle) { write(FeaturePreferenceKeys.notificationOpenArticle, it) }

        section(stringResource(R.string.settings_notifications_content_section))
        toggle(stringResource(R.string.settings_notifications_podcasts), settings.notificationPodcastEpisodes, settingKey = SettingKeys.NotificationsPodcasts) { write(FeaturePreferenceKeys.notificationPodcastEpisodes, it) }

        section(stringResource(R.string.settings_notifications_system_section))
        action(stringResource(R.string.settings_notifications_system), stringResource(R.string.settings_notifications_system_desc), settingKey = SettingKeys.NotificationsSystem, type = SettingItemType.External) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
                    Settings.EXTRA_APP_PACKAGE,
                    context.packageName,
                )
            )
        }
    }
}

@Composable
fun DataPrivacySettingsPage(
    onBack: () -> Unit,
    targetSetting: String? = null,
    navigateToBackupAndMigration: () -> Unit,
    navigateToDiagnosticDetails: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val viewModel: CacheSettingsViewModel = hiltViewModel()
    val cacheUsage by viewModel.usage.collectAsStateWithLifecycle()
    val storageOperationInProgress by viewModel.operationInProgress.collectAsStateWithLifecycle()
    var confirmClearAi by remember { mutableStateOf(false) }
    var confirmClearReader by remember { mutableStateOf(false) }
    var confirmCleanArticles by remember { mutableStateOf(false) }
    var confirmOptimizeDatabase by remember { mutableStateOf(false) }
    FeatureSettingsPage(title = stringResource(R.string.settings_privacy_title), onBack = onBack, targetSetting = targetSetting) { settings, write ->
        section(stringResource(R.string.settings_data_sync_section))
        choice(stringResource(R.string.settings_data_duplicate_detection), settings.deduplicationMode, listOf(
            stringResource(R.string.settings_data_duplicate_id),
            stringResource(R.string.settings_data_duplicate_link),
            stringResource(R.string.settings_data_duplicate_link_title_date),
        ), settingKey = SettingKeys.DataDuplicateDetection) {
            write(FeaturePreferenceKeys.deduplicationMode, it)
        }
        toggle(stringResource(R.string.settings_data_sync_full_content), settings.syncFullContent, settingKey = SettingKeys.DataSyncFullContent) { write(FeaturePreferenceKeys.syncFullContent, it) }
        toggle(stringResource(R.string.settings_data_confirm_cleanup), settings.cleanupConfirmation, settingKey = SettingKeys.DataCleanupConfirmation) { write(FeaturePreferenceKeys.cleanupConfirmation, it) }
        section(stringResource(R.string.settings_data_ai_cache_section))
        action(
            stringResource(R.string.settings_data_clear_ai_cache),
            stringResource(R.string.settings_data_cache_usage, cacheUsage.aiFiles, formatBytes(cacheUsage.aiBytes)),
            enabled = !storageOperationInProgress,
            settingKey = SettingKeys.DataClearAi,
            type = SettingItemType.Destructive,
            loading = storageOperationInProgress,
        ) {
            if (settings.cleanupConfirmation) confirmClearAi = true
            else viewModel.clearAiSummaryCache { success ->
                context.showToast(resources.getString(if (success) R.string.settings_data_ai_cache_cleared else R.string.settings_data_ai_cache_clear_failed))
            }
        }

        section(stringResource(R.string.settings_data_cache_section))
        val databaseDescription = if (cacheUsage.reclaimableDatabaseBytes > 0L) {
            "${formatBytes(cacheUsage.databaseBytes)} · ${stringResource(R.string.settings_data_reclaimable, formatBytes(cacheUsage.reclaimableDatabaseBytes))}"
        } else {
            formatBytes(cacheUsage.databaseBytes)
        }
        info(
            stringResource(R.string.settings_data_database_storage),
            databaseDescription,
            settingKey = SettingKeys.DataDatabaseStorage,
        )
        action(
            stringResource(R.string.settings_data_clear_temporary),
            stringResource(R.string.settings_data_cache_usage, cacheUsage.temporaryFiles, formatBytes(cacheUsage.temporaryBytes)),
            enabled = !storageOperationInProgress,
            settingKey = SettingKeys.DataClearTemporary,
            type = SettingItemType.Destructive,
            loading = storageOperationInProgress,
        ) {
            viewModel.clearTemporaryCache { success ->
                context.showToast(
                    resources.getString(if (success) R.string.settings_data_temporary_cleared else R.string.settings_data_temporary_clear_failed)
                )
            }
        }
        action(
            stringResource(R.string.settings_data_clear_article_cache),
            stringResource(R.string.settings_data_cache_usage, cacheUsage.readerFiles, formatBytes(cacheUsage.readerBytes)),
            enabled = !storageOperationInProgress,
            settingKey = SettingKeys.DataClearArticle,
            type = SettingItemType.Destructive,
            loading = storageOperationInProgress,
        ) {
            if (settings.cleanupConfirmation) confirmClearReader = true
            else viewModel.clearReaderCache { success ->
                context.showToast(resources.getString(if (success) R.string.settings_data_article_cache_cleared else R.string.settings_data_article_cache_clear_failed))
            }
        }
        action(
            stringResource(R.string.settings_data_cleanup_articles),
            stringResource(R.string.settings_data_cleanup_articles_desc),
            enabled = !storageOperationInProgress,
            settingKey = SettingKeys.DataCleanArticles,
            type = SettingItemType.Destructive,
            loading = storageOperationInProgress,
        ) {
            if (settings.cleanupConfirmation) confirmCleanArticles = true
            else viewModel.cleanOldReadArticles { result ->
                context.showToast(
                    result.fold(
                        onSuccess = { resources.getQuantityString(R.plurals.settings_data_articles_removed, it, it) },
                        onFailure = { resources.getString(R.string.settings_data_cleanup_articles_failed) },
                    )
                )
            }
        }
        action(
            stringResource(R.string.settings_data_optimize),
            stringResource(R.string.settings_data_optimize_desc),
            enabled = !storageOperationInProgress,
            settingKey = SettingKeys.DataOptimize,
            type = SettingItemType.Action,
            loading = storageOperationInProgress,
        ) { confirmOptimizeDatabase = true }

        section(stringResource(R.string.settings_data_backup_section))
        action(
            stringResource(R.string.settings_data_backup),
            stringResource(R.string.settings_data_backup_desc),
            settingKey = SettingKeys.DataBackup,
            type = SettingItemType.Navigation,
            onClick = navigateToBackupAndMigration,
        )

        section(stringResource(R.string.settings_data_diagnostics_section))
        action(
            stringResource(R.string.settings_data_diagnostics),
            stringResource(R.string.settings_data_diagnostics_desc),
            settingKey = SettingKeys.DataDiagnostics,
            type = SettingItemType.Navigation,
            onClick = navigateToDiagnosticDetails,
        )
        toggle(stringResource(R.string.settings_data_include_feed_urls), settings.diagnosticIncludeFeedUrls, settingKey = SettingKeys.DataDiagnosticUrls) { write(FeaturePreferenceKeys.diagnosticIncludeFeedUrls, it) }
    }
    if (confirmClearAi) {
        ConfirmationDialog(
            title = stringResource(R.string.settings_data_clear_ai_cache_title),
            text = stringResource(R.string.settings_data_clear_ai_cache_text),
            confirmLabel = stringResource(R.string.settings_action_clear),
            onDismiss = { confirmClearAi = false },
            onConfirm = {
                confirmClearAi = false
                viewModel.clearAiSummaryCache { success ->
                    context.showToast(resources.getString(if (success) R.string.settings_data_ai_cache_cleared else R.string.settings_data_ai_cache_clear_failed))
                }
            },
        )
    }
    if (confirmClearReader) {
        ConfirmationDialog(
            title = stringResource(R.string.settings_data_clear_article_title),
            text = stringResource(R.string.settings_data_clear_article_text),
            confirmLabel = stringResource(R.string.settings_action_clear),
            onDismiss = { confirmClearReader = false },
            onConfirm = {
                confirmClearReader = false
                viewModel.clearReaderCache { success ->
                    context.showToast(resources.getString(if (success) R.string.settings_data_article_cache_cleared else R.string.settings_data_article_cache_clear_failed))
                }
            },
        )
    }
    if (confirmCleanArticles) {
        ConfirmationDialog(
            title = stringResource(R.string.settings_data_cleanup_articles_title),
            text = stringResource(R.string.settings_data_cleanup_articles_text),
            confirmLabel = stringResource(R.string.settings_action_clean_up),
            onDismiss = { confirmCleanArticles = false },
            onConfirm = {
                confirmCleanArticles = false
                viewModel.cleanOldReadArticles { result ->
                    context.showToast(
                        result.fold(
                            onSuccess = { resources.getQuantityString(R.plurals.settings_data_articles_removed, it, it) },
                            onFailure = { resources.getString(R.string.settings_data_cleanup_articles_failed) },
                        )
                    )
                }
            },
        )
    }
    if (confirmOptimizeDatabase) {
        ConfirmationDialog(
            title = stringResource(R.string.settings_data_optimize_title),
            text = stringResource(R.string.settings_data_optimize_text),
            confirmLabel = stringResource(R.string.settings_action_optimize),
            onDismiss = { confirmOptimizeDatabase = false },
            onConfirm = {
                confirmOptimizeDatabase = false
                viewModel.optimizeDatabases { result ->
                    context.showToast(
                        resources.getString(if (result.isSuccess) R.string.settings_data_optimized else R.string.settings_data_optimize_failed)
                    )
                }
            },
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> "${bytes / (1024L * 1024L)} MB"
}

@Composable
private fun ConfirmationDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private class FeaturePageScope(
    private val settings: FeatureSettings,
    private val writeValue: (Preferences.Key<*>, Any) -> Unit,
    private val targetSetting: String?,
) {
    @Composable fun section(title: String) {
        Spacer(Modifier.height(20.dp))
        Subtitle(modifier = Modifier.padding(horizontal = 24.dp), text = title)
    }

    @Composable fun toggle(
        title: String,
        value: Boolean,
        enabled: Boolean = true,
        settingKey: String? = null,
        onChange: (Boolean) -> Unit,
    ) {
        SettingItem(
            enabled = enabled,
            title = title,
            settingKey = settingKey,
            targetKey = targetSetting,
            highlighted = settingKey == targetSetting,
            onClick = { onChange(!value) },
        ) {
            RYSwitch(activated = value, enable = enabled) { onChange(!value) }
        }
    }

    @Composable fun choice(
        title: String,
        selected: Int,
        options: List<String>,
        enabled: Boolean = true,
        settingKey: String? = null,
        onChange: (Int) -> Unit,
    ) {
        val safeIndex = selected.coerceIn(0, options.lastIndex)
        var dialogVisible by remember(title) { mutableStateOf(false) }
        SettingItem(
            enabled = enabled,
            title = title,
            desc = options[safeIndex],
            type = SettingItemType.Choice,
            settingKey = settingKey,
            targetKey = targetSetting,
            highlighted = settingKey == targetSetting,
            onClick = { dialogVisible = true },
        ) {}
        RadioDialog(
            visible = dialogVisible,
            title = title,
            options = options.mapIndexed { index, option ->
                RadioDialogOption(
                    text = option,
                    selected = index == safeIndex,
                    onClick = { onChange(index) },
                )
            },
            onDismissRequest = { dialogVisible = false },
        )
    }

    @Composable fun action(
        title: String,
        description: String,
        settingKey: String? = null,
        type: SettingItemType = SettingItemType.Action,
        loading: Boolean = false,
        onClick: () -> Unit,
    ) {
        action(
            title = title,
            description = description,
            enabled = true,
            settingKey = settingKey,
            type = type,
            loading = loading,
            onClick = onClick,
        )
    }

    @Composable fun action(
        title: String,
        description: String,
        enabled: Boolean,
        settingKey: String? = null,
        type: SettingItemType = SettingItemType.Action,
        loading: Boolean = false,
        onClick: () -> Unit,
    ) {
        SettingItem(
            enabled = enabled,
            title = title,
            desc = description,
            type = type,
            loading = loading,
            settingKey = settingKey,
            targetKey = targetSetting,
            highlighted = settingKey == targetSetting,
            onClick = onClick,
        ) {}
    }

    @Composable fun info(title: String, description: String, settingKey: String? = null) {
        SettingItem(
            enabled = true,
            title = title,
            desc = description,
            type = SettingItemType.Information,
            settingKey = settingKey,
            targetKey = targetSetting,
            highlighted = settingKey == targetSetting,
            onClick = {},
        ) {}
    }

    fun write(key: Preferences.Key<*>, value: Any) = writeValue(key, value)
}

@Composable
private fun FeatureSettingsPage(
    title: String,
    onBack: () -> Unit,
    targetSetting: String? = null,
    content: @Composable FeaturePageScope.(FeatureSettings, (Preferences.Key<*>, Any) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsFlow = remember(context) {
        context.dataStore.data.map { it.toFeatureSettings() }
    }
    val settings by settingsFlow
        .collectAsStateWithLifecycle(initialValue = FeatureSettings())
    val writer: (Preferences.Key<*>, Any) -> Unit = { key, value ->
        scope.launch {
            context.dataStore.edit { preferences ->
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is Boolean -> preferences[key as Preferences.Key<Boolean>] = value
                    is Int -> preferences[key as Preferences.Key<Int>] = value
                    is Float -> preferences[key as Preferences.Key<Float>] = value
                    is String -> preferences[key as Preferences.Key<String>] = value
                }
            }
        }
    }
    RYScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        content = {
            LazyColumn {
                item { DisplayText(text = title, desc = "") }
                item {
                    with(FeaturePageScope(settings, writer, targetSetting)) {
                        content(settings, writer)
                    }
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )
}
