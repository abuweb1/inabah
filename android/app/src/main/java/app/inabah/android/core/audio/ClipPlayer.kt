package app.inabah.android.core.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Короткие записи «Учимся читать» (слово-пример, слог) из `assets/audio/`: нажал - прозвучало, без
 * уведомления и панели плеера азкаров (решение пользователя 2026-10-15). Вызывать с главного потока.
 * [available] - какие записи есть в сборке: элемент без записи - без значка 🔊.
 */
class ClipPlayer(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private var player: ExoPlayer? = null
    private val _available = MutableStateFlow<Set<String>>(emptySet())

    val available: StateFlow<Set<String>> = _available.asStateFlow()

    /** Список записей в ассетах - один раз при запуске. */
    suspend fun load() {
        _available.value = withContext(ioDispatcher) { context.assets.list(AUDIO_DIR)?.toSet().orEmpty() }
    }

    /** Проиграть [file] с начала (прошлая запись прерывается); нет такой записи - ничего. */
    fun play(file: String) {
        if (file !in _available.value) return
        val exo = player ?: ExoPlayer.Builder(context)
            // Речь; фокус звука - на время записи (плеер азкаров встанет на паузу).
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true,
            )
            .build()
            .also { player = it }
        exo.setMediaItem(MediaItem.fromUri("asset:///$AUDIO_DIR/$file"))
        exo.prepare()
        exo.play()
    }

    private companion object {
        const val AUDIO_DIR = "audio"
    }
}
