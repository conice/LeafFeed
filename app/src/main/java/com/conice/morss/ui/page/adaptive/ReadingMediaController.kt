package com.conice.morss.ui.page.adaptive

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import com.conice.morss.domain.model.article.Article
import com.conice.morss.domain.repository.PodcastDao
import com.conice.morss.infrastructure.audio.PodcastDownloadRepository
import com.conice.morss.infrastructure.audio.PodcastPlayer
import com.conice.morss.infrastructure.audio.PodcastTranscriptCue
import com.conice.morss.infrastructure.audio.PodcastTranscriptRepository
import com.conice.morss.infrastructure.preference.FeaturePreferenceKeys
import com.conice.morss.infrastructure.preference.SettingsProvider

class ReadingMediaController
@Inject
constructor(
    val podcastPlayer: PodcastPlayer,
    private val podcastDao: PodcastDao,
    private val downloads: PodcastDownloadRepository,
    private val transcripts: PodcastTranscriptRepository,
    private val settingsProvider: SettingsProvider,
) {
    val downloadingArticleIds: Flow<Set<String>> = downloads.downloadingArticleIds

    fun download(article: Article): Result<Unit> {
        val wifiOnly = settingsProvider.get<Boolean>(FeaturePreferenceKeys.podcastWifiOnly) ?: true
        return downloads.enqueue(article, wifiOnly)
    }

    suspend fun removeDownload(article: Article): Result<Unit> = downloads.remove(article)

    suspend fun setPlayed(articleId: String, played: Boolean) =
        podcastDao.updatePlayedStatus(articleId, played)

    fun cancelDownload(articleId: String) = downloads.cancel(articleId)

    suspend fun loadTranscript(url: String): Result<List<PodcastTranscriptCue>> =
        transcripts.load(url)
}
