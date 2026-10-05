package app.inabah.android.core.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Движок для JVM-тестов: синхронный, как ExoPlayer с точки зрения контроллера. Длительность записи —
 * [durationOf] (по умолчанию 10 с); [finishItem] — элемент доиграл (дальше — следующий или конец);
 * [fail] — ошибка текущего элемента.
 */
class FakeAudioEngine(
    private val durationOf: (PlaylistItem) -> Long = { if (it is PlaylistItem.Pause) it.durationMs else DEFAULT_DURATION },
) : AudioEngine {
    private val _state = MutableStateFlow(EngineState())
    override val state: StateFlow<EngineState> = _state

    var items: List<PlaylistItem> = emptyList()
        private set
    var speed = 1f
        private set
    var position = 0L
    var prepareCount = 0
        private set

    override fun setItems(items: List<PlaylistItem>, startIndex: Int) {
        setItemsCount++
        commandsAfterFailure++
        this.items = items
        position = 0
        publish(index = startIndex, ended = false)
    }

    override fun replaceFrom(fromIndex: Int, items: List<PlaylistItem>) {
        this.items = this.items.take(fromIndex) + items
        publish()
    }

    override fun play() {
        commandsAfterFailure++
        _state.update { it.copy(playWhenReady = true) }
    }

    override fun pause() = _state.update { it.copy(playWhenReady = false) }

    override fun prepare() {
        commandsAfterFailure++
        prepareCount++
    }

    override fun seekTo(itemIndex: Int, positionMs: Long) {
        commandsAfterFailure++
        position = positionMs
        val atEnd = itemIndex == items.lastIndex && positionMs >= durationAt(itemIndex) && durationAt(itemIndex) > 0
        publish(index = itemIndex, ended = atEnd)
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
    }

    override fun clear() {
        items = emptyList()
        position = 0
        _state.value = EngineState(errorCount = _state.value.errorCount)
    }

    override fun positionMs(): Long = position

    /** Текущий элемент доиграл: дальше — следующий, на последнем — конец очереди. */
    fun finishItem() {
        val index = _state.value.itemIndex
        position = 0
        if (index < items.lastIndex) publish(index = index + 1) else {
            position = durationAt(index)
            publish(ended = true)
        }
    }

    fun fail() = _state.update { it.copy(errorCount = it.errorCount + 1, errorItem = it.itemIndex, connectionFailed = false) }

    /** Служба не подключилась: команды до неё не дошли. */
    fun failConnection() {
        commandsAfterFailure = 0
        _state.update { it.copy(playWhenReady = false, errorCount = it.errorCount + 1, errorItem = it.itemIndex, connectionFailed = true) }
    }

    /** Команды после [failConnection] — каждая в приложении снова подключалась бы к службе. */
    var commandsAfterFailure = 0
        private set
    var setItemsCount = 0
        private set

    val itemIndex: Int get() = _state.value.itemIndex
    val playWhenReady: Boolean get() = _state.value.playWhenReady

    private fun durationAt(index: Int): Long = items.getOrNull(index)?.let(durationOf) ?: 0

    private fun publish(index: Int = _state.value.itemIndex, ended: Boolean = _state.value.ended) {
        _state.update {
            it.copy(itemIndex = index, itemCount = items.size, ended = ended, durationMs = durationAt(index))
        }
    }

    companion object {
        const val DEFAULT_DURATION = 10_000L
    }
}
