package app.inabah.android.core.settings

import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.content.model.AzkarSection
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** История азкаров: испорченная не пропадает без следа, разделы одного дня независимы. */
class AzkarHistoryTest {
    private val day = LocalDate.of(2026, 10, 3)
    private val historyKey = stringPreferencesKey("azkar.history")
    private val corruptKey = stringPreferencesKey("azkar.history.corrupt")

    // Регрессия (аудит 2026-10-06): нечитаемая история молча становилась пустой и затиралась первой записью.
    @Test
    fun `Нечитаемая история — сообщение в лог и копия, первая новая запись её не уничтожает`() = TestStorage.run { storage ->
        val errors = mutableListOf<Exception>()
        val history = AzkarHistory(storage.seed { it[historyKey] = "{испорчено" }) { errors += it }

        assertEquals(1, errors.size)
        assertNull(history.record(AzkarSection.Morning, day))

        history.record(completed = 3, total = 16, AzkarSection.Morning, day)
        val persisted = storage.persisted()
        assertEquals("{испорчено", persisted[corruptKey])
        assertEquals(AzkarDayRecord(3, 16), AzkarHistory(storage.storage) { throw AssertionError(it) }.record(AzkarSection.Morning, day))
    }

    @Test
    fun `Отложенная копия не перезаписывается при следующих сбоях`() = TestStorage.run { storage ->
        storage.seed {
            it[historyKey] = "второй сбой"
            it[corruptKey] = "первый сбой"
        }
        AzkarHistory(storage.storage) { }

        assertEquals("первый сбой", storage.persisted()[corruptKey])
    }

    @Test
    fun `Утренние и вечерние за один день независимы`() = TestStorage.run { storage ->
        val history = AzkarHistory(storage.storage) { throw AssertionError(it) }
        history.record(completed = 3, total = 16, AzkarSection.Morning, day)
        history.record(completed = 5, total = 16, AzkarSection.Evening, day)

        history.record(completed = 0, total = 16, AzkarSection.Morning, day)

        storage.restart()
        val restored = AzkarHistory(storage.storage) { throw AssertionError(it) }
        assertNull(restored.record(AzkarSection.Morning, day))
        assertEquals(AzkarDayRecord(5, 16), restored.record(AzkarSection.Evening, day))
    }
}
