package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.preference.PreferencesKey
import me.ash.reader.infrastructure.preference.getPreference
import me.ash.reader.infrastructure.preference.PreferencesKey.Companion.readingTextLetterSpacing
import me.ash.reader.infrastructure.preference.dataStore
import me.ash.reader.infrastructure.preference.put

val LocalReadingTextLetterSpacing = compositionLocalOf { ReadingTextLetterSpacingPreference.default }

object ReadingTextLetterSpacingPreference {

    const val default = 0.5F

    fun put(context: Context, scope: CoroutineScope, value: Float) {
        scope.launch {
            context.dataStore.put(PreferencesKey.readingTextLetterSpacing, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences.getPreference<Float>(readingTextLetterSpacing) ?: default
}
