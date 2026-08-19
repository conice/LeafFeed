package com.conice.morss.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.conice.morss.infrastructure.preference.PreferencesKey
import com.conice.morss.infrastructure.preference.getPreference
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.readingTitleBold
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.put

val LocalReadingTitleBold =
    compositionLocalOf<ReadingTitleBoldPreference> { ReadingTitleBoldPreference.default }

sealed class ReadingTitleBoldPreference(val value: Boolean) : Preference() {
    object ON : ReadingTitleBoldPreference(true)
    object OFF : ReadingTitleBoldPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                PreferencesKey.readingTitleBold,
                value
            )
        }
    }

    companion object {

        val default = OFF
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences.getPreference<Boolean>(readingTitleBold)) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun ReadingTitleBoldPreference.not(): ReadingTitleBoldPreference =
    when (value) {
        true -> ReadingTitleBoldPreference.OFF
        false -> ReadingTitleBoldPreference.ON
    }
