package me.ash.reader.ui.page.home.feeds.subscribe

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.rometools.rome.feed.synd.SyndFeed
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.ash.reader.R
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.OpmlService
import me.ash.reader.domain.service.OpmlImportProgress
import me.ash.reader.domain.service.OpmlImportResult
import me.ash.reader.domain.service.RssService
import me.ash.reader.domain.service.SyncWorker
import me.ash.reader.domain.data.ArticleRuleRepository
import me.ash.reader.domain.model.article.RuleType
import me.ash.reader.infrastructure.android.AndroidStringsHelper
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.rss.RssHelper
import me.ash.reader.ui.ext.formatUrl

@HiltViewModel
class SubscribeViewModel
@Inject
constructor(
    private val opmlService: OpmlService,
    val rssService: RssService,
    private val rssHelper: RssHelper,
    private val androidStringsHelper: AndroidStringsHelper,
    private val articleRuleRepository: ArticleRuleRepository,
    private val workManager: WorkManager,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val accountService: AccountService,
) : ViewModel() {

    private val _subscribeUiState = MutableStateFlow(SubscribeUiState())
    val subscribeUiState: StateFlow<SubscribeUiState> = _subscribeUiState.asStateFlow()

    private val _subscribeState: MutableStateFlow<SubscribeState> =
        MutableStateFlow(SubscribeState.Hidden)
    val subscribeState = _subscribeState.asStateFlow()

    val groupsFlow = MutableStateFlow<List<Group>>(emptyList())
    private var importJob: Job? = null
    private val subscribeMutex = Mutex()

    init {
        viewModelScope.launch {
            accountService.currentAccountFlow.collectLatest {
                rssService.get().pullGroups().collect { groupsFlow.value = it }
            }
        }
        viewModelScope.launch {
            groupsFlow.collect { groups ->
                _subscribeState.update {
                    when (it) {
                        is SubscribeState.Configure -> it.copy(groups = groups)
                        else -> it
                    }
                }
            }
        }
    }

    fun reset() {
        cancelSearch()
    }

    fun importFromInputStream(inputStream: InputStream) {
        if (importJob?.isActive == true) {
            inputStream.close()
            return
        }
        hideDrawer()
        importJob =
            viewModelScope.launch {
                val result =
                    runCatching {
                        inputStream.use { stream ->
                            opmlService.saveToDatabase(stream) { progress ->
                                _subscribeUiState.update {
                                    it.copy(importOperation = ImportOperationState.Importing(progress))
                                }
                            }
                        }
                    }
                        .getOrElse { error ->
                            showTerminalImportOperation(
                                ImportOperationState.Failed(error.message.orEmpty())
                            )
                            return@launch
                        }

                val workId =
                    runCatching { rssService.get().doSyncOneTime() }
                        .getOrElse { error ->
                            showTerminalImportOperation(
                                ImportOperationState.Failed(
                                    message = error.message.orEmpty(),
                                    importResult = result,
                                )
                            )
                            return@launch
                        }
                _subscribeUiState.update {
                    it.copy(importOperation = ImportOperationState.Updating(result = result))
                }
                val finalInfo =
                    workManager
                        .getWorkInfoByIdFlow(workId)
                        .filterNotNull()
                        .first { workInfo ->
                            val total =
                                workInfo.progress
                                    .getInt(SyncWorker.PROGRESS_TOTAL, -1)
                                    .takeIf { it >= 0 }
                            val completed =
                                workInfo.progress.getInt(SyncWorker.PROGRESS_COMPLETED, 0)
                            _subscribeUiState.update {
                                it.copy(
                                    importOperation =
                                        ImportOperationState.Updating(
                                            result = result,
                                            completed = completed,
                                            total = total,
                                        )
                                )
                            }
                            workInfo.state.isFinished
                        }
                showTerminalImportOperation(
                    if (finalInfo.state == WorkInfo.State.SUCCEEDED) {
                        ImportOperationState.Completed(result)
                    } else {
                        ImportOperationState.Failed(
                            message = "",
                            importResult = result,
                        )
                    }
                )
            }
    }

    private fun showTerminalImportOperation(state: ImportOperationState) {
        _subscribeUiState.update { it.copy(importOperation = state) }
        viewModelScope.launch {
            delay(if (state is ImportOperationState.Completed) 2_000L else 4_000L)
            _subscribeUiState.update {
                if (it.importOperation == state) {
                    it.copy(importOperation = ImportOperationState.Idle)
                } else {
                    it
                }
            }
        }
    }

    fun importRules(inputStream: InputStream, types: Set<RuleType>, callback: (Result<Int>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching {
                articleRuleRepository.importRules(accountService.getCurrentAccountId(), inputStream.bufferedReader().use { it.readText() }, types)
            }
            callback(result)
        }
    }

    fun selectedGroup(groupId: String) {
        _subscribeState.update {
            when (it) {
                is SubscribeState.Configure -> it.copy(selectedGroupId = groupId)
                else -> it
            }
        }
    }

    fun addNewGroup() {
        if (_subscribeUiState.value.newGroupContent.isNotBlank()) {
            applicationScope.launch {
                // TODO: How to add a single group without no feeds via Google Reader API?
                selectedGroup(
                    rssService.get().addGroup(null, _subscribeUiState.value.newGroupContent)
                )
                hideNewGroupDialog()
                _subscribeUiState.update { it.copy(newGroupContent = "") }
            }
        }
    }

    fun toggleParseFullContentPreset() {
        _subscribeState.update { state ->
            when (state) {
                is SubscribeState.Configure ->
                    state.copy(fullContent = !state.fullContent, browser = false)

                else -> state
            }
        }
    }

    fun toggleOpenInBrowserPreset() {
        _subscribeState.update { state ->
            when (state) {
                is SubscribeState.Configure ->
                    state.copy(browser = !state.browser, fullContent = false)

                else -> state
            }
        }
    }

    fun toggleAllowNotificationPreset() {
        _subscribeState.update { state ->
            when (state) {
                is SubscribeState.Configure -> state.copy(notification = !state.notification)
                else -> state
            }
        }
    }

    fun searchFeed() {
        val currentState = _subscribeState.value
        if (currentState !is SubscribeState.Idle) return
        viewModelScope.launch {
            val feedLink = currentState.linkState.text.trim().toString().formatUrl()
            currentState.linkState.edit { this.replace(0, length, feedLink) }

            if (rssService.get().isFeedExist(feedLink)) {
                _subscribeState.value =
                    currentState.copy(
                        errorMessage = androidStringsHelper.getString(R.string.already_subscribed)
                    )
                return@launch
            }
            val groups = groupsFlow.value
            val firstGroupId = groups.firstOrNull()?.id ?: return@launch

            val job =
                viewModelScope.launch {
                    runCatching { rssHelper.searchFeed(feedLink) }
                        .onSuccess {
                            if (rssService.get().isFeedExist(it.feedLink)) {
                                _subscribeState.value =
                                    currentState.copy(
                                        errorMessage =
                                            androidStringsHelper.getString(
                                                R.string.already_subscribed
                                            )
                                    )
                                return@onSuccess
                            }
                            val groups = groupsFlow.value
                            _subscribeState.value =
                                SubscribeState.Configure(
                                    searchedFeed = it.feed,
                                    feedLink = it.feedLink,
                                    groups = groups,
                                    selectedGroupId = firstGroupId,
                                )
                        }
                        .onFailure {
                            _subscribeState.value = currentState.copy(errorMessage = it.message)
                        }
                }

            _subscribeState.value =
                SubscribeState.Fetching(linkState = currentState.linkState, job = job)
        }
    }

    fun cancelSearch() {
        _subscribeState.value.let {
            if (it is SubscribeState.Fetching && it.job.isActive) {
                it.job.cancel()
            }
        }
    }

    fun subscribe() {
        val state = _subscribeState.value
        if (state !is SubscribeState.Configure) return

        applicationScope.launch {
            subscribeMutex.withLock {
                val repository = rssService.get()
                if (repository.isFeedExist(state.feedLink)) {
                    _subscribeState.value =
                        SubscribeState.Idle(
                            linkState = TextFieldState(state.feedLink),
                            errorMessage = androidStringsHelper.getString(R.string.already_subscribed),
                        )
                    return@withLock
                }
                repository.subscribe(
                    searchedFeed = state.searchedFeed,
                    feedLink = state.feedLink,
                    groupId = state.selectedGroupId,
                    isNotification = state.notification,
                    isFullContent = state.fullContent,
                    isBrowser = state.browser,
                )
                hideDrawer()
            }
        }
    }

    fun inputNewGroup(content: String) {
        _subscribeUiState.update { it.copy(newGroupContent = content) }
    }

    fun handleSharedUrlFromIntent(url: String) {
        viewModelScope
            .launch {
                _subscribeState.update { SubscribeState.Idle(linkState = TextFieldState(url)) }
                delay(50)
            }
            .invokeOnCompletion { searchFeed() }
    }

    fun showDrawer() {
        _subscribeState.value =
            SubscribeState.Idle(importFromOpmlEnabled = rssService.get().importSubscription)
    }

    fun hideDrawer() {
        cancelSearch()
        _subscribeState.value = SubscribeState.Hidden
    }

    fun showNewGroupDialog() {
        _subscribeUiState.update { it.copy(newGroupDialogVisible = true) }
    }

    fun hideNewGroupDialog() {
        _subscribeUiState.update { it.copy(newGroupDialogVisible = false) }
    }

    fun showRenameDialog() {
        _subscribeUiState.update { it.copy(renameDialogVisible = true) }
        _subscribeUiState.update { uiState ->
            (_subscribeState.value as? SubscribeState.Configure)?.searchedFeed?.title?.let { title
                ->
                uiState.copy(newName = title)
            } ?: uiState
        }
    }

    fun hideRenameDialog() {
        _subscribeUiState.update { it.copy(renameDialogVisible = false, newName = "") }
    }

    fun inputNewName(content: String) {
        _subscribeUiState.update { it.copy(newName = content) }
    }

    fun renameFeed() {
        _subscribeState.update { state ->
            when (state) {
                is SubscribeState.Configure ->
                    state.copy(
                        searchedFeed =
                            state.searchedFeed.apply { title = _subscribeUiState.value.newName }
                    )

                else -> state
            }
        }
    }
}

data class SubscribeUiState(
    val newGroupDialogVisible: Boolean = false,
    val newGroupContent: String = "",
    val newName: String = "",
    val renameDialogVisible: Boolean = false,
    val importOperation: ImportOperationState = ImportOperationState.Idle,
)

sealed interface ImportOperationState {
    data object Idle : ImportOperationState

    data class Importing(val progress: OpmlImportProgress) : ImportOperationState

    data class Updating(
        val result: OpmlImportResult,
        val completed: Int = 0,
        val total: Int? = null,
    ) : ImportOperationState

    data class Completed(val result: OpmlImportResult) : ImportOperationState

    data class Failed(
        val message: String,
        val importResult: OpmlImportResult? = null,
    ) : ImportOperationState
}

sealed interface SubscribeState {
    object Hidden : SubscribeState

    sealed interface Visible

    sealed interface Input : SubscribeState, Visible {
        val linkState: TextFieldState
    }

    data class Idle(
        override val linkState: TextFieldState = TextFieldState(),
        val importFromOpmlEnabled: Boolean = false,
        val errorMessage: String? = null,
    ) : SubscribeState, Input

    data class Fetching(override val linkState: TextFieldState, val job: Job) :
        SubscribeState, Input

    data class Configure(
        val searchedFeed: SyndFeed,
        val feedLink: String,
        val groups: List<Group> = emptyList(),
        val notification: Boolean = false,
        val fullContent: Boolean = false,
        val browser: Boolean = false,
        val selectedGroupId: String,
    ) : SubscribeState, Visible
}
