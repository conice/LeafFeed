package com.conice.morss.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.conice.morss.domain.model.general.Version
import com.conice.morss.domain.model.general.toVersion
import com.conice.morss.infrastructure.preference.PreferencesKey
import com.conice.morss.infrastructure.preference.getPreference
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.newVersionNumber
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.put

val LocalNewVersionNumber = compositionLocalOf { NewVersionNumberPreference.default }

object NewVersionNumberPreference {

    val provide: (Settings) -> ProvidedValue<Version> =
        fun(settings: Settings) = LocalNewVersionNumber provides settings.newVersionNumber

    val default = Version()

    fun put(context: Context, scope: CoroutineScope, value: String) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(newVersionNumber, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences.getPreference<String>(newVersionNumber).toVersion()
}
