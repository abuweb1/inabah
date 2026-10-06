package app.inabah.android.core.audio

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * [AudioEngine] над службой [PlaybackService] через `MediaController`. Подключается при первой команде
 * (служба запускается только когда звук нужен); команды до подключения копятся и выполняются по порядку.
 * Состояние меняется сразу на команду (контроллер Media3 тоже показывает её до ответа службы) и
 * уточняется событиями плеера.
 */
class MediaControllerEngine(private val context: Context) : AudioEngine {
    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var connecting: ListenableFuture<MediaController>? = null
    private val pending = ArrayDeque<(MediaController) -> Unit>()
    private var errorCount = 0
    private var errorItem = -1

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Ошибка воспроизведения: ${error.errorCodeName}", error)
            errorCount++
            errorItem = controller?.currentMediaItemIndex ?: -1
            controller?.let(::publish)
        }
    }

    override fun setItems(items: List<PlaylistItem>, startIndex: Int) {
        _state.update { it.copy(itemIndex = startIndex, itemCount = items.size, ended = false, durationMs = 0) }
        val mediaItems = items.map(::toMediaItem)
        withController {
            it.setMediaItems(mediaItems, startIndex, 0)
            it.prepare()
        }
    }

    override fun replaceFrom(fromIndex: Int, items: List<PlaylistItem>) {
        val mediaItems = items.map(::toMediaItem)
        withController { it.replaceMediaItems(fromIndex, it.mediaItemCount, mediaItems) }
    }

    override fun play() {
        _state.update { it.copy(playWhenReady = true) }
        withController { it.play() }
    }

    /** Не подключены — и ставить на паузу нечего: не будить службу (и не зацикливать переподключение). */
    override fun pause() {
        _state.update { it.copy(playWhenReady = false) }
        val connected = controller
        when {
            connected != null -> connected.pause()
            // Подключение уже идёт (запуск и сразу пауза) — пауза встанет в очередь после запуска.
            connecting != null -> pending.addLast { it.pause() }
        }
    }

    override fun prepare() = withController { it.prepare() }

    override fun seekTo(itemIndex: Int, positionMs: Long) {
        _state.update { it.copy(itemIndex = itemIndex, ended = false) }
        withController { it.seekTo(itemIndex, positionMs) }
    }

    override fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

    /**
     * Остановить и отпустить контроллер: пока приложение держит его, служба не может остановиться
     * (смахнули из «Недавних» — уведомление осталось бы). Следующий звук подключит заново.
     */
    override fun clear() {
        _state.update { EngineState(errorCount = it.errorCount, errorItem = it.errorItem) }
        pending.clear()
        // ✕ во время подключения: подключение отменить, иначе контроллер подключится позже и будет
        // держать службу (аудит 2026-10-06).
        connecting?.let { MediaController.releaseFuture(it) }
        connecting = null
        val connected = controller ?: return
        connected.stop()
        connected.clearMediaItems()
        connected.removeListener(listener)
        connected.release()
        controller = null
    }

    override fun positionMs(): Long = controller?.currentPosition ?: 0

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let {
            action(it)
            return
        }
        pending.addLast(action)
        connect()
    }

    private fun connect() {
        if (connecting != null) return
        val future = try {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            MediaController.Builder(context, token).buildAsync()
        } catch (failure: IllegalArgumentException) {
            // Служба не объявлена в манифесте (пока записей нет, она только в debug-сборке): со своими
            // записями без переноса блока <service> в main — ошибка плеера, а не падение (ревью 2026-10-06).
            onConnectionFailed(failure)
            return
        }
        connecting = future
        future.addListener(
            {
                // Подключение отменили ([clear]) или уже начато новое — это не нужно.
                if (connecting !== future) {
                    MediaController.releaseFuture(future)
                    return@addListener
                }
                connecting = null
                val connected = try {
                    future.get()
                } catch (failure: Exception) {
                    onConnectionFailed(failure)
                    return@addListener
                }
                controller = connected
                connected.addListener(listener)
                while (pending.isNotEmpty()) pending.removeFirst()(connected)
                publish(connected)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /** Нет связи со службой: сообщить плееру ошибкой, а не молча стоять «играет» без звука. */
    private fun onConnectionFailed(failure: Exception) {
        Log.e(TAG, "Нет связи со службой воспроизведения", failure)
        pending.clear()
        errorCount++
        _state.update {
            it.copy(playWhenReady = false, errorCount = errorCount, errorItem = it.itemIndex, connectionFailed = true)
        }
    }

    private fun publish(player: Player) {
        _state.value = EngineState(
            playWhenReady = player.playWhenReady,
            itemIndex = player.currentMediaItemIndex,
            itemCount = player.mediaItemCount,
            ended = player.playbackState == Player.STATE_ENDED,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            errorCount = errorCount,
            errorItem = errorItem,
        )
    }

    private fun toMediaItem(item: PlaylistItem): MediaItem {
        val (track, uri) = when (item) {
            is PlaylistItem.Track -> item.track to "${PlaybackService.ASSET_SCHEME}:///audio/${item.track.assetFile}"
            // В паузе экран блокировки показывает следующий зикр; тишина — отрезок общего файла.
            is PlaylistItem.Pause -> item.next to SILENCE_URI
        }
        val builder = MediaItem.Builder()
            .setMediaId(item.mediaId)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri.toUri()).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.subtitle)
                    .setAlbumTitle(track.category)
                    .build(),
            )
        if (item is PlaylistItem.Pause) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setEndPositionMs(item.durationMs).build(),
            )
        }
        return builder.build()
    }

    private companion object {
        const val TAG = "MediaControllerEngine"

        /** 8 с тишины (WAV 8 кГц): самая длинная пауза — 5 с × 1,5. */
        const val SILENCE_URI = "${PlaybackService.ASSET_SCHEME}:///silence/silence.wav"
    }
}
