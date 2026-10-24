package app.inabah.android.core.qibla

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Сглаживание метки, гистерезис совпадения, наклона и точности, сторона поворота, однократный виброотклик
 * (ревью 2026-10-24: без гистерезиса наклона телефон «около 30°» давал серию откликов).
 */
class QiblaTrackerTest {
    private val tuning = QiblaTuning.Default
    private val instant = tuning.copy(smoothingMillis = 0)

    private fun sample(time: Long, tilt: Double = 2.0, accuracy: Double? = 5.0, low: Boolean = false) =
        HeadingSample(trueHeading = 0.0, accuracyDegrees = accuracy, lowAccuracy = low, tiltDegrees = tilt, timeMillis = time)

    /** Показания с шагом [stepMillis], как от датчика. */
    private class Feed(val tracker: QiblaTracker, val stepMillis: Long = 20) {
        var time = 0L
        fun next(target: Double, tilt: Double = 2.0, accuracy: Double? = 5.0, low: Boolean = false): QiblaPointer {
            time += stepMillis
            return tracker.update(target, HeadingSample(0.0, accuracy, low, tilt, time))
        }
    }

    @Test
    fun `Первое показание - метка встаёт сразу`() {
        val pointer = QiblaTracker(tuning).update(40.0, sample(0))
        assertEquals(40.0, pointer.offset, 1e-9)
        assertEquals(TurnSide.Right, pointer.side)
    }

    @Test
    fun `Через 0 и 360 метка идёт коротким путём, без оборота`() {
        val feed = Feed(QiblaTracker(tuning))
        feed.next(355.0)
        var previous = feed.next(355.0).angle
        repeat(50) {
            val angle = feed.next(5.0).angle
            assertTrue(abs(angle - previous) < 10.0, "шаг метки без скачка: $previous → $angle")
            previous = angle
        }
        assertEquals(5.0, previous, 0.5, "дошла до 5° через 0°, угол без перескока")
    }

    @Test
    fun `Сглаживание не зависит от частоты датчика`() {
        val slow = Feed(QiblaTracker(tuning), stepMillis = 100)
        val fast = Feed(QiblaTracker(tuning), stepMillis = 20)
        slow.tracker.update(0.0, sample(0))
        fast.tracker.update(0.0, sample(0))
        var a = slow.next(60.0)
        repeat(4) { a = slow.next(60.0) }
        var b = fast.next(60.0)
        repeat(24) { b = fast.next(60.0) }
        assertEquals(a.offset, b.offset, 1e-6, "за те же 500 мс - то же положение")
    }

    @Test
    fun `После паузы метка встаёт сразу`() {
        val tracker = QiblaTracker(tuning)
        tracker.update(0.0, sample(0))
        val pointer = tracker.update(120.0, sample(tuning.snapAfterMillis + 1))
        assertEquals(120.0, pointer.offset, 1e-9)
    }

    @Test
    fun `Вход в совпадение по порогу входа, выход - по порогу выхода`() {
        val feed = Feed(QiblaTracker(instant))
        assertFalse(feed.next(6.0).aligned, "6° - ещё не найдено")
        assertTrue(feed.next(4.9).aligned, "внутри 5° - найдено")
        assertTrue(feed.next(7.5).aligned, "до 8° держится")
        assertFalse(feed.next(8.5).aligned, "за 8° - погасло")
        assertFalse(feed.next(6.0).aligned, "между порогами снова не загорается")
    }

    @Test
    fun `Дрожание у границы даёт один вход и один виброотклик`() {
        val feed = Feed(QiblaTracker(instant))
        var pointer = feed.next(4.0)
        assertEquals(1, pointer.alignedEntries)
        for (jitter in listOf(7.0, 3.0, 7.9, 2.0, -7.0, 6.5, 4.0)) pointer = feed.next(jitter)
        assertTrue(pointer.aligned)
        assertEquals(1, pointer.alignedEntries, "дрожание внутри 8° не повторяет вход")
    }

    @Test
    fun `Новый вход - новый отклик, только после настоящего ухода`() {
        val feed = Feed(QiblaTracker(instant), stepMillis = 100)
        assertEquals(1, feed.next(0.0).alignedEntries)
        feed.next(20.0)
        assertEquals(1, feed.next(0.0).alignedEntries, "ушёл на 100 мс - отклик не повторяется")
        repeat(12) { feed.next(30.0) }
        assertEquals(2, feed.next(0.0).alignedEntries, "ушёл больше чем на секунду - новый отклик")
    }

    @Test
    fun `Ненадёжное показание не подтверждает и гасит совпадение`() {
        val feed = Feed(QiblaTracker(instant))
        assertFalse(feed.next(0.0, low = true).aligned, "датчик сам сообщил о низкой точности")
        assertTrue(feed.next(0.0).aligned)
        val pointer = feed.next(0.0, accuracy = 30.0)
        assertFalse(pointer.aligned)
        assertTrue(pointer.lowAccuracy)
    }

    @Test
    fun `Наклон около 30 градусов не мигает и не повторяет отклик`() {
        val feed = Feed(QiblaTracker(instant), stepMillis = 100)
        assertTrue(feed.next(0.0, tilt = 28.0).aligned)
        val tilts = listOf(31.0, 29.0, 30.5, 27.0, 26.0, 31.0, 24.0)
        val states = tilts.map { feed.next(0.0, tilt = it) }
        assertEquals(listOf(true, true, true, true, true, true, false), states.map { it.tilted }, "наклонён с 31°, ровно - только ниже 25°")
        assertTrue(states.last().aligned)
        assertEquals(1, states.last().alignedEntries, "за всё время - один отклик")
    }

    @Test
    fun `Оценка точности у порога не мигает`() {
        val feed = Feed(QiblaTracker(instant))
        val flags = listOf(21.0, 19.0, 18.0, 16.0, 14.0, 19.0).map { feed.next(0.0, accuracy = it).lowAccuracy }
        assertEquals(listOf(true, true, true, true, false, false), flags, "неточен хуже 20°, снова точен лучше 15°")
    }

    @Test
    fun `Степень поворота - Поверните, Правее, Немного правее - не мигает у границ`() {
        val feed = Feed(QiblaTracker(instant))
        assertEquals(TurnAmount.Far, feed.next(60.0).amount)
        assertEquals(TurnAmount.Far, feed.next(44.0).amount, "у 45° - запас 2°")
        assertEquals(TurnAmount.Mid, feed.next(42.0).amount)
        assertEquals(TurnAmount.Mid, feed.next(46.0).amount, "назад - тоже с запасом")
        assertEquals(TurnAmount.Mid, feed.next(-30.0).amount, "слева - те же границы")
        assertEquals(TurnAmount.Mid, feed.next(14.0).amount)
        assertEquals(TurnAmount.Near, feed.next(12.0).amount)
        assertEquals(TurnAmount.Near, feed.next(16.0).amount)
        assertEquals(TurnAmount.Mid, feed.next(18.0).amount)
    }

    @Test
    fun `Около противоположного направления сторона не скачет`() {
        val feed = Feed(QiblaTracker(instant))
        assertEquals(TurnSide.Right, feed.next(160.0).side)
        for (jitter in listOf(178.0, -179.0, 175.0, -172.0, 179.5)) {
            assertEquals(TurnSide.Right, feed.next(jitter).side, "у 180° ($jitter) - прежняя сторона")
        }
        assertEquals(TurnSide.Left, feed.next(-165.0).side, "ушла из зоны - сторона сменилась")
    }
}
