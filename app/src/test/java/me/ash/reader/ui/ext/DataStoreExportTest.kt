package me.ash.reader.ui.ext

import me.ash.reader.infrastructure.ai.AiBindingBackup
import me.ash.reader.infrastructure.ai.AiConfigurationBackup
import me.ash.reader.infrastructure.ai.AiConnectionBackup
import me.ash.reader.infrastructure.ai.AiModelBackup
import me.ash.reader.infrastructure.ai.AiPromptBackup
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys
import me.ash.reader.infrastructure.preference.NavigationPreferenceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreExportTest {
    @Test
    fun `registers every portable feature preference for import`() {
        val featureKeys =
            listOf(
                FeaturePreferenceKeys.markReadOnOpen,
                FeaturePreferenceKeys.markReadAtEnd,
                FeaturePreferenceKeys.showArticleTags,
                FeaturePreferenceKeys.showNotesAction,
                FeaturePreferenceKeys.showReadLaterIcon,
                FeaturePreferenceKeys.preferFullContent,
                FeaturePreferenceKeys.aiStreamingEnabled,
                FeaturePreferenceKeys.aiTimeoutSeconds,
                FeaturePreferenceKeys.podcastDefaultSpeed,
                FeaturePreferenceKeys.podcastRewindSeconds,
                FeaturePreferenceKeys.podcastForwardSeconds,
                FeaturePreferenceKeys.podcastAutoPlayNext,
                FeaturePreferenceKeys.podcastMarkPlayed,
                FeaturePreferenceKeys.podcastRememberProgress,
                FeaturePreferenceKeys.podcastAutoDownload,
                FeaturePreferenceKeys.podcastWifiOnly,
                FeaturePreferenceKeys.podcastDownloadScope,
                FeaturePreferenceKeys.podcastCacheMb,
                FeaturePreferenceKeys.podcastRetentionDays,
                FeaturePreferenceKeys.podcastAutoTranscript,
                FeaturePreferenceKeys.podcastShowEpisodeMetadata,
                FeaturePreferenceKeys.notificationsEnabled,
                FeaturePreferenceKeys.notificationMaxArticles,
                FeaturePreferenceKeys.notificationOpenArticle,
                FeaturePreferenceKeys.notificationPodcastEpisodes,
                FeaturePreferenceKeys.deduplicationMode,
                FeaturePreferenceKeys.cleanupConfirmation,
                FeaturePreferenceKeys.syncFullContent,
                FeaturePreferenceKeys.aiContentScope,
                FeaturePreferenceKeys.aiIncludeArticleLink,
                FeaturePreferenceKeys.diagnosticIncludeFeedUrls,
            )

        featureKeys.forEach { key ->
            assertTrue("Missing preference registration: ${key.name}", key.name in PreferencesKey.keys)
        }
    }

    @Test
    fun `registers every navigation preference for import`() {
        val navigationKeys =
            listOf(
                NavigationPreferenceKeys.mainBottomItems,
                NavigationPreferenceKeys.feedTopActions,
                NavigationPreferenceKeys.articleTopActions,
                NavigationPreferenceKeys.readingTopActions,
                NavigationPreferenceKeys.readingBottomActions,
                NavigationPreferenceKeys.mainTopIconSize,
                NavigationPreferenceKeys.mainBottomIconSize,
                NavigationPreferenceKeys.mainBottomHeight,
                NavigationPreferenceKeys.readingTopIconSize,
                NavigationPreferenceKeys.readingBottomIconSize,
                NavigationPreferenceKeys.readingBottomHeight,
                NavigationPreferenceKeys.mainTopElevation,
                NavigationPreferenceKeys.mainBottomElevation,
                NavigationPreferenceKeys.readingTopElevation,
                NavigationPreferenceKeys.readingBottomElevation,
            )

        navigationKeys.forEach { key ->
            assertTrue("Missing preference registration: ${key.name}", key.name in PreferencesKey.keys)
        }
    }

    @Test
    fun `encodes and decodes versioned preferences`() {
        val encoded =
            encodePreferencesJSON(
                mapOf(
                    FeaturePreferenceKeys.aiStreamingEnabled.name to true,
                    FeaturePreferenceKeys.aiTimeoutSeconds.name to 300,
                )
            )

        val decoded = decodePreferencesJSON(encoded)

        assertTrue(encoded.contains("\"format\":\"leaffeed.preferences\""))
        assertEquals(3, decoded.sourceVersion)
        assertEquals(true, decoded.preferences[FeaturePreferenceKeys.aiStreamingEnabled.name])
        assertEquals(300.0, decoded.preferences[FeaturePreferenceKeys.aiTimeoutSeconds.name])
    }

    @Test
    fun `encodes and decodes complete AI configuration`() {
        val backup =
            AiConfigurationBackup(
                connections =
                    listOf(
                        AiConnectionBackup(
                            id = "connection",
                            name = "Primary",
                            provider = "RESPONSES",
                            baseUrl = "https://example.com/v1",
                            authType = "BEARER",
                            secret = "secret-key",
                        )
                    ),
                models =
                    listOf(
                        AiModelBackup(
                            id = "model",
                            connectionId = "connection",
                            modelId = "example-model",
                            displayName = "Example model",
                            maxOutputTokens = 2048,
                        )
                    ),
                prompts =
                    listOf(
                        AiPromptBackup(
                            id = "prompt",
                            name = "Summary",
                            task = "ARTICLE_SUMMARY",
                            systemTemplate = "Summarize faithfully.",
                            userTemplate = "{content}",
                            itemTemplate = "{title}",
                            outputMode = "MARKDOWN",
                        )
                    ),
                bindings =
                    listOf(
                        AiBindingBackup(
                            task = "ARTICLE_SUMMARY",
                            promptId = "prompt",
                            primaryModelId = "model",
                        )
                    ),
            )

        val encoded = encodePreferencesJSON(emptyMap(), backup)
        val decoded = decodePreferencesJSON(encoded)

        assertEquals(backup, decoded.aiConfiguration)
        assertTrue(encoded.contains("secret-key"))
    }

    @Test
    fun `decodes legacy versioned preferences`() {
        val decoded =
            decodePreferencesJSON(
                """{"format":"readyou.preferences","version":2,"preferences":{"themeIndex":2}}"""
            )

        assertEquals(2, decoded.sourceVersion)
        assertEquals(2.0, decoded.preferences[PreferencesKey.themeIndex])
    }

    @Test
    fun `decodes legacy flat preferences`() {
        val decoded = decodePreferencesJSON("""{"themeIndex":2}""")

        assertNull(decoded.sourceVersion)
        assertEquals(2.0, decoded.preferences[PreferencesKey.themeIndex])
    }

    @Test
    fun `uses current types for migrated preferences`() {
        assertTrue(
            PreferencesKey.keys.getValue(PreferencesKey.flowArticleListDesc) is
                PreferencesKey.IntKey
        )
        assertTrue(
            PreferencesKey.keys.getValue(PreferencesKey.flowMarkAsReadFabPosition) is
                PreferencesKey.IntKey
        )
        assertTrue(
            PreferencesKey.keys.getValue(PreferencesKey.pullToLoadNextFeed) is
                PreferencesKey.IntKey
        )
    }

    @Test
    fun `excludes non-preference and device state data`() {
        assertTrue(isPreferenceExportable(FeaturePreferenceKeys.aiStreamingEnabled.name, false))
        assertTrue(!isPreferenceExportable("article_rules", true))
        assertTrue(!isPreferenceExportable(PreferencesKey.newVersionNumber, true))
    }
}
