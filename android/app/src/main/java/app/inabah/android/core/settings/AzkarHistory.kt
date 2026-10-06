package app.inabah.android.core.settings

import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.content.model.AzkarSection
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Сколько зикров раздела выполнено за одно время азкаров. */
@Serializable
data class AzkarDayRecord(val completed: Int, val total: Int)

/**
 * История чтения азкаров (iOS `AzkarHistory`): по дате начала окна и разделу — выполнено X из N.
 * Основа будущего календаря отметок, экрана пока нет.
 *
 * Пишется только во время азкаров и сразу при каждом выполненном или сброшенном зикре —
 * закрытое системой приложение ничего не теряет. Ключ `azkar.history` — JSON
 * `{ "2026-10-03": { "morning": { "completed": 12, "total": 16 } } }`.
 */
class AzkarHistory(private val storage: PreferencesStorage) {
    /** Дата окна (`yyyy-MM-dd`) → раздел → запись. Нечитаемое сохранение — пустая история. */
    private var records: Map<String, Map<String, AzkarDayRecord>> =
        storage.snapshot[key]?.let { text ->
            try {
                json.decodeFromString<Map<String, Map<String, AzkarDayRecord>>>(text)
            } catch (_: IllegalArgumentException) {
                // SerializationException — подкласс IllegalArgumentException.
                null
            }
        }.orEmpty()

    fun record(section: AzkarSection, day: LocalDate): AzkarDayRecord? = records[day.toString()]?.get(section.key)

    /**
     * Прогресс раздела в окне [day]. Ничего не выполнено — записи нет (как и у дня, когда раздел
     * не открывали); пустой день удаляется. Пишет только при изменении.
     */
    fun record(completed: Int, total: Int, section: AzkarSection, day: LocalDate) {
        val record = if (completed > 0) AzkarDayRecord(completed, total) else null
        val dayKey = day.toString()
        if (records[dayKey]?.get(section.key) == record) return
        val dayRecords = records[dayKey].orEmpty().let { if (record == null) it - section.key else it + (section.key to record) }
        records = if (dayRecords.isEmpty()) records - dayKey else records + (dayKey to dayRecords)
        val text = json.encodeToString(records)
        storage.edit { it[key] = text }
    }

    private companion object {
        val key = stringPreferencesKey("azkar.history")
        val json = Json { ignoreUnknownKeys = true }
    }
}
