package me.ash.reader.ui.page.home.reading

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.ActionPlacement
import me.ash.reader.infrastructure.preference.LocalFeedsFilterBarPadding
import me.ash.reader.infrastructure.preference.LocalReadingPageTonalElevation
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.preference.NavigationItemIds
import me.ash.reader.infrastructure.preference.ReadingPageTonalElevationPreference
import me.ash.reader.ui.component.base.CanBeDisabledIconButton
import me.ash.reader.ui.component.navigationTonalElevation
import me.ash.reader.ui.ext.surfaceColorAtElevation
import me.ash.reader.ui.motion.VerticalEdge
import me.ash.reader.ui.motion.slideInFromVerticalEdge
import me.ash.reader.ui.motion.slideOutToVerticalEdge

@Composable
fun BottomBar(
    isShow: Boolean,
    isUnread: Boolean,
    isStarred: Boolean,
    isFullContent: Boolean,
    isFullContentLoading: Boolean,
    isReadLater: Boolean,
    isPreviousArticleAvailable: Boolean,
    isNextArticleAvailable: Boolean,
    ttsButton: @Composable (iconSize: Dp) -> Unit,
    onUnread: (isUnread: Boolean) -> Unit = {},
    onStarred: (isStarred: Boolean) -> Unit = {},
    onReadLater: (Boolean) -> Unit = {},
    onFullContent: (isFullContent: Boolean) -> Unit = {},
    onPreviousArticle: () -> Unit = {},
    onNextArticle: () -> Unit = {},
) {
    val tonalElevation = LocalReadingPageTonalElevation.current
    val isOutlined = tonalElevation == ReadingPageTonalElevationPreference.Outlined
    val filterBarPadding = LocalFeedsFilterBarPadding.current.dp
    val navigationCustomization = LocalSettings.current.navigationCustomization
    val iconSize = navigationCustomization.readingBottomIconSize.dp
    val visibleActions = remember(navigationCustomization.readingBottomActions) {
        navigationCustomization.readingBottomActions.filter {
            it.placement == ActionPlacement.Toolbar
        }
    }
    val actionSpacing = if (visibleActions.size > 5) 0.dp else 8.dp
    val horizontalPadding = if (visibleActions.size > 5) 0.dp else filterBarPadding
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = isShow,
            enter = slideInFromVerticalEdge(VerticalEdge.Bottom),
            exit = slideOutToVerticalEdge(VerticalEdge.Bottom),
        ) {
            val view = LocalView.current
            Column {
                if (isOutlined && navigationCustomization.readingBottomElevation == 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        thickness = 0.5f.dp
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.run {
                        if (navigationCustomization.readingBottomElevation > 0) {
                            surfaceColorAtElevation(
                                navigationCustomization.readingBottomElevation
                                    .navigationTonalElevation()
                            )
                        } else if (isOutlined) surface else surfaceContainer
                    }
                ) {
                    // TODO: Component styles await refactoring
                    Row(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .fillMaxWidth()
                            .height(navigationCustomization.readingBottomHeight.dp),
                        horizontalArrangement = Arrangement.spacedBy(actionSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = Modifier.width(horizontalPadding))
                        visibleActions.forEach { action ->
                            ActionSlot {
                                when (action.id) {
                                    NavigationItemIds.STARRED -> CanBeDisabledIconButton(
                                        modifier = Modifier.size(48.dp),
                                        size = iconSize,
                                        disabled = false,
                                        imageVector = if (isStarred) Icons.Rounded.Star
                                            else Icons.Rounded.StarOutline,
                                        contentDescription = stringResource(
                                            if (isStarred) R.string.mark_as_unstar
                                            else R.string.mark_as_starred
                                        ),
                                        tint = if (isStarred) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else MaterialTheme.colorScheme.outline,
                                    ) {
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.KEYBOARD_TAP
                                        )
                                        onStarred(!isStarred)
                                    }
                                    NavigationItemIds.UNREAD -> CanBeDisabledIconButton(
                                        modifier = Modifier.size(48.dp),
                                        size = iconSize,
                                        disabled = false,
                                        imageVector = if (isUnread) {
                                            Icons.Filled.FiberManualRecord
                                        } else Icons.Outlined.FiberManualRecord,
                                        contentDescription = stringResource(
                                            if (isUnread) R.string.mark_as_read
                                            else R.string.mark_as_unread
                                        ),
                                        tint = if (isUnread) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else MaterialTheme.colorScheme.outline,
                                    ) {
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.KEYBOARD_TAP
                                        )
                                        onUnread(!isUnread)
                                    }
                                    NavigationItemIds.FULL_CONTENT -> CanBeDisabledIconButton(
                                        modifier = Modifier.size(48.dp),
                                        size = iconSize,
                                        disabled = isFullContentLoading,
                                        imageVector = if (isFullContent) {
                                            Icons.AutoMirrored.Rounded.Article
                                        } else Icons.AutoMirrored.Outlined.Article,
                                        contentDescription = stringResource(
                                            if (isFullContent) R.string.show_rss_content
                                            else R.string.parse_full_content
                                        ),
                                        tint = if (isFullContent) {
                                            MaterialTheme.colorScheme.primary
                                        } else MaterialTheme.colorScheme.outline,
                                    ) {
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.KEYBOARD_TAP
                                        )
                                        onFullContent(!isFullContent)
                                    }
                                    NavigationItemIds.TEXT_TO_SPEECH -> ttsButton(iconSize)
                                    NavigationItemIds.READ_LATER -> CanBeDisabledIconButton(
                                        size = iconSize,
                                        modifier = Modifier.size(48.dp),
                                        disabled = false,
                                        imageVector = if (isReadLater) Icons.Rounded.Bookmark
                                            else Icons.Outlined.BookmarkBorder,
                                        contentDescription = stringResource(
                                            if (isReadLater) R.string.remove_from_read_later
                                            else R.string.add_to_read_later
                                        ),
                                        tint = if (isReadLater) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                    ) {
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.KEYBOARD_TAP
                                        )
                                        onReadLater(!isReadLater)
                                    }
                                    NavigationItemIds.PREVIOUS_ARTICLE ->
                                        CanBeDisabledIconButton(
                                            modifier = Modifier.size(48.dp),
                                            size = iconSize,
                                            disabled = !isPreviousArticleAvailable,
                                            imageVector = Icons.Rounded.SkipPrevious,
                                            contentDescription = "Previous article",
                                            tint = MaterialTheme.colorScheme.outline,
                                            onClick = onPreviousArticle,
                                        )
                                    NavigationItemIds.NEXT_ARTICLE ->
                                        CanBeDisabledIconButton(
                                            modifier = Modifier.size(48.dp),
                                            size = iconSize,
                                            disabled = !isNextArticleAvailable,
                                            imageVector = Icons.Rounded.SkipNext,
                                            contentDescription = stringResource(
                                                R.string.next_article
                                            ),
                                            tint = MaterialTheme.colorScheme.outline,
                                            onClick = onNextArticle,
                                        )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(horizontalPadding))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ActionSlot(content: @Composable () -> Unit) {
    Box(
        Modifier.weight(1f).fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
