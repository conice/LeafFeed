package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.preference.PreferencesKey
import me.ash.reader.infrastructure.preference.getPreference
import me.ash.reader.infrastructure.preference.PreferencesKey.Companion.readingSubheadBold
import me.ash.reader.infrastructure.preference.dataStore
import me.ash.reader.infrastructure.preference.put

val LocalReadingSubheadBold =
    compositionLocalOf<ReadingSubheadBoldPreference> { ReadingSubheadBoldPreference.default }

sealed class ReadingSubheadBoldPreference(val value: Boolean) : Preference() {
    object ON : ReadingSubheadBoldPreference(true)
    object OFF : ReadingSubheadBoldPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                PreferencesKey.readingSubheadBold,
                value
            )
        }
    }

    companion object {

        val default = OFF
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences.getPreference<Boolean>(readingSubheadBold)) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun ReadingSubheadBoldPreference.not(): ReadingSubheadBoldPreference =
    when (value) {
        true -> ReadingSubheadBoldPreference.OFF
        false -> ReadingSubheadBoldPreference.ON
    }
