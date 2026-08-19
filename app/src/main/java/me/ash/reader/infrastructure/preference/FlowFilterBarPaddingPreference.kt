package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.preference.PreferencesKey
import me.ash.reader.infrastructure.preference.getPreference
import me.ash.reader.infrastructure.preference.PreferencesKey.Companion.flowFilterBarPadding
import me.ash.reader.infrastructure.preference.dataStore
import me.ash.reader.infrastructure.preference.put

val LocalFlowFilterBarPadding =
    compositionLocalOf { FlowFilterBarPaddingPreference.default }

object FlowFilterBarPaddingPreference {

    const val default = 60

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch {
            context.dataStore.put(PreferencesKey.flowFilterBarPadding, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences.getPreference<Int>(flowFilterBarPadding) ?: default
}
