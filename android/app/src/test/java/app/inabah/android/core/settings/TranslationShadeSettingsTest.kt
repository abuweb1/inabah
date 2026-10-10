package app.inabah.android.core.settings

import androidx.datastore.preferences.core.floatPreferencesKey
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class TranslationShadeSettingsTest {
    private val key = floatPreferencesKey("appearance.translationShade")

    @Test
    fun `По умолчанию — по центру`() = TestStorage.run { storage ->
        assertEquals(0f, TranslationShadeSettings(storage.storage).shade.value)
    }

    @Test
    fun `Сдвиг сохраняется между запусками`() = TestStorage.run { storage ->
        TranslationShadeSettings(storage.storage).set(0.4f)

        assertEquals(0.4f, TranslationShadeSettings(storage.restart()).shade.value)
    }

    @Test
    fun `Вне диапазона — край`() = TestStorage.run { storage ->
        val settings = TranslationShadeSettings(storage.storage)
        settings.set(2f)
        assertEquals(1f, settings.shade.value)
        settings.set(-5f)
        assertEquals(-1f, settings.shade.value)
    }

    @Test
    fun `Сохранённое вне диапазона или NaN — край или центр`() = TestStorage.run { storage ->
        assertEquals(-1f, TranslationShadeSettings(storage.seed { it[key] = -7f }).shade.value)
        assertEquals(0f, TranslationShadeSettings(storage.seed { it[key] = Float.NaN }).shade.value)
    }

    @Test
    fun `Повтор того же значения не уведомляет и не пишет`() = TestStorage.run { storage ->
        val settings = TranslationShadeSettings(storage.storage)
        settings.set(-0.3f)
        val before = storage.storage.snapshot

        settings.shade.test {
            assertEquals(-0.3f, awaitItem())

            settings.set(-0.3f)
            expectNoEvents()
            assertTrue(before === storage.storage.snapshot, "повтор не должен писать в хранилище")

            settings.set(0f)
            assertEquals(0f, awaitItem())
        }
    }
}
