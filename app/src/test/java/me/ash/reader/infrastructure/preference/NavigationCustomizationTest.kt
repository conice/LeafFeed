package me.ash.reader.infrastructure.preference

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationCustomizationTest {
    @Test
    fun `uses defaults when customization is absent`() {
        assertEquals(NavigationCustomization(), mutablePreferencesOf().toNavigationCustomization())
    }

    @Test
    fun `keeps configured order and normalizes invalid entries`() {
        val preferences = mutablePreferencesOf(
            NavigationPreferenceKeys.articleTopActions to
                "search:more,unknown:toolbar,search:hidden,history:hidden",
        )

        val actions = preferences.toNavigationCustomization().articleTopActions

        assertEquals(
            listOf(
                NavigationItemPreference(NavigationItemIds.SEARCH, ActionPlacement.More),
                NavigationItemPreference(NavigationItemIds.HISTORY, ActionPlacement.Hidden),
                NavigationItemPreference(NavigationItemIds.AI_SUMMARY, ActionPlacement.Toolbar),
                NavigationItemPreference(NavigationItemIds.MARK_ALL_READ, ActionPlacement.Toolbar),
            ),
            actions,
        )
    }

    @Test
    fun `restores one bottom item when all are hidden`() {
        val preferences = mutablePreferencesOf(
            NavigationPreferenceKeys.mainBottomItems to
                "all:hidden,unread:hidden,starred:hidden,readLater:hidden",
        )

        val items = preferences.toNavigationCustomization().mainBottomItems

        assertEquals(NavigationItemIds.ALL, items.first().id)
        assertEquals(ActionPlacement.Toolbar, items.first().placement)
        assertTrue(items.drop(1).all { it.placement == ActionPlacement.Hidden })
    }

    @Test
    fun `normalizes unsupported placement and numeric ranges`() {
        val preferences = mutablePreferencesOf(
            NavigationPreferenceKeys.mainBottomItems to
                "starred:more,unread:hidden,all:hidden,readLater:hidden",
            NavigationPreferenceKeys.mainTopIconSize to 100,
            NavigationPreferenceKeys.readingBottomIconSize to 1,
            NavigationPreferenceKeys.mainTopElevation to -1,
            NavigationPreferenceKeys.readingBottomElevation to 99,
        )

        val customization = preferences.toNavigationCustomization()

        assertEquals(ActionPlacement.Toolbar, customization.mainBottomItems.first().placement)
        assertEquals(NavigationCustomization.MAX_ICON_SIZE, customization.mainTopIconSize)
        assertEquals(NavigationCustomization.MIN_ICON_SIZE, customization.readingBottomIconSize)
        assertEquals(NavigationCustomization.MIN_ELEVATION, customization.mainTopElevation)
        assertEquals(
            NavigationCustomization.MAX_ELEVATION,
            customization.readingBottomElevation,
        )
    }

    @Test
    fun `encodes item order and placement`() {
        val items = listOf(
            NavigationItemPreference(NavigationItemIds.SEARCH, ActionPlacement.More),
            NavigationItemPreference(NavigationItemIds.HISTORY, ActionPlacement.Hidden),
        )

        assertEquals("search:more,history:hidden", encodeNavigationItems(items))
    }
}
