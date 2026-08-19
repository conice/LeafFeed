package com.conice.morss.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.conice.morss.infrastructure.preference.PreferencesKey
import com.conice.morss.infrastructure.preference.getPreference
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.readingBoldCharacters
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.put

val LocalReadingBoldCharacters =
    compositionLocalOf<ReadingBoldCharactersPreference> { ReadingBoldCharactersPreference.default }

sealed class ReadingBoldCharactersPreference(val value: Boolean) : Preference() {
    object ON : ReadingBoldCharactersPreference(true)
    object OFF : ReadingBoldCharactersPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(readingBoldCharacters, value)
        }
    }

    companion object {

        val default = OFF
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences.getPreference<Boolean>(readingBoldCharacters)) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun ReadingBoldCharactersPreference.not(): ReadingBoldCharactersPreference =
    when (value) {
        true -> ReadingBoldCharactersPreference.OFF
        false -> ReadingBoldCharactersPreference.ON
    }
