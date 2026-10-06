package app.inabah.android.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Движок для UI-тестов: без звука и службы, команды сразу меняют состояние. */
class TestAudioEngine : AudioEngine {
    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state

    var items: List<PlaylistItem> = emptyList()
        private set

    override fun setItems(items: List<PlaylistItem>, startIndex: Int) {
        this.items = items
        _state.update { it.copy(itemIndex = startIndex, itemCount = items.size, ended = false, durationMs = DURATION) }
    }

    override fun replaceFrom(fromIndex: Int, items: List<PlaylistItem>) {
        this.items = this.items.take(fromIndex) + items
    }

    override fun play() = _state.update { it.copy(playWhenReady = true) }

    override fun pause() = _state.update { it.copy(playWhenReady = false) }

    override fun prepare() = Unit

    /** Позиция стоит на месте (звука нет), но помнит перемотку: опрос плеера раз в 250 мс её не затрёт. */
    private var position = 0L

    override fun seekTo(itemIndex: Int, positionMs: Long) {
        position = positionMs
        _state.update { it.copy(itemIndex = itemIndex, ended = false) }
    }

    override fun setSpeed(speed: Float) = Unit

    override fun clear() {
        items = emptyList()
        position = 0
        _state.value = EngineState()
    }

    override fun positionMs(): Long = position

    private companion object {
        const val DURATION = 30_000L
    }
}
