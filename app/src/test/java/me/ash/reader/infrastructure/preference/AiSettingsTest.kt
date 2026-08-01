package me.ash.reader.infrastructure.preference

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class AiSettingsTest {
    @Test
    fun newTasksFallbackToExistingConnectionSettings() {
        val preferences =
            mutablePreferencesOf(
                AiPreferenceKeys.url to "https://example.com/v1",
                AiPreferenceKeys.apiKey to "secret",
                AiPreferenceKeys.model to "model",
            )

        val settings = preferences.toAiSettings("title prompt", "article prompt")

        assertEquals(
            settings.titleSummary.copy(
                prompt = "article prompt",
                inputTemplate = DEFAULT_AI_ARTICLE_INPUT_TEMPLATE,
            ),
            settings.articleSummary,
        )
    }

    @Test
    fun taskSpecificConnectionOverridesExistingSettings() {
        val preferences =
            mutablePreferencesOf(
                AiPreferenceKeys.url to "shared-url",
                AiPreferenceKeys.apiKey to "shared-key",
                AiPreferenceKeys.model to "shared-model",
                AiPreferenceKeys.articleUrl to "article-url",
                AiPreferenceKeys.articleApiKey to "article-key",
                AiPreferenceKeys.articleModel to "article-model",
            )

        val settings = preferences.toAiSettings("title", "article")

        assertEquals("article-url", settings.articleSummary.url)
        assertEquals("article-key", settings.articleSummary.apiKey)
        assertEquals("article-model", settings.articleSummary.model)
    }

    @Test
    fun taskSpecificInputTemplatesAreLoaded() {
        val preferences =
            mutablePreferencesOf(
                AiPreferenceKeys.titleInputTemplate to "{序号}: {标题}",
                AiPreferenceKeys.articleInputTemplate to "{标题}\n{正文}",
            )

        val settings = preferences.toAiSettings("title", "article")

        assertEquals("{序号}: {标题}", settings.titleSummary.inputTemplate)
        assertEquals("{标题}\n{正文}", settings.articleSummary.inputTemplate)
    }
}
