package com.conice.morss.infrastructure.preference

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.conice.morss.infrastructure.preference.PreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowMarkAsReadFabPositionPreferenceTest {
    private val key = intPreferencesKey(PreferencesKey.flowMarkAsReadFabPosition)

    @Test
    fun `defaults to center`() {
        assertEquals(
            FlowMarkAsReadFabPositionPreference.Center,
            FlowMarkAsReadFabPositionPreference.fromPreferences(mutablePreferencesOf()),
        )
    }

    @Test
    fun `loads left center and right values`() {
        assertEquals(
            FlowMarkAsReadFabPositionPreference.Left,
            FlowMarkAsReadFabPositionPreference.fromPreferences(mutablePreferencesOf(key to 0)),
        )
        assertEquals(
            FlowMarkAsReadFabPositionPreference.Center,
            FlowMarkAsReadFabPositionPreference.fromPreferences(mutablePreferencesOf(key to 1)),
        )
        assertEquals(
            FlowMarkAsReadFabPositionPreference.Right,
            FlowMarkAsReadFabPositionPreference.fromPreferences(mutablePreferencesOf(key to 2)),
        )
    }
}
