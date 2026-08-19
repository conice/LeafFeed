package me.ash.reader.ui.page.adaptive

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.repository.PodcastDao
import me.ash.reader.infrastructure.audio.PodcastDownloadRepository
import me.ash.reader.infrastructure.audio.PodcastPlayer
import me.ash.reader.infrastructure.audio.PodcastTranscriptCue
import me.ash.reader.infrastructure.audio.PodcastTranscriptRepository
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys
import me.ash.reader.infrastructure.preference.SettingsProvider

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
