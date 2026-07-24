package me.ash.reader.ui.page.home.feeds

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.ui.component.base.AnimatedIcon
import me.ash.reader.ui.component.base.ExpressiveIconButton
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.model.group.GroupWithFeed
import me.ash.reader.ui.page.home.feeds.drawer.group.GroupOptionViewModel
import me.ash.reader.ui.theme.ShapeBottom32
import me.ash.reader.ui.theme.ShapeTop32

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GroupItem(
    group: Group,
    isExpanded: () -> Boolean,
    articleCount: Int? = null,
    groupOptionViewModel: GroupOptionViewModel = hiltViewModel(),
    onExpanded: () -> Unit = {},
    onLongClick: () -> Unit = {},
    groupOnClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    groupOnClick()
                },
                onLongClick = {
                    groupOptionViewModel.fetchGroup(group)
                    onLongClick()
                }
            )
            .padding(top = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 28.dp),
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                articleCount?.let {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.outline,
                    ) {
                        Text(text = it.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
                ExpressiveIconButton(
                    onClick = onExpanded,
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                ) {
                    AnimatedIcon(
                        imageVector = if (isExpanded()) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(if (isExpanded()) R.string.expand_less else R.string.expand_more),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
inline fun GroupWithFeedsContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .padding(top = 16.dp)
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        content = { content() }
    )
}

@Composable
inline fun StickyGroupHeaderContainer(
    standalone: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(top = 16.dp)
                .padding(horizontal = 16.dp)
                .clip(if (standalone) MaterialTheme.shapes.large else ShapeTop32)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        content = { content() },
    )
}

@Composable
inline fun StickyGroupFeedContainer(
    isLast: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .padding(horizontal = 16.dp)
                .clip(if (isLast) ShapeBottom32 else RectangleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
