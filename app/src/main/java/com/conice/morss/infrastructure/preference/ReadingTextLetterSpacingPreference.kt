package com.conice.morss.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.conice.morss.infrastructure.preference.PreferencesKey
import com.conice.morss.infrastructure.preference.getPreference
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.readingTextLetterSpacing
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.put

val LocalReadingTextLetterSpacing = compositionLocalOf { ReadingTextLetterSpacingPreference.default }

object ReadingTextLetterSpacingPreference {

    const val default = 0.5F

    fun put(context: Context, scope: CoroutineScope, value: Float) {
        scope.launch {
            context.dataStore.put(PreferencesKey.readingTextLetterSpacing, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences.getPreference<Float>(readingTextLetterSpacing) ?: default
}
