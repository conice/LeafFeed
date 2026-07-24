package me.ash.reader.infrastructure.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.infrastructure.preference.FeaturePreferenceKeys
import me.ash.reader.infrastructure.preference.SettingsProvider
import org.json.JSONArray
import org.json.JSONObject

data class PodcastPlayerState(
    val articleId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val bufferedPositionMs: Long = 0L,
    val playbackState: Int = Player.STATE_IDLE,
    val errorMessage: String? = null,
    val queueSize: Int = 0,
    val queueIndex: Int = -1,
    val title: String = "",
    val artist: String? = null,
    val artworkUri: String? = null,
    val queue: List<PodcastQueueItem> = emptyList(),
    val sleepTimerRemainingMs: Long? = null,
)

data class PodcastQueueItem(val id: String, val title: String, val artist: String?)

val PodcastPlaybackSpeeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

@Singleton
class PodcastPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val articleDao: ArticleDao,
    private val settingsProvider: SettingsProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val _state = MutableStateFlow(PodcastPlayerState())
    val state: StateFlow<PodcastPlayerState> = _state.asStateFlow()
    private var lastSnapshot: Snapshot? = null
    private var lastPersistAt = 0L
    private var sleepTimerJob: Job? = null
    private var sleepTimerEndAt: Long? = null
    private val queuePreferences by lazy { context.getSharedPreferences("podcast_queue", Context.MODE_PRIVATE) }
    private data class Snapshot(val id: String, val positionMs: Long, val durationMs: Long)

    private val controllerFuture = MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, PodcastPlaybackService::class.java)),
    ).buildAsync()

    init {
        controllerFuture.addListener({
            if (controllerFuture.isCancelled) return@addListener
            val controller = runCatching { controllerFuture.get() }.getOrNull() ?: return@addListener
            restoreQueue(controller)
            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = publish(controller)
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    persistLastSnapshot()
                    if (
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                        settingsProvider.get(FeaturePreferenceKeys.podcastAutoPlayNext) == false
                    ) {
                        controller.pause()
                    }
                    persistQueue(controller)
                    publish(controller)
                }
                override fun onPlaybackStateChanged(playbackState: Int) = publish(controller)
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    publish(controller, errorMessage = error.message ?: "Playback failed")
                }
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) = publish(controller)
            })
            publish(controller)
            scope.launch {
                while (isActive) {
                    delay(1_000)
                    if (controller.isPlaying) {
                        val now = System.currentTimeMillis()
                        publish(controller, persist = now - lastPersistAt >= 30_000L)
                        if (now - lastPersistAt >= 30_000L) {
                            persistQueue(controller)
                            lastPersistAt = now
                        }
                    }
                }
            }
        }, mainExecutor)
    }

    fun play(article: Article, title: String = article.title, artist: String? = null, artwork: String? = article.img) {
        val url = article.downloadedPath?.takeIf { File(it).exists() }?.let { Uri.fromFile(File(it)).toString() }
            ?: article.audioUrl ?: return
        withController { controller ->
            persistLastSnapshot()
            val remember =
                settingsProvider.get(FeaturePreferenceKeys.podcastRememberProgress) ?: true
            controller.setMediaItem(PodcastPlaybackService.mediaItem(article.id, url, title, artist, artwork), if (remember) article.playbackPositionMs else 0L)
            controller.setPlaybackSpeed(
                settingsProvider.get(FeaturePreferenceKeys.podcastDefaultSpeed) ?: 1f
            )
            controller.prepare()
            controller.play()
            persistQueue(controller)
            publish(controller)
        }
    }

    fun toggle() = withController {
        if (it.isPlaying) {
            persist(it)
            persistQueue(it)
            it.pause()
        } else it.play()
    }

    fun retry() = withController {
        it.prepare()
        it.play()
    }

    fun enqueue(article: Article, artist: String? = null) {
        val url = article.downloadedPath?.takeIf { File(it).exists() }?.let { Uri.fromFile(File(it)).toString() }
            ?: article.audioUrl ?: return
        withController {
            if ((0 until it.mediaItemCount).any { index -> it.getMediaItemAt(index).mediaId == article.id }) {
                publish(it, persist = false)
                return@withController
            }
            it.addMediaItem(PodcastPlaybackService.mediaItem(article.id, url, article.title, artist, article.img))
            persistQueue(it)
            publish(it, persist = false)
        }
    }

    fun seekBy(deltaMs: Long) = withController { seekTo(it.currentPosition + deltaMs) }
    fun seekTo(positionMs: Long) = withController {
        val duration = it.duration.takeIf { value -> value > 0 } ?: Long.MAX_VALUE
        it.seekTo(positionMs.coerceIn(0L, duration))
        publish(it)
    }
    fun setSpeed(speed: Float) = withController {
        it.setPlaybackSpeed(speed)
        persistQueue(it)
        publish(it, persist = false)
    }
    fun pause() = withController { persist(it); persistQueue(it); it.pause() }
    fun close() = withController {
        persist(it)
        it.stop()
        it.clearMediaItems()
        queuePreferences.edit().clear().apply()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerEndAt = null
        lastSnapshot = null
        _state.value = PodcastPlayerState()
    }
    fun skipNext() = withController { it.seekToNextMediaItem(); persistQueue(it); publish(it, persist = false) }
    fun skipPrevious() = withController {
        if (it.currentPosition > 5_000L) it.seekTo(0L) else it.seekToPreviousMediaItem()
        persistQueue(it)
        publish(it, persist = false)
    }

    /** Selects and starts an item from the expanded queue. */
    fun playQueueItem(index: Int) = withController {
        if (index in 0 until it.mediaItemCount) {
            it.seekToDefaultPosition(index)
            it.play()
            persistQueue(it)
            publish(it, persist = false)
        }
    }

    fun removeFromQueue(index: Int) = withController {
        if (index in 0 until it.mediaItemCount) {
            it.removeMediaItem(index)
            persistQueue(it)
            publish(it, persist = false)
        }
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) = withController {
        if (fromIndex in 0 until it.mediaItemCount && toIndex in 0 until it.mediaItemCount) {
            it.moveMediaItem(fromIndex, toIndex)
            persistQueue(it)
            publish(it, persist = false)
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerEndAt = minutes?.takeIf { it > 0 }?.let { System.currentTimeMillis() + it * 60_000L }
        if (sleepTimerEndAt != null) {
            sleepTimerJob = scope.launch {
                while (isActive && sleepTimerEndAt != null) {
                    val remaining = sleepTimerEndAt!! - System.currentTimeMillis()
                    if (remaining <= 0L) {
                        pause()
                        sleepTimerEndAt = null
                        publishCurrent()
                        break
                    }
                    publishCurrent()
                    delay(1_000L)
                }
            }
        }
        publishCurrent()
    }

    private fun withController(action: (MediaController) -> Unit) {
        if (controllerFuture.isDone) runCatching { action(controllerFuture.get()) }
        else controllerFuture.addListener({ runCatching { action(controllerFuture.get()) } }, mainExecutor)
    }

    private fun publishCurrent() {
        if (controllerFuture.isDone) runCatching { publish(controllerFuture.get(), persist = false) }
    }

    private fun persistQueue(controller: Player) {
        val items = JSONArray()
        for (index in 0 until controller.mediaItemCount) {
            val item = controller.getMediaItemAt(index)
            val uri = item.localConfiguration?.uri?.toString() ?: continue
            items.put(JSONObject().apply {
                put("id", item.mediaId)
                put("uri", uri)
                put("title", item.mediaMetadata.title?.toString().orEmpty())
                put("artist", item.mediaMetadata.artist?.toString())
                put("artwork", item.mediaMetadata.artworkUri?.toString())
            })
        }
        queuePreferences.edit()
            .putString("items", items.toString())
            .putInt("index", controller.currentMediaItemIndex)
            .putLong("position", controller.currentPosition.coerceAtLeast(0L))
            .putFloat("speed", controller.playbackParameters.speed)
            .apply()
    }

    private fun restoreQueue(controller: MediaController) {
        if (controller.mediaItemCount > 0) return
        val stored = queuePreferences.getString("items", null) ?: return
        val items = runCatching {
            val array = JSONArray(stored)
            (0 until array.length()).mapNotNull { index ->
                val item = array.getJSONObject(index)
                val uri = item.optString("uri").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (uri.startsWith("file:") && !File(Uri.parse(uri).path.orEmpty()).exists()) return@mapNotNull null
                PodcastPlaybackService.mediaItem(
                    item.optString("id"),
                    uri,
                    item.optString("title"),
                    item.optString("artist").takeIf { it.isNotBlank() },
                    item.optString("artwork").takeIf { it.isNotBlank() },
                )
            }
        }.getOrNull().orEmpty()
        if (items.isEmpty()) return
        controller.setMediaItems(
            items,
            queuePreferences.getInt("index", 0).coerceIn(0, items.lastIndex),
            queuePreferences.getLong("position", 0L),
        )
        controller.setPlaybackSpeed(queuePreferences.getFloat("speed", 1f))
        controller.prepare()
    }

    private fun publish(controller: Player, persist: Boolean = true, errorMessage: String? = null) {
        val id = controller.currentMediaItem?.mediaId
        val position = controller.currentPosition.coerceAtLeast(0L)
        val duration = controller.duration.takeIf { it > 0 } ?: 0L
        if (id != null) lastSnapshot = Snapshot(id, position, duration)
        _state.value = PodcastPlayerState(
            articleId = id,
            isPlaying = controller.isPlaying,
            positionMs = position,
            durationMs = duration,
            speed = controller.playbackParameters.speed,
            bufferedPositionMs = controller.bufferedPosition.coerceAtLeast(0L),
            playbackState = controller.playbackState,
            errorMessage = errorMessage,
            queueSize = controller.mediaItemCount,
            queueIndex = controller.currentMediaItemIndex,
            title = controller.mediaMetadata.title?.toString().orEmpty(),
            artist = controller.mediaMetadata.artist?.toString(),
            artworkUri = controller.mediaMetadata.artworkUri?.toString(),
            queue = (0 until controller.mediaItemCount).mapNotNull { index ->
                controller.getMediaItemAt(index).mediaMetadata.let { metadata ->
                    metadata.title?.toString()?.let { title ->
                        PodcastQueueItem(controller.getMediaItemAt(index).mediaId, title, metadata.artist?.toString())
                    }
                }
            },
            sleepTimerRemainingMs = sleepTimerEndAt?.minus(System.currentTimeMillis())?.coerceAtLeast(0L),
        )
        if (persist) persist(controller)
    }

    private fun persist(controller: Player) {
        val id = controller.currentMediaItem?.mediaId ?: return
        persistSnapshot(id, controller.currentPosition.coerceAtLeast(0L), controller.duration)
    }

    private fun persistLastSnapshot() {
        lastSnapshot?.let { persistSnapshot(it.id, it.positionMs, it.durationMs) }
    }

    private fun persistSnapshot(id: String, position: Long, duration: Long) {
        val remember = settingsProvider.get(FeaturePreferenceKeys.podcastRememberProgress) ?: true
        val markPlayed = settingsProvider.get(FeaturePreferenceKeys.podcastMarkPlayed) ?: true
        val completed = markPlayed && duration > 0 && (position >= duration * 95 / 100 || position >= duration - 60_000)
        scope.launch(Dispatchers.IO) {
            if (remember) articleDao.updatePlayback(id, position, completed)
            else if (completed) articleDao.updatePlayedStatus(id, true)
        }
        lastPersistAt = System.currentTimeMillis()
    }
}
