package app.inabah.android.core.designsystem.components

import app.inabah.android.core.formatting.formatPercent
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** Процент на кольце карточки главной (docs/android/03-domain-logic.md, 3.7). */
class NavRingPercentTest {
    @Test
    fun `До выполнения — не больше 99, при выполнении — галочка`() {
        assertEquals(0, navRingPercent(0.0))
        assertEquals(13, navRingPercent(2.0 / 16))
        assertEquals(99, navRingPercent(0.994))
        assertEquals(99, navRingPercent(0.996), "100 % без галочки сбивало бы с толку")
        assertNull(navRingPercent(1.0))
        assertNull(navRingPercent(1.5))
        assertEquals(0, navRingPercent(-0.2))
        assertEquals(0, navRingPercent(Double.NaN))
    }

    @Test
    fun `Округление как в iOS — в Double, 21 из 40 — 53`() {
        assertEquals(53, navRingPercent(21.0 / 40))
        assertEquals(57, navRingPercent(23.0 / 40))
    }

    @Test
    fun `Процент русской локали — с неразрывным пробелом`() {
        assertEquals("13 %", formatPercent(13, Locale.forLanguageTag("ru")))
    }
}
