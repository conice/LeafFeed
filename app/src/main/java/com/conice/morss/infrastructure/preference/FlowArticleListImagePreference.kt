package com.conice.morss.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.conice.morss.infrastructure.preference.PreferencesKey
import com.conice.morss.infrastructure.preference.getPreference
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.flowArticleListImage
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.put

val LocalFlowArticleListImage =
    compositionLocalOf<FlowArticleListImagePreference> { FlowArticleListImagePreference.default }

sealed class FlowArticleListImagePreference(val value: Boolean) : Preference() {
    object ON : FlowArticleListImagePreference(true)
    object OFF : FlowArticleListImagePreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                PreferencesKey.flowArticleListImage,
                value
            )
        }
    }

    companion object {

        val default = ON
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences.getPreference<Boolean>(flowArticleListImage)) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun FlowArticleListImagePreference.not(): FlowArticleListImagePreference =
    when (value) {
        true -> FlowArticleListImagePreference.OFF
        false -> FlowArticleListImagePreference.ON
    }
