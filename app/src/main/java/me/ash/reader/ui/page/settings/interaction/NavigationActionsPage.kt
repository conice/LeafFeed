package me.ash.reader.ui.page.settings.interaction

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.FlowFilterBarStylePreference
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarStyle
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.preference.NavigationCustomization
import me.ash.reader.infrastructure.preference.NavigationItemPreference
import me.ash.reader.infrastructure.preference.NavigationPreferenceKeys
import me.ash.reader.infrastructure.preference.encodeNavigationItems
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.theme.palette.onLight

@Composable
fun NavigationActionsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val settings = LocalSettings.current.navigationCustomization
    val automaticMainBottomHeight =
        if (LocalFlowFilterBarStyle.current == FlowFilterBarStylePreference.Icon) 64 else 80

    val mainBottomState = rememberNavigationSectionState(settings.mainBottomItems)
    val feedTopState = rememberNavigationSectionState(settings.feedTopActions)
    val articleTopState = rememberNavigationSectionState(settings.articleTopActions)
    val readingTopState = rememberNavigationSectionState(settings.readingTopActions)
    val readingBottomState = rememberNavigationSectionState(settings.readingBottomActions)

    var placementRequest by remember { mutableStateOf<PlacementRequest?>(null) }
    var addRequest by remember { mutableStateOf<AddRequest?>(null) }
    var moveRequest by remember { mutableStateOf<MoveRequest?>(null) }

    fun writeItems(key: Preferences.Key<String>, items: List<NavigationItemPreference>) {
        scope.launch { context.dataStore.edit { it[key] = encodeNavigationItems(items) } }
    }

    fun writeInt(key: Preferences.Key<Int>, value: Int) {
        scope.launch { context.dataStore.edit { it[key] = value } }
    }

    RYScaffold(
        containerColor =
            MaterialTheme.colorScheme.surface onLight
                MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        content = {
            LazyColumn(state = listState) {
                item(key = "page-header") {
                    DisplayText(text = "Navigation and actions", desc = "")
                    Spacer(Modifier.height(16.dp))
                    Subtitle(Modifier.padding(horizontal = 24.dp), "Main screen")
                }
                navigationActionSection(
                    sectionId = "main-bottom",
                    title = "Bottom filters",
                    state = mainBottomState,
                    key = NavigationPreferenceKeys.mainBottomItems,
                    listState = listState,
                    allowMore = false,
                    requireVisible = true,
                    addLabel = "Add filter",
                    onCommit = ::writeItems,
                    onPlacement = {},
                    onAdd = {
                        addRequest = AddRequest(
                            "Add filter",
                            mainBottomState,
                            NavigationPreferenceKeys.mainBottomItems,
                        )
                    },
                    onMove = {
                        moveRequest = MoveRequest(
                            it,
                            mainBottomState,
                            NavigationPreferenceKeys.mainBottomItems,
                        )
                    },
                )
                item(key = "main-bottom-size") {
                    NavigationNumericSlider(
                        title = "Bottom icon size",
                        value = settings.mainBottomIconSize,
                        range = NavigationCustomization.MIN_ICON_SIZE..
                            NavigationCustomization.MAX_ICON_SIZE,
                        onChange = { writeInt(NavigationPreferenceKeys.mainBottomIconSize, it) },
                    )
                }
                item(key = "main-bottom-height") {
                    AutomaticNavigationNumericSlider(
                        title = "Bottom background height",
                        value = settings.mainBottomHeight,
                        automaticValue = automaticMainBottomHeight,
                        range = NavigationCustomization.MIN_BOTTOM_HEIGHT..
                            NavigationCustomization.MAX_BOTTOM_HEIGHT,
                        onChange = { writeInt(NavigationPreferenceKeys.mainBottomHeight, it) },
                    )
                }
                item(key = "main-bottom-elevation") {
                    NavigationNumericSlider(
                        title = "Bottom tonal elevation",
                        value = settings.mainBottomElevation,
                        range = NavigationCustomization.MIN_ELEVATION..
                            NavigationCustomization.MAX_ELEVATION,
                        onChange = { writeInt(NavigationPreferenceKeys.mainBottomElevation, it) },
                    )
                }
                navigationActionSection(
                    sectionId = "feed-top",
                    title = "Feed page actions",
                    state = feedTopState,
                    key = NavigationPreferenceKeys.feedTopActions,
                    listState = listState,
                    onCommit = ::writeItems,
                    onPlacement = {
                        placementRequest = PlacementRequest(
                            it,
                            feedTopState,
                            NavigationPreferenceKeys.feedTopActions,
                        )
                    },
                    onAdd = {
                        addRequest = AddRequest(
                            "Add action",
                            feedTopState,
                            NavigationPreferenceKeys.feedTopActions,
                        )
                    },
                    onMove = {
                        moveRequest = MoveRequest(
                            it,
                            feedTopState,
                            NavigationPreferenceKeys.feedTopActions,
                        )
                    },
                )
                navigationActionSection(
                    sectionId = "article-top",
                    title = "Article list actions",
                    state = articleTopState,
                    key = NavigationPreferenceKeys.articleTopActions,
                    listState = listState,
                    onCommit = ::writeItems,
                    onPlacement = {
                        placementRequest = PlacementRequest(
                            it,
                            articleTopState,
                            NavigationPreferenceKeys.articleTopActions,
                        )
                    },
                    onAdd = {
                        addRequest = AddRequest(
                            "Add action",
                            articleTopState,
                            NavigationPreferenceKeys.articleTopActions,
                        )
                    },
                    onMove = {
                        moveRequest = MoveRequest(
                            it,
                            articleTopState,
                            NavigationPreferenceKeys.articleTopActions,
                        )
                    },
                )
                item(key = "main-top-size") {
                    NavigationNumericSlider(
                        title = "Top icon size",
                        value = settings.mainTopIconSize,
                        range = NavigationCustomization.MIN_ICON_SIZE..
                            NavigationCustomization.MAX_ICON_SIZE,
                        onChange = { writeInt(NavigationPreferenceKeys.mainTopIconSize, it) },
                    )
                }
                item(key = "main-top-elevation") {
                    NavigationNumericSlider(
                        title = "Top tonal elevation",
                        value = settings.mainTopElevation,
                        range = NavigationCustomization.MIN_ELEVATION..
                            NavigationCustomization.MAX_ELEVATION,
                        onChange = { writeInt(NavigationPreferenceKeys.mainTopElevation, it) },
                    )
                }
                item(key = "reading-header") {
                    Spacer(Modifier.height(24.dp))
                    Subtitle(Modifier.padding(horizontal = 24.dp), "Reading screen")
                }
                navigationActionSection(
                    sectionId = "reading-top",
                    title = "Top actions",
                    state = readingTopState,
                    key = NavigationPreferenceKeys.readingTopActions,
                    listState = listState,
                    onCommit = ::writeItems,
                    onPlacement = {
                        placementRequest = PlacementRequest(
                            it,
                            readingTopState,
                            NavigationPreferenceKeys.readingTopActions,
                        )
                    },
                    onAdd = {
                        addRequest = AddRequest(
                            "Add action",
                            readingTopState,
                            NavigationPreferenceKeys.readingTopActions,
                        )
                    },
                    onMove = {
                        moveRequest = MoveRequest(
                            it,
                            readingTopState,
                            NavigationPreferenceKeys.readingTopActions,
                        )
                    },
                )
                item(key = "reading-top-size") {
                    NavigationNumericSlider(
                        title = "Top icon size",
                        value = settings.readingTopIconSize,
                        range = NavigationCustomization.MIN_ICON_SIZE..
                            NavigationCustomization.MAX_ICON_SIZE,
                        onChange = { writeInt(NavigationPreferenceKeys.readingTopIconSize, it) },
                    )
                }
                item(key = "reading-top-elevation") {
                    NavigationNumericSlider(
                        title = "Top tonal elevation",
                        value = settings.readingTopElevation,
                        range = NavigationCustomization.MIN_ELEVATION..
                            NavigationCustomization.MAX_ELEVATION,
                        onChange = { writeInt(NavigationPreferenceKeys.readingTopElevation, it) },
                    )
                }
                navigationActionSection(
                    sectionId = "reading-bottom",
                    title = "Bottom actions",
                    state = readingBottomState,
                    key = NavigationPreferenceKeys.readingBottomActions,
                    listState = listState,
                    allowMore = false,
                    requireVisible = true,
                    onCommit = ::writeItems,
                    onPlacement = {},
                    onAdd = {
                        addRequest = AddRequest(
                            "Add action",
                            readingBottomState,
                            NavigationPreferenceKeys.readingBottomActions,
                        )
                    },
                    onMove = {
                        moveRequest = MoveRequest(
                            it,
                            readingBottomState,
                            NavigationPreferenceKeys.readingBottomActions,
                        )
                    },
                )
                item(key = "reading-bottom-size") {
                    NavigationNumericSlider(
                        title = "Bottom icon size",
                        value = settings.readingBottomIconSize,
                        range = NavigationCustomization.MIN_ICON_SIZE..
                            NavigationCustomization.MAX_ICON_SIZE,
                        onChange = {
                            writeInt(NavigationPreferenceKeys.readingBottomIconSize, it)
                        },
                    )
                }
                item(key = "reading-bottom-height") {
                    NavigationNumericSlider(
                        title = "Bottom background height",
                        value = settings.readingBottomHeight,
                        range = NavigationCustomization.MIN_BOTTOM_HEIGHT..
                            NavigationCustomization.MAX_BOTTOM_HEIGHT,
                        onChange = { writeInt(NavigationPreferenceKeys.readingBottomHeight, it) },
                    )
                }
                item(key = "reading-bottom-elevation") {
                    NavigationNumericSlider(
                        title = "Bottom tonal elevation",
                        value = settings.readingBottomElevation,
                        range = NavigationCustomization.MIN_ELEVATION..
                            NavigationCustomization.MAX_ELEVATION,
                        onChange = {
                            writeInt(NavigationPreferenceKeys.readingBottomElevation, it)
                        },
                    )
                }
                item(key = "reset") {
                    TextButton(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        onClick = {
                            scope.launch {
                                context.dataStore.edit { preferences ->
                                    navigationPreferenceKeys.forEach { preferences.remove(it) }
                                }
                            }
                        },
                    ) { Text("Reset to defaults") }
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )

    NavigationActionDialogs(
        placementRequest = placementRequest,
        addRequest = addRequest,
        moveRequest = moveRequest,
        onWrite = ::writeItems,
        onDismissPlacement = { placementRequest = null },
        onDismissAdd = { addRequest = null },
        onDismissMove = { moveRequest = null },
    )
}

private val navigationPreferenceKeys: List<Preferences.Key<*>> = listOf(
    NavigationPreferenceKeys.mainBottomItems,
    NavigationPreferenceKeys.feedTopActions,
    NavigationPreferenceKeys.articleTopActions,
    NavigationPreferenceKeys.readingTopActions,
    NavigationPreferenceKeys.readingBottomActions,
    NavigationPreferenceKeys.mainTopIconSize,
    NavigationPreferenceKeys.mainBottomIconSize,
    NavigationPreferenceKeys.mainBottomHeight,
    NavigationPreferenceKeys.readingTopIconSize,
    NavigationPreferenceKeys.readingBottomIconSize,
    NavigationPreferenceKeys.readingBottomHeight,
    NavigationPreferenceKeys.mainTopElevation,
    NavigationPreferenceKeys.mainBottomElevation,
    NavigationPreferenceKeys.readingTopElevation,
    NavigationPreferenceKeys.readingBottomElevation,
)
