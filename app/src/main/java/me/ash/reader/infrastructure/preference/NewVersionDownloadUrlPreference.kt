package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.preference.PreferencesKey
import me.ash.reader.infrastructure.preference.getPreference
import me.ash.reader.infrastructure.preference.PreferencesKey.Companion.newVersionDownloadUrl
import me.ash.reader.infrastructure.preference.dataStore
import me.ash.reader.infrastructure.preference.put

val LocalNewVersionDownloadUrl = compositionLocalOf { NewVersionDownloadUrlPreference.default }

object NewVersionDownloadUrlPreference {

    const val default = ""

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(PreferencesKey.newVersionDownloadUrl, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences.getPreference<String>(newVersionDownloadUrl) ?: default
}
