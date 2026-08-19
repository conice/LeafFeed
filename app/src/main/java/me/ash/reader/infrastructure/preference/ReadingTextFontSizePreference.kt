package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.preference.PreferencesKey
import me.ash.reader.infrastructure.preference.getPreference
import me.ash.reader.infrastructure.preference.PreferencesKey.Companion.readingTextFontSize
import me.ash.reader.infrastructure.preference.dataStore
import me.ash.reader.infrastructure.preference.put

val LocalReadingTextFontSize = compositionLocalOf { ReadingTextFontSizePreference.default }

object ReadingTextFontSizePreference {

    const val default = 17

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch {
            context.dataStore.put(PreferencesKey.readingTextFontSize, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences.getPreference<Int>(readingTextFontSize) ?: default
}
