package me.ash.reader.ui.page.settings.troubleshooting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.domain.data.Log
import me.ash.reader.domain.data.ArticleCollectionRepository
import me.ash.reader.domain.data.ArticleCollectionImportResult
import me.ash.reader.domain.data.SyncLogger
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.OpmlService
import me.ash.reader.domain.service.RssService
import me.ash.reader.domain.service.SyncWorker
import me.ash.reader.domain.repository.FeedDao
import androidx.work.workDataOf
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.DefaultDispatcher
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.di.MainDispatcher
import me.ash.reader.infrastructure.preference.SyncStatusStore
import me.ash.reader.infrastructure.preference.SyncSummary
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.ui.ext.PreferencesImportResult
import me.ash.reader.ui.ext.fromDataStoreToJSONString
import me.ash.reader.ui.ext.fromJSONStringToDataStore

@HiltViewModel
class TroubleshootingViewModel
@Inject
constructor(
    private val accountService: AccountService,
    private val rssService: RssService,
    private val opmlService: OpmlService,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope,
    val workManager: WorkManager,
    private val syncLogger: SyncLogger,
    private val syncStatusStore: SyncStatusStore,
    private val articleCollectionRepository: ArticleCollectionRepository,
    private val settingsProvider: SettingsProvider,
    private val feedDao: FeedDao,
) : ViewModel() {

    val currentSyncSummary = accountService.currentAccountIdFlow
        .filterNotNull()
        .flatMapLatest(syncStatusStore::observe)
        .mapLatest { summary ->
            summary?.let {
                it.copy(
                    failedFeedNames = if (it.failedFeedIds.isEmpty()) emptyList()
                    else feedDao.queryByIds(it.failedFeedIds).sortedBy { feed -> feed.name }
                        .map { feed -> feed.name },
                )
            }
        }
        .combine(settingsProvider.preferencesFlow) { summary, preferences ->
            if (preferences[FeaturePreferenceKeys.diagnosticIncludeFeedUrls] == true) {
                summary
            } else {
                summary?.redacted()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _troubleshootingUiState = MutableStateFlow(TroubleshootingUiState())
    val troubleshootingUiState: StateFlow<TroubleshootingUiState> =
        _troubleshootingUiState.asStateFlow()

    fun showWarningDialog() {
        _troubleshootingUiState.update { it.copy(warningDialogVisible = true) }
    }

    fun hideWarningDialog() {
        _troubleshootingUiState.update { it.copy(warningDialogVisible = false) }
    }

    fun tryImport(
        context: Context,
        byteArray: ByteArray,
        callback: (Result<PreferencesImportResult>) -> Unit = {},
    ) {
        //        if (!byteArray.isProbableProtobuf()) {
        //            showWarningDialog()
        //        } else {
        importPreferencesFromJSON(context, byteArray, callback)
        //        }
    }

    fun importPreferencesFromJSON(
        context: Context,
        byteArray: ByteArray,
        callback: (Result<PreferencesImportResult>) -> Unit = {},
    ) {
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching { String(byteArray).fromJSONStringToDataStore(context) }
            withContext(mainDispatcher) { callback(result) }
        }
    }

    fun exportPreferencesAsJSON(
        context: Context,
        includeSensitive: Boolean = false,
        callback: (ByteArray) -> Unit = {},
    ) {
        viewModelScope.launch(ioDispatcher) {
            callback(context.fromDataStoreToJSONString(includeSensitive).toByteArray())
        }
    }

    suspend fun getSyncLogs(): List<Log> =
        syncLogger.list().let { logs ->
            if (settingsProvider.get(FeaturePreferenceKeys.diagnosticIncludeFeedUrls) == true) {
                logs
            } else {
                logs.map { it.copy(content = redactUrls(it.content)) }
            }
        }

    suspend fun getCurrentSyncSummary(): SyncSummary? =
        syncStatusStore.get(accountService.getCurrentAccountId())?.let { summary ->
            if (settingsProvider.get(FeaturePreferenceKeys.diagnosticIncludeFeedUrls) == true) {
                summary
            } else {
                summary.redacted()
            }
        }

    fun clearSyncLogs() = viewModelScope.launch { syncLogger.clear() }

    fun retrySync() {
        SyncWorker.enqueueOneTimeWork(
            workManager,
            workDataOf("accountId" to accountService.getCurrentAccountId()),
        )
    }

    fun exportReadingData(callback: (ByteArray) -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            callback(articleCollectionRepository.exportBackup().toByteArray())
        }
    }

    fun importReadingData(
        content: ByteArray,
        callback: (Result<ArticleCollectionImportResult>) -> Unit,
    ) {
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching {
                require(content.size <= 10 * 1024 * 1024) { "Reading data backup is too large" }
                articleCollectionRepository.importBackup(content.toString(Charsets.UTF_8))
            }
            withContext(mainDispatcher) { callback(result) }
        }
    }
}

private val diagnosticUrlPattern = Regex("https?://[^\\s)\\]}>,]+", RegexOption.IGNORE_CASE)

internal fun redactUrls(value: String): String =
    value.replace(diagnosticUrlPattern, "[feed address hidden]")

private fun SyncSummary.redacted(): SyncSummary = copy(
    errorMessage = errorMessage?.let(::redactUrls),
    failedFeedIds = failedFeedIds.map(::redactUrls),
    failedFeedNames = failedFeedNames.map(::redactUrls),
)

data class TroubleshootingUiState(
    val isLoading: Boolean = false,
    val warningDialogVisible: Boolean = false,
)
