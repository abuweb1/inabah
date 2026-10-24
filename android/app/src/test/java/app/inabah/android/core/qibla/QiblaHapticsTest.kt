package app.inabah.android.core.qibla

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** Один отклик на вход в совпадение - и после ↻ / возврата на экран (аудит 2026-10-24); градусы для TalkBack. */
class QiblaHapticsTest {
    private fun pointer(entries: Int, generation: Int) =
        QiblaPointer(0.0, 0.0, TurnSide.Right, aligned = entries > 0, alignedEntries = entries, generation = generation)

    @Test
    fun `Отклик - на рост счёта, повтор того же значения молчит`() {
        val gate = AlignmentHaptics()
        assertFalse(gate.shouldVibrate(pointer(0, 1)))
        assertTrue(gate.shouldVibrate(pointer(1, 1)))
        assertFalse(gate.shouldVibrate(pointer(1, 1)), "дрожание внутри совпадения")
        assertTrue(gate.shouldVibrate(pointer(2, 1)), "новый вход")
    }

    @Test
    fun `После перезапуска старое значение не даёт отклик, первый вход новой подписки - даёт`() {
        val gate = AlignmentHaptics()
        assertTrue(gate.shouldVibrate(pointer(1, 1)))
        // ↻: экран ещё видит прежнее значение той же подписки.
        assertFalse(gate.shouldVibrate(pointer(1, 1)))
        // Новая подписка начинается с нуля и сразу в совпадении.
        assertTrue(gate.shouldVibrate(pointer(1, 2)))
        assertFalse(gate.shouldVibrate(pointer(1, 2)))
    }

    @Test
    fun `Градусы для TalkBack - шагом 5, не 0, без прыжков у границы`() {
        assertEquals(40, spokenTurnDegrees(41.0, null))
        assertEquals(5, spokenTurnDegrees(1.0, null), "не «поверните на 0°»")
        val first = spokenTurnDegrees(12.4, null)
        assertEquals(10, first)
        assertEquals(10, spokenTurnDegrees(12.6, first), "у 12,5° держится прежнее")
        assertEquals(10, spokenTurnDegrees(13.5, first))
        assertEquals(15, spokenTurnDegrees(14.0, first), "ушло на три четверти шага - новое значение")
    }
}
