package me.ash.reader.infrastructure.preference

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object FeaturePreferenceKeys {
    val markReadOnOpen = booleanPreferencesKey("reading_mark_read_on_open")
    val markReadAtEnd = booleanPreferencesKey("reading_mark_read_at_end")
    val showHighlightMatches = booleanPreferencesKey("reading_show_highlight_matches")
    val showArticleTags = booleanPreferencesKey("reading_show_article_tags")
    val showNotesAction = booleanPreferencesKey("reading_show_notes_action")
    val showReadLaterIcon = booleanPreferencesKey("reading_show_read_later_icon")
    val preferFullContent = booleanPreferencesKey("reading_prefer_full_content")
    val aiStreamingEnabled = booleanPreferencesKey("ai_streaming_enabled")
    val aiTimeoutSeconds = intPreferencesKey("ai_timeout_seconds")

    val filterRulesEnabled = booleanPreferencesKey("rules_filter_enabled")
    val highlightRulesEnabled = booleanPreferencesKey("rules_highlight_enabled")
    val ruleMatchDescription = booleanPreferencesKey("rules_match_description")
    val ruleFailureMode = intPreferencesKey("rules_failure_mode")
    val ruleConflictMode = intPreferencesKey("rules_conflict_mode")

    val podcastDefaultSpeed = floatPreferencesKey("podcast_default_speed")
    val podcastRewindSeconds = intPreferencesKey("podcast_rewind_seconds")
    val podcastForwardSeconds = intPreferencesKey("podcast_forward_seconds")
    val podcastAutoPlayNext = booleanPreferencesKey("podcast_auto_play_next")
    val podcastMarkPlayed = booleanPreferencesKey("podcast_mark_played")
    val podcastRememberProgress = booleanPreferencesKey("podcast_remember_progress")
    val podcastAutoDownload = booleanPreferencesKey("podcast_auto_download")
    val podcastWifiOnly = booleanPreferencesKey("podcast_wifi_only")
    val podcastDownloadScope = intPreferencesKey("podcast_download_scope")
    val podcastCacheMb = intPreferencesKey("podcast_cache_mb")
    val podcastRetentionDays = intPreferencesKey("podcast_retention_days")
    val podcastAutoTranscript = booleanPreferencesKey("podcast_auto_transcript")
    val podcastShowEpisodeMetadata = booleanPreferencesKey("podcast_show_episode_metadata")

    val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
    val notificationMaxArticles = intPreferencesKey("notifications_max_articles")
    val notificationOpenArticle = booleanPreferencesKey("notifications_open_article")
    val notificationHighlightsOnly = booleanPreferencesKey("notifications_highlights_only")
    val notificationExcludeFiltered = booleanPreferencesKey("notifications_exclude_filtered")
    val notificationPodcastEpisodes = booleanPreferencesKey("notifications_podcast_episodes")

    val deduplicationMode = intPreferencesKey("data_deduplication_mode")
    val cleanupConfirmation = booleanPreferencesKey("data_cleanup_confirmation")
    val syncFullContent = booleanPreferencesKey("data_sync_full_content")
    val aiContentScope = intPreferencesKey("privacy_ai_content_scope")
    val aiIncludeArticleLink = booleanPreferencesKey("privacy_ai_include_article_link")
    val diagnosticIncludeFeedUrls = booleanPreferencesKey("privacy_diagnostic_feed_urls")
    val reportRange = stringPreferencesKey("report_last_range")
}

data class FeatureSettings(
    val markReadOnOpen: Boolean = true,
    val markReadAtEnd: Boolean = false,
    val showHighlightMatches: Boolean = true,
    val showArticleTags: Boolean = true,
    val showNotesAction: Boolean = true,
    val showReadLaterIcon: Boolean = true,
    val preferFullContent: Boolean = false,
    val aiStreamingEnabled: Boolean = true,
    val aiTimeoutSeconds: Int = 300,
    val filterRulesEnabled: Boolean = true,
    val highlightRulesEnabled: Boolean = true,
    val ruleMatchDescription: Boolean = true,
    val ruleFailureMode: Int = 0,
    val ruleConflictMode: Int = 0,
    val podcastDefaultSpeed: Float = 1f,
    val podcastRewindSeconds: Int = 10,
    val podcastForwardSeconds: Int = 30,
    val podcastAutoPlayNext: Boolean = true,
    val podcastMarkPlayed: Boolean = true,
    val podcastRememberProgress: Boolean = true,
    val podcastAutoDownload: Boolean = false,
    val podcastWifiOnly: Boolean = true,
    val podcastDownloadScope: Int = 0,
    val podcastCacheMb: Int = 512,
    val podcastRetentionDays: Int = 30,
    val podcastAutoTranscript: Boolean = false,
    val podcastShowEpisodeMetadata: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val notificationMaxArticles: Int = 5,
    val notificationOpenArticle: Boolean = true,
    val notificationHighlightsOnly: Boolean = false,
    val notificationExcludeFiltered: Boolean = true,
    val notificationPodcastEpisodes: Boolean = true,
    val deduplicationMode: Int = 1,
    val cleanupConfirmation: Boolean = true,
    val syncFullContent: Boolean = false,
    val aiContentScope: Int = 2,
    val aiIncludeArticleLink: Boolean = false,
    val diagnosticIncludeFeedUrls: Boolean = false,
)

fun Preferences.toFeatureSettings(): FeatureSettings = FeatureSettings(
    markReadOnOpen = this[FeaturePreferenceKeys.markReadOnOpen] ?: true,
    markReadAtEnd = this[FeaturePreferenceKeys.markReadAtEnd] ?: false,
    showHighlightMatches = this[FeaturePreferenceKeys.showHighlightMatches] ?: true,
    showArticleTags = this[FeaturePreferenceKeys.showArticleTags] ?: true,
    showNotesAction = this[FeaturePreferenceKeys.showNotesAction] ?: true,
    showReadLaterIcon = this[FeaturePreferenceKeys.showReadLaterIcon] ?: true,
    preferFullContent = this[FeaturePreferenceKeys.preferFullContent] ?: false,
    aiStreamingEnabled = this[FeaturePreferenceKeys.aiStreamingEnabled] ?: true,
    aiTimeoutSeconds = this[FeaturePreferenceKeys.aiTimeoutSeconds] ?: 300,
    filterRulesEnabled = this[FeaturePreferenceKeys.filterRulesEnabled] ?: true,
    highlightRulesEnabled = this[FeaturePreferenceKeys.highlightRulesEnabled] ?: true,
    ruleMatchDescription = this[FeaturePreferenceKeys.ruleMatchDescription] ?: true,
    ruleFailureMode = this[FeaturePreferenceKeys.ruleFailureMode] ?: 0,
    ruleConflictMode = this[FeaturePreferenceKeys.ruleConflictMode] ?: 0,
    podcastDefaultSpeed = this[FeaturePreferenceKeys.podcastDefaultSpeed] ?: 1f,
    podcastRewindSeconds = this[FeaturePreferenceKeys.podcastRewindSeconds] ?: 10,
    podcastForwardSeconds = this[FeaturePreferenceKeys.podcastForwardSeconds] ?: 30,
    podcastAutoPlayNext = this[FeaturePreferenceKeys.podcastAutoPlayNext] ?: true,
    podcastMarkPlayed = this[FeaturePreferenceKeys.podcastMarkPlayed] ?: true,
    podcastRememberProgress = this[FeaturePreferenceKeys.podcastRememberProgress] ?: true,
    podcastAutoDownload = this[FeaturePreferenceKeys.podcastAutoDownload] ?: false,
    podcastWifiOnly = this[FeaturePreferenceKeys.podcastWifiOnly] ?: true,
    podcastDownloadScope = this[FeaturePreferenceKeys.podcastDownloadScope] ?: 0,
    podcastCacheMb = this[FeaturePreferenceKeys.podcastCacheMb] ?: 512,
    podcastRetentionDays = this[FeaturePreferenceKeys.podcastRetentionDays] ?: 30,
    podcastAutoTranscript = this[FeaturePreferenceKeys.podcastAutoTranscript] ?: false,
    podcastShowEpisodeMetadata = this[FeaturePreferenceKeys.podcastShowEpisodeMetadata] ?: true,
    notificationsEnabled = this[FeaturePreferenceKeys.notificationsEnabled] ?: true,
    notificationMaxArticles = this[FeaturePreferenceKeys.notificationMaxArticles] ?: 5,
    notificationOpenArticle = this[FeaturePreferenceKeys.notificationOpenArticle] ?: true,
    notificationHighlightsOnly = this[FeaturePreferenceKeys.notificationHighlightsOnly] ?: false,
    notificationExcludeFiltered = this[FeaturePreferenceKeys.notificationExcludeFiltered] ?: true,
    notificationPodcastEpisodes = this[FeaturePreferenceKeys.notificationPodcastEpisodes] ?: true,
    deduplicationMode = this[FeaturePreferenceKeys.deduplicationMode] ?: 1,
    cleanupConfirmation = this[FeaturePreferenceKeys.cleanupConfirmation] ?: true,
    syncFullContent = this[FeaturePreferenceKeys.syncFullContent] ?: false,
    aiContentScope = this[FeaturePreferenceKeys.aiContentScope] ?: 2,
    aiIncludeArticleLink = this[FeaturePreferenceKeys.aiIncludeArticleLink] ?: false,
    diagnosticIncludeFeedUrls = this[FeaturePreferenceKeys.diagnosticIncludeFeedUrls] ?: false,
)
