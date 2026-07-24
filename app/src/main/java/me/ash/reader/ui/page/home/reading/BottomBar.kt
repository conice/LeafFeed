package me.ash.reader.ui.page.home.reading

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.LocalFeedsFilterBarPadding
import me.ash.reader.infrastructure.preference.LocalReadingPageTonalElevation
import me.ash.reader.infrastructure.preference.ReadingPageTonalElevationPreference
import me.ash.reader.ui.component.base.CanBeDisabledIconButton
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
    showReadLaterAction: Boolean = true,
    ttsButton: @Composable () -> Unit,
    onUnread: (isUnread: Boolean) -> Unit = {},
    onStarred: (isStarred: Boolean) -> Unit = {},
    onReadLater: (Boolean) -> Unit = {},
    onFullContent: (isFullContent: Boolean) -> Unit = {},
) {
    val tonalElevation = LocalReadingPageTonalElevation.current
    val isOutlined = tonalElevation == ReadingPageTonalElevationPreference.Outlined
    val filterBarPadding = LocalFeedsFilterBarPadding.current.dp
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
                if (isOutlined) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        thickness = 0.5f.dp
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.run { if (isOutlined) surface else surfaceContainer }
                ) {
                    // TODO: Component styles await refactoring
                    Row(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .fillMaxWidth()
                            .height(60.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = Modifier.width(filterBarPadding))
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            CanBeDisabledIconButton(
                                modifier = Modifier.size(40.dp),
                                disabled = false,
                                imageVector = if (isStarred) {
                                    Icons.Rounded.Star
                                } else {
                                    Icons.Rounded.StarOutline
                                },
                                contentDescription = stringResource(if (isStarred) R.string.mark_as_unstar else R.string.mark_as_starred),
                                tint = if (isStarred) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            ) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onStarred(!isStarred)
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            CanBeDisabledIconButton(
                                modifier = Modifier.size(40.dp),
                                disabled = false,
                                imageVector = if (isUnread) {
                                    Icons.Filled.FiberManualRecord
                                } else {
                                    Icons.Outlined.FiberManualRecord
                                },
                                contentDescription = stringResource(if (isUnread) R.string.mark_as_read else R.string.mark_as_unread),
                                tint = if (isUnread) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            ) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onUnread(!isUnread)
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            CanBeDisabledIconButton(
                                disabled = isFullContentLoading,
                                modifier = Modifier.size(40.dp),
                                imageVector = if (isFullContent) {
                                    Icons.AutoMirrored.Rounded.Article
                                } else {
                                    Icons.AutoMirrored.Outlined.Article
                                },
                                contentDescription = stringResource(
                                    if (isFullContent) R.string.show_rss_content
                                    else R.string.parse_full_content,
                                ),
                                tint = if (isFullContent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            ) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onFullContent(!isFullContent)
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            ttsButton()
                        }
                        if (showReadLaterAction) {
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                CanBeDisabledIconButton(
                                    modifier = Modifier.size(40.dp),
                                    disabled = false,
                                    imageVector = if (isReadLater) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = stringResource(
                                        if (isReadLater) R.string.remove_from_read_later else R.string.add_to_read_later,
                                    ),
                                    tint = if (isReadLater) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                ) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    onReadLater(!isReadLater)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(filterBarPadding))
                    }
                }
            }
        }
    }
}
