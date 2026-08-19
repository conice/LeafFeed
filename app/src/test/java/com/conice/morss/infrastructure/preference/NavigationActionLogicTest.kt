package com.conice.morss.infrastructure.preference

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationActionLogicTest {
    @Test
    fun `resolver filters unavailable and hidden actions`() {
        val actions = listOf(
            item("first"),
            item("hidden", ActionPlacement.Hidden),
            item("unavailable"),
        )

        val layout = resolveNavigationActionLayout(actions, setOf("first", "hidden"), 3)

        assertEquals(listOf("first"), layout.toolbar.map { it.id })
        assertEquals(emptyList<String>(), layout.overflow.map { it.id })
    }

    @Test
    fun `resolver reserves one toolbar slot for overflow`() {
        val actions = listOf(
            item("first"),
            item("second"),
            item("explicit-more", ActionPlacement.More),
        )

        val layout = resolveNavigationActionLayout(
            actions,
            actions.mapTo(mutableSetOf()) { it.id },
            2,
        )

        assertEquals(listOf("first"), layout.toolbar.map { it.id })
        assertEquals(listOf("second", "explicit-more"), layout.overflow.map { it.id })
    }

    @Test
    fun `resolver handles zero capacity without losing actions`() {
        val actions = listOf(item("first"), item("second"))

        val layout = resolveNavigationActionLayout(actions, setOf("first", "second"), 0)

        assertEquals(emptyList<String>(), layout.toolbar.map { it.id })
        assertEquals(listOf("first", "second"), layout.overflow.map { it.id })
    }

    @Test
    fun `required visible item cannot hide the final toolbar action`() {
        val items = listOf(item("first"), item("second", ActionPlacement.Hidden))

        val result = NavigationCustomizationEditor.changePlacement(
            items,
            "first",
            ActionPlacement.Hidden,
            requireVisible = true,
        )

        assertEquals(items, result)
    }

    @Test
    fun `placement change ignores unknown ids`() {
        val items = listOf(item("first"))

        assertEquals(
            items,
            NavigationCustomizationEditor.changePlacement(
                items,
                "unknown",
                ActionPlacement.Hidden,
                requireVisible = true,
            ),
        )
    }

    @Test
    fun `placement change respects toolbar capacity`() {
        val items = listOf(
            item("first"),
            item("second"),
            item("hidden", ActionPlacement.Hidden),
        )

        val result = NavigationCustomizationEditor.changePlacement(
            items = items,
            itemId = "hidden",
            placement = ActionPlacement.Toolbar,
            maxToolbarItems = 2,
        )

        assertEquals(items, result)
    }

    @Test
    fun `move reorders every item including hidden actions`() {
        val items = listOf(
            item("first"),
            item("hidden", ActionPlacement.Hidden),
            item("second"),
        )

        val result = NavigationCustomizationEditor.move(items, "hidden", 2)

        assertEquals(listOf("first", "second", "hidden"), result.map { it.id })
        assertEquals(ActionPlacement.Hidden, result.last().placement)
    }

    @Test
    fun `move clamps target index and ignores unknown ids`() {
        val items = listOf(item("first"), item("second"))

        assertEquals(
            listOf("second", "first"),
            NavigationCustomizationEditor.move(items, "second", -10).map { it.id },
        )
        assertEquals(
            listOf("second", "first"),
            NavigationCustomizationEditor.move(items, "first", 10).map { it.id },
        )
        assertEquals(items, NavigationCustomizationEditor.move(items, "unknown", 0))
    }

    private fun item(
        id: String,
        placement: ActionPlacement = ActionPlacement.Toolbar,
    ) = NavigationItemPreference(id, placement)
}
