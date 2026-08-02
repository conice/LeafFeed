package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.material3.FabPosition
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.ui.ext.PreferencesKey
import me.ash.reader.ui.ext.PreferencesKey.Companion.flowMarkAsReadFabPosition
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalFlowMarkAsReadFabPosition =
    compositionLocalOf<FlowMarkAsReadFabPositionPreference> {
        FlowMarkAsReadFabPositionPreference.default
    }

sealed class FlowMarkAsReadFabPositionPreference(val value: Int) : Preference() {
    data object Left : FlowMarkAsReadFabPositionPreference(0)
    data object Center : FlowMarkAsReadFabPositionPreference(1)
    data object Right : FlowMarkAsReadFabPositionPreference(2)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(flowMarkAsReadFabPosition, value)
        }
    }

    @Stable
    fun toDesc(context: Context): String =
        when (this) {
            Left -> context.getString(R.string.position_left)
            Center -> context.getString(R.string.position_center)
            Right -> context.getString(R.string.position_right)
        }

    @Stable
    fun toFabPosition(): FabPosition =
        when (this) {
            Left -> FabPosition.Start
            Center -> FabPosition.Center
            Right -> FabPosition.End
        }

    companion object {
        val default = Center
        val values = listOf(Left, Center, Right)
        private val preferenceKey = intPreferencesKey(flowMarkAsReadFabPosition)

        fun fromPreferences(preferences: Preferences): FlowMarkAsReadFabPositionPreference {
            if (preferenceKey !in preferences) return default
            return when (preferences[preferenceKey]) {
                0 -> Left
                1 -> Center
                2 -> Right
                else -> default
            }
        }
    }
}
