package app.inabah.android.core.releases

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** Номер версии и правило «показывать ли „Что нового“» (docs/android/03-domain-logic.md). */
class WhatsNewDecisionTest {
    private fun v(text: String) = requireNotNull(AppVersion.parse(text)) { text }

    @Test
    fun `Версии сравниваются по числам, не как строки`() {
        assertTrue(v("1.10.0") > v("1.9.0"))
        assertTrue(v("2.0.0") > v("1.99.99"))
        assertTrue(v("1.1.1") > v("1.1.0"))
        assertEquals(0, v("1.1.1").compareTo(v("1.1.1")))
        assertEquals(v("1.1.1"), v("1.1.1"))
        // Равенство согласовано со сравнением: запись «1.01.0» в файле находится для приложения 1.1.0.
        assertEquals(v("1.1.0"), v("1.01.0"))
        assertEquals(v("1.1.0").hashCode(), v("1.01.0").hashCode())
    }

    @Test
    fun `Не X_Y_Z — не версия`() {
        listOf(null, "", "1.1", "1.1.1.1", "v1.1.1", "1.1.1-beta", "1.a.1", " 1.1.1", "99999999999.0.0").forEach {
            assertNull(AppVersion.parse(it), "«$it»")
        }
    }

    @Test
    fun `Новая установка — окна нет, версия запоминается`() {
        assertEquals(WhatsNewDecision.RecordSilently, whatsNewDecision(null, v("1.1.1"), isFreshInstall = true, hasNotes = true))
    }

    @Test
    fun `Обновление с 1_1_0 (ключа ещё нет) — показать`() {
        assertEquals(WhatsNewDecision.Show, whatsNewDecision(null, v("1.1.1"), isFreshInstall = false, hasNotes = true))
    }

    @Test
    fun `Версия выросла — показать, если для неё есть запись`() {
        assertEquals(WhatsNewDecision.Show, whatsNewDecision(v("1.1.1"), v("1.2.0"), isFreshInstall = false, hasNotes = true))
        assertEquals(WhatsNewDecision.RecordSilently, whatsNewDecision(v("1.1.1"), v("1.2.0"), isFreshInstall = false, hasNotes = false))
        assertEquals(WhatsNewDecision.RecordSilently, whatsNewDecision(null, v("1.1.1"), isFreshInstall = false, hasNotes = false))
    }

    @Test
    fun `Та же версия или откат — ничего`() {
        assertEquals(WhatsNewDecision.Nothing, whatsNewDecision(v("1.1.1"), v("1.1.1"), isFreshInstall = false, hasNotes = true))
        assertEquals(WhatsNewDecision.Nothing, whatsNewDecision(v("1.2.0"), v("1.1.1"), isFreshInstall = false, hasNotes = true))
        // После переустановки из резервной копии ключ уже есть — новая установка его не перекрывает.
        assertEquals(WhatsNewDecision.Nothing, whatsNewDecision(v("1.1.1"), v("1.1.1"), isFreshInstall = true, hasNotes = true))
    }
}
