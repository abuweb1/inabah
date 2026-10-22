package app.inabah.android.core.audio

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Проигрывание коротких записей для экранов «Учимся читать» - [ClipPlayer] в приложении, подделка в тестах.
 * [available] - какие записи есть в сборке (кнопка звука - только у них), [playing] - что звучит.
 */
interface ClipPlayback {
    val available: StateFlow<Set<String>>

    /** Звучащая запись (с [play] до конца, ошибки или [stop]); ничего не звучит - `null`. */
    val playing: StateFlow<String?>

    /** Сколько раз запись прервал другой звук (плеер азкаров, звонок, вынули наушники) - не доиграла. */
    val interruptions: StateFlow<Int>

    /** Проиграть [file] с начала (прошлая запись прерывается); нет такой записи - ничего. */
    fun play(file: String)

    /** Остановить звучащую запись; ничего не звучит - ничего. */
    fun stop()

    /** Записей нет: превью и экраны, которым их не передали. */
    object None : ClipPlayback {
        override val available: StateFlow<Set<String>> = MutableStateFlow(emptySet())
        override val playing: StateFlow<String?> = MutableStateFlow(null)
        override val interruptions: StateFlow<Int> = MutableStateFlow(0)
        override fun play(file: String) = Unit
        override fun stop() = Unit
    }
}

/**
 * Проиграть [file] и дождаться, пока он замолкнет (цепочка листа буквы, «Послушать алфавит»). `true` - доиграл
 * и ничего другого не звучит: можно играть следующую; `false` - звук прервали, зазвучала другая запись или
 * записи нет - дальше не играть.
 */
suspend fun ClipPlayback.playToEnd(file: String): Boolean {
    val before = interruptions.value
    play(file)
    if (playing.value != file) return false
    playing.first { it != file }
    return playing.value == null && interruptions.value == before
}

/**
 * Короткие записи «Учимся читать» (слово-пример, слог) из `assets/audio/`: нажал - прозвучало, без
 * уведомления и панели плеера азкаров (решение пользователя 2026-10-15). Вызывать с главного потока.
 * [available] - какие записи есть в сборке: элемент без записи - без значка 🔊; [playing] - что звучит.
 */
class ClipPlayer(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : ClipPlayback {
    private var player: ExoPlayer? = null
    private val _available = MutableStateFlow<Set<String>>(emptySet())
    private val _playing = MutableStateFlow<String?>(null)
    private val _interruptions = MutableStateFlow(0)

    override val available: StateFlow<Set<String>> = _available.asStateFlow()
    override val playing: StateFlow<String?> = _playing.asStateFlow()
    override val interruptions: StateFlow<Int> = _interruptions.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) _playing.value = null
        }

        // Звук забрал другой (плеер азкаров, звонок) или вынули наушники - запись не продолжится сама:
        // остановить, иначе [playing] остался бы прежним и на экране висел бы «Стоп».
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && _playing.value != null) {
                _interruptions.value += 1
                stop()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Запись не проигрывается: ${_playing.value} (${error.errorCodeName})", error)
            _playing.value = null
        }
    }

    /** Список записей в ассетах - один раз при запуске. */
    suspend fun load() {
        _available.value = withContext(ioDispatcher) { context.assets.list(AUDIO_DIR)?.toSet().orEmpty() }
    }

    override fun play(file: String) {
        if (file !in _available.value) return
        val exo = player ?: ExoPlayer.Builder(context)
            // Речь; фокус звука - на время записи (плеер азкаров встанет на паузу).
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also {
                it.addListener(listener)
                player = it
            }
        exo.setMediaItem(MediaItem.fromUri("asset:///$AUDIO_DIR/$file"))
        exo.prepare()
        exo.play()
        _playing.value = file
    }

    override fun stop() {
        player?.stop()
        _playing.value = null
    }

    private companion object {
        const val AUDIO_DIR = "audio"
        const val TAG = "ClipPlayer"
    }
}
