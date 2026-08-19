package com.conice.morss.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.conice.morss.infrastructure.preference.PreferencesKey
import com.conice.morss.infrastructure.preference.getPreference
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.flowArticleListDateStickyHeader
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.put

val LocalFlowArticleListDateStickyHeader =
    compositionLocalOf<FlowArticleListDateStickyHeaderPreference> { FlowArticleListDateStickyHeaderPreference.default }

sealed class FlowArticleListDateStickyHeaderPreference(val value: Boolean) : Preference() {
    object ON : FlowArticleListDateStickyHeaderPreference(true)
    object OFF : FlowArticleListDateStickyHeaderPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                PreferencesKey.flowArticleListDateStickyHeader,
                value
            )
        }
    }

    companion object {

        val default = OFF
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences.getPreference<Boolean>(flowArticleListDateStickyHeader)) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun FlowArticleListDateStickyHeaderPreference.not(): FlowArticleListDateStickyHeaderPreference =
    when (value) {
        true -> FlowArticleListDateStickyHeaderPreference.OFF
        false -> FlowArticleListDateStickyHeaderPreference.ON
    }
