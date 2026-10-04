package app.inabah.android.core.settings

import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import app.inabah.android.core.designsystem.ThemeStyle
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** «Размер арабского шрифта». */
class ReadingSettingsTest {
    @Test
    fun `По умолчанию — 21`() = TestStorage.run { storage ->
        assertEquals(21.0, ReadingSettings(storage.storage).arabicFontSize.value)
    }

    @Test
    fun `Шаг 2, верхняя граница 40`() = TestStorage.run { storage ->
        val settings = ReadingSettings(storage.storage)

        settings.increaseArabicFontSize()
        assertEquals(23.0, settings.arabicFontSize.value)

        repeat(20) { settings.increaseArabicFontSize() }
        assertEquals(40.0, settings.arabicFontSize.value)
        assertFalse(settings.canIncreaseArabicFontSize)
    }

    @Test
    fun `Нижняя граница 14`() = TestStorage.run { storage ->
        val settings = ReadingSettings(storage.storage)

        repeat(10) { settings.decreaseArabicFontSize() }
        assertEquals(14.0, settings.arabicFontSize.value)
        assertFalse(settings.canDecreaseArabicFontSize)
        assertTrue(settings.canIncreaseArabicFontSize)
    }

    @Test
    fun `Размер сохраняется между запусками`() = TestStorage.run { storage ->
        ReadingSettings(storage.storage).increaseArabicFontSize()

        assertEquals(23.0, ReadingSettings(storage.restart()).arabicFontSize.value)
    }

    @Test
    fun `Сохранённое значение вне диапазона приводится к границе`() = TestStorage.run { storage ->
        val restarted = storage.seed { it[doublePreferencesKey("reading.arabicFontSize")] = 100.0 }

        assertEquals(40.0, ReadingSettings(restarted).arabicFontSize.value)
    }
}

/** «Настройки „Прослушать все“». */
class PlaylistSettingsTest {
    @Test
    fun `Значения по умолчанию`() = TestStorage.run { storage ->
        val settings = PlaylistSettings(storage.storage)

        assertTrue(settings.repeatsByCount.value)
        assertEquals(1.0, settings.pauseBetween.value)
        assertEquals(1f, settings.rate.value)
    }

    @Test
    fun `Настройки сохраняются между запусками`() = TestStorage.run { storage ->
        val settings = PlaylistSettings(storage.storage)
        settings.setRepeatsByCount(false)
        settings.setPauseBetween(3.0)
        settings.setRate(1.5f)

        val restored = PlaylistSettings(storage.restart())
        assertFalse(restored.repeatsByCount.value)
        assertEquals(3.0, restored.pauseBetween.value)
        assertEquals(1.5f, restored.rate.value)
    }

    @Test
    fun `Недопустимое сохранённое значение — значение по умолчанию`() = TestStorage.run { storage ->
        val restarted = storage.seed {
            it[doublePreferencesKey("playlist.pauseBetween")] = 7.0
            it[floatPreferencesKey("playlist.rate")] = 3f
        }

        val settings = PlaylistSettings(restarted)
        assertEquals(1.0, settings.pauseBetween.value)
        assertEquals(1f, settings.rate.value)
    }
}

/** «Выбор палитры». */
class AppearanceSettingsTest {
    @Test
    fun `По умолчанию — свой цвет у каждого раздела`() = TestStorage.run { storage ->
        assertEquals(ThemeStyle.Sections, AppearanceSettings(storage.storage).style.value)
    }

    @Test
    fun `Выбор сохраняется между запусками`() = TestStorage.run { storage ->
        AppearanceSettings(storage.storage).select(ThemeStyle.Amber)

        assertEquals(ThemeStyle.Amber, AppearanceSettings(storage.restart()).style.value)
    }

    @Test
    fun `Повторный выбор той же палитры не уведомляет наблюдателей`() = TestStorage.run { storage ->
        val settings = AppearanceSettings(storage.storage)
        settings.select(ThemeStyle.Emerald)
        val before = storage.storage.snapshot

        settings.style.test {
            assertEquals(ThemeStyle.Emerald, awaitItem())

            settings.select(ThemeStyle.Emerald)
            expectNoEvents()
            assertTrue(before === storage.storage.snapshot, "повторный выбор не должен писать в хранилище")

            settings.select(ThemeStyle.Graphite)
            assertEquals(ThemeStyle.Graphite, awaitItem())
        }
    }

    @Test
    fun `Повреждённое значение — палитра по умолчанию`() = TestStorage.run { storage ->
        val restarted = storage.seed { it[stringPreferencesKey("appearance.themeStyle")] = "neon" }

        assertEquals(ThemeStyle.Sections, AppearanceSettings(restarted).style.value)
    }
}

/** «Размер текста». */
class TextSizeSettingsTest {
    @Test
    fun `По умолчанию — «Обычный» у переводов и интерфейса`() = TestStorage.run { storage ->
        val settings = TextSizeSettings(storage.storage)

        assertEquals(ContentTextSize.Standard, settings.content.value)
        assertEquals(InterfaceTextSize.Standard, settings.interfaceSize.value)
    }

    @Test
    fun `Выбор сохраняется между запусками`() = TestStorage.run { storage ->
        TextSizeSettings(storage.storage).apply {
            select(ContentTextSize.Largest)
            select(InterfaceTextSize.Smaller)
        }

        val restarted = TextSizeSettings(storage.restart())
        assertEquals(ContentTextSize.Largest, restarted.content.value)
        assertEquals(InterfaceTextSize.Smaller, restarted.interfaceSize.value)
    }

    @Test
    fun `Повторный выбор того же шага не уведомляет и не пишет`() = TestStorage.run { storage ->
        val settings = TextSizeSettings(storage.storage)
        settings.select(InterfaceTextSize.Larger)
        val before = storage.storage.snapshot

        settings.interfaceSize.test {
            assertEquals(InterfaceTextSize.Larger, awaitItem())

            settings.select(InterfaceTextSize.Larger)
            expectNoEvents()
            assertTrue(before === storage.storage.snapshot, "повторный выбор не должен писать в хранилище")

            settings.select(InterfaceTextSize.Standard)
            assertEquals(InterfaceTextSize.Standard, awaitItem())
        }
    }

    @Test
    fun `Неизвестное значение и прежнее iOS largest у интерфейса — «Обычный»`() = TestStorage.run { storage ->
        val restarted = storage.seed {
            it[stringPreferencesKey("appearance.contentTextSize")] = "huge"
            it[stringPreferencesKey("appearance.interfaceTextSize")] = "largest"
        }

        val settings = TextSizeSettings(restarted)
        assertEquals(ContentTextSize.Standard, settings.content.value)
        assertEquals(InterfaceTextSize.Standard, settings.interfaceSize.value)
    }

    @Test
    fun `Шаги — как в iOS, кроме «Мельче» (мельче по решению пользователя)`() {
        assertEquals(listOf(0.8f, 1f, 1.15f, 1.3f, 1.5f), ContentTextSize.entries.map { it.scale })
        assertEquals(listOf(0.88f, 1f, 1.12f), InterfaceTextSize.entries.map { it.fontScale })
    }
}
