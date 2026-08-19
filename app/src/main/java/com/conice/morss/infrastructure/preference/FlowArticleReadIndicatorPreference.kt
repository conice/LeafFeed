package com.conice.morss.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.conice.morss.R
import com.conice.morss.infrastructure.preference.PreferencesKey
import com.conice.morss.infrastructure.preference.getPreference
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.flowArticleListReadIndicator
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.put

val LocalFlowArticleListReadIndicator =
    compositionLocalOf<FlowArticleReadIndicatorPreference> { FlowArticleReadIndicatorPreference.default }

sealed class FlowArticleReadIndicatorPreference(val value: Int) : Preference() {
    data object ExcludingStarred : FlowArticleReadIndicatorPreference(0)
    data object AllRead : FlowArticleReadIndicatorPreference(1)
    data object None : FlowArticleReadIndicatorPreference(2)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                flowArticleListReadIndicator,
                value
            )
        }
    }

    val description: String
        @Composable get() {
            return when (this) {
                AllRead -> stringResource(id = R.string.all_read)
                ExcludingStarred -> stringResource(id = R.string.read_excluding_starred)
                None -> stringResource(id = R.string.none)
            }
        }

    companion object {

        val default = ExcludingStarred
        val values = listOf(ExcludingStarred, AllRead, None)

        fun fromPreferences(preferences: Preferences) =
            when (preferences.getPreference<Int>(flowArticleListReadIndicator)) {
                0 -> ExcludingStarred
                1 -> AllRead
                2 -> None
                else -> default
            }

    }
}