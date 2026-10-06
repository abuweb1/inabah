package app.inabah.android.core.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Движок воспроизведения (iOS `AudioEngine`): очередь элементов, переходы между ними — сам.
 * В приложении — `MediaControllerEngine` (служба Media3 с ExoPlayer), в JVM-тестах — подделка.
 * Вызывать с главного потока.
 */
interface AudioEngine {
    val state: StateFlow<EngineState>

    /** Новая очередь с элемента [startIndex] с начала; играть — отдельной командой [play]. */
    fun setItems(items: List<PlaylistItem>, startIndex: Int)

    /** Заменить элементы с [fromIndex] до конца (смена паузы и скорости на лету); текущий — не трогать. */
    fun replaceFrom(fromIndex: Int, items: List<PlaylistItem>)

    fun play()

    fun pause()

    /** Заново подготовить текущий элемент после ошибки. */
    fun prepare()

    fun seekTo(itemIndex: Int, positionMs: Long)

    fun setSpeed(speed: Float)

    /** Остановить и очистить очередь. */
    fun clear()

    /** Позиция в текущем элементе, мс — без ожидания (опрос раз в 250 мс). */
    fun positionMs(): Long
}

/**
 * Состояние движка. [playWhenReady] — играть, как только готово (намерение, а не «звук идёт сейчас»);
 * [ended] — очередь доиграна; [errorCount] растёт на каждой новой ошибке ([errorItem] — у какого
 * элемента), чтобы повторная ошибка того же элемента тоже была замечена.
 */
data class EngineState(
    val playWhenReady: Boolean = false,
    val itemIndex: Int = 0,
    val itemCount: Int = 0,
    val ended: Boolean = false,
    /** Длительность текущего элемента, мс; неизвестна — 0. */
    val durationMs: Long = 0,
    val errorCount: Int = 0,
    val errorItem: Int = -1,
    /**
     * Последняя ошибка — нет связи со службой, а не битая запись: пропускать к следующему зикру
     * бессмысленно (и каждая команда переподключалась бы снова); повтор — только по действию пользователя.
     */
    val connectionFailed: Boolean = false,
)
