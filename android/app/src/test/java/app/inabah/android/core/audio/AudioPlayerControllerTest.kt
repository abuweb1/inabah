@file:OptIn(ExperimentalCoroutinesApi::class)

package app.inabah.android.core.audio

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Аудиоплеер (iOS «Аудиоплеер», `docs/android/04-audio.md`). Не перенесены «доиграла на паузе →
 * следующий шаг после продолжить» и «поздний результат старой загрузки отбрасывается»: очередь
 * развёрнута в движке, ExoPlayer не доигрывает элемент на паузе и меняет очередь сразу, без гонок.
 */
class AudioPlayerControllerTest {
    private val engine = FakeAudioEngine()
    private val player = AudioPlayerController(engine)
    private val state get() = player.state.value
    private val playlist = listOf(track(1), track(2, repeats = 3), track(3))

    private fun test(body: suspend TestScope.() -> Unit) = runTest {
        backgroundScope.launch { player.run() }
        runCurrent()
        body()
    }

    private fun TestScope.playAll(rate: Float = 1f, pause: Double = 1.0) {
        player.playAll("azkar.morning", playlist, rate, pause)
        runCurrent()
    }

    @Test
    fun `▶ запускает запись и сразу открывает плеер`() = test {
        player.play(track(1))
        assertTrue(state.isPanelVisible)
        assertTrue(state.isPlaying)
        assertTrue(engine.playWhenReady)
        assertEquals("azkar.morning.1", state.track?.id)
    }

    @Test
    fun `Пауза сразу после запуска отменяет старт`() = test {
        player.play(track(1))
        player.pause()
        runCurrent()
        assertFalse(engine.playWhenReady)
        assertFalse(state.isPlaying)
    }

    @Test
    fun `Закрыть сразу после запуска — звук не начинается, очередь пуста`() = test {
        player.play(track(1))
        player.close()
        runCurrent()
        assertTrue(engine.items.isEmpty())
        assertNull(state.track)
        assertFalse(state.isPanelVisible)
    }

    @Test
    fun `Пауза и продолжение`() = test {
        player.play(track(1))
        player.togglePlayPause()
        runCurrent()
        assertFalse(state.isPlaying)
        player.togglePlayPause()
        runCurrent()
        assertTrue(state.isPlaying && engine.playWhenReady)
    }

    @Test
    fun `Повторный ▶ на незаконченной записи только показывает плеер`() = test {
        player.play(track(1))
        engine.position = 4_000
        player.hidePanel()
        player.play(track(1))
        assertTrue(state.isPanelVisible)
        assertEquals(4_000, engine.position)
    }

    @Test
    fun `Повторный ▶ после окончания играет заново`() = test {
        player.play(track(1))
        engine.finishItem()
        runCurrent()
        assertTrue(state.isFinished)
        player.play(track(1))
        runCurrent()
        assertFalse(state.isFinished)
        assertTrue(engine.playWhenReady)
        assertEquals(0, engine.position)
    }

    @Test
    fun `▶ на другом зикре заменяет текущую запись`() = test {
        player.play(track(1))
        player.play(track(2))
        runCurrent()
        assertEquals("azkar.morning.2", state.track?.id)
        assertEquals(1, engine.items.size)
    }

    @Test
    fun `−10 и +10 секунд в границах записи`() = test {
        player.play(track(1))
        runCurrent()
        engine.position = 4_000
        player.skip(-10_000)
        assertEquals(0, engine.position)
        player.skip(3_000)
        assertEquals(3_000, engine.position)
    }

    @Test
    fun `Перемотка в конец во время игры — запись доиграла`() = test {
        player.play(track(1))
        runCurrent()
        player.seek(FakeAudioEngine.DEFAULT_DURATION)
        runCurrent()
        assertTrue(state.isFinished)
        assertFalse(state.isPlaying)
    }

    @Test
    fun `Перемотка назад после окончания снимает «доиграла» и не запускает звук`() = test {
        player.play(track(1))
        runCurrent()
        engine.finishItem()
        runCurrent()
        player.seek(2_000)
        runCurrent()
        assertFalse(state.isFinished)
        assertFalse(engine.playWhenReady)
    }

    @Test
    fun `Стоп — позиция в начало, плеер открыт, ▶ — с начала`() = test {
        player.play(track(1))
        engine.position = 5_000
        player.stop()
        runCurrent()
        assertEquals(0, engine.position)
        assertTrue(state.isPanelVisible)
        assertFalse(state.isPlaying)
        player.togglePlayPause()
        runCurrent()
        assertTrue(engine.playWhenReady)
        assertEquals(0, engine.position)
    }

    @Test
    fun `Стоп сбрасывает номер повтора`() = test {
        playAll(pause = 0.0)
        player.next()
        engine.finishItem()
        runCurrent()
        assertEquals(2, state.repetition)
        player.stop()
        runCurrent()
        assertEquals(1, state.repetition)
        assertEquals(engine.items.firstItemOf(1), engine.itemIndex)
    }

    @Test
    fun `«Сначала» перезапускает текущий зикр с первого повтора`() = test {
        playAll(pause = 0.0)
        player.next()
        engine.finishItem()
        engine.position = 3_000
        runCurrent()
        player.restart()
        runCurrent()
        assertEquals(1, state.repetition)
        assertEquals(0, engine.position)
        assertTrue(engine.playWhenReady)
    }

    @Test
    fun `Скрыть — звук играет`() = test {
        player.play(track(1))
        player.hidePanel()
        assertFalse(state.isPanelVisible)
        assertTrue(engine.playWhenReady)
    }

    @Test
    fun `Фон — одиночная запись на паузе, плейлист играет`() = test {
        player.play(track(1))
        player.onBackground()
        runCurrent()
        assertFalse(engine.playWhenReady)

        player.onForeground()
        playAll()
        player.onBackground()
        runCurrent()
        assertTrue(engine.playWhenReady)
    }

    @Test
    fun `Ошибка одиночной записи — не играет, показана ошибка`() = test {
        player.play(track(1))
        runCurrent()
        engine.fail()
        runCurrent()
        assertTrue(state.hasError)
        assertFalse(state.isPlaying)
        assertFalse(engine.playWhenReady)
    }

    @Test
    fun `После ошибки «продолжить» загружает запись заново`() = test {
        player.play(track(1))
        runCurrent()
        engine.fail()
        runCurrent()
        player.togglePlayPause()
        runCurrent()
        assertEquals(1, engine.prepareCount)
        assertTrue(state.isPlaying)
        assertFalse(state.hasError)
    }

    @Test
    fun `В плейлисте битая запись пропускается, ошибка показана`() = test {
        playAll()
        engine.fail()
        runCurrent()
        assertEquals(1, state.zikrIndex)
        assertEquals(engine.items.firstItemOf(1), engine.itemIndex)
        assertTrue(state.hasError)
        assertTrue(state.isPlaying)
        // Следующий зикр — уже без ошибки.
        engine.finishItem()
        engine.finishItem()
        engine.finishItem()
        runCurrent()
        assertEquals(2, state.zikrIndex)
        assertFalse(state.hasError)
    }

    @Test
    fun `В плейлисте после пропуска битой записи «продолжить» не начинает следующий зикр сначала`() = test {
        playAll()
        engine.fail()
        runCurrent()
        engine.position = 4_000
        player.pause()
        player.togglePlayPause()
        runCurrent()
        assertEquals(4_000, engine.position)
        assertEquals(1, engine.prepareCount)
    }

    @Test
    fun `Нет связи со службой — ошибка без пропуска и переподключений, «продолжить» отдаёт очередь заново`() = test {
        playAll()
        engine.failConnection()
        runCurrent()
        assertTrue(state.hasError)
        assertFalse(state.isPlaying)
        assertEquals(0, engine.commandsAfterFailure)

        player.togglePlayPause()
        runCurrent()
        assertEquals(2, engine.setItemsCount)
        assertTrue(engine.playWhenReady)
        assertFalse(state.hasError)
    }

    @Test
    fun `Перемотка в паузе между зикрами — по следующей записи, а не по тишине`() = test {
        playAll()
        // Второй зикр уже звучал — его длительность известна и в паузе перед ним.
        player.next()
        runCurrent()
        player.previous()
        engine.finishItem() // зикр 1 → пауза
        runCurrent()
        assertTrue(engine.items[engine.itemIndex] is PlaylistItem.Pause)
        player.seek(5_000)
        assertEquals(2, engine.itemIndex)
        assertEquals(5_000, engine.position)
    }

    @Test
    fun `Повторы по числу раз, затем пауза и следующий зикр`() = test {
        playAll()
        engine.finishItem() // зикр 1 → пауза
        runCurrent()
        assertEquals(1, state.zikrIndex)
        assertEquals(0, player.positionMs.value)
        engine.finishItem() // пауза → зикр 2, повтор 1
        engine.finishItem() // повтор 2
        runCurrent()
        assertEquals(2, state.repetition)
        assertTrue(engine.items[engine.itemIndex] is PlaylistItem.Track)
    }

    @Test
    fun `Конец очереди — плейлист завершён и не активен`() = test {
        playAll(pause = 0.0)
        repeat(engine.items.size) { engine.finishItem() }
        runCurrent()
        assertTrue(state.isFinished)
        assertFalse(state.isPlaying)
        assertFalse(state.isPlaylistActive("azkar.morning"))
    }

    @Test
    fun `Следующий и предыдущий — в границах очереди`() = test {
        playAll()
        assertFalse(state.canGoPrevious)
        player.previous()
        runCurrent()
        assertEquals(0, state.zikrIndex)
        player.next()
        player.next()
        runCurrent()
        assertEquals(2, state.zikrIndex)
        assertFalse(state.canGoNext)
        player.next()
        runCurrent()
        assertEquals(2, state.zikrIndex)
    }

    @Test
    fun `В одиночном режиме нет перехода к соседним`() = test {
        player.play(track(1))
        assertFalse(state.canGoNext || state.canGoPrevious)
        player.next()
        runCurrent()
        assertEquals(0, engine.itemIndex)
    }

    @Test
    fun `Скорость передаётся движку, одиночная запись — всегда 1×`() = test {
        playAll(rate = 1.5f)
        assertEquals(1.5f, engine.speed)
        player.play(track(1))
        assertEquals(1f, engine.speed)
    }

    @Test
    fun `Плейлист распознаётся по идентификатору — «Слушать» только показывает плеер`() = test {
        playAll()
        player.next()
        runCurrent()
        player.hidePanel()
        playAll()
        assertTrue(state.isPanelVisible)
        assertEquals(1, state.zikrIndex)
        assertTrue(state.isPlaylistActive("azkar.morning"))
        assertFalse(state.isPlaylistActive("azkar.evening"))
    }

    @Test
    fun `Смена паузы на лету — меняется хвост очереди, звучащий элемент не трогается`() = test {
        playAll(pause = 1.0)
        player.updatePlaylist("azkar.morning", rate = 1f, pauseSeconds = 5.0)
        assertEquals(0, engine.itemIndex)
        val pauses = engine.items.filterIsInstance<PlaylistItem.Pause>().map { it.durationMs }
        assertEquals(listOf(5_000L, 5_000L), pauses)

        player.updatePlaylist("azkar.morning", rate = 1f, pauseSeconds = 0.0)
        assertTrue(engine.items.none { it is PlaylistItem.Pause })
        assertEquals(playlist.sumOf { it.repeats }, engine.items.size)
    }

    @Test
    fun `Смена скорости на лету — в движок и в длительность пауз`() = test {
        playAll(rate = 1f, pause = 3.0)
        player.updatePlaylist("azkar.morning", rate = 1.5f, pauseSeconds = 3.0)
        assertEquals(1.5f, engine.speed)
        assertEquals(4_500L, engine.items.filterIsInstance<PlaylistItem.Pause>().first().durationMs)
    }

    @Test
    fun `Пауза с экрана блокировки — плеер видит, что не играет`() = test {
        playAll()
        engine.pause()
        runCurrent()
        assertFalse(state.isPlaying)
    }

    @Test
    fun `Позиция обновляется раз в 250 мс, только когда панель видна и приложение на экране`() = test {
        player.play(track(1))
        runCurrent()
        engine.position = 1_000
        advanceTimeBy(260)
        assertEquals(1_000, player.positionMs.value)

        player.hidePanel()
        runCurrent()
        engine.position = 2_000
        advanceTimeBy(1_000)
        assertEquals(1_000, player.positionMs.value)

        // Показ панели — сразу свежая позиция, без ожидания отсчёта.
        player.showPanel()
        assertEquals(2_000, player.positionMs.value)

        player.onBackground()
        runCurrent()
        engine.position = 3_000
        advanceTimeBy(1_000)
        assertEquals(2_000, player.positionMs.value)
    }
}
