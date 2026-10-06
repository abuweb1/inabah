package app.inabah.android.core.settings

import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import app.inabah.android.core.designsystem.ParchmentStyle
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ParchmentSettingsTest {
    @Test
    fun `По умолчанию — нынешний пергамент`() = TestStorage.run { storage ->
        assertEquals(ParchmentStyle.Classic, ParchmentSettings(storage.storage).style.value)
    }

    @Test
    fun `Выбор сохраняется между запусками`() = TestStorage.run { storage ->
        ParchmentSettings(storage.storage).select(ParchmentStyle.Mint)

        assertEquals(ParchmentStyle.Mint, ParchmentSettings(storage.restart()).style.value)
    }

    @Test
    fun `Повторный выбор того же фона не уведомляет и не пишет`() = TestStorage.run { storage ->
        val settings = ParchmentSettings(storage.storage)
        settings.select(ParchmentStyle.Sepia)
        val before = storage.storage.snapshot

        settings.style.test {
            assertEquals(ParchmentStyle.Sepia, awaitItem())

            settings.select(ParchmentStyle.Sepia)
            expectNoEvents()
            assertTrue(before === storage.storage.snapshot, "повторный выбор не должен писать в хранилище")

            settings.select(ParchmentStyle.Night)
            assertEquals(ParchmentStyle.Night, awaitItem())
        }
    }

    @Test
    fun `Повреждённое значение — пергамент по умолчанию`() = TestStorage.run { storage ->
        val restarted = storage.seed { it[stringPreferencesKey("appearance.parchment")] = "sunset" }

        assertEquals(ParchmentStyle.Classic, ParchmentSettings(restarted).style.value)
    }
}
