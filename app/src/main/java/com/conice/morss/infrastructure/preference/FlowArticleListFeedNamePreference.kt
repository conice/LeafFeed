package com.conice.morss.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.conice.morss.infrastructure.preference.PreferencesKey
import com.conice.morss.infrastructure.preference.getPreference
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.flowArticleListFeedName
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.put

val LocalFlowArticleListFeedName =
    compositionLocalOf<FlowArticleListFeedNamePreference> { FlowArticleListFeedNamePreference.default }

sealed class FlowArticleListFeedNamePreference(val value: Boolean) : Preference() {
    object ON : FlowArticleListFeedNamePreference(true)
    object OFF : FlowArticleListFeedNamePreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                PreferencesKey.flowArticleListFeedName,
                value
            )
        }
    }

    companion object {

        val default = ON
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences.getPreference<Boolean>(flowArticleListFeedName)) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun FlowArticleListFeedNamePreference.not(): FlowArticleListFeedNamePreference =
    when (value) {
        true -> FlowArticleListFeedNamePreference.OFF
        false -> FlowArticleListFeedNamePreference.ON
    }
