package com.conice.morss.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.conice.morss.infrastructure.preference.PreferencesKey
import com.conice.morss.infrastructure.preference.PreferencesKey.Companion.pullToSwitchArticle
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.infrastructure.preference.getPreference
import com.conice.morss.infrastructure.preference.put

val LocalPullToSwitchArticle = compositionLocalOf { PullToSwitchArticlePreference.default }

class PullToSwitchArticlePreference(val value: Boolean) : Preference() {
    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(PreferencesKey.pullToSwitchArticle, value)
        }
    }

    fun toggle(context: Context, scope: CoroutineScope) =
        PullToSwitchArticlePreference(!value).put(context, scope)

    companion object {
        val default = PullToSwitchArticlePreference(true)
        fun fromPreference(preference: Preferences): PullToSwitchArticlePreference {
            return PullToSwitchArticlePreference(
                preference.getPreference<Boolean>(pullToSwitchArticle) ?: return default
            )
        }
    }
}
