package me.ash.reader.ui.ext

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import me.ash.reader.infrastructure.preference.AiPreferenceKeys

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

suspend fun DataStore<Preferences>.put(dataStoreKeys: String, value: Any) {
    val preferenceKey = PreferencesKey.keys[dataStoreKeys] ?: return
    this.edit {
        when (preferenceKey) {
            is PreferencesKey.IntKey -> it[preferenceKey.key] = value.requireType()
            is PreferencesKey.LongKey -> it[preferenceKey.key] = value.requireType()
            is PreferencesKey.StringKey -> it[preferenceKey.key] = value.requireType()
            is PreferencesKey.BooleanKey -> it[preferenceKey.key] = value.requireType()
            is PreferencesKey.FloatKey -> it[preferenceKey.key] = value.requireType()
        }
    }
}

private inline fun <reified T> Any.requireType(): T =
    this as? T
        ?: throw IllegalArgumentException("Expected ${T::class.simpleName}, got ${this::class.simpleName}")

sealed interface PreferencesKey {
    val name: String
    val key: Preferences.Key<*>

    data class IntKey(
        override val name: String,
        override val key: Preferences.Key<Int> = intPreferencesKey(name),
    ) : PreferencesKey

    data class LongKey(
        override val name: String,
        override val key: Preferences.Key<Long> = longPreferencesKey(name),
    ) : PreferencesKey

    data class StringKey(
        override val name: String,
        override val key: Preferences.Key<String> = stringPreferencesKey(name),
    ) : PreferencesKey

    data class BooleanKey(
        override val name: String,
        override val key: Preferences.Key<Boolean> = booleanPreferencesKey(name),
    ) : PreferencesKey

    data class FloatKey(
        override val name: String,
        override val key: Preferences.Key<Float> = floatPreferencesKey(name),
    ) : PreferencesKey

    companion object {
        // Version
        const val isFirstLaunch = "isFirstLaunch"
        const val newVersionPublishDate = "newVersionPublishDate"
        const val newVersionLog = "newVersionLog"
        const val newVersionSizeString = "newVersionSizeString"
        const val newVersionDownloadUrl = "newVersionDownloadUrl"
        const val newVersionNumber = "newVersionNumber"
        const val skipVersionNumber = "skipVersionNumber"
        const val currentAccountId = "currentAccountId"
        const val currentAccountType = "currentAccountType"
        const val themeIndex = "themeIndex"
        const val customPrimaryColor = "customPrimaryColor"
        const val darkTheme = "darkTheme"
        const val amoledDarkTheme = "amoledDarkTheme"
        const val basicFonts = "basicFonts"

        // Feeds page
        const val feedsFilterBarStyle = "feedsFilterBarStyle"
        const val feedsFilterBarPadding = "feedsFilterBarPadding"
        const val feedsFilterBarTonalElevation = "feedsFilterBarTonalElevation"
        const val feedsTopBarTonalElevation = "feedsTopBarTonalElevation"
        const val feedsGroupListExpand = "feedsGroupListExpand"
        const val feedsGroupListTonalElevation = "feedsGroupListTonalElevation"

        // Flow page
        const val flowFilterBarStyle = "flowFilterBarStyle"
        const val flowFilterBarPadding = "flowFilterBarPadding"
        const val flowFilterBarTonalElevation = "flowFilterBarTonalElevation"
        const val flowTopBarTonalElevation = "flowTopBarTonalElevation"
        const val flowArticleListFeedIcon = "flowArticleListFeedIcon"
        const val flowArticleListFeedName = "flowArticleListFeedName"
        const val flowArticleListImage = "flowArticleListImage"
        const val flowArticleListDesc = "flowArticleListDescription"
        const val flowArticleListTime = "flowArticleListTime"
        const val flowArticleListDateStickyHeader = "flowArticleListDateStickyHeader"
        const val flowArticleListTonalElevation = "flowArticleListTonalElevation"
        const val flowArticleListReadIndicator = "flowArticleListReadStatusIndicator"
        const val flowSortUnreadArticles = "flowArticleListSortUnreadArticles"

        // Reading page
        const val readingRenderer = "readingRender"
        const val readingBoldCharacters = "readingBoldCharacters"
        const val readingPageTonalElevation = "readingPageTonalElevation"
        const val readingTextFontSize = "readingTextFontSize"
        const val readingTextLineHeight = "readingTextLineHeight"
        const val readingTextLetterSpacing = "readingTextLetterSpacing"
        const val readingTextHorizontalPadding = "readingTextHorizontalPadding"
        const val readingTextBold = "readingTextBold"
        const val readingTextAlign = "readingTextAlign"
        const val readingTitleAlign = "readingTitleAlign"
        const val readingSubheadAlign = "readingSubheadAlign"
        const val readingTheme = "readingTheme"
        const val readingFonts = "readingFonts"
        const val readingAutoHideToolbar = "readingAutoHideToolbar"
        const val readingTitleBold = "readingTitleBold"
        const val readingSubheadBold = "readingSubheadBold"
        const val readingTitleUpperCase = "readingTitleUpperCase"
        const val readingSubheadUpperCase = "readingSubheadUpperCase"
        const val readingImageMaximize = "readingImageMaximize"
        const val readingImageHorizontalPadding = "readingImageHorizontalPadding"
        const val readingImageRoundedCorners = "readingImageRoundedCorners"

        // Interaction
        const val initialPage = "initialPage"
        const val initialFilter = "initialFilter"
        const val swipeStartAction = "swipeStartAction"
        const val swipeEndAction = "swipeEndAction"
        const val markAsReadOnScroll = "markAsReadOnScroll"
        const val hideEmptyGroups = "hideEmptyGroups"
        const val pullToLoadNextFeed = "pullToLoadNextFeed"
        const val pullToSwitchArticle = "pullToSwitchArticle"
        const val openLink = "openLink"
        const val openLinkAppSpecificBrowser = "openLinkAppSpecificBrowser"
        const val sharedContent = "sharedContent"

        // Languages
        const val languages = "languages"

        private val keyList =
            listOf(
                // Version
                BooleanKey(isFirstLaunch),
                StringKey(newVersionPublishDate),
                StringKey(newVersionLog),
                StringKey(newVersionSizeString),
                StringKey(newVersionDownloadUrl),
                StringKey(newVersionNumber),
                StringKey(skipVersionNumber),
                IntKey(currentAccountId),
                IntKey(currentAccountType),
                IntKey(themeIndex),
                StringKey(customPrimaryColor),
                IntKey(darkTheme),
                BooleanKey(amoledDarkTheme),
                IntKey(basicFonts),
                // Feeds page
                IntKey(feedsFilterBarStyle),
                IntKey(feedsFilterBarPadding),
                IntKey(feedsFilterBarTonalElevation),
                IntKey(feedsTopBarTonalElevation),
                BooleanKey(feedsGroupListExpand),
                IntKey(feedsGroupListTonalElevation),
                // Flow page
                IntKey(flowFilterBarStyle),
                IntKey(flowFilterBarPadding),
                IntKey(flowFilterBarTonalElevation),
                IntKey(flowTopBarTonalElevation),
                BooleanKey(flowArticleListFeedIcon),
                BooleanKey(flowArticleListFeedName),
                BooleanKey(flowArticleListImage),
                IntKey(flowArticleListDesc),
                BooleanKey(flowArticleListTime),
                BooleanKey(flowArticleListDateStickyHeader),
                IntKey(flowArticleListTonalElevation),
                IntKey(flowArticleListReadIndicator),
                BooleanKey(flowSortUnreadArticles),
                // Reading page
                IntKey(readingRenderer),
                BooleanKey(readingBoldCharacters),
                IntKey(readingPageTonalElevation),
                IntKey(readingTextFontSize),
                FloatKey(readingTextLineHeight),
                FloatKey(readingTextLetterSpacing),
                IntKey(readingTextHorizontalPadding),
                BooleanKey(readingTextBold),
                IntKey(readingTextAlign),
                IntKey(readingTitleAlign),
                IntKey(readingSubheadAlign),
                IntKey(readingTheme),
                IntKey(readingFonts),
                BooleanKey(readingAutoHideToolbar),
                BooleanKey(readingTitleBold),
                BooleanKey(readingSubheadBold),
                BooleanKey(readingTitleUpperCase),
                BooleanKey(readingSubheadUpperCase),
                BooleanKey(readingImageMaximize),
                IntKey(readingImageHorizontalPadding),
                IntKey(readingImageRoundedCorners),
                // Interaction
                IntKey(initialPage),
                IntKey(initialFilter),
                IntKey(swipeStartAction),
                IntKey(swipeEndAction),
                BooleanKey(markAsReadOnScroll),
                BooleanKey(hideEmptyGroups),
                IntKey(pullToLoadNextFeed),
                BooleanKey(pullToSwitchArticle),
                IntKey(openLink),
                StringKey(openLinkAppSpecificBrowser),
                IntKey(sharedContent),
                // Languages
                IntKey(languages),
                // AI
                StringKey(AiPreferenceKeys.url.name, AiPreferenceKeys.url),
                StringKey(AiPreferenceKeys.apiKey.name, AiPreferenceKeys.apiKey),
                StringKey(AiPreferenceKeys.model.name, AiPreferenceKeys.model),
                StringKey(AiPreferenceKeys.titlePrompt.name, AiPreferenceKeys.titlePrompt),
                StringKey(AiPreferenceKeys.articleUrl.name, AiPreferenceKeys.articleUrl),
                StringKey(AiPreferenceKeys.articleApiKey.name, AiPreferenceKeys.articleApiKey),
                StringKey(AiPreferenceKeys.articleModel.name, AiPreferenceKeys.articleModel),
                StringKey(AiPreferenceKeys.articlePrompt.name, AiPreferenceKeys.articlePrompt),
                IntKey(AiPreferenceKeys.articleCount.name, AiPreferenceKeys.articleCount),
            )

        val keys = keyList.associateBy { it.name }
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified T> Preferences.getPreference(name: String): T? {
    val preferenceKey = PreferencesKey.keys[name] ?: return null
    val typeMatches =
        when (preferenceKey) {
            is PreferencesKey.IntKey -> T::class == Int::class
            is PreferencesKey.LongKey -> T::class == Long::class
            is PreferencesKey.StringKey -> T::class == String::class
            is PreferencesKey.BooleanKey -> T::class == Boolean::class
            is PreferencesKey.FloatKey -> T::class == Float::class
        }
    return if (typeMatches) this[preferenceKey.key as Preferences.Key<T>] else null
}

val ignorePreferencesOnExportAndImport =
    setOf(
        PreferencesKey.currentAccountId,
        PreferencesKey.currentAccountType,
        PreferencesKey.isFirstLaunch,
        PreferencesKey.newVersionPublishDate,
        PreferencesKey.newVersionLog,
        PreferencesKey.newVersionSizeString,
        PreferencesKey.newVersionDownloadUrl,
        PreferencesKey.newVersionNumber,
    )

private const val PREFERENCES_EXPORT_FORMAT = "leaffeed.preferences"
private const val LEGACY_PREFERENCES_EXPORT_FORMAT = "readyou.preferences"
private const val PREFERENCES_EXPORT_VERSION = 2

private val sensitivePreferences =
    setOf(
        AiPreferenceKeys.apiKey.name,
        AiPreferenceKeys.articleApiKey.name,
    )

private data class PreferencesExport(
    val format: String = PREFERENCES_EXPORT_FORMAT,
    val version: Int = PREFERENCES_EXPORT_VERSION,
    val preferences: Map<String, Any?>,
)

internal data class DecodedPreferences(
    val preferences: Map<String, Any?>,
    val sourceVersion: Int?,
)

data class PreferencesImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    val sourceVersion: Int?,
)

internal fun encodePreferencesJSON(preferences: Map<String, Any?>): String =
    Gson().toJson(PreferencesExport(preferences = preferences))

internal fun isPreferenceExportable(name: String, includeSensitive: Boolean): Boolean =
    name in PreferencesKey.keys &&
        name !in ignorePreferencesOnExportAndImport &&
        (includeSensitive || name !in sensitivePreferences)

internal fun decodePreferencesJSON(content: String): DecodedPreferences {
    val type = object : TypeToken<Map<String, Any?>>() {}.type
    val root: Map<String, Any?> = Gson().fromJson(content, type)
        ?: error("The preferences file is empty")
    val isVersioned = root["format"] == PREFERENCES_EXPORT_FORMAT ||
        root["format"] == LEGACY_PREFERENCES_EXPORT_FORMAT
    if (!isVersioned) return DecodedPreferences(root, null)

    val sourceVersion = (root["version"] as? Number)?.toInt()
        ?: error("The preferences file does not contain a version")
    val nested = root["preferences"] as? Map<*, *>
        ?: error("The preferences file does not contain preferences")
    val preferences = nested.entries.associate { (key, value) ->
        (key as? String ?: error("Invalid preference key")) to value
    }
    return DecodedPreferences(preferences, sourceVersion)
}

suspend fun Context.fromDataStoreToJSONString(includeSensitive: Boolean = false): String {
    val preferences = dataStore.data.first()
    val map: Map<String, Any?> =
        preferences
            .asMap()
            .mapKeys { it.key.name }
            .filterKeys { isPreferenceExportable(it, includeSensitive) }
    return encodePreferencesJSON(map)
}

suspend fun String.fromJSONStringToDataStore(context: Context): PreferencesImportResult {
    val decoded = decodePreferencesJSON(this)
    val deserializedMap = decoded.preferences
    var importedCount = 0
    var skippedCount = 0
    context.dataStore.edit { preferences ->
        deserializedMap
            .forEach { (keyString, value) ->
                if (keyString in ignorePreferencesOnExportAndImport) {
                    skippedCount++
                    return@forEach
                }
                val preferencesKey = PreferencesKey.keys[keyString]
                val imported = when (preferencesKey) {
                    is PreferencesKey.BooleanKey -> {
                        if (value is Boolean) {
                            preferences[preferencesKey.key] = value
                            true
                        } else false
                    }
                    is PreferencesKey.FloatKey -> {
                        if (value is Number) {
                            preferences[preferencesKey.key] = value.toFloat()
                            true
                        } else false
                    }
                    is PreferencesKey.IntKey -> {
                        if (value is Number) {
                            preferences[preferencesKey.key] = value.toInt()
                            true
                        } else false
                    }
                    is PreferencesKey.LongKey -> {
                        if (value is Number) {
                            preferences[preferencesKey.key] = value.toLong()
                            true
                        } else false
                    }
                    is PreferencesKey.StringKey -> {
                        if (value is String) {
                            preferences[preferencesKey.key] = value
                            true
                        } else false
                    }
                    null -> false
                }
                if (imported) importedCount++ else skippedCount++
            }
    }
    return PreferencesImportResult(importedCount, skippedCount, decoded.sourceVersion)
}
