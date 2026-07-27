package me.ash.reader.infrastructure.preference

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
    fun `add activates an existing hidden item without duplication`() {
        val items = listOf(item("first"), item("second", ActionPlacement.Hidden))

        val result = NavigationCustomizationEditor.add(items, "second")

        assertEquals(2, result.size)
        assertEquals(ActionPlacement.Toolbar, result[1].placement)
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
    fun `move reorders active items while preserving hidden slots`() {
        val items = listOf(
            item("first"),
            item("hidden", ActionPlacement.Hidden),
            item("second", ActionPlacement.More),
            item("third"),
        )

        val result = NavigationCustomizationEditor.moveActive(items, "third", 0)

        assertEquals(listOf("third", "hidden", "first", "second"), result.map { it.id })
        assertEquals(ActionPlacement.Hidden, result[1].placement)
        assertEquals(ActionPlacement.More, result[3].placement)
    }

    @Test
    fun `move clamps target index and ignores unknown ids`() {
        val items = listOf(item("first"), item("second"))

        assertEquals(
            listOf("second", "first"),
            NavigationCustomizationEditor.moveActive(items, "second", -10).map { it.id },
        )
        assertEquals(
            listOf("second", "first"),
            NavigationCustomizationEditor.moveActive(items, "first", 10).map { it.id },
        )
        assertEquals(items, NavigationCustomizationEditor.moveActive(items, "unknown", 0))
    }

    private fun item(
        id: String,
        placement: ActionPlacement = ActionPlacement.Toolbar,
    ) = NavigationItemPreference(id, placement)
}
