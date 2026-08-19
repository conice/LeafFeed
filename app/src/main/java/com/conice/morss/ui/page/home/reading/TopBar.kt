package com.conice.morss.ui.page.home.reading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MenuOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.conice.morss.R
import com.conice.morss.infrastructure.preference.LocalReadingPageTonalElevation
import com.conice.morss.infrastructure.preference.LocalSettings
import com.conice.morss.infrastructure.preference.LocalSharedContent
import com.conice.morss.infrastructure.preference.NavigationItemIds
import com.conice.morss.infrastructure.preference.NavigationItemPreference
import com.conice.morss.infrastructure.preference.ReadingPageTonalElevationPreference
import com.conice.morss.infrastructure.preference.resolveNavigationActionLayout
import com.conice.morss.ui.component.base.FeedbackIconButton
import com.conice.morss.ui.component.navigationActionIcon
import com.conice.morss.ui.component.navigationActionLabel
import com.conice.morss.ui.component.navigationTonalElevation
import com.conice.morss.ui.component.responsiveToolbarCapacity
import com.conice.morss.ui.ext.surfaceColorAtElevation
import com.conice.morss.ui.motion.VerticalEdge
import com.conice.morss.ui.motion.slideInFromVerticalEdge
import com.conice.morss.ui.motion.slideOutToVerticalEdge
import com.conice.morss.ui.page.adaptive.NavigationAction

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TopBar(
    isShow: Boolean,
    isScrolled: Boolean = false,
    title: String? = "",
    link: String? = "",
    isAiSummaryAvailable: Boolean = false,
    navigationAction: NavigationAction,
    onClick: (() -> Unit)? = null,
    onNavButtonClick: (NavigationAction) -> Unit = {},
    onAiSummary: () -> Unit = {},
    onAddNote: () -> Unit = {},
    onManageTags: () -> Unit = {},
    onNavigateToStylePage: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val sharedContent = LocalSharedContent.current
    val navigationCustomization = LocalSettings.current.navigationCustomization
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    var menuExpanded by remember { mutableStateOf(false) }
    val isOutlined =
        LocalReadingPageTonalElevation.current == ReadingPageTonalElevationPreference.Outlined

    val containerColor by
        animateColorAsState(
            with(MaterialTheme.colorScheme) {
                if (navigationCustomization.readingTopElevation > 0) {
                    surfaceColorAtElevation(
                        navigationCustomization.readingTopElevation.navigationTonalElevation()
                    )
                } else if (isOutlined || !isScrolled) surface else surfaceContainer
            },
            label = "readingTopBarColor",
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        )

    Box(modifier = Modifier.fillMaxSize().zIndex(1f), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.drawBehind { drawRect(containerColor) }) {
            Spacer(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            )
            AnimatedVisibility(
                visible = isShow,
                enter = slideInFromVerticalEdge(VerticalEdge.Top),
                exit = slideOutToVerticalEdge(VerticalEdge.Top),
            ) {
                TopAppBar(
                    title = {},
                    modifier =
                        if (onClick == null) Modifier
                        else
                            Modifier.clickable(
                                onClick = onClick,
                            ),
                    windowInsets = WindowInsets(0.dp),
                    navigationIcon = {
                        val imageVector =
                            when (navigationAction) {
                                NavigationAction.Close -> Icons.Rounded.Close
                                NavigationAction.HideList -> Icons.AutoMirrored.Rounded.MenuOpen
                                NavigationAction.ExpandList -> Icons.Rounded.Menu
                            }
                        val contentDescription =
                            when (navigationAction) {
                                NavigationAction.Close -> stringResource(R.string.close)
                                NavigationAction.HideList -> "Hide list"
                                NavigationAction.ExpandList -> "Expand list"
                            }
                        FeedbackIconButton(
                            imageVector = imageVector,
                            contentDescription = contentDescription,
                            tint = MaterialTheme.colorScheme.onSurface,
                        ) {
                            onNavButtonClick(navigationAction)
                        }
                    },
                    actions = {
                        val availableIds = remember(link) {
                            buildSet {
                                add(NavigationItemIds.TAGS)
                                add(NavigationItemIds.ADD_NOTE)
                                add(NavigationItemIds.AI_SUMMARY)
                                add(NavigationItemIds.STYLE)
                                add(NavigationItemIds.SHARE)
                                if (!link.isNullOrBlank()) {
                                    add(NavigationItemIds.OPEN_IN_BROWSER)
                                }
                            }
                        }
                        val capacity = responsiveToolbarCapacity(
                            iconSize = navigationCustomization.readingTopIconSize,
                            screenWidthDp = configuration.screenWidthDp,
                            fontScale = fontScale,
                            normalCapacity = 3,
                        )
                        val actionLayout = remember(
                            navigationCustomization.readingTopActions,
                            availableIds,
                            capacity,
                        ) {
                            resolveNavigationActionLayout(
                                navigationCustomization.readingTopActions,
                                availableIds,
                                capacity,
                            )
                        }
                        actionLayout.toolbar.forEach { action ->
                            FeedbackIconButton(
                                modifier = Modifier.size(
                                    navigationCustomization.readingTopIconSize.dp
                                ),
                                imageVector = action.icon(),
                                contentDescription = action.label(),
                                tint = when {
                                    action.id == NavigationItemIds.AI_SUMMARY &&
                                        isAiSummaryAvailable -> MaterialTheme.colorScheme.tertiary
                                    action.id == NavigationItemIds.AI_SUMMARY ->
                                        MaterialTheme.colorScheme.outline
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                enabled = action.id != NavigationItemIds.AI_SUMMARY ||
                                    isAiSummaryAvailable,
                                onClick = {
                                    action.performAction(
                                        onAiSummary = onAiSummary,
                                        onManageTags = onManageTags,
                                        onAddNote = onAddNote,
                                        onStyle = onNavigateToStylePage,
                                        onShare = { sharedContent.share(context, title, link) },
                                        onOpenInBrowser = {
                                            uriHandler.openUri(link.orEmpty())
                                        },
                                    )
                                },
                            )
                        }
                        if (actionLayout.overflow.isNotEmpty()) {
                            FeedbackIconButton(
                                modifier = Modifier.size(
                                    navigationCustomization.readingTopIconSize.dp
                                ),
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.more),
                                tint = MaterialTheme.colorScheme.onSurface,
                            ) { menuExpanded = true }
                        }
                        DropdownMenu(
                            expanded = menuExpanded && actionLayout.overflow.isNotEmpty(),
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            actionLayout.overflow.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label()) },
                                    leadingIcon = { Icon(action.icon(), null) },
                                    enabled = action.id != NavigationItemIds.AI_SUMMARY ||
                                        isAiSummaryAvailable,
                                    onClick = {
                                        menuExpanded = false
                                        action.performAction(
                                            onAiSummary = onAiSummary,
                                            onManageTags = onManageTags,
                                            onAddNote = onAddNote,
                                            onStyle = onNavigateToStylePage,
                                            onShare = {
                                                sharedContent.share(context, title, link)
                                            },
                                            onOpenInBrowser = {
                                                uriHandler.openUri(link.orEmpty())
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            }
            if (isOutlined && isScrolled && navigationCustomization.readingTopElevation == 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    thickness = 0.5f.dp,
                )
            }
        }
    }
}

@Composable
private fun NavigationItemPreference.label(): String = navigationActionLabel(id)

private fun NavigationItemPreference.icon() = navigationActionIcon(id)

private fun NavigationItemPreference.performAction(
    onAiSummary: () -> Unit,
    onManageTags: () -> Unit,
    onAddNote: () -> Unit,
    onStyle: () -> Unit,
    onShare: () -> Unit,
    onOpenInBrowser: () -> Unit,
) = when (id) {
    NavigationItemIds.AI_SUMMARY -> onAiSummary()
    NavigationItemIds.TAGS -> onManageTags()
    NavigationItemIds.ADD_NOTE -> onAddNote()
    NavigationItemIds.STYLE -> onStyle()
    NavigationItemIds.OPEN_IN_BROWSER -> onOpenInBrowser()
    else -> onShare()
}
