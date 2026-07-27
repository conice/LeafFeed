package me.ash.reader.infrastructure.preference

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

enum class ActionPlacement(val storedValue: String) {
    Toolbar("toolbar"),
    More("more"),
    Hidden("hidden");

    companion object {
        fun fromStoredValue(value: String): ActionPlacement? =
            entries.firstOrNull { it.storedValue == value }
    }
}

data class NavigationItemPreference(
    val id: String,
    val placement: ActionPlacement,
)

object NavigationItemIds {
    const val STARRED = "starred"
    const val UNREAD = "unread"
    const val ALL = "all"
    const val READ_LATER = "readLater"

    const val SUBSCRIPTION_REPORT = "subscriptionReport"
    const val ADD_SUBSCRIPTION = "addSubscription"

    const val HISTORY = "history"
    const val AI_SUMMARY = "aiSummary"
    const val MARK_ALL_READ = "markAllRead"
    const val SEARCH = "search"

    const val TAGS = "tags"
    const val ADD_NOTE = "addNote"
    const val STYLE = "style"
    const val SHARE = "share"
    const val FULL_CONTENT = "fullContent"
    const val TEXT_TO_SPEECH = "textToSpeech"
}

object NavigationPreferenceKeys {
    val mainBottomItems = stringPreferencesKey("navigation_main_bottom_items")
    val feedTopActions = stringPreferencesKey("navigation_feed_top_actions")
    val articleTopActions = stringPreferencesKey("navigation_article_top_actions")
    val readingTopActions = stringPreferencesKey("navigation_reading_top_actions")
    val readingBottomActions = stringPreferencesKey("navigation_reading_bottom_actions")

    val mainTopIconSize = intPreferencesKey("navigation_main_top_icon_size")
    val mainBottomIconSize = intPreferencesKey("navigation_main_bottom_icon_size")
    val readingTopIconSize = intPreferencesKey("navigation_reading_top_icon_size")
    val readingBottomIconSize = intPreferencesKey("navigation_reading_bottom_icon_size")

    val mainTopElevation = intPreferencesKey("navigation_main_top_elevation")
    val mainBottomElevation = intPreferencesKey("navigation_main_bottom_elevation")
    val readingTopElevation = intPreferencesKey("navigation_reading_top_elevation")
    val readingBottomElevation = intPreferencesKey("navigation_reading_bottom_elevation")
}

data class NavigationCustomization(
    val mainBottomItems: List<NavigationItemPreference> = Defaults.mainBottomItems,
    val feedTopActions: List<NavigationItemPreference> = Defaults.feedTopActions,
    val articleTopActions: List<NavigationItemPreference> = Defaults.articleTopActions,
    val readingTopActions: List<NavigationItemPreference> = Defaults.readingTopActions,
    val readingBottomActions: List<NavigationItemPreference> = Defaults.readingBottomActions,
    val mainTopIconSize: Int = DEFAULT_TOP_ICON_SIZE,
    val mainBottomIconSize: Int = DEFAULT_BOTTOM_ICON_SIZE,
    val readingTopIconSize: Int = DEFAULT_TOP_ICON_SIZE,
    val readingBottomIconSize: Int = DEFAULT_BOTTOM_ICON_SIZE,
    val mainTopElevation: Int = 0,
    val mainBottomElevation: Int = 0,
    val readingTopElevation: Int = 0,
    val readingBottomElevation: Int = 0,
) {
    object Defaults {
        val mainBottomItems = toolbarItems(
            NavigationItemIds.STARRED,
            NavigationItemIds.UNREAD,
            NavigationItemIds.ALL,
            NavigationItemIds.READ_LATER,
        )
        val feedTopActions = toolbarItems(
            NavigationItemIds.SUBSCRIPTION_REPORT,
            NavigationItemIds.ADD_SUBSCRIPTION,
        )
        val articleTopActions = toolbarItems(
            NavigationItemIds.HISTORY,
            NavigationItemIds.AI_SUMMARY,
            NavigationItemIds.MARK_ALL_READ,
            NavigationItemIds.SEARCH,
        )
        val readingTopActions = listOf(
            NavigationItemPreference(NavigationItemIds.AI_SUMMARY, ActionPlacement.Toolbar),
            NavigationItemPreference(NavigationItemIds.TAGS, ActionPlacement.Toolbar),
            NavigationItemPreference(NavigationItemIds.ADD_NOTE, ActionPlacement.More),
            NavigationItemPreference(NavigationItemIds.STYLE, ActionPlacement.More),
            NavigationItemPreference(NavigationItemIds.SHARE, ActionPlacement.More),
        )
        val readingBottomActions = toolbarItems(
            NavigationItemIds.STARRED,
            NavigationItemIds.UNREAD,
            NavigationItemIds.FULL_CONTENT,
            NavigationItemIds.TEXT_TO_SPEECH,
            NavigationItemIds.READ_LATER,
        )

        private fun toolbarItems(vararg ids: String) =
            ids.map { NavigationItemPreference(it, ActionPlacement.Toolbar) }
    }

    companion object {
        const val MIN_ICON_SIZE = 20
        const val MAX_ICON_SIZE = 32
        const val DEFAULT_TOP_ICON_SIZE = 22
        const val DEFAULT_BOTTOM_ICON_SIZE = 24
        const val MIN_ELEVATION = 0
        const val MAX_ELEVATION = 8
    }
}

fun Preferences.toNavigationCustomization(): NavigationCustomization {
    val defaults = NavigationCustomization()
    return NavigationCustomization(
        mainBottomItems = parseItems(
            this[NavigationPreferenceKeys.mainBottomItems],
            defaults.mainBottomItems,
            allowMore = false,
            requireVisible = true,
        ),
        feedTopActions = parseItems(
            this[NavigationPreferenceKeys.feedTopActions],
            defaults.feedTopActions,
        ),
        articleTopActions = parseItems(
            this[NavigationPreferenceKeys.articleTopActions],
            defaults.articleTopActions,
        ),
        readingTopActions = parseItems(
            this[NavigationPreferenceKeys.readingTopActions],
            defaults.readingTopActions,
        ),
        readingBottomActions = parseItems(
            this[NavigationPreferenceKeys.readingBottomActions],
            defaults.readingBottomActions,
            allowMore = false,
            requireVisible = true,
        ),
        mainTopIconSize = iconSize(NavigationPreferenceKeys.mainTopIconSize, defaults.mainTopIconSize),
        mainBottomIconSize = iconSize(
            NavigationPreferenceKeys.mainBottomIconSize,
            defaults.mainBottomIconSize,
        ),
        readingTopIconSize = iconSize(
            NavigationPreferenceKeys.readingTopIconSize,
            defaults.readingTopIconSize,
        ),
        readingBottomIconSize = iconSize(
            NavigationPreferenceKeys.readingBottomIconSize,
            defaults.readingBottomIconSize,
        ),
        mainTopElevation = elevation(
            NavigationPreferenceKeys.mainTopElevation,
            defaults.mainTopElevation,
        ),
        mainBottomElevation = elevation(
            NavigationPreferenceKeys.mainBottomElevation,
            defaults.mainBottomElevation,
        ),
        readingTopElevation = elevation(
            NavigationPreferenceKeys.readingTopElevation,
            defaults.readingTopElevation,
        ),
        readingBottomElevation = elevation(
            NavigationPreferenceKeys.readingBottomElevation,
            defaults.readingBottomElevation,
        ),
    )
}

fun encodeNavigationItems(items: List<NavigationItemPreference>): String =
    items.joinToString(",") { "${it.id}:${it.placement.storedValue}" }

private fun Preferences.iconSize(key: Preferences.Key<Int>, default: Int): Int =
    (this[key] ?: default).coerceIn(
        NavigationCustomization.MIN_ICON_SIZE,
        NavigationCustomization.MAX_ICON_SIZE,
    )

private fun Preferences.elevation(key: Preferences.Key<Int>, default: Int): Int =
    (this[key] ?: default).coerceIn(
        NavigationCustomization.MIN_ELEVATION,
        NavigationCustomization.MAX_ELEVATION,
    )

private fun parseItems(
    stored: String?,
    defaults: List<NavigationItemPreference>,
    allowMore: Boolean = true,
    requireVisible: Boolean = false,
): List<NavigationItemPreference> {
    if (stored == null) return defaults
    val defaultsById = defaults.associateBy { it.id }
    val seen = mutableSetOf<String>()
    val parsed = stored.split(',').mapNotNull { entry ->
        val id = entry.substringBefore(':')
        if (id !in defaultsById || !seen.add(id)) return@mapNotNull null
        val placement = ActionPlacement.fromStoredValue(entry.substringAfter(':', ""))
            ?: defaultsById.getValue(id).placement
        NavigationItemPreference(
            id,
            if (!allowMore && placement == ActionPlacement.More) ActionPlacement.Toolbar
            else placement,
        )
    }.toMutableList()
    defaults.filterNot { it.id in seen }.forEach(parsed::add)
    if (requireVisible && parsed.none { it.placement == ActionPlacement.Toolbar }) {
        parsed[0] = parsed[0].copy(placement = ActionPlacement.Toolbar)
    }
    return parsed
}
