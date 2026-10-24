package app.inabah.android.core.qibla

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Один поиск координат: выключено, старые → свежие, таймаут с старыми и без (виртуальное время). */
class LocateFlowTest {
    private val old = LocationFix(43.3, 45.7, 0.0, timeMillis = 1)
    private val fresh = LocationFix(43.31, 45.69, 0.0, timeMillis = 2)
    private val timeout = 30_000L

    @Test
    fun `Геолокация выключена`() = runTest {
        locateFlow({ false }, { old }, { fresh }, timeout).test {
            assertEquals(LocationStatus.Off, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `Сразу старые координаты, потом свежие`() = runTest {
        locateFlow({ true }, { old }, { delay(2_000); fresh }, timeout).test {
            assertEquals(LocationStatus.Found(old), awaitItem())
            assertEquals(LocationStatus.Found(fresh), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `Свежих нет - старые остаются, без ошибки`() = runTest {
        locateFlow({ true }, { old }, { awaitCancellation() }, timeout).test {
            assertEquals(LocationStatus.Found(old), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `Ни старых, ни свежих - ищем, потом не нашлось`() = runTest {
        locateFlow({ true }, { null }, { awaitCancellation() }, timeout).test {
            assertEquals(LocationStatus.Searching, awaitItem())
            assertEquals(LocationStatus.Failed, awaitItem())
            awaitComplete()
        }
    }
}
