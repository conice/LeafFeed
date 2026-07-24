package me.ash.reader.ui.page.settings.features

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.audio.PodcastDownloadRepository

@HiltViewModel
class PodcastSettingsViewModel @Inject constructor(
    private val downloads: PodcastDownloadRepository,
) : ViewModel() {
    val downloadLocation: String
        get() = downloads.downloadDirectory.absolutePath

    fun clearDownloads(onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onComplete(downloads.clearAll()) }
    }
}
