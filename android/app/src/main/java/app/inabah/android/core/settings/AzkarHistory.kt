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
class AzkarHistory(
    private val storage: PreferencesStorage,
    /** Сохранённая история испорчена — дальше пишется новая; сообщить в лог. */
    onUnreadable: (Exception) -> Unit,
) {
    /**
     * Дата окна (`yyyy-MM-dd`) → раздел → запись. Нечитаемое сохранение — пустая история, а прежний
     * текст один раз откладывается под [corruptKey]: первая новая запись не уничтожает его без следа
     * (аудит 2026-10-06 — это основа будущего календаря).
     */
    private var records: Map<String, Map<String, AzkarDayRecord>> =
        storage.snapshot[key]?.let { text ->
            try {
                json.decodeFromString<Map<String, Map<String, AzkarDayRecord>>>(text)
            } catch (error: IllegalArgumentException) {
                // SerializationException — подкласс IllegalArgumentException.
                onUnreadable(error)
                if (corruptKey !in storage.snapshot) storage.edit { it[corruptKey] = text }
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

        /** Отложенная нечитаемая история — для ручного восстановления. */
        val corruptKey = stringPreferencesKey("azkar.history.corrupt")
        val json = Json { ignoreUnknownKeys = true }
    }
}
