package app.inabah.android.core.audio

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Одиночная запись (▶ на карточке) или «Прослушать все». */
enum class PlaybackMode { Single, Playlist }

/**
 * Состояние плеера для экрана. [isPlaying] — намерение пользователя (пауза во время загрузки
 * отменяет старт), [isFinished] — очередь доиграна, [hasError] — запись не воспроизвелась.
 */
data class PlayerState(
    val mode: PlaybackMode = PlaybackMode.Single,
    val playlistId: String? = null,
    val tracks: List<AudioTrack> = emptyList(),
    val zikrIndex: Int = 0,
    val repetition: Int = 1,
    val isPlaying: Boolean = false,
    val isFinished: Boolean = false,
    val durationMs: Long = 0,
    val isPanelVisible: Boolean = false,
    val hasError: Boolean = false,
) {
    val track: AudioTrack? get() = tracks.getOrNull(zikrIndex)
    val count: Int get() = tracks.size
    val canGoPrevious: Boolean get() = mode == PlaybackMode.Playlist && zikrIndex > 0
    val canGoNext: Boolean get() = mode == PlaybackMode.Playlist && zikrIndex < tracks.lastIndex

    /** Запись выбрана в плеере и не доиграла (карточка зикра — «волна», «Открыть плеер»). */
    fun isActive(trackId: String): Boolean = track?.id == trackId && !isFinished

    /** Плейлист [id] идёт или на паузе («Прослушать все» — «Открыть плеер»). */
    fun isPlaylistActive(id: String): Boolean =
        mode == PlaybackMode.Playlist && playlistId == id && track != null && !isFinished
}

/**
 * Плеер азкаров (iOS `AudioPlayerController`, правила — `docs/android/04-audio.md`): команды экрана,
 * состояние для него и позиция для полосы. Очередь отдаётся движку развёрнутой ([expandPlaylist]):
 * повторы, паузы и переходы делает он, контроллер отражает, где он сейчас. Вызывать с главного потока;
 * отражение движка и опрос позиции идут, пока работает [run] (запускает `AppContainer`).
 */
class AudioPlayerController(private val engine: AudioEngine) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)

    /** Позиция в текущей записи, мс; обновляется раз в 250 мс, пока звук идёт, панель видна и приложение на экране. */
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val inBackground = MutableStateFlow(false)
    private var items: List<PlaylistItem> = emptyList()
    private var pauseSeconds = 0.0
    private var rate = 1f
    private var handledErrors = engine.state.value.errorCount

    /** Ошибку показываем, пока движок не ушёл с элемента, на котором её показали. */
    private var errorShownAt = NO_ITEM

    /** Служба не подключилась — очередь до неё не дошла: «продолжить» отдаёт её заново. */
    private var connectionLost = false

    /** Длительности уже звучавших записей: в паузе перед зикром — его длительность, а не 0. */
    private val durations = mutableMapOf<String, Long>()

    /** Отражение движка и опрос позиции; живёт, пока живёт вызвавшая область. */
    suspend fun run(): Unit = coroutineScope {
        launch { engine.state.collect(::onEngine) }
        launch {
            combine(_state, inBackground) { state, background -> state.isPlaying && state.isPanelVisible && !background }
                .distinctUntilChanged()
                .collectLatest { ticking ->
                    refreshPosition()
                    while (ticking) {
                        delay(TICK_MILLIS)
                        refreshPosition()
                    }
                }
        }
    }

    // region Запуск

    /** ▶ на карточке: та же незаконченная запись без ошибки — только показать плеер, иначе — играть её. */
    fun play(track: AudioTrack) {
        val current = _state.value
        if (current.mode == PlaybackMode.Single && current.track?.id == track.id && !current.isFinished && !current.hasError) {
            showPanel()
            return
        }
        start(PlaybackMode.Single, playlistId = null, listOf(track), pause = 0.0, speed = 1f)
    }

    /** «Слушать»: этот плейлист уже идёт — показать плеер, иначе — весь раздел с первого зикра. */
    fun playAll(id: String, tracks: List<AudioTrack>, rate: Float, pauseSeconds: Double) {
        if (_state.value.isPlaylistActive(id)) {
            showPanel()
            return
        }
        if (tracks.isEmpty()) return
        start(PlaybackMode.Playlist, id, tracks, pauseSeconds, rate)
    }

    /** Скорость и пауза с карточки — на лету, если плейлист [id] идёт; звучащий элемент не прерывается. */
    fun updatePlaylist(id: String, rate: Float, pauseSeconds: Double) {
        val current = _state.value
        if (current.mode != PlaybackMode.Playlist || current.playlistId != id || items.isEmpty()) return
        if (rate == this.rate && pauseSeconds == this.pauseSeconds) return
        val index = engine.state.value.itemIndex.coerceIn(0, items.lastIndex)
        val updated = expandPlaylist(current.tracks, pauseSeconds, rate)
        // Хвост после звучащего элемента — из новой раскладки; сама пауза, если звучит она, доигрывает старую длину.
        val tail = when (val playing = items[index]) {
            is PlaylistItem.Track -> {
                val same = updated.indexOfFirst {
                    it is PlaylistItem.Track && it.zikrIndex == playing.zikrIndex && it.repetition == playing.repetition
                }
                updated.drop(same + 1)
            }
            is PlaylistItem.Pause -> updated.drop(updated.firstItemOf(playing.zikrIndex) ?: updated.size)
        }
        items = items.take(index + 1) + tail
        engine.replaceFrom(index + 1, tail)
        if (rate != this.rate) engine.setSpeed(rate)
        this.rate = rate
        this.pauseSeconds = pauseSeconds
    }

    private fun start(mode: PlaybackMode, playlistId: String?, tracks: List<AudioTrack>, pause: Double, speed: Float) {
        items = expandPlaylist(tracks, pause, speed)
        pauseSeconds = pause
        rate = speed
        errorShownAt = NO_ITEM
        connectionLost = false
        handledErrors = engine.state.value.errorCount
        engine.setItems(items, startIndex = 0)
        engine.setSpeed(speed)
        engine.play()
        _positionMs.value = 0
        _state.value = PlayerState(
            mode = mode,
            playlistId = playlistId,
            tracks = tracks,
            isPlaying = true,
            durationMs = durations[tracks.first().id] ?: 0,
            isPanelVisible = true,
        )
    }

    // endregion

    // region Управление

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else resume()
    }

    fun pause() {
        engine.pause()
        _state.update { it.copy(isPlaying = false) }
    }

    /**
     * Продолжить: после ошибки одиночной записи — загрузить её заново, после окончания — текущий зикр
     * сначала. В плейлисте битая запись уже пропущена и звучит следующая — просто продолжить её.
     */
    fun resume() {
        val current = _state.value
        if (current.track == null) return
        when {
            connectionLost -> {
                connectionLost = false
                errorShownAt = NO_ITEM
                engine.setItems(items, currentZikrStart())
                engine.setSpeed(rate)
                engine.play()
                _positionMs.value = 0
            }
            current.hasError && current.mode == PlaybackMode.Single -> restartZikr(prepare = true)
            current.isFinished -> restartZikr(prepare = false)
            else -> engine.play()
        }
        _state.update { it.copy(isPlaying = true, isFinished = false, hasError = false) }
    }

    /** Стоп ■: в начало зикра с первого повтора, плеер остаётся; ▶ — с начала. */
    fun stop() {
        if (_state.value.track == null) return
        engine.pause()
        engine.seekTo(currentZikrStart(), 0)
        _positionMs.value = 0
        _state.update { it.copy(isPlaying = false, isFinished = false, repetition = 1) }
    }

    /** «Сначала» ↺: текущий зикр с первого повтора, играть. */
    fun restart() {
        if (_state.value.track == null) return
        restartZikr(prepare = _state.value.hasError)
        _state.update { it.copy(isPlaying = true, isFinished = false, hasError = false, repetition = 1) }
    }

    private fun restartZikr(prepare: Boolean) {
        engine.seekTo(currentZikrStart(), 0)
        if (prepare) engine.prepare()
        engine.play()
        errorShownAt = NO_ITEM
        _positionMs.value = 0
    }

    /** −10 / +10 с в границах записи; до самого конца во время игры — запись доиграла (движок перейдёт дальше). */
    fun skip(deltaMs: Long) {
        if (_state.value.track == null) return
        seek(engine.positionMs() + deltaMs)
    }

    /** Перемотка (бегунок — при отпускании); назад после окончания снимает «доиграла». */
    fun seek(positionMs: Long) {
        val current = _state.value
        if (current.track == null) return
        val target = positionMs.coerceIn(0, current.durationMs.coerceAtLeast(0))
        // В паузе между зикрами полоса — уже следующей записи: перематывается она, а не тишина.
        val index = engine.state.value.itemIndex
        val itemIndex = if (items.getOrNull(index) is PlaylistItem.Pause) index + 1 else index
        engine.seekTo(itemIndex, target)
        _positionMs.value = target
        if (current.isFinished && target < current.durationMs) _state.update { it.copy(isFinished = false) }
    }

    /** ⌄ — следующий зикр с первого повтора (только плейлист, в границах). */
    fun next() = jumpZikr(step = 1)

    /** ⌃ — предыдущий зикр с первого повтора. */
    fun previous() = jumpZikr(step = -1)

    private fun jumpZikr(step: Int) {
        val current = _state.value
        if (current.mode != PlaybackMode.Playlist) return
        val target = items.neighbourZikrItem(engine.state.value.itemIndex, step) ?: return
        engine.seekTo(target, 0)
        if (current.hasError) engine.prepare()
        _positionMs.value = 0
    }

    private fun currentZikrStart(): Int = items.firstItemOf(_state.value.zikrIndex) ?: 0

    // endregion

    // region Панель и жизненный цикл

    fun showPanel() {
        if (_state.value.track == null) return
        _state.update { it.copy(isPanelVisible = true) }
        refreshPosition()
    }

    /** Скрыть 👁 / свайп вниз: звук играет дальше. */
    fun hidePanel() = _state.update { it.copy(isPanelVisible = false) }

    /** Закрыть ✕: стоп, очередь пуста, панель скрыта. */
    fun close() {
        engine.clear()
        items = emptyList()
        errorShownAt = NO_ITEM
        connectionLost = false
        _positionMs.value = 0
        _state.value = PlayerState()
    }

    /** Приложение ушло в фон: одиночная запись — на паузу (как в iOS), плейлист играет дальше. */
    fun onBackground() {
        inBackground.value = true
        val current = _state.value
        if (current.mode == PlaybackMode.Single && current.isPlaying) pause()
    }

    fun onForeground() {
        inBackground.value = false
        refreshPosition()
    }

    // endregion

    private fun refreshPosition() {
        val item = items.getOrNull(engine.state.value.itemIndex)
        // В паузе между зикрами под полосой — начало следующей записи.
        _positionMs.value = if (item is PlaylistItem.Track) engine.positionMs() else 0
    }

    private fun onEngine(engineState: EngineState) {
        if (items.isEmpty()) return
        val index = engineState.itemIndex.coerceIn(0, items.lastIndex)
        val item = items[index]
        val trackId = _state.value.tracks.getOrNull(item.zikrIndex)?.id
        if (item is PlaylistItem.Track && engineState.durationMs > 0) durations[item.track.id] = engineState.durationMs
        if (engineState.errorCount != handledErrors) {
            handledErrors = engineState.errorCount
            val failedAt = engineState.errorItem.takeIf { it in items.indices } ?: index
            if (engineState.connectionFailed) onConnectionLost(failedAt) else onError(failedAt)
            return
        }
        if (errorShownAt != NO_ITEM && index != errorShownAt) errorShownAt = NO_ITEM
        val ended = engineState.ended
        // Доиграла — на паузе, как в iOS: перемотка назад не запускает звук сама.
        if (ended && engineState.playWhenReady) engine.pause()
        // Пауза с экрана блокировки, отключение наушников, звонок — приходят от движка.
        val stoppedByError = _state.value.mode == PlaybackMode.Single && errorShownAt != NO_ITEM
        _state.update {
            it.copy(
                zikrIndex = item.zikrIndex,
                repetition = items.repetitionAt(index),
                isPlaying = engineState.playWhenReady && !ended && !stoppedByError,
                isFinished = ended,
                durationMs = trackId?.let(durations::get) ?: 0,
                hasError = errorShownAt != NO_ITEM,
            )
        }
        if (ended) _positionMs.value = _state.value.durationMs
    }

    /** В плейлисте битая запись пропускается (к следующему зикру без паузы), в одиночном режиме — стоп. */
    private fun onError(itemIndex: Int) {
        val skipTo = if (_state.value.mode == PlaybackMode.Playlist) items.neighbourZikrItem(itemIndex, step = 1) else null
        if (skipTo != null) {
            errorShownAt = skipTo
            engine.seekTo(skipTo, 0)
            engine.prepare()
            engine.play()
            _positionMs.value = 0
            _state.update {
                it.copy(zikrIndex = items.zikrAt(skipTo), repetition = 1, hasError = true, isFinished = false)
            }
        } else {
            errorShownAt = itemIndex
            engine.pause()
            _state.update { it.copy(zikrIndex = items.zikrAt(itemIndex), isPlaying = false, hasError = true) }
        }
    }

    /**
     * Нет связи со службой: пропуск к следующему зикру не поможет, а каждая команда движку подключалась бы
     * снова. Только показать ошибку; ▶ («продолжить» после ошибки) — новая попытка.
     */
    private fun onConnectionLost(itemIndex: Int) {
        errorShownAt = itemIndex
        connectionLost = true
        _state.update { it.copy(isPlaying = false, hasError = true) }
    }

    private companion object {
        const val TICK_MILLIS = 250L
        const val NO_ITEM = -1
    }
}
