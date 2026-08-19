package me.ash.reader.application.data

import androidx.compose.ui.util.fastFilteredMap
import androidx.compose.ui.util.fastMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.domain.model.group.GroupWithFeed
import me.ash.reader.application.service.AccountService
import me.ash.reader.application.service.RssService
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.sync.DiffMapHolder
import me.ash.reader.domain.model.general.getDefaultGroupId
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class GroupWithFeedsListUseCase @Inject constructor(
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val settingsProvider: SettingsProvider,
    private val rssService: RssService,
    private val filterStateUseCase: FilterStateUseCase,
    private val diffMapHolder: DiffMapHolder,
    private val accountService: AccountService,
) {

    private var currentJob: Job? = null
    private val accountFlow = accountService.currentAccountFlow.mapNotNull { it }

    init {
        applicationScope.launch {
            accountFlow.collectLatest {
                rssService.get(it.type.id).pullFeeds().collect { feeds -> feedsFlow.value = feeds }
            }
        }
        applicationScope.launch {
            combine(
                filterStateUseCase.filterStateFlow,
                accountFlow,
                filterStateUseCase.contentTypeRevision,
            ) { filterState, _, _ ->
                filterState
            }.collectLatest {
                currentJob?.cancel()
                currentJob = when (it.filter) {
                    Filter.Unread -> pullUnreadFeeds()
                    Filter.Starred -> pullStarredFeeds()
                    Filter.ReadLater -> pullReadLaterFeeds()
                    else -> pullAllFeeds()
                }
            }
        }
    }

    private val _groupWithFeedsListFlow: MutableStateFlow<List<GroupWithFeed>> =
        MutableStateFlow<List<GroupWithFeed>>(emptyList())
    val groupWithFeedListFlow: StateFlow<List<GroupWithFeed>> = _groupWithFeedsListFlow

    private val feedsFlow: MutableStateFlow<List<GroupWithFeed>> = MutableStateFlow(emptyList())

    private val defaultGroupId get() = accountService.getCurrentAccountId().getDefaultGroupId()

    private val hideEmptyGroups get() = settingsProvider.settings.hideEmptyGroups.value

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun pullAllFeeds(): Job {
        val articleCountMapFlow =
            rssService.get().pullImportant(
                isStarred = false,
                isUnread = false,
                contentType = ArticleContentType.ARTICLE,
            )
        val audioCountMapFlow =
            rssService.get().pullImportant(false, false, ArticleContentType.AUDIO)

        return applicationScope.launch {
            combine(
                feedsFlow,
                articleCountMapFlow,
                audioCountMapFlow,
            ) { groupWithFeedsList, articleCountMap, audioCountMap ->
                groupWithFeedsList.fastFilteredMap(predicate = {
                    it.group.id != defaultGroupId || it.feeds.isNotEmpty()
                }, transform = {
                    val feedList = it.feeds.map { feed ->
                        feed.copy(important = countFor(feed, articleCountMap, audioCountMap))
                    }
                    it.copy(feeds = feedList.toMutableList())
                })
            }.flowOn(ioDispatcher).collect { _groupWithFeedsListFlow.value = it }

        }
    }

    private fun pullStarredFeeds(): Job {
        val starredCountMap = rssService.get().pullImportant(
            isStarred = true,
            isUnread = false,
            contentType = ArticleContentType.ARTICLE,
        )
        val audioCountMap = rssService.get().pullImportant(true, false, ArticleContentType.AUDIO)

        return applicationScope.launch {
            combine(
                feedsFlow,
                starredCountMap,
                audioCountMap,
            ) { groupWithFeedsList, starredCountMap, audioCounts ->
                val result = mutableListOf<GroupWithFeed>()
                for (groupItem in groupWithFeedsList) {

                    val feedList = groupItem.feeds.fastMap { feed ->
                        val feedCount = countFor(feed, starredCountMap, audioCounts)
                        feed.copy(important = feedCount)
                    }

                    val groupItem = if (hideEmptyGroups) {
                        val filteredFeeds = feedList.filterNot { it.important == 0 }
                        if (filteredFeeds.isEmpty()) {
                            continue
                        } else {
                            groupItem.copy(feeds = filteredFeeds.toMutableList())
                        }
                    } else {
                        groupItem.copy(feeds = feedList.toMutableList())
                    }

                    if (groupItem.group.id != defaultGroupId || groupItem.feeds.isNotEmpty()) {
                        result.add(groupItem)
                    }
                }
                result
            }.flowOn(ioDispatcher).collect {
                _groupWithFeedsListFlow.value = it
            }
        }
    }

    private fun pullReadLaterFeeds(): Job {
        val countMapFlow = rssService.get().pullReadLater()
        return applicationScope.launch {
            combine(
                feedsFlow,
                countMapFlow,
            ) { groups, countMap ->
                groups.mapNotNull { group ->
                    val feeds = group.feeds
                        .map { feed ->
                            feed.copy(important = countMap[feed.id] ?: 0)
                        }
                        .let { feeds ->
                            if (hideEmptyGroups) feeds.filter { it.important > 0 } else feeds
                        }
                    if (group.group.id == defaultGroupId && feeds.isEmpty()) null
                    else if (hideEmptyGroups && feeds.isEmpty()) null
                    else group.copy(feeds = feeds.toMutableList())
                }
            }.flowOn(ioDispatcher).collect { _groupWithFeedsListFlow.value = it }
        }
    }

    @OptIn(FlowPreview::class)
    private fun pullUnreadFeeds(): Job {
        val unreadCountMapFlow = rssService.get().pullImportant(
            isStarred = false,
            isUnread = true,
            contentType = ArticleContentType.ARTICLE,
        )
        val audioCountMapFlow =
            rssService.get().pullImportant(false, true, ArticleContentType.AUDIO)
        return applicationScope.launch {
            combine(
                feedsFlow,
                unreadCountMapFlow,
                audioCountMapFlow,
                diffMapHolder.diffMapSnapshotFlow,
            ) { groupWithFeedsList, unreadCountMap, audioCounts, diffMap ->
                val result = mutableListOf<GroupWithFeed>()
                val diffCountByFeed = HashMap<String, Int>()
                diffMap.values.forEach { diff ->
                    diffCountByFeed[diff.feedId] =
                        (diffCountByFeed[diff.feedId] ?: 0) + if (diff.isUnread) 1 else -1
                }

                for (groupItem in groupWithFeedsList) {

                    val feedList = groupItem.feeds.map { feed ->
                        val feedId = feed.id
                        val feedCount = countFor(feed, unreadCountMap, audioCounts)
                        val combinedFeedCount =
                            feedCount + (diffCountByFeed[feedId] ?: 0)
                        feed.copy(important = combinedFeedCount.coerceAtLeast(0))
                    }

                    val groupItem = if (hideEmptyGroups) {
                        val filteredFeeds = feedList.filterNot { it.important == 0 }
                        if (filteredFeeds.isEmpty()) {
                            continue
                        } else {
                            groupItem.copy(feeds = filteredFeeds.toMutableList())
                        }
                    } else {
                        groupItem.copy(feeds = feedList.toMutableList())
                    }

                    if (groupItem.group.id != defaultGroupId || groupItem.feeds.isNotEmpty()) {
                        result.add(groupItem)
                    }

                }
                result
            }.debounce(200L).flowOn(ioDispatcher).collect { _groupWithFeedsListFlow.value = it }
        }
    }

    private fun countFor(
        feed: Feed,
        articleCounts: Map<String, Int>,
        audioCounts: Map<String, Int>,
    ): Int =
        if (
            filterStateUseCase.contentTypeForScope(FilterState(feed = feed)) ==
                ArticleContentType.AUDIO
        ) {
            audioCounts[feed.id] ?: 0
        } else {
            articleCounts[feed.id] ?: 0
        }

}
