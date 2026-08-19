package com.conice.morss.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.conice.morss.infrastructure.preference.PreferencesKey
import com.conice.morss.infrastructure.preference.getPreference
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.customPrimaryColor
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.put

val LocalCustomPrimaryColor =
    compositionLocalOf { CustomPrimaryColorPreference.default }

object CustomPrimaryColorPreference {

    const val default = ""

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch {
            context.dataStore.put(PreferencesKey.customPrimaryColor, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences.getPreference<String>(customPrimaryColor) ?: default
}
