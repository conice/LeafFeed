package me.ash.reader.ui.page.home.reading

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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Rule
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalReadingPageTonalElevation
import me.ash.reader.infrastructure.preference.LocalSharedContent
import me.ash.reader.infrastructure.preference.ReadingPageTonalElevationPreference
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.motion.VerticalEdge
import me.ash.reader.ui.motion.slideInFromVerticalEdge
import me.ash.reader.ui.motion.slideOutToVerticalEdge
import me.ash.reader.ui.page.adaptive.NavigationAction

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TopBar(
    isShow: Boolean,
    isScrolled: Boolean = false,
    title: String? = "",
    link: String? = "",
    isAiSummaryAvailable: Boolean = false,
    showNotesAction: Boolean = true,
    showTagsAction: Boolean = true,
    navigationAction: NavigationAction,
    onClick: (() -> Unit)? = null,
    onNavButtonClick: (NavigationAction) -> Unit = {},
    onAiSummary: () -> Unit = {},
    onAddNote: () -> Unit = {},
    onManageTags: () -> Unit = {},
    onExplainRules: () -> Unit = {},
    onNavigateToStylePage: () -> Unit,
) {
    val context = LocalContext.current
    val sharedContent = LocalSharedContent.current
    var menuExpanded by remember { mutableStateOf(false) }
    val isOutlined =
        LocalReadingPageTonalElevation.current == ReadingPageTonalElevationPreference.Outlined

    val containerColor by
        animateColorAsState(
            with(MaterialTheme.colorScheme) {
                if (isOutlined || !isScrolled) surface else surfaceContainer
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
                        FeedbackIconButton(
                            modifier = Modifier.size(22.dp),
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = stringResource(R.string.ai_summary_article),
                            tint =
                                if (isAiSummaryAvailable) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.outline,
                            enabled = isAiSummaryAvailable,
                        ) {
                            onAiSummary()
                        }
                        if (showTagsAction) {
                            FeedbackIconButton(
                                modifier = Modifier.size(22.dp),
                                imageVector = Icons.Outlined.Label,
                                contentDescription = stringResource(R.string.manage_tags),
                                tint = MaterialTheme.colorScheme.onSurface,
                            ) { onManageTags() }
                        }
                        FeedbackIconButton(
                            modifier = Modifier.size(22.dp),
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.more),
                            tint = MaterialTheme.colorScheme.onSurface,
                        ) { menuExpanded = true }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            if (showNotesAction) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.add_note)) },
                                    leadingIcon = { Icon(Icons.Outlined.NoteAdd, null) },
                                    onClick = { menuExpanded = false; onAddNote() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Explain article rules") },
                                leadingIcon = { Icon(Icons.Outlined.Rule, null) },
                                onClick = { menuExpanded = false; onExplainRules() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.style)) },
                                leadingIcon = { Icon(Icons.Outlined.Palette, null) },
                                onClick = { menuExpanded = false; onNavigateToStylePage() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share)) },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = {
                                    menuExpanded = false
                                    sharedContent.share(context, title, link)
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            }
            if (isOutlined && isScrolled) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    thickness = 0.5f.dp,
                )
            }
        }
    }
}
