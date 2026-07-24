package me.ash.reader.ui.page.home.feeds

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.R
import me.ash.reader.domain.data.ArticleContentType
import me.ash.reader.domain.model.account.Account
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.domain.model.general.OperationFailure
import me.ash.reader.domain.model.general.OperationFailureKind
import me.ash.reader.domain.model.general.toOperationFailure
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.android.AndroidStringsHelper
import me.ash.reader.domain.data.DiffMapHolder
import me.ash.reader.domain.data.FilterState
import me.ash.reader.domain.data.FilterStateUseCase
import me.ash.reader.domain.data.GroupWithFeedsListUseCase
import me.ash.reader.domain.service.SyncWorker
import me.ash.reader.infrastructure.di.DefaultDispatcher
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.ui.page.home.SyncOperationState
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedsViewModel @Inject constructor(
    private val accountService: AccountService,
    private val rssService: RssService,
    private val workManager: WorkManager,
    private val androidStringsHelper: AndroidStringsHelper,
    @DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
    private val diffMapHolder: DiffMapHolder,
    private val filterStateUseCase: FilterStateUseCase,
    private val groupWithFeedsListUseCase: GroupWithFeedsListUseCase,
) : ViewModel() {

    private val _feedsUiState =
        MutableStateFlow(FeedsUiState())
    val feedsUiState: StateFlow<FeedsUiState> = _feedsUiState.asStateFlow()

    val syncWorkLiveData = workManager.getWorkInfosByTagLiveData(SyncWorker.SYNC_TAG)

    private val _syncOperationState =
        MutableStateFlow<SyncOperationState>(SyncOperationState.Idle)
    val syncOperationState: StateFlow<SyncOperationState> = _syncOperationState.asStateFlow()

    val filterStateFlow = filterStateUseCase.filterStateFlow
    val groupWithFeedsListFlow = groupWithFeedsListUseCase.groupWithFeedListFlow

    fun sync() {
        if (_syncOperationState.value is SyncOperationState.Running) return
        _syncOperationState.value = SyncOperationState.Running()
        viewModelScope.launch {
            val workId =
                runCatching {
                    withContext(ioDispatcher) { rssService.get().doSyncOneTime() }
                }.getOrElse {
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

    fun commitDiffs() = diffMapHolder.commitDiffsToDb()

    fun changeFilter(filterState: FilterState) {
        filterStateUseCase.updateFilterState(filterState)
    }

    fun contentTypeForScope(filterState: FilterState): ArticleContentType =
        filterStateUseCase.contentTypeForScope(filterState)

    fun selectContentType(filterState: FilterState, onSaved: () -> Unit = {}) {
        viewModelScope.launch(ioDispatcher) {
            filterStateUseCase.saveContentType(filterState)
            filterStateUseCase.updateFilterState(filterState)
            withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { onSaved() }
        }
    }

    fun openFilter(filterState: FilterState, onReady: () -> Unit) {
        viewModelScope.launch {
            val scopedFilterState = filterState.copy(
                contentType = filterStateUseCase.contentTypeForScope(filterState),
            )
            val resolvedFilterState =
                if (filterStateUseCase.hasStoredContentType(filterState)) scopedFilterState
                else filterStateUseCase.autoSelectContentType(scopedFilterState)
            filterStateUseCase.updateFilterState(
                resolvedFilterState
            )
            onReady()
        }
    }

    private val accountFlow = accountService.currentAccountFlow

    init {
        viewModelScope.launch {
            accountFlow.collect { account ->
                _feedsUiState.update { it.copy(account = account) }
            }
        }
        viewModelScope.launch {
            groupWithFeedsListFlow
                .combine(filterStateUseCase.filterStateFlow) { groups, filterState ->
                    groups to filterState.filter
                }
                .mapLatest { (groups, filter) ->
                    val sum = groups.sumOf { group -> group.feeds.sumOf { it.important } }
                    val plural = when (filter) {
                        Filter.Unread -> R.plurals.unread_desc
                        Filter.Starred -> R.plurals.starred_desc
                        Filter.Highlighted -> R.plurals.highlighted_desc
                        Filter.ReadLater -> R.plurals.read_later_desc
                        else -> R.plurals.all_desc
                    }
                    androidStringsHelper.getQuantityString(plural, sum, sum)
                }
                .flowOn(defaultDispatcher)
                .collect { text -> _feedsUiState.update { it.copy(importantSum = text) } }
        }
    }

}

data class FeedsUiState(
    val account: Account? = null,
    val importantSum: String = "",
    val listState: LazyListState = LazyListState(),
    val groupsVisible: SnapshotStateMap<String, Boolean> = mutableStateMapOf(),
)
