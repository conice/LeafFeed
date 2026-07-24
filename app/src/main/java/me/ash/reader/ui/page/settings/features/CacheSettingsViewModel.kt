package me.ash.reader.ui.page.settings.features

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.ash.reader.infrastructure.rss.ReaderCacheHelper
import me.ash.reader.infrastructure.ai.AiSummaryCache
import me.ash.reader.domain.service.AccountService

@HiltViewModel
class CacheSettingsViewModel @Inject constructor(
    private val readerCache: ReaderCacheHelper,
    private val aiSummaryCache: AiSummaryCache,
    private val accountService: AccountService,
) : ViewModel() {
    private val _usage = MutableStateFlow(CacheUsageState())
    val usage: StateFlow<CacheUsageState> = _usage.asStateFlow()

    init { refreshUsage() }

    fun refreshUsage() {
        viewModelScope.launch {
            val reader = readerCache.usage()
            val ai = aiSummaryCache.usage(accountService.getCurrentAccountId())
            _usage.value = CacheUsageState(reader.files, reader.bytes, ai.files, ai.bytes)
        }
    }

    fun clearReaderCache(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = readerCache.clearCache()
            refreshUsage()
            onComplete(result)
        }
    }

    fun clearAiSummaryCache(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = aiSummaryCache.clearAccount(accountService.getCurrentAccountId())
            refreshUsage()
            onComplete(result)
        }
    }
}

data class CacheUsageState(
    val readerFiles: Int = 0,
    val readerBytes: Long = 0,
    val aiFiles: Int = 0,
    val aiBytes: Long = 0,
)
