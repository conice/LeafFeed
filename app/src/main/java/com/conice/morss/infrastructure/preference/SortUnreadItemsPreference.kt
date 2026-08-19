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
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.flowSortUnreadArticles
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.put

val LocalSortUnreadArticles =
    compositionLocalOf<SortUnreadArticlesPreference> { SortUnreadArticlesPreference.default }

sealed class SortUnreadArticlesPreference(val value: Boolean) : Preference() {
    data object Latest : SortUnreadArticlesPreference(false)
    data object Earliest : SortUnreadArticlesPreference(true)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                flowSortUnreadArticles,
                value
            )
        }
    }

    @Composable
    fun description(): String {
        return when (this) {
            Earliest -> stringResource(R.string.earliest)
            Latest -> stringResource(R.string.latest)
        }
    }

    companion object {

        val default = Latest
        val values = listOf(Latest, Earliest)

        fun fromPreferences(preferences: Preferences) =
            when (preferences.getPreference<Boolean>(flowSortUnreadArticles)) {
                true -> Earliest
                false -> Latest
                else -> default
            }
    }
}