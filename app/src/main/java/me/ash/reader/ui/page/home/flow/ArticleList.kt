@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package me.ash.reader.ui.page.home.flow

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import me.ash.reader.domain.data.Diff
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed

@Suppress("FunctionName")
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.ArticleList(
    pagingItems: LazyPagingItems<ArticleFlowItem>,
    diffMap: Map<String, Diff>,
    isShowFeedIcon: Boolean,
    isShowStickyHeader: Boolean,
    articleListTonalElevation: Int,
    isMenuEnabled: Boolean = true,
    onClick: (ArticleWithFeed, Int) -> Unit = { _, _ -> },
    onToggleStarred: (ArticleWithFeed) -> Unit = {},
    onToggleReadLater: (ArticleWithFeed) -> Unit = {},
    onToggleRead: (ArticleWithFeed) -> Unit = {},
    onMarkAboveAsRead: ((ArticleWithFeed, Int) -> Unit)? = null,
    onMarkBelowAsRead: ((ArticleWithFeed, Int) -> Unit)? = null,
    onShare: ((ArticleWithFeed) -> Unit)? = null,
    playingPodcastId: String? = null,
    isPodcastPlaying: Boolean = false,
    podcastPositionMs: Long = 0L,
    queuedPodcastIds: Set<String> = emptySet(),
    observePodcastDownload: (String) -> Flow<List<WorkInfo>> = { flowOf(emptyList()) },
    onPodcastPlay: (ArticleWithFeed) -> Unit = {},
    onPodcastEnqueue: (ArticleWithFeed) -> Unit = {},
    onPodcastDownload: (ArticleWithFeed) -> Unit = {},
) {
    // https://issuetracker.google.com/issues/193785330
    // FIXME: Using sticky header with paging-compose need to iterate through the entire list
    //  to figure out where to add sticky headers, which significantly impacts the performance
    if (!isShowStickyHeader) {
        items(
            count = pagingItems.itemCount,
            key = pagingItems.itemKey(::key),
            contentType = pagingItems.itemContentType(::contentType),
        ) { index ->
            when (val item = pagingItems[index]) {
                is ArticleFlowItem.Article -> {
                    ArticleListItem(
                        modifier = Modifier.animateItem(
                            fadeInSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        ),
                        item = item,
                        index = index,
                        itemCount = pagingItems.itemCount,
                        diffMap = diffMap,
                        articleListTonalElevation = articleListTonalElevation,
                        isMenuEnabled = isMenuEnabled,
                        onClick = onClick,
                        onToggleStarred = onToggleStarred,
                        onToggleReadLater = onToggleReadLater,
                        onToggleRead = onToggleRead,
                        onMarkAboveAsRead = onMarkAboveAsRead,
                        onMarkBelowAsRead = onMarkBelowAsRead,
                        onShare = onShare,
                        playingPodcastId = playingPodcastId,
                        isPodcastPlaying = isPodcastPlaying,
                        podcastPositionMs = podcastPositionMs,
                        queuedPodcastIds = queuedPodcastIds,
                        observePodcastDownload = observePodcastDownload,
                        onPodcastPlay = onPodcastPlay,
                        onPodcastEnqueue = onPodcastEnqueue,
                        onPodcastDownload = onPodcastDownload,
                    )
                }

                is ArticleFlowItem.Date -> {
                    if (item.showSpacer) {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                    StickyHeader(item.date, isShowFeedIcon, articleListTonalElevation)
                }

                else -> {}
            }
        }
    } else {
        for (index in 0 until pagingItems.itemCount) {
            when (val item = pagingItems.peek(index)) {
                is ArticleFlowItem.Article -> {
                    item(key = key(item), contentType = contentType(item)) {
                        ArticleListItem(
                            modifier = Modifier.animateItem(
                                fadeInSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                                placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                            ),
                            item = item,
                            index = index,
                            itemCount = pagingItems.itemCount,
                            diffMap = diffMap,
                            articleListTonalElevation = articleListTonalElevation,
                            isMenuEnabled = isMenuEnabled,
                            onClick = onClick,
                            onToggleStarred = onToggleStarred,
                            onToggleReadLater = onToggleReadLater,
                            onToggleRead = onToggleRead,
                            onMarkAboveAsRead = onMarkAboveAsRead,
                            onMarkBelowAsRead = onMarkBelowAsRead,
                            onShare = onShare,
                            playingPodcastId = playingPodcastId,
                            isPodcastPlaying = isPodcastPlaying,
                            podcastPositionMs = podcastPositionMs,
                            queuedPodcastIds = queuedPodcastIds,
                            observePodcastDownload = observePodcastDownload,
                            onPodcastPlay = onPodcastPlay,
                            onPodcastEnqueue = onPodcastEnqueue,
                            onPodcastDownload = onPodcastDownload,
                        )
                    }
                }

                is ArticleFlowItem.Date -> {
                    if (item.showSpacer) {
                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                    stickyHeader(key = key(item), contentType = contentType(item)) {
                        StickyHeader(item.date, isShowFeedIcon, articleListTonalElevation)
                    }
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun ArticleListItem(
    modifier: Modifier,
    item: ArticleFlowItem.Article,
    index: Int,
    itemCount: Int,
    diffMap: Map<String, Diff>,
    articleListTonalElevation: Int,
    isMenuEnabled: Boolean,
    onClick: (ArticleWithFeed, Int) -> Unit,
    onToggleStarred: (ArticleWithFeed) -> Unit,
    onToggleReadLater: (ArticleWithFeed) -> Unit,
    onToggleRead: (ArticleWithFeed) -> Unit,
    onMarkAboveAsRead: ((ArticleWithFeed, Int) -> Unit)?,
    onMarkBelowAsRead: ((ArticleWithFeed, Int) -> Unit)?,
    onShare: ((ArticleWithFeed) -> Unit)?,
    playingPodcastId: String?,
    isPodcastPlaying: Boolean,
    podcastPositionMs: Long,
    queuedPodcastIds: Set<String>,
    observePodcastDownload: (String) -> Flow<List<WorkInfo>>,
    onPodcastPlay: (ArticleWithFeed) -> Unit,
    onPodcastEnqueue: (ArticleWithFeed) -> Unit,
    onPodcastDownload: (ArticleWithFeed) -> Unit,
) {
    val article = item.articleWithFeed.article
    val downloadWork = (if (article.audioUrl != null) {
        observePodcastDownload(article.id)
    } else {
        flowOf(emptyList())
    }).collectAsStateWithLifecycle(emptyList())
    val isPodcastDownloading = downloadWork.value.any {
        it.state == WorkInfo.State.ENQUEUED ||
            it.state == WorkInfo.State.BLOCKED ||
            it.state == WorkInfo.State.RUNNING
    }
    SwipeableArticleItem(
        modifier = modifier,
        articleWithFeed = item.articleWithFeed,
        isUnread = diffMap[article.id]?.isUnread ?: article.isUnread,
        articleListTonalElevation = articleListTonalElevation,
        onClick = { onClick(it, index) },
        isMenuEnabled = isMenuEnabled,
        onToggleStarred = onToggleStarred,
        onToggleReadLater = onToggleReadLater,
        onToggleRead = onToggleRead,
        onMarkAboveAsRead =
            if (index == 1) null
            else onMarkAboveAsRead?.let { callback ->
                { selectedArticle -> callback(selectedArticle, index) }
            }, // index == 0 -> ArticleFlowItem.Date
        onMarkBelowAsRead =
            if (index == itemCount - 1) null
            else onMarkBelowAsRead?.let { callback ->
                { selectedArticle -> callback(selectedArticle, index) }
            },
        onShare = onShare,
        podcastPositionMs = if (playingPodcastId == article.id) podcastPositionMs else article.playbackPositionMs,
        isPodcastPlaying = playingPodcastId == article.id && isPodcastPlaying,
        isPodcastQueued = article.id in queuedPodcastIds,
        isPodcastDownloading = isPodcastDownloading,
        onPodcastPlay = { onPodcastPlay(item.articleWithFeed) },
        onPodcastEnqueue = { onPodcastEnqueue(item.articleWithFeed) },
        onPodcastDownload = { onPodcastDownload(item.articleWithFeed) },
        highlightRanges = item.highlightRanges,
    )
}

private fun key(item: ArticleFlowItem): String {
    return when (item) {
        is ArticleFlowItem.Article -> item.articleWithFeed.article.id
        is ArticleFlowItem.Date -> item.date
    }
}

private fun contentType(item: ArticleFlowItem): Int {
    return when (item) {
        is ArticleFlowItem.Article -> CONTENT_TYPE_ARTICLE
        is ArticleFlowItem.Date -> CONTENT_TYPE_DATE_HEADER
    }
}

const val CONTENT_TYPE_ARTICLE = 1
const val CONTENT_TYPE_DATE_HEADER = 2
