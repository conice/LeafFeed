package com.conice.morss.ui.page.settings.troubleshooting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.conice.morss.application.data.Log
import com.conice.morss.application.data.ArticleCollectionRepository
import com.conice.morss.application.data.ArticleCollectionImportResult
import com.conice.morss.application.data.AutomationImportResult
import com.conice.morss.application.data.SyncLogger
import com.conice.morss.application.service.AccountService
import com.conice.morss.application.service.SyncWorker
import com.conice.morss.infrastructure.di.IODispatcher
import com.conice.morss.infrastructure.di.MainDispatcher
import com.conice.morss.infrastructure.ai.AiConfigurationRepository
import com.conice.morss.infrastructure.preference.SyncStatusStore
import com.conice.morss.infrastructure.preference.SyncSummary
import com.conice.morss.infrastructure.preference.FeaturePreferenceKeys
import com.conice.morss.infrastructure.preference.SettingsProvider
import com.conice.morss.infrastructure.preference.PreferencesImportResult
import com.conice.morss.infrastructure.preference.fromDataStoreToJSONString
import com.conice.morss.infrastructure.preference.fromJSONStringToDataStore

@HiltViewModel
class TroubleshootingViewModel
@Inject
constructor(
    private val accountService: AccountService,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
    val workManager: WorkManager,
    private val syncLogger: SyncLogger,
    private val syncStatusStore: SyncStatusStore,
    private val articleCollectionRepository: ArticleCollectionRepository,
    private val settingsProvider: SettingsProvider,
    private val aiConfigurationRepository: AiConfigurationRepository,
) : ViewModel() {

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
        importPreferencesFromJSON(context, byteArray, callback)
    }

    fun importPreferencesFromJSON(
        context: Context,
        byteArray: ByteArray,
        callback: (Result<PreferencesImportResult>) -> Unit = {},
    ) {
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching {
                String(byteArray).fromJSONStringToDataStore(context) { backup ->
                    aiConfigurationRepository.importBackup(backup)
                }
            }
            withContext(mainDispatcher) { callback(result) }
        }
    }

    fun exportPreferencesAsJSON(
        context: Context,
        includeSensitive: Boolean = false,
        callback: (ByteArray) -> Unit = {},
    ) {
        viewModelScope.launch(ioDispatcher) {
            val aiConfiguration = aiConfigurationRepository.exportBackup(includeSensitive)
            callback(
                context
                    .fromDataStoreToJSONString(includeSensitive, aiConfiguration)
                    .toByteArray()
            )
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
                require(content.size <= 50 * 1024 * 1024) { "Reading data backup is too large" }
                articleCollectionRepository.importBackup(content.toString(Charsets.UTF_8))
            }
            withContext(mainDispatcher) { callback(result) }
        }
    }

    fun exportAutomations(callback: (ByteArray) -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            callback(articleCollectionRepository.exportAutomations().toByteArray())
        }
    }

    fun importAutomations(
        content: ByteArray,
        callback: (Result<AutomationImportResult>) -> Unit,
    ) {
        viewModelScope.launch(ioDispatcher) {
            val result = runCatching {
                require(content.size <= 50 * 1024 * 1024) { "Automation backup is too large" }
                articleCollectionRepository.importAutomations(content.toString(Charsets.UTF_8))
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
