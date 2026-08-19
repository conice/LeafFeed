package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.PreferencesKey
import me.ash.reader.infrastructure.preference.getPreference
import me.ash.reader.infrastructure.preference.PreferencesKey.Companion.initialPage
import me.ash.reader.infrastructure.preference.dataStore
import me.ash.reader.infrastructure.preference.put

val LocalInitialPage = compositionLocalOf<InitialPagePreference> { InitialPagePreference.default }

sealed class InitialPagePreference(val value: Int) : Preference() {
    object FeedsPage : InitialPagePreference(0)
    object FlowPage : InitialPagePreference(1)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                PreferencesKey.initialPage,
                value
            )
        }
    }

    fun toDesc(context: Context): String =
        when (this) {
            FeedsPage -> context.getString(R.string.feeds_page)
            FlowPage -> context.getString(R.string.flow_page)
        }

    companion object {

        val default = FeedsPage
        val values = listOf(FeedsPage, FlowPage)

        fun fromPreferences(preferences: Preferences) =
            when (preferences.getPreference<Int>(initialPage)) {
                0 -> FeedsPage
                1 -> FlowPage
                else -> default
            }
    }
}
