package app.inabah.android.core.releases

import app.inabah.android.core.content.ContentLanguage
import app.inabah.android.core.content.ContentLanguagePriority
import app.inabah.android.core.settings.PreferencesStorage
import app.inabah.android.core.settings.TestStorage
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Test

/** «Что нового» с настоящим хранилищем: когда показывается и что запоминается (переживает перезапуск). */
class WhatsNewTest {
    private val json = """{ "versions": [
        { "version": "1.1.1", "date": "2026-10-12", "translations": { "ru": { "items": ["Новое"] } } },
        { "version": "1.1.0", "date": "2026-10-08", "translations": { "ru": { "items": ["Первая"] } } } ] }"""

    private fun TestScope.whatsNew(storage: PreferencesStorage, version: String = "1.1.1", isFreshInstall: Boolean = false) =
        WhatsNew(
            storage,
            ReleaseNotesSource({ json.byteInputStream() }, ContentLanguagePriority(listOf(ContentLanguage.Base)), StandardTestDispatcher(testScheduler)),
            currentVersion = version,
            isFreshInstall = isFreshInstall,
        )

    @Test
    fun `Обновление — окно с пунктами версии, закрыли — и после перезапуска окна нет`() = TestStorage.run { test ->
        val first = whatsNew(test.storage).apply { check() }
        assertEquals(listOf("Новое"), assertNotNull(first.pending.value).items)

        first.dismiss()
        assertNull(first.pending.value)

        val afterRestart = whatsNew(test.restart()).apply { check() }
        assertNull(afterRestart.pending.value)
        assertEquals("1.1.1", test.persisted()[WhatsNew.KEY])
    }

    @Test
    fun `Проверка закончена - видно и когда показывать нечего`() = TestStorage.run { test ->
        val fresh = whatsNew(test.storage, isFreshInstall = true)
        assertFalse(fresh.checked.value, "до проверки null в pending ещё не значит «нечего»")
        fresh.check()
        assertTrue(fresh.checked.value)
        assertNull(fresh.pending.value)
    }

    @Test
    fun `Не закрыли (процесс умер) — окно снова при следующем запуске`() = TestStorage.run { test ->
        whatsNew(test.storage).check()

        val next = whatsNew(test.restart()).apply { check() }

        assertNotNull(next.pending.value)
    }

    @Test
    fun `Новая установка — окна нет, версия записана, следующая без записи — тоже без окна`() = TestStorage.run { test ->
        val fresh = whatsNew(test.storage, isFreshInstall = true).apply { check() }
        assertNull(fresh.pending.value)
        assertEquals("1.1.1", test.persisted()[WhatsNew.KEY])

        // 1.1.1 записана — обновление на 1.2.0 без записи в файле: окна нет, но версия запоминается.
        whatsNew(test.storage, version = "1.2.0").check()
        assertEquals("1.2.0", test.persisted()[WhatsNew.KEY])
    }

    @Test
    fun `Испорченный ключ — как будто его нет`() = TestStorage.run { test ->
        val storage = test.seed { it[WhatsNew.KEY] = "не версия" }

        val whatsNew = whatsNew(storage).apply { check() }

        assertNotNull(whatsNew.pending.value)
    }

    @Test
    fun `Номер приложения не X_Y_Z — окна нет и ничего не записано`() = TestStorage.run { test ->
        val whatsNew = whatsNew(test.storage, version = "1.1.1-debug").apply { check() }

        assertNull(whatsNew.pending.value)
        assertNull(test.persisted()[WhatsNew.KEY])
    }
}
