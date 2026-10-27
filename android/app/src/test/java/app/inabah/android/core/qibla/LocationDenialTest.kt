package app.inabah.android.core.qibla

import kotlin.test.assertEquals
import org.junit.Test

/** Отказ в доступе к местоположению: обычный, «больше не спрашивать», закрытое окно (аудит 2026-10-24). */
class LocationDenialTest {
    @Test
    fun `Первый отказ - можно спросить снова`() {
        assertEquals(DenialOutcome.Denied, classifyDenial(rationaleBefore = false, rationaleNow = true, answeredInMillis = 3_000))
        assertEquals(DenialOutcome.Denied, classifyDenial(rationaleBefore = true, rationaleNow = true, answeredInMillis = 3_000))
    }

    @Test
    fun `Пояснение было и пропало - больше не спрашивать`() {
        assertEquals(DenialOutcome.DeniedForever, classifyDenial(rationaleBefore = true, rationaleNow = false, answeredInMillis = 3_000))
    }

    @Test
    fun `Окно закрыли Назад - не отказ, сколько бы раз`() {
        repeat(3) {
            assertEquals(DenialOutcome.Dismissed, classifyDenial(rationaleBefore = false, rationaleNow = false, answeredInMillis = 2_000))
        }
    }

    @Test
    fun `Окна Android не было вовсе - спрашивать уже нельзя`() {
        assertEquals(DenialOutcome.DeniedForever, classifyDenial(rationaleBefore = false, rationaleNow = false, answeredInMillis = 40))
    }
}
