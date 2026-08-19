package com.conice.morss.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.conice.morss.infrastructure.preference.PreferencesKey
import com.conice.morss.infrastructure.preference.getPreference
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.markAsReadOnScroll
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.put

val LocalMarkAsReadOnScroll =
    compositionLocalOf<MarkAsReadOnScrollPreference> { MarkAsReadOnScrollPreference.default }

sealed class MarkAsReadOnScrollPreference(val value: Boolean) : Preference() {
    data object ON : MarkAsReadOnScrollPreference(true)
    data object OFF : MarkAsReadOnScrollPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                markAsReadOnScroll,
                value
            )
        }
    }

    fun toggle(context: Context, scope: CoroutineScope) = scope.launch {
        context.dataStore.put(
            markAsReadOnScroll,
            !value
        )
    }

    companion object {

        val default = OFF
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences.getPreference<Boolean>(markAsReadOnScroll)) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}