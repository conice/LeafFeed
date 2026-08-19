package com.conice.morss.infrastructure.preference

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                NavigationItemPreference(NavigationItemIds.AI_SUMMARY, ActionPlacement.Hidden),
                NavigationItemPreference(NavigationItemIds.REFRESH, ActionPlacement.Hidden),
            ),
            actions,
        )
    }

    @Test
    fun `restores all fixed bottom items from legacy hidden configuration`() {
        val preferences = mutablePreferencesOf(
            NavigationPreferenceKeys.mainBottomItems to
                "all:hidden,unread:hidden,starred:hidden,readLater:hidden",
        )

        val items = preferences.toNavigationCustomization().mainBottomItems

        assertEquals(NavigationItemIds.ALL, items.first().id)
        assertTrue(items.all { it.placement == ActionPlacement.Toolbar })
    }

    @Test
    fun `normalizes unsupported placement and numeric ranges`() {
        val preferences = mutablePreferencesOf(
            NavigationPreferenceKeys.mainBottomItems to
                "starred:more,unread:hidden,all:hidden,readLater:hidden",
            NavigationPreferenceKeys.mainTopIconSize to 100,
            NavigationPreferenceKeys.readingBottomIconSize to 1,
            NavigationPreferenceKeys.mainBottomHeight to 1,
            NavigationPreferenceKeys.readingBottomHeight to 200,
            NavigationPreferenceKeys.mainTopElevation to -1,
            NavigationPreferenceKeys.readingBottomElevation to 99,
        )

        val customization = preferences.toNavigationCustomization()

        assertEquals(ActionPlacement.Toolbar, customization.mainBottomItems.first().placement)
        assertEquals(NavigationCustomization.MAX_ICON_SIZE, customization.mainTopIconSize)
        assertEquals(NavigationCustomization.MIN_ICON_SIZE, customization.readingBottomIconSize)
        assertEquals(NavigationCustomization.MIN_BOTTOM_HEIGHT, customization.mainBottomHeight)
        assertEquals(
            NavigationCustomization.MAX_BOTTOM_HEIGHT,
            customization.readingBottomHeight,
        )
        assertEquals(NavigationCustomization.MIN_ELEVATION, customization.mainTopElevation)
        assertEquals(
            NavigationCustomization.MAX_ELEVATION,
            customization.readingBottomElevation,
        )
    }

    @Test
    fun `encodes versioned JSON and round trips item order and placement`() {
        val items = listOf(
            NavigationItemPreference(NavigationItemIds.SEARCH, ActionPlacement.More),
            NavigationItemPreference(NavigationItemIds.HISTORY, ActionPlacement.Hidden),
        )

        val encoded = encodeNavigationItems(items)
        val preferences = mutablePreferencesOf(
            NavigationPreferenceKeys.articleTopActions to encoded,
        )

        assertTrue(encoded.startsWith("{"))
        assertTrue(encoded.contains("\"version\":2"))
        assertEquals(items, preferences.toNavigationCustomization().articleTopActions.take(2))
    }

    @Test
    fun `loads legacy comma separated feed actions`() {
        val preferences = mutablePreferencesOf(
            NavigationPreferenceKeys.feedTopActions to
                "addSubscription:more,subscriptionReport:hidden",
        )

        val actions = preferences.toNavigationCustomization().feedTopActions

        assertEquals(NavigationItemIds.ADD_SUBSCRIPTION, actions[0].id)
        assertEquals(ActionPlacement.More, actions[0].placement)
        assertEquals(NavigationItemIds.SUBSCRIPTION_REPORT, actions[1].id)
        assertEquals(ActionPlacement.Hidden, actions[1].placement)
    }

    @Test
    fun `drops the legacy duplicate settings action from feed toolbar`() {
        val preferences = mutablePreferencesOf(
            NavigationPreferenceKeys.feedTopActions to
                "settings:toolbar,sync:more,addSubscription:hidden",
        )

        val actions = preferences.toNavigationCustomization().feedTopActions

        assertFalse(actions.any { it.id == "settings" })
        assertEquals(NavigationItemIds.SYNC, actions.first().id)
        assertEquals(ActionPlacement.More, actions.first().placement)
    }

    @Test
    fun `limits reading bottom bar to five visible actions`() {
        val preferences = mutablePreferencesOf(
            NavigationPreferenceKeys.readingBottomActions to
                """
                    {"version":2,"items":[
                      {"id":"starred","placement":"toolbar"},
                      {"id":"unread","placement":"toolbar"},
                      {"id":"fullContent","placement":"toolbar"},
                      {"id":"textToSpeech","placement":"toolbar"},
                      {"id":"readLater","placement":"toolbar"},
                      {"id":"previousArticle","placement":"toolbar"},
                      {"id":"nextArticle","placement":"toolbar"}
                    ]}
                """.trimIndent(),
        )

        val actions = preferences.toNavigationCustomization().readingBottomActions

        assertEquals(
            NavigationCustomization.MAX_READING_BOTTOM_ACTIONS,
            actions.count { it.placement == ActionPlacement.Toolbar },
        )
        assertTrue(actions.take(5).all { it.placement == ActionPlacement.Toolbar })
        assertTrue(actions.drop(5).all { it.placement == ActionPlacement.Hidden })
    }

    @Test
    fun `drops legacy mark all read action now provided by the floating button`() {
        val preferences = mutablePreferencesOf(
            NavigationPreferenceKeys.articleTopActions to
                "markAllRead:toolbar,search:more",
        )

        val actions = preferences.toNavigationCustomization().articleTopActions

        assertFalse(actions.any { it.id == "markAllRead" })
        assertEquals(NavigationItemIds.SEARCH, actions.first().id)
        assertEquals(ActionPlacement.More, actions.first().placement)
    }

    @Test
    fun `uses the action default for an invalid legacy placement`() {
        val preferences = mutablePreferencesOf(
            NavigationPreferenceKeys.articleTopActions to "search:invalid",
        )

        val actions = preferences.toNavigationCustomization().articleTopActions

        assertEquals(NavigationItemIds.SEARCH, actions.first().id)
        assertEquals(ActionPlacement.Toolbar, actions.first().placement)
    }

    @Test
    fun `normalizes duplicate and unknown JSON items`() {
        val preferences = mutablePreferencesOf(
            NavigationPreferenceKeys.articleTopActions to """
                {"version":2,"items":[
                  {"id":"search","placement":"more"},
                  {"id":"unknown","placement":"toolbar"},
                  {"id":"search","placement":"hidden"}
                ]}
            """.trimIndent(),
        )

        val actions = preferences.toNavigationCustomization().articleTopActions

        assertEquals(1, actions.count { it.id == NavigationItemIds.SEARCH })
        assertEquals(ActionPlacement.More, actions.first().placement)
        assertFalse(actions.any { it.id == "unknown" })
        assertTrue(actions.drop(1).all { it.placement == ActionPlacement.Hidden })
    }

    @Test
    fun `falls back to defaults for malformed or unsupported JSON`() {
        val malformed = mutablePreferencesOf(
            NavigationPreferenceKeys.articleTopActions to "{not-json",
        )
        val futureVersion = mutablePreferencesOf(
            NavigationPreferenceKeys.articleTopActions to "{\"version\":99,\"items\":[]}",
        )

        assertEquals(
            NavigationCustomization.Defaults.articleTopActions,
            malformed.toNavigationCustomization().articleTopActions,
        )
        assertEquals(
            NavigationCustomization.Defaults.articleTopActions,
            futureVersion.toNavigationCustomization().articleTopActions,
        )
    }

    @Test
    fun `catalog entries are unique within each surface`() {
        val entries = NavigationActionCatalog.definitions

        assertEquals(
            entries.size,
            entries.distinctBy { it.surface to it.id }.size,
        )
    }

    @Test
    fun `keeps automatic main bottom height`() {
        val preferences = mutablePreferencesOf(
            NavigationPreferenceKeys.mainBottomHeight to
                NavigationCustomization.AUTOMATIC_BOTTOM_HEIGHT,
        )

        assertEquals(
            NavigationCustomization.AUTOMATIC_BOTTOM_HEIGHT,
            preferences.toNavigationCustomization().mainBottomHeight,
        )
    }
}
