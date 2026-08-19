package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.preference.PreferencesKey
import me.ash.reader.infrastructure.preference.getPreference
import me.ash.reader.infrastructure.preference.PreferencesKey.Companion.readingTextLineHeight
import me.ash.reader.infrastructure.preference.dataStore
import me.ash.reader.infrastructure.preference.put

val LocalReadingTextLineHeight = compositionLocalOf { ReadingTextLineHeightPreference.default }

data object ReadingTextLineHeightPreference {
    const val default = 1.0F
    private val range = 0.8F..2F

    fun put(context: Context, scope: CoroutineScope, value: Float) {
        scope.launch {
            context.dataStore.put(PreferencesKey.readingTextLineHeight, value)
        }
    }

    fun Float.coerceToRange() = coerceIn(range)

    fun fromPreferences(preferences: Preferences) =
        preferences.getPreference<Float>(readingTextLineHeight) ?: default
}
