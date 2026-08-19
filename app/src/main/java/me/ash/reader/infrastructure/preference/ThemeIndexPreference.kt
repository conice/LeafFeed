package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.preference.PreferencesKey
import me.ash.reader.infrastructure.preference.getPreference
import me.ash.reader.infrastructure.preference.PreferencesKey.Companion.themeIndex
import me.ash.reader.infrastructure.preference.dataStore
import me.ash.reader.infrastructure.preference.put

val LocalThemeIndex =
    compositionLocalOf { ThemeIndexPreference.default }

object ThemeIndexPreference {

    const val default = 5

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(PreferencesKey.themeIndex, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences.getPreference<Int>(themeIndex) ?: default
}
