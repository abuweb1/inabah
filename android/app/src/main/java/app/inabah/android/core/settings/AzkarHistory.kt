package app.inabah.android.core.settings

import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.content.model.AzkarSection
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Сколько зикров раздела выполнено за одно время азкаров. */
@Serializable
data class AzkarDayRecord(val completed: Int, val total: Int) {
    /** Раздел прочитан полностью — половина дня в календаре. */
    val isComplete: Boolean get() = total > 0 && completed >= total
}

/** Утренние и вечерние одного дня (даты начала окна) — для календаря и серии. */
data class AzkarDay(val morning: AzkarDayRecord? = null, val evening: AzkarDayRecord? = null) {
    fun record(section: AzkarSection): AzkarDayRecord? = when (section) {
        AzkarSection.Morning -> morning
        AzkarSection.Evening -> evening
    }
}

/**
 * История чтения азкаров (iOS `AzkarHistory`): по дате начала окна и разделу — выполнено X из N.
 * Основа календаря и серии дней ([AzkarStreak]).
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

    private val _days = MutableStateFlow(days(records))

    /** Дни, за которые что-то прочитано, — для календаря; обновляется при каждой записи и сбросе. */
    val days: StateFlow<Map<LocalDate, AzkarDay>> = _days.asStateFlow()

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
        _days.value = days(records)
    }

    /**
     * Сброс из настроек: календарь и серия начинаются заново. Удаляется и отложенная нечитаемая копия.
     * Текущий счёт не меняется — раздел, который читают сейчас, попадёт в историю со следующим зикром.
     */
    fun clear() {
        records = emptyMap()
        storage.edit {
            it.remove(key)
            it.remove(corruptKey)
        }
        _days.value = emptyMap()
    }

    private companion object {
        /** Типизированный вид для календаря; неизвестные даты и разделы пропускаются, но в хранилище остаются. */
        fun days(records: Map<String, Map<String, AzkarDayRecord>>): Map<LocalDate, AzkarDay> =
            records.mapNotNull { (dayKey, sections) ->
                val day = try {
                    LocalDate.parse(dayKey)
                } catch (_: DateTimeParseException) {
                    return@mapNotNull null
                }
                val value = AzkarDay(sections[AzkarSection.Morning.key], sections[AzkarSection.Evening.key])
                if (value.morning == null && value.evening == null) null else day to value
            }.toMap()

        val key = stringPreferencesKey("azkar.history")

        /** Отложенная нечитаемая история — для ручного восстановления. */
        val corruptKey = stringPreferencesKey("azkar.history.corrupt")
        val json = Json { ignoreUnknownKeys = true }
    }
}
