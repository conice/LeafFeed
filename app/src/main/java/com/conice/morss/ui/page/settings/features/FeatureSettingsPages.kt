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
import com.conice.morss.ui.theme.palette.onLight

@Composable
fun ReadingOptionsPage(
    onBack: () -> Unit,
    navigateToAiSettings: () -> Unit,
    navigateToCollections: () -> Unit,
    navigateToAutomations: () -> Unit,
) {
    FeatureSettingsPage(title = "Reading and articles", onBack = onBack) { settings, write ->
        section("AI")
        action("AI settings", "Models, prompts and request behavior", navigateToAiSettings)

        section("Reading status")
        toggle("Mark read when opened", settings.markReadOnOpen) { write(FeaturePreferenceKeys.markReadOnOpen, it) }
        toggle("Mark read at the end", settings.markReadAtEnd) { write(FeaturePreferenceKeys.markReadAtEnd, it) }
        toggle("Prefer parsed full content", settings.preferFullContent) { write(FeaturePreferenceKeys.preferFullContent, it) }

        section("Article controls")

        section("Management")
        action("Tags and notes", "Manage reading data", navigateToCollections)
        action("Automations", "Conditions, actions and execution history", navigateToAutomations)
    }
}

@Composable
fun PodcastSettingsPage(
    onBack: () -> Unit,
    navigateToLibrary: () -> Unit,
    navigateToNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: PodcastSettingsViewModel = hiltViewModel()
    var confirmClearDownloads by remember { mutableStateOf(false) }
    FeatureSettingsPage(title = "Podcasts and notifications", onBack = onBack) { settings, write ->
        section("Notifications")
        action(
            "Notification settings",
            "New articles, podcasts and system controls",
            navigateToNotifications,
        )

        section("Playback")
        choice("Default speed", PodcastPlaybackSpeeds.indexOf(settings.podcastDefaultSpeed).coerceAtLeast(0), PodcastPlaybackSpeeds.map { "${it}x" }) {
            write(FeaturePreferenceKeys.podcastDefaultSpeed, PodcastPlaybackSpeeds[it])
        }
        choice("Rewind interval", listOf(10, 15, 30, 60).indexOf(settings.podcastRewindSeconds).coerceAtLeast(0), listOf("10 seconds", "15 seconds", "30 seconds", "60 seconds")) {
            write(FeaturePreferenceKeys.podcastRewindSeconds, listOf(10, 15, 30, 60)[it])
        }
        choice("Forward interval", listOf(15, 30, 60, 90).indexOf(settings.podcastForwardSeconds).coerceAtLeast(0), listOf("15 seconds", "30 seconds", "60 seconds", "90 seconds")) {
            write(FeaturePreferenceKeys.podcastForwardSeconds, listOf(15, 30, 60, 90)[it])
        }
        toggle("Play next episode automatically", settings.podcastAutoPlayNext) { write(FeaturePreferenceKeys.podcastAutoPlayNext, it) }
        toggle("Mark completed episodes as played", settings.podcastMarkPlayed) { write(FeaturePreferenceKeys.podcastMarkPlayed, it) }
        toggle("Remember playback position", settings.podcastRememberProgress) { write(FeaturePreferenceKeys.podcastRememberProgress, it) }

        section("Downloads")
        action("Podcast library", "Browse unplayed and downloaded episodes", navigateToLibrary)
        toggle("Automatically download new episodes", settings.podcastAutoDownload) { write(FeaturePreferenceKeys.podcastAutoDownload, it) }
        toggle("Download on Wi-Fi only", settings.podcastWifiOnly, settings.podcastAutoDownload) { write(FeaturePreferenceKeys.podcastWifiOnly, it) }
        choice("Automatic download scope", settings.podcastDownloadScope, listOf("All new episodes", "Starred only", "Read later only"), settings.podcastAutoDownload) {
            write(FeaturePreferenceKeys.podcastDownloadScope, it)
        }
        choice("Maximum cache", listOf(256, 512, 1024, 2048).indexOf(settings.podcastCacheMb).coerceAtLeast(1), listOf("256 MB", "512 MB", "1 GB", "2 GB")) {
            write(FeaturePreferenceKeys.podcastCacheMb, listOf(256, 512, 1024, 2048)[it])
        }
        choice("Keep downloads", listOf(7, 30, 90, 0).indexOf(settings.podcastRetentionDays).coerceAtLeast(1), listOf("7 days", "30 days", "90 days", "Until manually removed")) {
            write(FeaturePreferenceKeys.podcastRetentionDays, listOf(7, 30, 90, 0)[it])
        }
        info("Download location", viewModel.downloadLocation)
        action("Clear podcast downloads", "Remove all downloaded episode files") {
            if (settings.cleanupConfirmation) confirmClearDownloads = true
            else viewModel.clearDownloads { result ->
                context.showToast(if (result.isSuccess) "Podcast downloads cleared" else "Unable to clear downloads")
            }
        }

        section("Transcript and metadata")
        toggle("Load transcripts automatically", settings.podcastAutoTranscript) { write(FeaturePreferenceKeys.podcastAutoTranscript, it) }
        toggle("Show season, episode and explicit labels", settings.podcastShowEpisodeMetadata) { write(FeaturePreferenceKeys.podcastShowEpisodeMetadata, it) }
    }
    if (confirmClearDownloads) {
        ConfirmationDialog(
            title = "Clear podcast downloads?",
            text = "All downloaded episode files will be removed.",
            onDismiss = { confirmClearDownloads = false },
            onConfirm = {
                confirmClearDownloads = false
                viewModel.clearDownloads { result ->
                    context.showToast(if (result.isSuccess) "Podcast downloads cleared" else "Unable to clear downloads")
                }
            },
        )
    }
}

@Composable
fun NotificationSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    FeatureSettingsPage(title = "Notifications", onBack = onBack) { settings, write ->
        section("New articles")
        toggle("Enable new article notifications", settings.notificationsEnabled) { write(FeaturePreferenceKeys.notificationsEnabled, it) }
        choice("Articles shown per feed", listOf(1, 3, 5, 10).indexOf(settings.notificationMaxArticles).coerceAtLeast(2), listOf("1", "3", "5", "10")) {
            write(FeaturePreferenceKeys.notificationMaxArticles, listOf(1, 3, 5, 10)[it])
        }
        toggle("Open the article directly", settings.notificationOpenArticle) { write(FeaturePreferenceKeys.notificationOpenArticle, it) }

        section("Content selection")
        toggle("Notify new podcast episodes", settings.notificationPodcastEpisodes) { write(FeaturePreferenceKeys.notificationPodcastEpisodes, it) }

        section("System controls")
        action("Sound, vibration and importance", "Managed by Android notification settings") {
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
    navigateToBackupAndMigration: () -> Unit,
    navigateToDiagnosticDetails: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: CacheSettingsViewModel = hiltViewModel()
    val cacheUsage by viewModel.usage.collectAsStateWithLifecycle()
    val storageOperationInProgress by viewModel.operationInProgress.collectAsStateWithLifecycle()
    var confirmClearAi by remember { mutableStateOf(false) }
    var confirmClearReader by remember { mutableStateOf(false) }
    var confirmCleanArticles by remember { mutableStateOf(false) }
    var confirmOptimizeDatabase by remember { mutableStateOf(false) }
    FeatureSettingsPage(title = "Data and privacy", onBack = onBack) { settings, write ->
        section("Synchronization and storage")
        choice("Duplicate detection", settings.deduplicationMode, listOf("Article ID", "Normalized link", "Link, title and date")) {
            write(FeaturePreferenceKeys.deduplicationMode, it)
        }
        toggle("Fetch full content during sync", settings.syncFullContent) { write(FeaturePreferenceKeys.syncFullContent, it) }
        toggle("Confirm before destructive cleanup", settings.cleanupConfirmation) { write(FeaturePreferenceKeys.cleanupConfirmation, it) }
        section("AI privacy")
        choice("Content sent for article summaries", settings.aiContentScope, listOf("Title only", "Title and description", "Displayed article content")) {
            write(FeaturePreferenceKeys.aiContentScope, it)
        }
        toggle("Include the article link", settings.aiIncludeArticleLink) { write(FeaturePreferenceKeys.aiIncludeArticleLink, it) }
        action(
            "Clear AI summary cache",
            "${cacheUsage.aiFiles} files, ${formatBytes(cacheUsage.aiBytes)}",
            enabled = !storageOperationInProgress,
        ) {
            if (settings.cleanupConfirmation) confirmClearAi = true
            else viewModel.clearAiSummaryCache { success ->
                context.showToast(if (success) "AI summary cache cleared" else "Unable to clear AI summary cache")
            }
        }

        section("Cache controls")
        info(
            "Database storage",
            buildString {
                append(formatBytes(cacheUsage.databaseBytes))
                if (cacheUsage.reclaimableDatabaseBytes > 0L) {
                    append("; ")
                    append(formatBytes(cacheUsage.reclaimableDatabaseBytes))
                    append(" reclaimable")
                }
            },
        )
        action(
            "Clear temporary cache",
            "${cacheUsage.temporaryFiles} files, ${formatBytes(cacheUsage.temporaryBytes)}",
            enabled = !storageOperationInProgress,
        ) {
            viewModel.clearTemporaryCache { success ->
                context.showToast(
                    if (success) "Temporary cache cleared" else "Unable to clear temporary cache"
                )
            }
        }
        action(
            "Clear parsed article cache",
            "${cacheUsage.readerFiles} files, ${formatBytes(cacheUsage.readerBytes)}",
            enabled = !storageOperationInProgress,
        ) {
            if (settings.cleanupConfirmation) confirmClearReader = true
            else viewModel.clearReaderCache { success ->
                context.showToast(if (success) "Article cache cleared" else "Unable to clear article cache")
            }
        }
        action(
            "Clean up old read articles",
            "Uses account retention; keeps unread, starred and Read later articles",
            enabled = !storageOperationInProgress,
        ) {
            if (settings.cleanupConfirmation) confirmCleanArticles = true
            else viewModel.cleanOldReadArticles { result ->
                context.showToast(
                    result.fold(
                        onSuccess = { "$it old articles removed" },
                        onFailure = { "Unable to clean up old articles" },
                    )
                )
            }
        }
        action(
            "Optimize database",
            "Reclaim unused space after cleanup",
            enabled = !storageOperationInProgress,
        ) { confirmOptimizeDatabase = true }

        section("Backups")
        action(
            "Backup and migration",
            "Preferences, tags, notes and saved searches",
            navigateToBackupAndMigration,
        )

        section("Diagnostics")
        action(
            "Diagnostic details",
            "Worker information, errors and repair tools",
            navigateToDiagnosticDetails,
        )
        toggle("Include feed addresses in diagnostics", settings.diagnosticIncludeFeedUrls) { write(FeaturePreferenceKeys.diagnosticIncludeFeedUrls, it) }
    }
    if (confirmClearAi) {
        ConfirmationDialog(
            title = "Clear AI summary cache?",
            text = "Locally cached AI responses will be removed.",
            onDismiss = { confirmClearAi = false },
            onConfirm = {
                confirmClearAi = false
                viewModel.clearAiSummaryCache { success ->
                    context.showToast(if (success) "AI summary cache cleared" else "Unable to clear AI summary cache")
                }
            },
        )
    }
    if (confirmClearReader) {
        ConfirmationDialog(
            title = "Clear parsed article cache?",
            text = "Downloaded full article content will be removed and can be fetched again.",
            onDismiss = { confirmClearReader = false },
            onConfirm = {
                confirmClearReader = false
                viewModel.clearReaderCache { success ->
                    context.showToast(if (success) "Article cache cleared" else "Unable to clear article cache")
                }
            },
        )
    }
    if (confirmCleanArticles) {
        ConfirmationDialog(
            title = "Clean up old read articles?",
            text = "Articles older than the account retention period will be removed. Unread, starred and Read later articles are preserved.",
            onDismiss = { confirmCleanArticles = false },
            onConfirm = {
                confirmCleanArticles = false
                viewModel.cleanOldReadArticles { result ->
                    context.showToast(
                        result.fold(
                            onSuccess = { "$it old articles removed" },
                            onFailure = { "Unable to clean up old articles" },
                        )
                    )
                }
            },
        )
    }
    if (confirmOptimizeDatabase) {
        ConfirmationDialog(
            title = "Optimize database?",
            text = "Synchronization and search may pause while unused database space is reclaimed.",
            onDismiss = { confirmOptimizeDatabase = false },
            onConfirm = {
                confirmOptimizeDatabase = false
                viewModel.optimizeDatabases { result ->
                    context.showToast(
                        if (result.isSuccess) "Database optimized" else "Unable to optimize database"
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
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private class FeaturePageScope(
    private val settings: FeatureSettings,
    private val writeValue: (Preferences.Key<*>, Any) -> Unit,
) {
    @Composable fun section(title: String) {
        Spacer(Modifier.height(20.dp))
        Subtitle(modifier = Modifier.padding(horizontal = 24.dp), text = title)
    }

    @Composable fun toggle(
        title: String,
        value: Boolean,
        enabled: Boolean = true,
        onChange: (Boolean) -> Unit,
    ) {
        SettingItem(enabled = enabled, title = title, onClick = { onChange(!value) }) {
            RYSwitch(activated = value, enable = enabled) { onChange(!value) }
        }
    }

    @Composable fun choice(
        title: String,
        selected: Int,
        options: List<String>,
        enabled: Boolean = true,
        onChange: (Int) -> Unit,
    ) {
        val safeIndex = selected.coerceIn(0, options.lastIndex)
        var dialogVisible by remember(title) { mutableStateOf(false) }
        SettingItem(
            enabled = enabled,
            title = title,
            desc = options[safeIndex],
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
        onClick: () -> Unit,
    ) {
        action(title, description, enabled = true, onClick = onClick)
    }

    @Composable fun action(
        title: String,
        description: String,
        enabled: Boolean,
        onClick: () -> Unit,
    ) {
        SettingItem(enabled = enabled, title = title, desc = description, onClick = onClick) {}
    }

    @Composable fun info(title: String, description: String) {
        SettingItem(enabled = false, title = title, desc = description, onClick = {}) {}
    }

    fun write(key: Preferences.Key<*>, value: Any) = writeValue(key, value)
}

@Composable
private fun FeatureSettingsPage(
    title: String,
    onBack: () -> Unit,
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
                    with(FeaturePageScope(settings, writer)) {
                        content(settings, writer)
                    }
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )
}
