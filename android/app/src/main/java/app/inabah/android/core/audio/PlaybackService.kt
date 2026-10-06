package app.inabah.android.core.audio

import android.app.PendingIntent
import android.content.Intent
import android.os.Process
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.inabah.android.R
import app.inabah.android.app.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Служба воспроизведения (`docs/android/04-audio.md`, «Реализация на Media3»): один ExoPlayer и
 * MediaSession — фон, медиауведомление, экран блокировки, аудиофокус. Очередь приходит от приложения
 * развёрнутой ([expandPlaylist]): повторы и паузы — элементы, ⏮ ⏭ — по зикрам ([ZikrNavigationPlayer]).
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            // Речь, звук и в беззвучном режиме (iOS `.playback` + `.spokenAudio`); фокус — сам ExoPlayer.
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                /* handleAudioFocus = */ true,
            )
            // Отключили наушники или Bluetooth — пауза.
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer.addListener(SingleTrackInterruptionListener(exoPlayer))
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )
        session = MediaSession.Builder(this, ZikrNavigationPlayer(exoPlayer))
            .setSessionActivity(openPlayerIntent())
            .setCallback(SessionCallback())
            .build()
    }

    // Смахнули из «Недавних»: onTaskRemoved по умолчанию (Media3) ставит на паузу и останавливает
    // службу, если звук не идёт. Остановиться она может, только когда приложение отпустило свой
    // MediaController — его отпускает MediaControllerEngine.clear (✕ в плеере).
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Нажатие на уведомление — приложение с открытым плеером на разделе записи. */
    private fun openPlayerIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_PLAYER)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private inner class SessionCallback : MediaSession.Callback {
        /**
         * Управляют плеером только само приложение и доверенные системные контроллеры (уведомление,
         * экран блокировки, Bluetooth); посторонние приложения — нет.
         */
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            // Своё приложение — по uid (имя пакета Media3 может не суметь проверить).
            val allowed = controller.uid == Process.myUid() || controller.isTrusted ||
                session.isMediaNotificationController(controller)
            if (!allowed) {
                Log.w(TAG, "Отклонено подключение ${controller.packageName}")
                return MediaSession.ConnectionResult.reject()
            }
            return super.onConnect(session, controller)
        }

        /** Адрес записи передаётся в `requestMetadata` (сам адрес в другие процессы не уходит); только ассеты. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri?.takeIf { it.scheme == ASSET_SCHEME }
                    ?: return Futures.immediateFailedFuture(IllegalArgumentException("Не ассет: ${item.mediaId}"))
                item.buildUpon().setUri(uri).build()
            }
            return Futures.immediateFuture(resolved.toMutableList())
        }
    }

    /**
     * Звонок во время одиночной записи (один элемент в очереди): ExoPlayer после звонка продолжил бы сам,
     * а одиночная запись, как в iOS, сама не заигрывает — ставим на паузу. Плейлист продолжает.
     */
    private class SingleTrackInterruptionListener(private val player: Player) : Player.Listener {
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS &&
                player.mediaItemCount == 1
            ) {
                player.pause()
            }
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        const val ASSET_SCHEME = "asset"
    }
}
