package com.conice.morss.ui.page.settings.interaction

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.launch
import com.conice.morss.R
import com.conice.morss.infrastructure.preference.LocalSettings
import com.conice.morss.infrastructure.preference.NavigationCustomization
import com.conice.morss.infrastructure.preference.NavigationItemPreference
import com.conice.morss.infrastructure.preference.NavigationPreferenceKeys
import com.conice.morss.infrastructure.preference.encodeNavigationItems
import com.conice.morss.ui.component.base.DisplayText
import com.conice.morss.ui.component.base.FeedbackIconButton
import com.conice.morss.ui.component.base.RYScaffold
import com.conice.morss.infrastructure.preference.dataStore
import com.conice.morss.ui.theme.LayoutTokens
import com.conice.morss.ui.theme.palette.onLight

private const val HOME_TAB = 0

@Composable
fun NavigationActionsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val settings = LocalSettings.current.navigationCustomization
    var selectedTab by rememberSaveable { mutableIntStateOf(HOME_TAB) }
    var placementRequest by remember { mutableStateOf<NavigationPlacementRequest?>(null) }
    var moveRequest by remember { mutableStateOf<NavigationMoveRequest?>(null) }
    var resetAllVisible by remember { mutableStateOf(false) }

    val mainBottomState = rememberNavigationSectionState(settings.mainBottomItems)
    val feedTopState = rememberNavigationSectionState(settings.feedTopActions)
    val articleTopState = rememberNavigationSectionState(settings.articleTopActions)
    val readingTopState = rememberNavigationSectionState(settings.readingTopActions)
    val readingBottomState = rememberNavigationSectionState(settings.readingBottomActions)
    val homeFiltersTitle = stringResource(R.string.navigation_home_filters_title)
    val homeFiltersDescription = stringResource(R.string.navigation_home_filters_desc)
    val homeFiltersHint = stringResource(R.string.navigation_home_filters_hint)
    val feedActionsTitle = stringResource(R.string.navigation_feed_actions_title)
    val feedActionsDescription = stringResource(R.string.navigation_feed_actions_desc)
    val articleActionsTitle = stringResource(R.string.navigation_article_actions_title)
    val articleActionsDescription = stringResource(R.string.navigation_article_actions_desc)
    val readingTopTitle = stringResource(R.string.navigation_reading_top_title)
    val readingTopDescription = stringResource(R.string.navigation_reading_top_desc)
    val readingBottomTitle = stringResource(R.string.navigation_reading_bottom_title)
    val readingBottomDescription = stringResource(R.string.navigation_reading_bottom_desc)
    val contextualHint = stringResource(R.string.navigation_contextual_hint)
    val readingBottomHint = stringResource(
        R.string.navigation_reading_bottom_hint,
        NavigationCustomization.MAX_READING_BOTTOM_ACTIONS,
    )

    fun writeItems(key: Preferences.Key<String>, items: List<NavigationItemPreference>) {
        scope.launch { context.dataStore.edit { it[key] = encodeNavigationItems(items) } }
    }

    fun resetSection(key: Preferences.Key<String>) {
        scope.launch { context.dataStore.edit { it.remove(key) } }
    }

    fun resetAll() {
        scope.launch {
            context.dataStore.edit { preferences ->
                navigationPreferenceKeys.forEach { preferences.remove(it) }
            }
        }
    }

    RYScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight
            MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        actions = {
            FeedbackIconButton(
                imageVector = Icons.Rounded.RestartAlt,
                contentDescription = stringResource(R.string.navigation_reset_all),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = { resetAllVisible = true },
            )
        },
        content = {
            LazyColumn(state = listState) {
                item(key = "page-header") {
                    DisplayText(
                        text = stringResource(R.string.navigation_actions_title),
                        desc = stringResource(R.string.navigation_actions_desc),
                    )
                }
                item(key = "page-tabs") {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth().padding(
                            horizontal = LayoutTokens.PageHorizontalPadding,
                            vertical = 8.dp,
                        ),
                    ) {
                        listOf(
                            stringResource(R.string.navigation_home),
                            stringResource(R.string.navigation_reading),
                        ).forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = selectedTab == index,
                                onClick = {
                                    if (selectedTab != index) {
                                        selectedTab = index
                                        scope.launch { listState.scrollToItem(0) }
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }

                if (selectedTab == HOME_TAB) {
                    navigationActionSection(
                        sectionId = "main-bottom",
                        title = homeFiltersTitle,
                        description = homeFiltersDescription,
                        helperText = homeFiltersHint,
                        state = mainBottomState,
                        key = NavigationPreferenceKeys.mainBottomItems,
                        listState = listState,
                        mode = NavigationSectionMode.Fixed,
                        onCommit = ::writeItems,
                        onPlacement = { placementRequest = it },
                        onMove = { moveRequest = it },
                        onReset = ::resetSection,
                    )
                    navigationActionSection(
                        sectionId = "feed-top",
                        title = feedActionsTitle,
                        description = feedActionsDescription,
                        helperText = contextualHint,
                        state = feedTopState,
                        key = NavigationPreferenceKeys.feedTopActions,
                        listState = listState,
                        mode = NavigationSectionMode.Placement,
                        onCommit = ::writeItems,
                        onPlacement = { placementRequest = it },
                        onMove = { moveRequest = it },
                        onReset = ::resetSection,
                    )
                    navigationActionSection(
                        sectionId = "article-top",
                        title = articleActionsTitle,
                        description = articleActionsDescription,
                        helperText = contextualHint,
                        state = articleTopState,
                        key = NavigationPreferenceKeys.articleTopActions,
                        listState = listState,
                        mode = NavigationSectionMode.Placement,
                        onCommit = ::writeItems,
                        onPlacement = { placementRequest = it },
                        onMove = { moveRequest = it },
                        onReset = ::resetSection,
                    )
                } else {
                    navigationActionSection(
                        sectionId = "reading-top",
                        title = readingTopTitle,
                        description = readingTopDescription,
                        helperText = contextualHint,
                        state = readingTopState,
                        key = NavigationPreferenceKeys.readingTopActions,
                        listState = listState,
                        mode = NavigationSectionMode.Placement,
                        onCommit = ::writeItems,
                        onPlacement = { placementRequest = it },
                        onMove = { moveRequest = it },
                        onReset = ::resetSection,
                    )
                    navigationActionSection(
                        sectionId = "reading-bottom",
                        title = readingBottomTitle,
                        description = readingBottomDescription,
                        helperText = readingBottomHint,
                        state = readingBottomState,
                        key = NavigationPreferenceKeys.readingBottomActions,
                        listState = listState,
                        mode = NavigationSectionMode.Visibility,
                        requireVisible = true,
                        maxToolbarItems = NavigationCustomization.MAX_READING_BOTTOM_ACTIONS,
                        onCommit = ::writeItems,
                        onPlacement = { placementRequest = it },
                        onMove = { moveRequest = it },
                        onReset = ::resetSection,
                    )
                }

                item(key = "page-bottom") {
                    Spacer(Modifier.height(LayoutTokens.PageBottomPadding))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )

    NavigationActionSheets(
        placementRequest = placementRequest,
        moveRequest = moveRequest,
        onWrite = ::writeItems,
        onDismissPlacement = { placementRequest = null },
        onDismissMove = { moveRequest = null },
    )

    if (resetAllVisible) {
        AlertDialog(
            onDismissRequest = { resetAllVisible = false },
            title = { Text(stringResource(R.string.navigation_reset_all_title)) },
            text = { Text(stringResource(R.string.navigation_reset_all_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetAllVisible = false
                        resetAll()
                    },
                ) {
                    Text(stringResource(R.string.restore_default))
                }
            },
            dismissButton = {
                TextButton(onClick = { resetAllVisible = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
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
