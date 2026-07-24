package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.ui.ext.PreferencesKey
import me.ash.reader.ui.ext.getPreference
import me.ash.reader.ui.ext.PreferencesKey.Companion.languages
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put
import java.util.Locale

val LocalLanguages = compositionLocalOf<LanguagesPreference> { LanguagesPreference.default }

sealed class LanguagesPreference(val value: Int) : Preference() {
    data object UseDeviceLanguages : LanguagesPreference(0)
    data object English : LanguagesPreference(1)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                PreferencesKey.languages, value
            )
            scope.launch(Dispatchers.Main) { setLocale(this@LanguagesPreference) }
        }
    }

    @Composable
    fun toDesc(): String = toLocale().toDisplayName()

    fun toLocale(): Locale? = when (this) {
        UseDeviceLanguages -> null
        English -> Locale("en")
    }

    private fun toLocaleList(): LocaleListCompat =
        toLocale()?.let { LocaleListCompat.create(it) } ?: LocaleListCompat.getEmptyLocaleList()

    companion object {

        val default = UseDeviceLanguages

        val values = listOf(UseDeviceLanguages, English)

        fun fromPreferences(preferences: Preferences): LanguagesPreference =
            fromValue(preferences.getPreference<Int>(languages) ?: 0)

        fun fromValue(value: Int): LanguagesPreference =
            if (value == English.value) English else default

        fun setLocale(preference: LanguagesPreference) {
            AppCompatDelegate.setApplicationLocales(preference.toLocaleList())
        }
    }
}

@Composable
fun Locale?.toDisplayName(): String = this?.getDisplayName(this) ?: stringResource(
    id = R.string.use_device_languages
)
