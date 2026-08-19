package com.conice.morss.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.conice.morss.infrastructure.preference.PreferencesKey
import com.conice.morss.infrastructure.preference.getPreference
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.flowArticleListFeedIcon
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.put

val LocalFlowArticleListFeedIcon =
    compositionLocalOf<FlowArticleListFeedIconPreference> { FlowArticleListFeedIconPreference.default }

sealed class FlowArticleListFeedIconPreference(val value: Boolean) : Preference() {
    object ON : FlowArticleListFeedIconPreference(true)
    object OFF : FlowArticleListFeedIconPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                PreferencesKey.flowArticleListFeedIcon,
                value
            )
        }
    }

    companion object {

        val default = ON
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences.getPreference<Boolean>(flowArticleListFeedIcon)) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun FlowArticleListFeedIconPreference.not(): FlowArticleListFeedIconPreference =
    when (value) {
        true -> FlowArticleListFeedIconPreference.OFF
        false -> FlowArticleListFeedIconPreference.ON
    }
