package com.conice.morss.ui.page.settings

internal object SettingKeys {
    const val ReadingMarkOpened = "reading.mark_opened"
    const val ReadingMarkEnd = "reading.mark_end"
    const val ReadingFullContent = "reading.full_content"
    const val ReadingTagsNotes = "reading.tags_notes"
    const val ReadingAutomations = "reading.automations"

    const val AiConnections = "ai.connections"
    const val AiModels = "ai.models"
    const val AiRouting = "ai.routing"
    const val AiPrompts = "ai.prompts"
    const val AiStreaming = "ai.streaming"
    const val AiTimeout = "ai.timeout"
    const val AiContentScope = "ai.content_scope"
    const val AiIncludeLink = "ai.include_link"

    const val PodcastNotifications = "podcast.notifications"
    const val PodcastSpeed = "podcast.speed"
    const val PodcastRewind = "podcast.rewind"
    const val PodcastForward = "podcast.forward"
    const val PodcastAutoPlay = "podcast.auto_play"
    const val PodcastMarkPlayed = "podcast.mark_played"
    const val PodcastRememberProgress = "podcast.remember_progress"
    const val PodcastLibrary = "podcast.library"
    const val PodcastAutoDownload = "podcast.auto_download"
    const val PodcastWifiOnly = "podcast.wifi_only"
    const val PodcastDownloadScope = "podcast.download_scope"
    const val PodcastCache = "podcast.cache"
    const val PodcastRetention = "podcast.retention"
    const val PodcastDownloadLocation = "podcast.download_location"
    const val PodcastClearDownloads = "podcast.clear_downloads"
    const val PodcastTranscript = "podcast.transcript"
    const val PodcastMetadata = "podcast.metadata"

    const val NotificationsEnabled = "notifications.enabled"
    const val NotificationsMaxArticles = "notifications.max_articles"
    const val NotificationsOpenArticle = "notifications.open_article"
    const val NotificationsPodcasts = "notifications.podcasts"
    const val NotificationsSystem = "notifications.system"

    const val AppearanceDarkTheme = "appearance.dark_theme"
    const val AppearanceFonts = "appearance.fonts"
    const val AppearanceFeeds = "appearance.feeds"
    const val AppearanceFlow = "appearance.flow"
    const val AppearanceReading = "appearance.reading"

    const val InteractionInitialPage = "interaction.initial_page"
    const val InteractionInitialFilter = "interaction.initial_filter"
    const val InteractionNavigation = "interaction.navigation"
    const val InteractionHideEmpty = "interaction.hide_empty"
    const val InteractionSwipeStart = "interaction.swipe_start"
    const val InteractionSwipeEnd = "interaction.swipe_end"
    const val InteractionSortUnread = "interaction.sort_unread"
    const val InteractionMarkScroll = "interaction.mark_scroll"
    const val InteractionPullFeed = "interaction.pull_feed"
    const val InteractionPullArticle = "interaction.pull_article"
    const val InteractionOpenLinks = "interaction.open_links"
    const val InteractionBrowser = "interaction.browser"
    const val InteractionShare = "interaction.share"
    const val InteractionLanguages = "interaction.languages"

    const val DataDuplicateDetection = "data.duplicate_detection"
    const val DataSyncFullContent = "data.sync_full_content"
    const val DataCleanupConfirmation = "data.cleanup_confirmation"
    const val DataDatabaseStorage = "data.database_storage"
    const val DataClearAi = "data.clear_ai"
    const val DataClearTemporary = "data.clear_temporary"
    const val DataClearArticle = "data.clear_article"
    const val DataCleanArticles = "data.clean_articles"
    const val DataOptimize = "data.optimize"
    const val DataBackup = "data.backup"
    const val DataDiagnostics = "data.diagnostics"
    const val DataDiagnosticUrls = "data.diagnostic_urls"
}

internal enum class SettingsDestination {
    Accounts,
    Reading,
    Ai,
    Podcast,
    Notifications,
    Appearance,
    Interaction,
    Data,
    Support,
}

internal data class SettingsSearchEntry(
    val title: String,
    val path: String,
    val currentValue: String? = null,
    val keywords: List<String> = emptyList(),
    val destination: SettingsDestination,
    val targetSetting: String? = null,
) {
    fun matches(query: String): Boolean {
        val normalized = query.trim()
        if (normalized.isEmpty()) return true
        return sequenceOf(title, path, currentValue.orEmpty())
            .plus(keywords.asSequence())
            .any { it.contains(normalized, ignoreCase = true) }
    }
}

internal fun List<SettingsSearchEntry>.searchSettings(query: String): List<SettingsSearchEntry> =
    filter { it.matches(query) }
