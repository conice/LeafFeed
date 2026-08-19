package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.preference.PreferencesKey
import me.ash.reader.infrastructure.preference.getPreference
import me.ash.reader.infrastructure.preference.PreferencesKey.Companion.readingTextBold
import me.ash.reader.infrastructure.preference.dataStore
import me.ash.reader.infrastructure.preference.put

val LocalReadingTextBold =
    compositionLocalOf<ReadingTextBoldPreference> { ReadingTextBoldPreference.default }

sealed class ReadingTextBoldPreference(val value: Boolean) : Preference() {
    object ON : ReadingTextBoldPreference(true)
    object OFF : ReadingTextBoldPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                PreferencesKey.readingTextBold,
                value
            )
        }
    }

    companion object {

        val default = OFF
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences.getPreference<Boolean>(readingTextBold)) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun ReadingTextBoldPreference.not(): ReadingTextBoldPreference =
    when (value) {
        true -> ReadingTextBoldPreference.OFF
        false -> ReadingTextBoldPreference.ON
    }
