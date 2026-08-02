package me.ash.reader.infrastructure.preference

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class ActionPlacement(val storedValue: String) {
    @SerialName("toolbar")
    Toolbar("toolbar"),
    @SerialName("more")
    More("more"),
    @SerialName("hidden")
    Hidden("hidden");

    companion object {
        fun fromStoredValue(value: String): ActionPlacement? =
            entries.firstOrNull { it.storedValue == value }
    }
}

@Immutable
@Serializable
data class NavigationItemPreference(
    val id: String,
    val placement: ActionPlacement,
)

enum class NavigationSurface {
    MainBottom,
    FeedTop,
    ArticleTop,
    ReadingTop,
    ReadingBottom,
}

@Immutable
data class NavigationActionDefinition(
    val id: String,
    val label: String,
    val surface: NavigationSurface,
    val defaultPlacement: ActionPlacement,
)

object NavigationItemIds {
    const val STARRED = "starred"
    const val UNREAD = "unread"
    const val ALL = "all"
    const val READ_LATER = "readLater"

    const val SUBSCRIPTION_REPORT = "subscriptionReport"
    const val ADD_SUBSCRIPTION = "addSubscription"
    const val SYNC = "sync"

    const val HISTORY = "history"
    const val AI_SUMMARY = "aiSummary"
    const val SEARCH = "search"
    const val REFRESH = "refresh"

    const val TAGS = "tags"
    const val ADD_NOTE = "addNote"
    const val STYLE = "style"
    const val SHARE = "share"
    const val FULL_CONTENT = "fullContent"
    const val TEXT_TO_SPEECH = "textToSpeech"
    const val OPEN_IN_BROWSER = "openInBrowser"
    const val PREVIOUS_ARTICLE = "previousArticle"
    const val NEXT_ARTICLE = "nextArticle"
}

object NavigationActionCatalog {
    val definitions: List<NavigationActionDefinition> = buildList {
        fun register(
            surface: NavigationSurface,
            placement: ActionPlacement,
            vararg actions: Pair<String, String>,
        ) {
            actions.forEach { (id, label) ->
                this@buildList.add(NavigationActionDefinition(id, label, surface, placement))
            }
        }

        register(
            NavigationSurface.MainBottom,
            ActionPlacement.Toolbar,
            NavigationItemIds.STARRED to "Starred",
            NavigationItemIds.UNREAD to "Unread",
            NavigationItemIds.ALL to "All",
            NavigationItemIds.READ_LATER to "Read later",
        )
        register(
            NavigationSurface.FeedTop,
            ActionPlacement.Toolbar,
            NavigationItemIds.SUBSCRIPTION_REPORT to "Information intake",
            NavigationItemIds.ADD_SUBSCRIPTION to "Add subscription",
        )
        register(
            NavigationSurface.FeedTop,
            ActionPlacement.Hidden,
            NavigationItemIds.SYNC to "Sync",
        )
        register(
            NavigationSurface.ArticleTop,
            ActionPlacement.Toolbar,
            NavigationItemIds.HISTORY to "History",
            NavigationItemIds.AI_SUMMARY to "AI summary",
            NavigationItemIds.SEARCH to "Search",
        )
        register(
            NavigationSurface.ArticleTop,
            ActionPlacement.Hidden,
            NavigationItemIds.REFRESH to "Refresh",
        )
        register(
            NavigationSurface.ReadingTop,
            ActionPlacement.Toolbar,
            NavigationItemIds.AI_SUMMARY to "AI summary",
            NavigationItemIds.TAGS to "Tags",
        )
        register(
            NavigationSurface.ReadingTop,
            ActionPlacement.More,
            NavigationItemIds.ADD_NOTE to "Add note",
            NavigationItemIds.STYLE to "Style",
            NavigationItemIds.SHARE to "Share",
        )
        register(
            NavigationSurface.ReadingTop,
            ActionPlacement.Hidden,
            NavigationItemIds.OPEN_IN_BROWSER to "Open in browser",
        )
        register(
            NavigationSurface.ReadingBottom,
            ActionPlacement.Toolbar,
            NavigationItemIds.STARRED to "Starred",
            NavigationItemIds.UNREAD to "Unread",
            NavigationItemIds.FULL_CONTENT to "Full content",
            NavigationItemIds.TEXT_TO_SPEECH to "Text to speech",
            NavigationItemIds.READ_LATER to "Read later",
        )
        register(
            NavigationSurface.ReadingBottom,
            ActionPlacement.Hidden,
            NavigationItemIds.PREVIOUS_ARTICLE to "Previous article",
            NavigationItemIds.NEXT_ARTICLE to "Next article",
        )
    }

    fun definitions(surface: NavigationSurface): List<NavigationActionDefinition> =
        definitions.filter { it.surface == surface }

    fun defaults(surface: NavigationSurface): List<NavigationItemPreference> =
        definitions(surface).map { NavigationItemPreference(it.id, it.defaultPlacement) }

    fun label(id: String): String = definitions.firstOrNull { it.id == id }?.label ?: id
}

object NavigationPreferenceKeys {
    val mainBottomItems = stringPreferencesKey("navigation_main_bottom_items")
    val feedTopActions = stringPreferencesKey("navigation_feed_top_actions")
    val articleTopActions = stringPreferencesKey("navigation_article_top_actions")
    val readingTopActions = stringPreferencesKey("navigation_reading_top_actions")
    val readingBottomActions = stringPreferencesKey("navigation_reading_bottom_actions")

    val mainTopIconSize = intPreferencesKey("navigation_main_top_icon_size")
    val mainBottomIconSize = intPreferencesKey("navigation_main_bottom_icon_size")
    val mainBottomHeight = intPreferencesKey("navigation_main_bottom_height")
    val readingTopIconSize = intPreferencesKey("navigation_reading_top_icon_size")
    val readingBottomIconSize = intPreferencesKey("navigation_reading_bottom_icon_size")
    val readingBottomHeight = intPreferencesKey("navigation_reading_bottom_height")

    val mainTopElevation = intPreferencesKey("navigation_main_top_elevation")
    val mainBottomElevation = intPreferencesKey("navigation_main_bottom_elevation")
    val readingTopElevation = intPreferencesKey("navigation_reading_top_elevation")
    val readingBottomElevation = intPreferencesKey("navigation_reading_bottom_elevation")
}

@Immutable
data class NavigationCustomization(
    val mainBottomItems: List<NavigationItemPreference> = Defaults.mainBottomItems,
    val feedTopActions: List<NavigationItemPreference> = Defaults.feedTopActions,
    val articleTopActions: List<NavigationItemPreference> = Defaults.articleTopActions,
    val readingTopActions: List<NavigationItemPreference> = Defaults.readingTopActions,
    val readingBottomActions: List<NavigationItemPreference> = Defaults.readingBottomActions,
    val mainTopIconSize: Int = DEFAULT_TOP_ICON_SIZE,
    val mainBottomIconSize: Int = DEFAULT_BOTTOM_ICON_SIZE,
    val mainBottomHeight: Int = AUTOMATIC_BOTTOM_HEIGHT,
    val readingTopIconSize: Int = DEFAULT_TOP_ICON_SIZE,
    val readingBottomIconSize: Int = DEFAULT_BOTTOM_ICON_SIZE,
    val readingBottomHeight: Int = DEFAULT_READING_BOTTOM_HEIGHT,
    val mainTopElevation: Int = 0,
    val mainBottomElevation: Int = 0,
    val readingTopElevation: Int = 0,
    val readingBottomElevation: Int = 0,
) {
    object Defaults {
        val mainBottomItems = NavigationActionCatalog.defaults(NavigationSurface.MainBottom)
        val feedTopActions = NavigationActionCatalog.defaults(NavigationSurface.FeedTop)
        val articleTopActions = NavigationActionCatalog.defaults(NavigationSurface.ArticleTop)
        val readingTopActions = NavigationActionCatalog.defaults(NavigationSurface.ReadingTop)
        val readingBottomActions = NavigationActionCatalog.defaults(NavigationSurface.ReadingBottom)
    }

    companion object {
        const val MIN_ICON_SIZE = 20
        const val MAX_ICON_SIZE = 32
        const val DEFAULT_TOP_ICON_SIZE = 22
        const val DEFAULT_BOTTOM_ICON_SIZE = 24
        const val AUTOMATIC_BOTTOM_HEIGHT = 0
        const val DEFAULT_READING_BOTTOM_HEIGHT = 60
        const val MIN_BOTTOM_HEIGHT = 56
        const val MAX_BOTTOM_HEIGHT = 96
        const val MIN_ELEVATION = 0
        const val MAX_ELEVATION = 8
        const val MAX_READING_BOTTOM_ACTIONS = 5
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
            forceToolbar = true,
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
            maxToolbarItems = NavigationCustomization.MAX_READING_BOTTOM_ACTIONS,
        ),
        mainTopIconSize = iconSize(NavigationPreferenceKeys.mainTopIconSize, defaults.mainTopIconSize),
        mainBottomIconSize = iconSize(
            NavigationPreferenceKeys.mainBottomIconSize,
            defaults.mainBottomIconSize,
        ),
        mainBottomHeight = bottomHeight(
            NavigationPreferenceKeys.mainBottomHeight,
            defaults.mainBottomHeight,
            allowAutomatic = true,
        ),
        readingTopIconSize = iconSize(
            NavigationPreferenceKeys.readingTopIconSize,
            defaults.readingTopIconSize,
        ),
        readingBottomIconSize = iconSize(
            NavigationPreferenceKeys.readingBottomIconSize,
            defaults.readingBottomIconSize,
        ),
        readingBottomHeight = bottomHeight(
            NavigationPreferenceKeys.readingBottomHeight,
            defaults.readingBottomHeight,
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
    navigationItemsJson.encodeToString<StoredNavigationItems>(
        StoredNavigationItems(items = items),
    )

private const val NAVIGATION_ITEMS_VERSION = 2

@Serializable
private data class StoredNavigationItems(
    val version: Int = NAVIGATION_ITEMS_VERSION,
    val items: List<NavigationItemPreference>,
)

private val navigationItemsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

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

private fun Preferences.bottomHeight(
    key: Preferences.Key<Int>,
    default: Int,
    allowAutomatic: Boolean = false,
): Int {
    val value = this[key] ?: default
    if (allowAutomatic && value == NavigationCustomization.AUTOMATIC_BOTTOM_HEIGHT) {
        return value
    }
    return value.coerceIn(
        NavigationCustomization.MIN_BOTTOM_HEIGHT,
        NavigationCustomization.MAX_BOTTOM_HEIGHT,
    )
}

private fun parseItems(
    stored: String?,
    defaults: List<NavigationItemPreference>,
    allowMore: Boolean = true,
    requireVisible: Boolean = false,
    forceToolbar: Boolean = false,
    maxToolbarItems: Int? = null,
): List<NavigationItemPreference> {
    if (stored == null) return defaults
    val defaultsById = defaults.associateBy { it.id }
    val seen = mutableSetOf<String>()
    val storedItems = decodeStoredItems(stored, defaultsById) ?: return defaults
    val parsed = storedItems.mapNotNull { storedItem ->
        val id = storedItem.id
        if (id !in defaultsById || !seen.add(id)) return@mapNotNull null
        val placement = storedItem.placement
        NavigationItemPreference(
            id,
            when {
                forceToolbar -> ActionPlacement.Toolbar
                !allowMore && placement == ActionPlacement.More -> ActionPlacement.Toolbar
                else -> placement
            },
        )
    }.toMutableList()
    defaults.filterNot { it.id in seen }.forEach {
        parsed.add(
            it.copy(
                placement = if (forceToolbar) ActionPlacement.Toolbar else ActionPlacement.Hidden,
            )
        )
    }
    maxToolbarItems?.let { maximum ->
        var visibleCount = 0
        parsed.indices.forEach { index ->
            if (parsed[index].placement == ActionPlacement.Toolbar) {
                visibleCount += 1
                if (visibleCount > maximum) {
                    parsed[index] = parsed[index].copy(placement = ActionPlacement.Hidden)
                }
            }
        }
    }
    if (requireVisible && parsed.none { it.placement == ActionPlacement.Toolbar }) {
        parsed[0] = parsed[0].copy(placement = ActionPlacement.Toolbar)
    }
    return parsed
}

private fun decodeStoredItems(
    stored: String,
    defaultsById: Map<String, NavigationItemPreference>,
): List<NavigationItemPreference>? {
    if (stored.trimStart().startsWith('{')) {
        return runCatching {
            navigationItemsJson.decodeFromString<StoredNavigationItems>(stored)
        }.getOrNull()?.takeIf { it.version == NAVIGATION_ITEMS_VERSION }?.items
    }
    return stored.split(',').mapNotNull { entry ->
        val id = entry.substringBefore(':').takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val placement = ActionPlacement.fromStoredValue(entry.substringAfter(':', ""))
            ?: defaultsById[id]?.placement
            ?: return@mapNotNull null
        NavigationItemPreference(id, placement)
    }.takeIf { it.isNotEmpty() }
}
