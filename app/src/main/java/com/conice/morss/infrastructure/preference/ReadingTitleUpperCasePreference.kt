package com.conice.morss.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.conice.morss.infrastructure.preference.PreferencesKey
import com.conice.morss.infrastructure.preference.getPreference
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.readingTitleUpperCase
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.put

val LocalReadingTitleUpperCase =
    compositionLocalOf<ReadingTitleUpperCasePreference> { ReadingTitleUpperCasePreference.default }

sealed class ReadingTitleUpperCasePreference(val value: Boolean) : Preference() {
    object ON : ReadingTitleUpperCasePreference(true)
    object OFF : ReadingTitleUpperCasePreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                PreferencesKey.readingTitleUpperCase,
                value
            )
        }
    }

    companion object {

        val default = OFF
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences.getPreference<Boolean>(readingTitleUpperCase)) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun ReadingTitleUpperCasePreference.not(): ReadingTitleUpperCasePreference =
    when (value) {
        true -> ReadingTitleUpperCasePreference.OFF
        false -> ReadingTitleUpperCasePreference.ON
    }
