package com.conice.morss.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.conice.morss.infrastructure.preference.PreferencesKey
import com.conice.morss.infrastructure.preference.getPreference
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.hideEmptyGroups
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.put

val LocalHideEmptyGroups =
    compositionLocalOf<HideEmptyGroupsPreference> { HideEmptyGroupsPreference.default }

sealed class HideEmptyGroupsPreference(val value: Boolean) : Preference() {
    data object ON : HideEmptyGroupsPreference(true)
    data object OFF : HideEmptyGroupsPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                hideEmptyGroups,
                value
            )
        }
    }

    fun toggle(context: Context, scope: CoroutineScope) = scope.launch {
        context.dataStore.put(
            hideEmptyGroups,
            !value
        )
    }

    companion object {

        val default = ON
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences.getPreference<Boolean>(hideEmptyGroups)) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}
