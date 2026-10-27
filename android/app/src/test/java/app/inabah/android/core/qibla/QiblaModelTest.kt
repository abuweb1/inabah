package app.inabah.android.core.qibla

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Поток экрана «Къибла»: подписки по жизни коллектора, новый поиск координат, поправка на склонение. */
class QiblaModelTest {
    private val grozny = LocationFix(43.3178, 45.6986, 130.0, timeMillis = 0)

    private class FakeLocation(private val statuses: List<LocationStatus>) : LocationSource {
        var searches = 0
        var active = 0
        override fun locate(): Flow<LocationStatus> = flow {
            searches++
            active++
            try {
                statuses.forEach { emit(it) }
                kotlinx.coroutines.awaitCancellation()
            } finally {
                active--
            }
        }
    }

    private class FakeHeading(override val available: Boolean = true) : HeadingSource {
        val readings = MutableSharedFlow<MagneticHeading>()
        var listeners = 0
        override fun headings(): Flow<MagneticHeading> =
            readings.onStart { listeners++ }.onCompletion { listeners-- }
    }

    private fun reading(azimuth: Double, time: Long = 1) =
        MagneticHeading(azimuth, accuracyDegrees = 5.0, lowAccuracy = false, tiltDegrees = 2.0, timeMillis = time)

    private class FakeMagnetic(val declination: Double) : MagneticModel {
        override fun declination(fix: LocationFix) = declination
        override fun fieldMicroTesla(fix: LocationFix) = 50.0
    }

    private fun model(location: LocationSource, heading: HeadingSource, declination: Double = 0.0, now: () -> Long = { 0L }) =
        QiblaModel(location, heading, FakeMagnetic(declination), now = now)

    @Test
    fun `Без доступа датчик и поиск не запускаются`() = runTest {
        val location = FakeLocation(listOf(LocationStatus.Found(grozny)))
        val heading = FakeHeading()
        model(location, heading).state(LocationAccess.NotGranted).test {
            assertEquals(QiblaUiState.NeedsPermission(forever = false), awaitItem())
            awaitComplete()
        }
        assertEquals(0, location.searches)
        assertEquals(0, heading.listeners)
    }

    @Test
    fun `Подписки снимаются с коллектором, новая подписка ищет координаты заново`() = runTest {
        val location = FakeLocation(listOf(LocationStatus.Searching, LocationStatus.Found(grozny)))
        val heading = FakeHeading()
        val model = model(location, heading)

        model.state(LocationAccess.Precise).test {
            // «Ищем» и «нашли» приходят подряд: combine может отдать сразу последнее.
            while (awaitItem() !is QiblaUiState.Compass) Unit
            assertEquals(1, heading.listeners)
            assertEquals(1, location.active)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, heading.listeners, "ушли с экрана - датчик отпущен")
        assertEquals(0, location.active, "и поиск остановлен")

        model.state(LocationAccess.Precise).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, location.searches, "вернулись - координаты ищутся заново")
    }

    @Test
    fun `Обновить - новый поиск координат`() = runTest {
        val location = FakeLocation(listOf(LocationStatus.Found(grozny)))
        val model = model(location, FakeHeading())
        model.state(LocationAccess.Precise).test {
            awaitItem()
            model.refreshLocation()
            awaitItem()
            assertEquals(2, location.searches)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Магнитный азимут переводится в истинный`() = runTest {
        val heading = FakeHeading()
        val model = model(FakeLocation(listOf(LocationStatus.Found(grozny))), heading, declination = 10.0)
        model.state(LocationAccess.Precise).test {
            assertEquals(QiblaGuidance.Waiting, assertIs<QiblaUiState.Compass>(awaitItem()).guidance)
            // Къибла из Грозного - 194,45° от истинного севера; магнитный 184,45° + склонение 10° = истинный 194,45°.
            heading.readings.emit(reading(azimuth = 184.45))
            val compass = assertIs<QiblaUiState.Compass>(awaitItem())
            assertEquals(QiblaGuidance.Aligned, compass.guidance)
            assertEquals(0.0, compass.pointer!!.offset, 0.05)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Каждая подписка - новый трекер - метка без сглаживания, счёт и номер новые`() = runTest {
        val heading = FakeHeading()
        val model = model(FakeLocation(listOf(LocationStatus.Found(grozny))), heading)
        var first: QiblaPointer? = null
        model.state(LocationAccess.Precise).test {
            awaitItem()
            heading.readings.emit(reading(azimuth = 194.45 - 60.0, time = 1))
            first = assertIs<QiblaUiState.Compass>(awaitItem()).pointer
            cancelAndIgnoreRemainingEvents()
        }
        model.state(LocationAccess.Precise).test {
            awaitItem()
            heading.readings.emit(reading(azimuth = 194.45, time = 2))
            val pointer = assertIs<QiblaUiState.Compass>(awaitItem()).pointer!!
            assertEquals(0.0, pointer.offset, 0.05, "не сглажено от прежних 60°")
            assertEquals(1, pointer.alignedEntries)
            assertTrue(pointer.generation != first!!.generation)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Устаревшие координаты - на месте, но без подтверждения`() = runTest {
        val heading = FakeHeading()
        val model = model(FakeLocation(listOf(LocationStatus.Found(grozny))), heading, now = { QiblaTuning.Default.locationStaleMillis + 1 })
        model.state(LocationAccess.Precise).test {
            awaitItem()
            heading.readings.emit(reading(azimuth = 194.45))
            assertEquals(QiblaGuidance.LocationUnconfirmed, assertIs<QiblaUiState.Compass>(awaitItem()).guidance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Не удалось подписаться на датчик - как без датчика`() = runTest {
        val broken = object : HeadingSource {
            override val available = true
            override fun headings(): Flow<MagneticHeading> = flow { throw IllegalStateException("нет датчика") }
        }
        model(FakeLocation(listOf(LocationStatus.Found(grozny))), broken).state(LocationAccess.Precise).test {
            while (awaitItem() !is QiblaUiState.NoCompass) Unit
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Без датчика - направление числом`() = runTest {
        val model = model(FakeLocation(listOf(LocationStatus.Found(grozny))), FakeHeading(available = false))
        model.state(LocationAccess.Precise).test {
            assertIs<QiblaUiState.NoCompass>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
