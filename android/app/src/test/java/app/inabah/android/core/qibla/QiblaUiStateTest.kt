package app.inabah.android.core.qibla

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

/** Состояние экрана: разрешение, местоположение, датчик; подтверждение только при надёжных данных. */
class QiblaUiStateTest {
    private val tuning = QiblaTuning.Default
    private val now = 10_000_000L
    private val grozny = LocationFix(43.3178, 45.6986, 130.0, timeMillis = now)
    private val found = LocationStatus.Found(grozny)

    private fun sample() =
        HeadingSample(trueHeading = 194.0, accuracyDegrees = 5.0, lowAccuracy = false, tiltDegrees = 3.0, timeMillis = now)

    private fun pointer(aligned: Boolean, side: TurnSide = TurnSide.Right, tilted: Boolean = false, lowAccuracy: Boolean = false) =
        QiblaPointer(if (aligned) 1.0 else 40.0, 0.0, side, aligned, alignedEntries = 0, tilted = tilted, lowAccuracy = lowAccuracy)

    private fun state(
        access: LocationAccess = LocationAccess.Precise,
        location: LocationStatus = found,
        heading: HeadingStatus = HeadingStatus.Reading(sample()),
        pointer: QiblaPointer? = pointer(aligned = false),
    ) = qiblaUiState(access, location, heading, pointer, now, tuning)

    @Test
    fun `Доступ - загрузка, просьба, отказ навсегда`() {
        assertEquals(QiblaUiState.Loading, state(access = LocationAccess.Unknown))
        assertEquals(QiblaUiState.NeedsPermission(forever = false), state(access = LocationAccess.NotGranted))
        assertEquals(QiblaUiState.NeedsPermission(forever = true), state(access = LocationAccess.DeniedForever))
    }

    @Test
    fun `Местоположение - выключено, ищем, не нашлось`() {
        assertEquals(QiblaUiState.LocationOff, state(location = LocationStatus.Off))
        assertEquals(QiblaUiState.Locating, state(location = LocationStatus.Searching))
        assertEquals(QiblaUiState.LocationFailed, state(location = LocationStatus.Failed))
    }

    @Test
    fun `Нет датчика - направление числом`() {
        val noCompass = assertIs<QiblaUiState.NoCompass>(state(heading = HeadingStatus.NoSensor))
        assertEquals(194.45, noCompass.bearing, 0.05)
    }

    @Test
    fun `Датчик ещё молчит - ожидание без метки`() {
        val compass = assertIs<QiblaUiState.Compass>(state(heading = HeadingStatus.Waiting, pointer = null))
        assertEquals(QiblaGuidance.Waiting, compass.guidance)
    }

    @Test
    fun `Низкая точность и наклон - подсказка вместо подтверждения`() {
        // Трекер сам не засчитывает совпадение при них; подсказка берёт его флаги (с гистерезисом).
        assertEquals(QiblaGuidance.LowAccuracy, guidance(state(pointer = pointer(aligned = false, lowAccuracy = true))))
        assertEquals(QiblaGuidance.Tilted(TurnSide.Left, TurnAmount.Far), guidance(state(pointer = pointer(aligned = false, side = TurnSide.Left, tilted = true))))
        assertEquals(QiblaGuidance.LowAccuracy, guidance(state(pointer = pointer(aligned = false, tilted = true, lowAccuracy = true))), "точность важнее")
    }

    @Test
    fun `Надёжно - поворот или найдено`() {
        assertEquals(QiblaGuidance.Turn(TurnSide.Left, TurnAmount.Far), guidance(state(pointer = pointer(false, TurnSide.Left))))
        assertEquals(QiblaGuidance.Aligned, guidance(state(pointer = pointer(aligned = true))))
    }

    @Test
    fun `Приблизительные и устаревшие координаты - пометки`() {
        val approximate = assertIs<QiblaUiState.Compass>(state(access = LocationAccess.Approximate))
        assertEquals(LocationNote(approximate = true, stale = false, nearKaaba = false), approximate.location)
        assertTrue(approximate.location.confirmable, "в Грозном приблизительное направление верное")
        val old = LocationStatus.Found(grozny.copy(timeMillis = now - tuning.locationStaleMillis - 1))
        val stale = assertIs<QiblaUiState.Compass>(state(location = old))
        assertTrue(stale.location.stale)
        assertFalse(stale.location.confirmable, "устаревшие - без подтверждения (решение пользователя 2026-10-24)")
    }

    @Test
    fun `Приблизительное у самой Мекки - нужно точное`() {
        val mecca = LocationStatus.Found(LocationFix(21.45, 39.85, 0.0, timeMillis = now))
        val near = assertIs<QiblaUiState.Compass>(state(access = LocationAccess.Approximate, location = mecca))
        assertTrue(near.location.nearKaaba)
        assertFalse(near.location.confirmable)
        val precise = assertIs<QiblaUiState.Compass>(state(access = LocationAccess.Precise, location = mecca))
        assertTrue(precise.location.confirmable, "точное у Мекки - можно")
    }

    @Test
    fun `На месте, но координатам нельзя доверить - отдельная подсказка`() {
        val held = QiblaPointer(1.0, 1.0, TurnSide.Right, aligned = false, alignedEntries = 0, heldByLocation = true)
        assertEquals(QiblaGuidance.LocationUnconfirmed, guidance(state(pointer = held)))
    }

    private fun guidance(state: QiblaUiState) = assertIs<QiblaUiState.Compass>(state).guidance
}
