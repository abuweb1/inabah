package app.inabah.android.core.settings

import app.inabah.android.core.content.model.AzkarSection
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Только для debug-сборки — прошлые дни для проверки календаря и скриншотов: JSON в формате `azkar.history`
 * в файле `files/debug-azkar-history`
 * (`adb shell run-as app.inabah.android sh -c 'echo {…} > files/debug-azkar-history'`) дописывается в историю
 * при запуске, файл удаляется. Нет файла или он не читается — ничего.
 */
suspend fun seedDebugAzkarHistory(history: AzkarHistory, file: File, ioDispatcher: CoroutineDispatcher) {
    val records = withContext(ioDispatcher) {
        try {
            if (!file.isFile) return@withContext null
            Json.decodeFromString<Map<String, Map<String, AzkarDayRecord>>>(file.readText()).also { file.delete() }
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        }
    } ?: return
    for ((dayKey, sections) in records) {
        val day = try {
            LocalDate.parse(dayKey)
        } catch (_: DateTimeParseException) {
            continue
        }
        for (section in AzkarSection.entries) {
            val record = sections[section.key] ?: continue
            history.record(record.completed, record.total, section, day)
        }
    }
}

const val DEBUG_AZKAR_HISTORY_FILE = "debug-azkar-history"
