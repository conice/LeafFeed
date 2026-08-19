package com.conice.morss.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.conice.morss.infrastructure.preference.PreferencesKey
import com.conice.morss.infrastructure.preference.getPreference
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.readingSubheadUpperCase
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.put

val LocalReadingSubheadUpperCase =
    compositionLocalOf<ReadingSubheadUpperCasePreference> { ReadingSubheadUpperCasePreference.default }

sealed class ReadingSubheadUpperCasePreference(val value: Boolean) : Preference() {
    object ON : ReadingSubheadUpperCasePreference(true)
    object OFF : ReadingSubheadUpperCasePreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                PreferencesKey.readingSubheadUpperCase,
                value
            )
        }
    }

    companion object {

        val default = OFF
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences.getPreference<Boolean>(readingSubheadUpperCase)) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun ReadingSubheadUpperCasePreference.not(): ReadingSubheadUpperCasePreference =
    when (value) {
        true -> ReadingSubheadUpperCasePreference.OFF
        false -> ReadingSubheadUpperCasePreference.ON
    }
