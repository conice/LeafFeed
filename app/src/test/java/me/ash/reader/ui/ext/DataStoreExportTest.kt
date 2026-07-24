package me.ash.reader.ui.ext

import me.ash.reader.infrastructure.preference.AiPreferenceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreExportTest {
    @Test
    fun `registers every AI preference for import`() {
        val aiKeys =
            listOf(
                AiPreferenceKeys.url,
                AiPreferenceKeys.apiKey,
                AiPreferenceKeys.model,
                AiPreferenceKeys.titlePrompt,
                AiPreferenceKeys.articleUrl,
                AiPreferenceKeys.articleApiKey,
                AiPreferenceKeys.articleModel,
                AiPreferenceKeys.articlePrompt,
                AiPreferenceKeys.articleCount,
            )

        aiKeys.forEach { key -> assertTrue(key.name in PreferencesKey.keys) }
    }

    @Test
    fun `encodes and decodes versioned preferences`() {
        val encoded =
            encodePreferencesJSON(
                mapOf(
                    AiPreferenceKeys.url.name to "https://example.com/v1",
                    AiPreferenceKeys.articleCount.name to 30,
                )
            )

        val decoded = decodePreferencesJSON(encoded)

        assertTrue(encoded.contains("\"format\":\"leaffeed.preferences\""))
        assertEquals(2, decoded.sourceVersion)
        assertEquals(
            "https://example.com/v1",
            decoded.preferences[AiPreferenceKeys.url.name],
        )
        assertEquals(30.0, decoded.preferences[AiPreferenceKeys.articleCount.name])
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
        val decoded = decodePreferencesJSON("""{"themeIndex":2,"ai_model":"fast-model"}""")

        assertNull(decoded.sourceVersion)
        assertEquals(2.0, decoded.preferences[PreferencesKey.themeIndex])
        assertEquals("fast-model", decoded.preferences[AiPreferenceKeys.model.name])
    }

    @Test
    fun `uses current types for migrated preferences`() {
        assertTrue(
            PreferencesKey.keys.getValue(PreferencesKey.flowArticleListDesc) is
                PreferencesKey.IntKey
        )
        assertTrue(
            PreferencesKey.keys.getValue(PreferencesKey.pullToLoadNextFeed) is
                PreferencesKey.IntKey
        )
    }

    @Test
    fun `excludes sensitive and non-preference data by default`() {
        assertTrue(isPreferenceExportable(AiPreferenceKeys.model.name, false))
        assertTrue(!isPreferenceExportable(AiPreferenceKeys.apiKey.name, false))
        assertTrue(isPreferenceExportable(AiPreferenceKeys.apiKey.name, true))
        assertTrue(!isPreferenceExportable("article_rules", true))
        assertTrue(!isPreferenceExportable(PreferencesKey.newVersionNumber, true))
    }
}
