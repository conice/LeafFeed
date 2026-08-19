package com.conice.morss.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.conice.morss.infrastructure.preference.PreferencesKey
import com.conice.morss.infrastructure.preference.getPreference
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.feedsGroupListExpand
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.put

val LocalFeedsGroupListExpand =
    compositionLocalOf<FeedsGroupListExpandPreference> { FeedsGroupListExpandPreference.default }

sealed class FeedsGroupListExpandPreference(val value: Boolean) : Preference() {
    object ON : FeedsGroupListExpandPreference(true)
    object OFF : FeedsGroupListExpandPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                PreferencesKey.feedsGroupListExpand,
                value
            )
        }
    }

    companion object {

        val default = ON
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences.getPreference<Boolean>(feedsGroupListExpand)) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun FeedsGroupListExpandPreference.not(): FeedsGroupListExpandPreference =
    when (value) {
        true -> FeedsGroupListExpandPreference.OFF
        false -> FeedsGroupListExpandPreference.ON
    }
