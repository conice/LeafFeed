package me.ash.reader.ui.page.adaptive

import android.net.Uri
import androidx.compose.ui.util.fastFirstOrNull
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlin.collections.any
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.domain.data.ArticlePagingListUseCase
import me.ash.reader.domain.model.general.OperationFailure
import me.ash.reader.domain.model.general.OperationFailureKind
import me.ash.reader.domain.model.general.toOperationFailure
import me.ash.reader.domain.data.ArticleCollectionRepository
import me.ash.reader.domain.data.ArticleRuleRepository
import me.ash.reader.domain.data.DiffMapHolder
import me.ash.reader.domain.data.FilterState
import me.ash.reader.domain.data.FilterStateUseCase
import me.ash.reader.domain.data.GroupWithFeedsListUseCase
import me.ash.reader.domain.data.HighlightedArticleReadUseCase
import me.ash.reader.domain.data.PagerData
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.article.ArticleNote
import me.ash.reader.domain.model.article.ArticleTagLabel
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.article.SavedSearch
import me.ash.reader.domain.model.article.ArticleRule
import me.ash.reader.domain.model.article.ArticleRuleDiagnostic
import me.ash.reader.domain.model.article.diagnose
import me.ash.reader.domain.model.article.RuleScope
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.general.MarkAsReadConditions
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.GoogleReaderRssService
import me.ash.reader.domain.service.LocalRssService
import me.ash.reader.domain.service.RssService
import me.ash.reader.domain.service.SyncWorker
import me.ash.reader.domain.service.AiSummaryService
import me.ash.reader.infrastructure.android.AndroidImageDownloader
import me.ash.reader.infrastructure.android.TextToSpeechManager
import me.ash.reader.infrastructure.audio.PodcastPlayer
import me.ash.reader.infrastructure.audio.PodcastDownloadRepository
import me.ash.reader.infrastructure.audio.PodcastTranscriptRepository
import me.ash.reader.infrastructure.audio.PodcastTranscriptCue
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.PullToLoadNextFeedPreference
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.rss.ReaderCacheHelper
import me.ash.reader.ui.component.AiSummaryFailure
import me.ash.reader.ui.page.home.SyncOperationState
import timber.log.Timber
import org.jsoup.Jsoup

private const val TAG = "FlowViewModel"
private const val ARTICLE_SUMMARY_MAX_CHARS = 30_000
private const val SEARCH_DEBOUNCE_MILLIS = 300L

@OptIn(FlowPreview::class)
@HiltViewModel()
class ArticleListReaderViewModel
@Inject
constructor(
    private val rssService: RssService,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope,
    val diffMapHolder: DiffMapHolder,
    private val filterStateUseCase: FilterStateUseCase,
    private val groupWithFeedsListUseCase: GroupWithFeedsListUseCase,
    private val settingsProvider: SettingsProvider,
    private val readerCacheHelper: ReaderCacheHelper,
    val textToSpeechManager: TextToSpeechManager,
    val podcastPlayer: PodcastPlayer,
    private val podcastDownloadRepository: PodcastDownloadRepository,
    private val podcastTranscriptRepository: PodcastTranscriptRepository,
    private val imageDownloader: AndroidImageDownloader,
    private val articleListUseCase: ArticlePagingListUseCase,
    private val highlightedArticleReadUseCase: HighlightedArticleReadUseCase,
    private val articleDao: ArticleDao,
    private val accountService: AccountService,
    private val aiSummaryService: AiSummaryService,
    private val workManager: WorkManager,
    private val articleCollectionRepository: ArticleCollectionRepository,
    private val articleRuleRepository: ArticleRuleRepository,
) : ViewModel() {

    fun diagnoseCurrentArticle(onComplete: (List<ArticleRuleDiagnostic>) -> Unit) {
        val item = readingUiState.value.articleWithFeed ?: return onComplete(emptyList())
        viewModelScope.launch {
            val diagnostics = articleRuleRepository.currentRules(item.article.accountId).diagnose(
                accountId = item.article.accountId,
                groupId = item.feed.groupId,
                feedId = item.feed.id,
                title = item.article.title,
                description = item.article.rawDescription,
            )
            onComplete(diagnostics)
        }
    }

    fun downloadPodcast(article: Article, onComplete: (Result<Unit>) -> Unit = {}) {
        val wifiOnly = settingsProvider.get<Boolean>(FeaturePreferenceKeys.podcastWifiOnly) ?: true
        onComplete(podcastDownloadRepository.enqueue(article, wifiOnly))
    }

    fun removePodcastDownload(article: Article, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch { onComplete(podcastDownloadRepository.remove(article)) }
    }

    fun setPodcastPlayed(article: Article, played: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { articleDao.updatePlayedStatus(article.id, played) }
    }

    fun observePodcastDownload(articleId: String) = podcastDownloadRepository.observe(articleId)

    fun cancelPodcastDownload(articleId: String) = podcastDownloadRepository.cancel(articleId)

    suspend fun loadPodcastTranscript(url: String): Result<List<PodcastTranscriptCue>> =
        podcastTranscriptRepository.load(url)

    val flowUiState: StateFlow<FlowUiState?> =
        combine(
            articleListUseCase.pagerFlow,
            groupWithFeedsListUseCase.groupWithFeedListFlow,
            articleListUseCase.highlightRules,
        ) { pagerData, groupWithFeedsList, highlightRules ->
                val filterState = pagerData.filterState
                var nextFilterState: FilterState? = null
                if (filterState.group != null) {
                    val groupList = groupWithFeedsList.map { it.group }
                    val visibleGroupIds = groupList.mapTo(mutableSetOf()) { it.id }
                    val index = groupList.indexOfFirst { it.id == filterState.group.id }
                    if (index != -1) {
                        val nextGroup = groupList.getOrNull(index + 1)
                        if (nextGroup != null) {
                            nextFilterState = filterState.copy(group = nextGroup)
                        }
                    } else {
                        val allGroupList =
                            rssService.get().queryAllGroupWithFeeds().map { it.group }
                        val index = allGroupList.indexOfFirst { it.id == filterState.group.id }
                        if (index != -1) {
                            val nextGroup =
                                allGroupList.subList(index, allGroupList.size).fastFirstOrNull {
                                    it.id in visibleGroupIds
                                }
                            if (nextGroup != null) {
                                nextFilterState = filterState.copy(group = nextGroup)
                            }
                        }
                    }
                } else if (filterState.feed != null) {
                    val feedList = groupWithFeedsList.flatMap { it.feeds }
                    val visibleFeedIds = feedList.mapTo(mutableSetOf()) { it.id }
                    val index = feedList.indexOfFirst { it.id == filterState.feed.id }
                    if (index != -1) {
                        val nextFeed = feedList.getOrNull(index + 1)
                        if (nextFeed != null) {
                            nextFilterState = filterState.copy(feed = nextFeed)
                        }
                    } else {
                        val allFeedList =
                            rssService.get().queryAllGroupWithFeeds().flatMap { it.feeds }
                        val index = allFeedList.indexOfFirst { it.id == filterState.feed.id }
                        if (index != -1) {
                            val nextFeed =
                                allFeedList.subList(index, allFeedList.size).fastFirstOrNull {
                                    it.id in visibleFeedIds
                                }
                            if (nextFeed != null) {
                                nextFilterState = filterState.copy(feed = nextFeed)
                            }
                        }
                    }
                }
                val feedIds = filterState.group?.id?.let { groupId ->
                    groupWithFeedsList.firstOrNull { it.group.id == groupId }
                        ?.feeds?.mapTo(mutableSetOf()) { it.id }
                }.orEmpty()
                val availableHighlightRules = highlightRules.filter { rule ->
                    when {
                        filterState.feed != null ->
                            rule.scope == RuleScope.GLOBAL ||
                                rule.scope == RuleScope.FEED && rule.scopeId == filterState.feed.id ||
                                rule.scope == RuleScope.GROUP && rule.scopeId == filterState.feed.groupId
                        filterState.group != null ->
                            rule.scope == RuleScope.GLOBAL ||
                                rule.scope == RuleScope.GROUP && rule.scopeId == filterState.group.id ||
                                rule.scope == RuleScope.FEED && rule.scopeId in feedIds
                        else -> true
                    }
                }
                FlowUiState(
                    nextFilterState = nextFilterState,
                    pagerData = pagerData,
                    highlightRules = availableHighlightRules,
                )
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val syncWorkerStatusFlow =
        workManager
            .getWorkInfosByTagFlow(SyncWorker.SYNC_TAG)
            .map { it.any { workInfo -> workInfo.state == WorkInfo.State.RUNNING } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isSyncingFlow = MutableStateFlow(false)
    val isSyncingFlow = _isSyncingFlow.asStateFlow()

    private val _syncOperationState =
        MutableStateFlow<SyncOperationState>(SyncOperationState.Idle)
    val syncOperationState: StateFlow<SyncOperationState> = _syncOperationState.asStateFlow()

    private val _searchContentInput =
        MutableStateFlow(filterStateUseCase.filterStateFlow.value.searchContent)
    val searchContentInput = _searchContentInput.asStateFlow()
    val savedSearches =
        articleCollectionRepository.observeSavedSearches()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            syncWorkerStatusFlow.debounce(500L).collect { _isSyncingFlow.value = it }
        }
        viewModelScope.launch {
            _searchContentInput
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .collectLatest { input ->
                    val searchContent = input?.trim()?.takeIf { it.isNotEmpty() }
                    if (searchContent != filterStateUseCase.filterStateFlow.value.searchContent) {
                        filterStateUseCase.updateFilterState(searchContent = searchContent)
                    }
                }
        }
    }

    fun updateReadStatus(
        filterState: FilterState,
        articleId: String?,
        conditions: MarkAsReadConditions,
        isUnread: Boolean,
    ) {
        applicationScope.launch(ioDispatcher) {
            if (filterState.filter.isHighlighted() && articleId == null && !isUnread) {
                highlightedArticleReadUseCase(filterState, conditions.toDate())
                return@launch
            }
            rssService
                .get()
                .markAsRead(
                    groupId = filterState.group?.id,
                    feedId = filterState.feed?.id,
                    articleId = articleId,
                    before = conditions.toDate(),
                    isUnread = isUnread,
                )
        }
    }

    fun updateStarredStatus(articleId: String?, isStarred: Boolean) {
        applicationScope.launch(ioDispatcher) {
            if (articleId != null) {
                rssService.get().markAsStarred(articleId = articleId, isStarred = isStarred)
            }
        }
    }

    fun updateReadLaterStatus(articleId: String?, isReadLater: Boolean) {
        applicationScope.launch(ioDispatcher) {
            if (articleId != null) {
                rssService.get().markAsReadLater(articleId, isReadLater)
            }
        }
    }

    fun markAsReadFromList(articleId: String, itemIndex: Int, markAbove: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val snapshot = articleListUseCase.itemSnapshotList
            val snapshotArticleId =
                (snapshot.getOrNull(itemIndex) as? ArticleFlowItem.Article)
                    ?.articleWithFeed
                    ?.article
                    ?.id
            val resolvedIndex =
                if (snapshotArticleId == articleId) {
                    itemIndex
                } else {
                    snapshot.indexOfFirst { item ->
                        item is ArticleFlowItem.Article &&
                            item.articleWithFeed.article.id == articleId
                    }
                }
            if (resolvedIndex == -1) return@launch

            val relativeItems =
                if (markAbove) {
                    snapshot.subList(0, resolvedIndex)
                } else {
                    snapshot.subList(resolvedIndex + 1, snapshot.size)
                }
            val items =
                relativeItems
                    .asSequence()
                    .filterIsInstance<ArticleFlowItem.Article>()
                    .map { it.articleWithFeed }
                    .filter(diffMapHolder::checkIfUnread)
                    .distinctBy { it.article.id }
                    .toList()

            if (items.isEmpty()) return@launch
            diffMapHolder.updateDiff(articleWithFeed = items.toTypedArray(), isUnread = false)
        }
    }

    fun loadNextFeedOrGroup() {
        viewModelScope.launch {
            if (
                settingsProvider.settings.pullToSwitchFeed ==
                    PullToLoadNextFeedPreference.MarkAsReadAndLoadNextFeed
            ) {
                markAllAsRead()
            }
            flowUiState.value?.nextFilterState?.let { filterStateUseCase.updateFilterState(it) }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            val items =
                articleListUseCase.itemSnapshotList.items
                    .filterIsInstance<ArticleFlowItem.Article>()
                    .map { it.articleWithFeed }

            diffMapHolder.updateDiff(articleWithFeed = items.toTypedArray(), isUnread = false)
        }
    }

    fun sync() {
        if (_syncOperationState.value is SyncOperationState.Running) return
        diffMapHolder.commitDiffsToDb()
        _isSyncingFlow.value = true
        _syncOperationState.value = SyncOperationState.Running()
        viewModelScope.launch {
            val workId =
                runCatching {
                    withContext(ioDispatcher) {
                        val filterState = filterStateUseCase.filterStateFlow.value
                        val service = rssService.get()
                        when (service) {
                            is LocalRssService ->
                                service.doSyncOneTime(
                                    feedId = filterState.feed?.id,
                                    groupId = filterState.group?.id,
                                )

                            is GoogleReaderRssService ->
                                service.doSyncOneTime(
                                    feedId = filterState.feed?.id,
                                    groupId = filterState.group?.id,
                                )

                            else -> service.doSyncOneTime()
                        }
                    }
                }.getOrElse {
                    _isSyncingFlow.value = false
                    showSyncResult(SyncOperationState.Failed(it.toOperationFailure()))
                    return@launch
                }
            val finalInfo =
                workManager.getWorkInfoByIdFlow(workId).filterNotNull().first { workInfo ->
                    val total =
                        workInfo.progress
                            .getInt(SyncWorker.PROGRESS_TOTAL, -1)
                            .takeIf { it >= 0 }
                    val completed =
                        workInfo.progress.getInt(SyncWorker.PROGRESS_COMPLETED, 0)
                    _syncOperationState.value =
                        SyncOperationState.Running(completed = completed, total = total)
                    workInfo.state.isFinished
                }
            _isSyncingFlow.value = false
            showSyncResult(
                if (finalInfo.state == WorkInfo.State.SUCCEEDED) {
                    SyncOperationState.Completed
                } else {
                    SyncOperationState.Failed(
                        finalInfo.outputData.getString(SyncWorker.ERROR_MESSAGE)?.let { message ->
                            OperationFailure(OperationFailureKind.UNKNOWN, message)
                        },
                    )
                }
            )
        }
    }

    private suspend fun showSyncResult(result: SyncOperationState) {
        _syncOperationState.value = result
        delay(if (result is SyncOperationState.Completed) 2_000L else 4_000L)
        if (_syncOperationState.value == result) {
            _syncOperationState.value = SyncOperationState.Idle
        }
    }

    fun resetFilter() {
        _searchContentInput.value = null
        filterStateUseCase.updateFilterState(feed = null, group = null, searchContent = null)
    }

    fun changeFilter(filterState: FilterState) {
        filterStateUseCase.updateFilterState(
            filterState.feed,
            filterState.group,
            filterState.filter,
            highlightRuleId = filterState.highlightRuleId,
            highlightUnreadOnly = filterState.highlightUnreadOnly,
        )
    }

    fun inputSearchContent(content: String? = null) {
        if (content != _searchContentInput.value) _searchContentInput.value = content
    }

    fun saveCurrentSearch(name: String) {
        val state = filterStateUseCase.filterStateFlow.value
        val query = _searchContentInput.value?.trim().orEmpty()
        if (name.isBlank() || query.isBlank()) return
        viewModelScope.launch(ioDispatcher) {
            articleCollectionRepository.saveSearch(
                name = name,
                query = query,
                filterIndex = state.filter.index,
                groupId = state.group?.id,
                feedId = state.feed?.id,
            )
        }
    }

    fun applySavedSearch(search: SavedSearch) {
        viewModelScope.launch(ioDispatcher) {
            val filter = Filter.articleValues.firstOrNull { it.index == search.filterIndex }
                ?: Filter.All
            val group = search.groupId?.let { rssService.get().findGroupById(it) }
            val feed = search.feedId?.let { rssService.get().findFeedById(it) }
            _searchContentInput.value = search.query
            filterStateUseCase.updateFilterState(
                feed = feed,
                group = group,
                filter = filter,
                searchContent = search.query,
            )
        }
    }

    fun deleteSavedSearch(search: SavedSearch) {
        viewModelScope.launch(ioDispatcher) {
            articleCollectionRepository.deleteSearch(search)
        }
    }

    private val _readingUiState = MutableStateFlow(ReadingUiState())
    val readingUiState: StateFlow<ReadingUiState> = _readingUiState.asStateFlow()

    private val _readerState: MutableStateFlow<ReaderState> = MutableStateFlow(ReaderState())
    val readerStateStateFlow = _readerState.asStateFlow()

    private val _aiSummaryState = MutableStateFlow(AiSummaryUiState())
    val aiSummaryState = _aiSummaryState.asStateFlow()

    private val _titleSummaryState = MutableStateFlow(TitleSummaryUiState())
    val titleSummaryState = _titleSummaryState.asStateFlow()

    private val currentArticle: Article?
        get() = readingUiState.value.articleWithFeed?.article

    private val currentFeed: Feed?
        get() = readingUiState.value.articleWithFeed?.feed

    fun initData(articleId: String, listIndex: Int? = null) {
        viewModelScope.launch {
            val snapshotList = articleListUseCase.itemSnapshotList

            val itemByIndex =
                listIndex?.let { snapshotList.getOrNull(it) as? ArticleFlowItem.Article }

            val itemFromList =
                if (itemByIndex != null && itemByIndex.articleWithFeed.article.id != articleId) {
                    itemByIndex
                } else {
                    snapshotList.find { item ->
                        item is ArticleFlowItem.Article &&
                            item.articleWithFeed.article.id == articleId
                    } as? ArticleFlowItem.Article
                }

            val item =
                itemByIndex?.articleWithFeed
                    ?: (itemFromList?.articleWithFeed
                        ?: rssService.get().findArticleById(articleId)!!)

            val markReadOnOpen = settingsProvider.getOrDefault(
                FeaturePreferenceKeys.markReadOnOpen.name,
                true,
            )
            if (markReadOnOpen && diffMapHolder.checkIfUnread(item)) {
                diffMapHolder.updateDiff(item, isUnread = false)
            }
            item.run {
                _readingUiState.update {
                    it.copy(
                        articleWithFeed = this,
                        isStarred = article.isStarred,
                        isReadLater = article.isReadLater,
                        isUnread = if (markReadOnOpen) false else article.isUnread,
                    )
                }
                _readerState.update {
                    it.copy(
                            articleId = article.id,
                            feedName = feed.name,
                            title = article.title,
                            author = article.author,
                            link = article.link,
                            publishedDate = article.date,
                        )
                        .prefetchArticleId()
                        .renderContent(this)
                }
            }
        }
    }

    fun clearReadingData() {
        _readingUiState.update { ReadingUiState() }
        _readerState.update { ReaderState() }
        _aiSummaryState.update { AiSummaryUiState() }
    }

    suspend fun ReaderState.renderContent(articleWithFeed: ArticleWithFeed): ReaderState {
        val contentState =
            if (articleWithFeed.feed.isFullContent) {
                val fullContent =
                    readerCacheHelper.readFullContent(articleWithFeed.article.id).getOrNull()
                if (fullContent != null) ReaderState.FullContent(fullContent)
                else {
                    renderFullContent()
                    ReaderState.Loading
                }
            } else ReaderState.Description(articleWithFeed.article.rawDescription)

        return copy(content = contentState)
    }

    fun renderDescriptionContent() {
        _readerState.update {
            it.copy(
                content = ReaderState.Description(content = currentArticle?.rawDescription ?: "")
            )
        }
    }

    fun renderFullContent() {
        val fetchJob =
            viewModelScope.launch {
                readerCacheHelper
                    .readOrFetchFullContent(currentArticle!!)
                    .onSuccess { content ->
                        _readerState.update {
                            it.copy(content = ReaderState.FullContent(content = content))
                        }
                    }
                    .onFailure { th ->
                        _readerState.update {
                            // An unavailable full-page parser must not make an already synced RSS
                            // article unreadable. Keep the feed-provided description as a usable
                            // offline fallback and preserve the error only in diagnostics/logs.
                            val fallback = currentArticle?.rawDescription
                            if (!fallback.isNullOrBlank()) {
                                it.copy(content = ReaderState.Description(fallback))
                            } else {
                                it.copy(content = ReaderState.Error(th.message.toString()))
                            }
                        }
                    }
            }
        viewModelScope.launch {
            delay(100L)
            if (fetchJob.isActive) {
                setLoading()
            }
        }
    }

    fun updateReadStatus(isUnread: Boolean) {
        readingUiState.value.articleWithFeed?.let {
            diffMapHolder.updateDiff(it, isUnread = isUnread)
        }
        _readingUiState.update {
            it.copy(isUnread = diffMapHolder.checkIfUnread(it.articleWithFeed!!))
        }
    }

    fun updateStarredStatus(isStarred: Boolean) {
        applicationScope.launch(ioDispatcher) {
            _readingUiState.update { it.copy(isStarred = isStarred) }
            currentArticle?.let {
                rssService.get().markAsStarred(articleId = it.id, isStarred = isStarred)
            }
        }
    }

    fun updateReadLaterStatus(isReadLater: Boolean) {
        applicationScope.launch(ioDispatcher) {
            _readingUiState.update { it.copy(isReadLater = isReadLater) }
            currentArticle?.let {
                rssService.get().markAsReadLater(it.id, isReadLater)
            }
        }
    }

    fun saveNote(note: String, quote: String = "") {
        val article = currentArticle ?: return
        if (note.isBlank() && quote.isBlank()) return
        viewModelScope.launch(ioDispatcher) {
            articleCollectionRepository.saveNote(article.id, note = note, quote = quote)
        }
    }

    suspend fun queryCurrentNotes() =
        currentArticle?.let { articleCollectionRepository.queryNotes(it.id) }.orEmpty()

    fun deleteNote(note: ArticleNote) {
        viewModelScope.launch(ioDispatcher) { articleCollectionRepository.deleteNote(note) }
    }

    suspend fun queryCurrentTags() =
        currentArticle?.let { articleCollectionRepository.observeTagsForArticle(it.id).first() }
            .orEmpty()

    fun addTag(name: String) {
        val article = currentArticle ?: return
        if (name.isBlank()) return
        viewModelScope.launch(ioDispatcher) {
            val tag = articleCollectionRepository.createTag(name)
            articleCollectionRepository.addTag(article.id, tag.id)
        }
    }

    fun removeTag(tag: ArticleTagLabel) {
        val article = currentArticle ?: return
        viewModelScope.launch(ioDispatcher) {
            articleCollectionRepository.removeTag(article.id, tag.id)
        }
    }

    fun summarizeCurrentArticle(content: String, forceRefresh: Boolean = false) {
        val article = currentArticle ?: return
        val fallbackState = _aiSummaryState.value
        _aiSummaryState.update { current ->
            if (forceRefresh && current.visible) current.copy(loading = true, failure = null)
            else AiSummaryUiState(loading = true)
        }
        viewModelScope.launch(ioDispatcher) {
            val plainText = Jsoup.parse(content).text().take(ARTICLE_SUMMARY_MAX_CHARS)
            var hasVisibleContent = false
            val result = runCatching {
                aiSummaryService.summarizeArticle(
                    accountId = article.accountId,
                    articleId = article.id,
                    title = article.title,
                    content = plainText,
                    link = article.link,
                    forceRefresh = forceRefresh,
                ) { partialSummary ->
                    val shouldReveal = !hasVisibleContent && partialSummary.isNotBlank()
                    if (shouldReveal) {
                        hasVisibleContent = true
                    }
                    _aiSummaryState.update { current ->
                        current.copy(
                            visible = current.visible || shouldReveal,
                            summary = partialSummary,
                        )
                    }
                }
            }
            val failure = result.exceptionOrNull()?.let(AiSummaryFailure::from)
                ?: AiSummaryFailure.EmptyResponse.takeIf { result.getOrNull().isNullOrBlank() }
            if (failure != null) {
                val failedState = if (
                    forceRefresh && !hasVisibleContent && fallbackState.summary.isNotBlank()
                ) fallbackState else _aiSummaryState.value
                _aiSummaryState.value = failedState.copy(
                    visible = true,
                    loading = false,
                    failure = failure,
                )
            } else {
                _aiSummaryState.update { current ->
                    current.copy(
                        loading = false,
                        summary = result.getOrThrow(),
                        failure = null,
                    )
                }
            }
        }
    }

    fun hideAiSummary() = _aiSummaryState.update { AiSummaryUiState() }

    fun summarizeCurrentTitles(forceRefresh: Boolean = false) {
        val filterState = flowUiState.value?.pagerData?.filterState ?: return
        if (!filterState.filter.isUnread() && !filterState.filter.isAll()) return
        val accountId =
            filterState.feed?.accountId
                ?: filterState.group?.accountId
                ?: accountService.currentAccountIdFlow.value
                ?: return
        val fallbackState = _titleSummaryState.value
        _titleSummaryState.update { current ->
            if (forceRefresh && current.visible) current.copy(loading = true, failure = null)
            else TitleSummaryUiState(loading = true)
        }
        viewModelScope.launch(ioDispatcher) {
            var hasVisibleContent = false
            val result = runCatching {
                val count = aiSummaryService.settings().articleCount
                val articles =
                    articleDao.queryLatestArticlesForSummary(
                        accountId = accountId,
                        groupId = filterState.group?.id,
                        feedId = filterState.feed?.id,
                        unreadOnly = filterState.filter.isUnread(),
                        limit = count,
                    )
                _titleSummaryState.update {
                    it.copy(
                        articleIds = articles.map(Article::id),
                        unreadArticleIds =
                            articles.filter(Article::isUnread).mapTo(mutableSetOf(), Article::id),
                    )
                }
                aiSummaryService.summarizeTitles(
                    accountId = accountId,
                    titles = articles.map { it.id to it.title },
                    forceRefresh = forceRefresh,
                ) { partialSummary ->
                    val shouldReveal = !hasVisibleContent && partialSummary.isNotBlank()
                    if (shouldReveal) {
                        hasVisibleContent = true
                    }
                    _titleSummaryState.update {
                        it.copy(
                            visible = it.visible || shouldReveal,
                            summary = partialSummary,
                        )
                    }
                }
            }
            val failure = result.exceptionOrNull()?.let(AiSummaryFailure::from)
                ?: AiSummaryFailure.EmptyResponse.takeIf { result.getOrNull().isNullOrBlank() }
            if (failure != null) {
                val failedState = if (
                    forceRefresh && !hasVisibleContent && fallbackState.summary.isNotBlank()
                ) fallbackState else _titleSummaryState.value
                _titleSummaryState.value = failedState.copy(
                    visible = true,
                    loading = false,
                    failure = failure,
                )
            } else {
                _titleSummaryState.update {
                    it.copy(
                        loading = false,
                        summary = result.getOrThrow(),
                        failure = null,
                    )
                }
            }
        }
    }

    fun hideTitleSummary() =
        _titleSummaryState.update { it.copy(visible = false, loading = false) }

    fun saveTitleSummaryScrollOffset(scrollOffset: Int) =
        _titleSummaryState.update { it.copy(scrollOffset = scrollOffset) }

    fun markSummarizedTitlesAsRead() {
        val articleIds = _titleSummaryState.value.unreadArticleIds
        if (articleIds.isEmpty()) return
        hideTitleSummary()
        viewModelScope.launch(ioDispatcher) {
            rssService.get().apply {
                batchMarkAsRead(articleIds, isUnread = false)
                runCatching { syncReadStatus(articleIds, isUnread = false) }
            }
        }
    }

    fun findArticleIndex(articleId: String): Int =
        articleListUseCase.itemSnapshotList.indexOfFirst { item ->
            item is ArticleFlowItem.Article && item.articleWithFeed.article.id == articleId
        }

    private fun setLoading() {
        _readerState.update { it.copy(content = ReaderState.Loading) }
    }

    fun ReaderState.prefetchArticleId(): ReaderState {
        val items = articleListUseCase.itemSnapshotList
        val currentId = currentArticle?.id
        val index =
            items.indexOfFirst { item ->
                item is ArticleFlowItem.Article && item.articleWithFeed.article.id == currentId
            }
        var previousArticle: ReaderState.PrefetchResult? = null
        var nextArticle: ReaderState.PrefetchResult? = null

        if (index != -1 || currentId == null) {
            val prevIterator = items.listIterator(index)
            while (prevIterator.hasPrevious()) {
                val previousIndex = prevIterator.previousIndex()
                val prev = prevIterator.previous()
                if (prev is ArticleFlowItem.Article) {
                    previousArticle =
                        ReaderState.PrefetchResult(
                            articleId = prev.articleWithFeed.article.id,
                            index = previousIndex,
                        )
                    break
                }
            }
            val nextIterator = items.listIterator(index + 1)
            while (nextIterator.hasNext()) {
                val nextIndex = nextIterator.nextIndex()
                val next = nextIterator.next()
                if (
                    next is ArticleFlowItem.Article && next.articleWithFeed.article.id != currentId
                ) {
                    nextArticle =
                        ReaderState.PrefetchResult(
                            articleId = next.articleWithFeed.article.id,
                            index = nextIndex,
                        )
                    break
                }
            }
        }

        Timber.d("$previousArticle, $nextArticle, $listIndex")
        return copy(nextArticle = nextArticle, previousArticle = previousArticle, listIndex = index)
    }

    fun downloadImage(
        url: String,
        onSuccess: (Uri) -> Unit = {},
        onFailure: (Throwable) -> Unit = {},
    ) {
        viewModelScope.launch {
            imageDownloader.downloadImage(url).onSuccess(onSuccess).onFailure(onFailure)
        }
    }
}

data class FlowUiState(
    val pagerData: PagerData,
    val nextFilterState: FilterState? = null,
    val highlightRules: List<ArticleRule> = emptyList(),
)

data class ReadingUiState(
    val articleWithFeed: ArticleWithFeed? = null,
    val isUnread: Boolean = false,
    val isStarred: Boolean = false,
    val isReadLater: Boolean = false,
)

data class AiSummaryUiState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val summary: String = "",
    val failure: AiSummaryFailure? = null,
)

data class TitleSummaryUiState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val summary: String = "",
    val articleIds: List<String> = emptyList(),
    val unreadArticleIds: Set<String> = emptySet(),
    val scrollOffset: Int = 0,
    val failure: AiSummaryFailure? = null,
)

data class ReaderState(
    val articleId: String? = null,
    val feedName: String = "",
    val title: String? = null,
    val author: String? = null,
    val link: String? = null,
    val publishedDate: Date = Date(0L),
    val content: ContentState = Loading,
    val listIndex: Int? = null,
    val nextArticle: PrefetchResult? = null,
    val previousArticle: PrefetchResult? = null,
) {
    data class PrefetchResult(val articleId: String, val index: Int)

    sealed interface ContentState {
        val text: String?
            get() {
                return when (this) {
                    is Description -> content
                    is Error -> message
                    is FullContent -> content
                    Loading -> null
                }
            }
    }

    data class FullContent(val content: String) : ContentState

    data class Description(val content: String) : ContentState

    data class Error(val message: String) : ContentState

    data object Loading : ContentState
}
