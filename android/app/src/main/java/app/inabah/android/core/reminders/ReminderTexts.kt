package app.inabah.android.core.reminders

import app.inabah.android.core.content.ContentLanguagePriority
import app.inabah.android.core.content.ContentSource
import app.inabah.android.core.content.keyedByLanguage
import app.inabah.android.core.content.model.AzkarSection
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// data/reminders.json (docs/06-data-structure.md, «Тексты напоминаний») — общие тексты iOS и Android.

@Serializable
internal data class RemindersFileDto(val translations: Map<String, ReminderTranslationDto> = emptyMap())

@Serializable
internal data class ReminderTranslationDto(
    val azkarStart: Map<String, ReminderTextDto> = emptyMap(),
    val azkarUnfinished: Map<String, ReminderTextDto> = emptyMap(),
    val hadithOfDay: ReminderTextDto? = null,
    val streakAtRisk: Map<String, ReminderTextDto> = emptyMap(),
    val readingLesson: ReminderTextDto? = null,
)

@Serializable
internal data class ReminderTextDto(val title: String = "", val body: String = "")

/** Заголовок и текст уведомления; у хадиса дня — подстановки `{collection}`, `{number}`, `{text}`. */
data class ReminderText(val title: String, val body: String) {
    /** Заменить известные подстановки; нет подстановки в тексте — ничего (пользователь мог её убрать). */
    fun fill(values: Map<String, String>): ReminderText = ReminderText(title.fill(values), body.fill(values))

    private fun String.fill(values: Map<String, String>): String =
        values.entries.fold(this) { text, (name, value) -> text.replace("{$name}", value) }.trim()
}

/** Тексты напоминаний на выбранном языке; вида или раздела нет — `null` у него (уведомление не показывается). */
data class ReminderTexts(
    val azkarStart: Map<AzkarSection, ReminderText>,
    val azkarUnfinished: Map<AzkarSection, ReminderText>,
    val hadithOfDay: ReminderText?,
    /** «Серия под угрозой»; подстановка `{streak}` — «12 дней подряд». */
    val streakAtRisk: Map<AzkarSection, ReminderText> = emptyMap(),
    /** «Пора на урок»; подстановка `{step}` — «Шаг 7 · Сукун». */
    val readingLesson: ReminderText? = null,
) {
    fun forKind(kind: ReminderKind): ReminderText? = when {
        kind == ReminderKind.HadithOfDay -> hadithOfDay
        kind.readingSlot != null -> readingLesson
        kind.isStreak -> streakAtRisk[kind.section]
        kind.isUnfinished -> azkarUnfinished[kind.section]
        else -> azkarStart[kind.section]
    }
}

/** Подстановки текстов (имена — как в `reminders.json` и `check-data`). */
object ReminderPlaceholders {
    const val COLLECTION = "collection"
    const val NUMBER = "number"
    const val TEXT = "text"
    const val STREAK = "streak"
    const val STEP = "step"
}

/**
 * Тексты из `assets/data/reminders.json`: разбираются один раз (кэш под [Mutex]), чтение — на [ioDispatcher].
 * Файла нет, он повреждён или в нём нет подходящего языка — `null`, и уведомления не показываются: ошибку ловит
 * `check-data` до сборки, а в приложении — [onError] (лог, аудит 2026-10-10, К5: иначе причину не найти).
 */
class ReminderTextsSource(
    private val source: ContentSource,
    private val priority: ContentLanguagePriority,
    private val ioDispatcher: CoroutineDispatcher,
    private val onError: (message: String, error: Throwable?) -> Unit = { _, _ -> },
) {
    private val mutex = Mutex()
    private var cached: ReminderTexts? = null

    suspend fun texts(): ReminderTexts? = mutex.withLock {
        cached ?: read()?.also { cached = it }
    }

    private suspend fun read(): ReminderTexts? = withContext(ioDispatcher) {
        val file = try {
            source.open(FILE).use { json.decodeFromString(RemindersFileDto.serializer(), it.bufferedReader().readText()) }
        } catch (error: IOException) {
            onError("Не прочитан $FILE", error)
            return@withContext null
        } catch (error: IllegalArgumentException) {
            // SerializationException — подкласс IllegalArgumentException.
            onError("Не разобран $FILE", error)
            return@withContext null
        }
        val texts = file.toTexts(priority)
        if (texts == null) onError("В $FILE нет текстов на языке интерфейса", null)
        texts
    }

    private companion object {
        const val FILE = "data/reminders.json"
        val json = Json { ignoreUnknownKeys = true }
    }
}

internal fun RemindersFileDto.toTexts(priority: ContentLanguagePriority): ReminderTexts? {
    val dto = priority.select(translations.keyedByLanguage())?.translation ?: return null
    fun ReminderTextDto.model() = takeIf { title.isNotBlank() || body.isNotBlank() }?.let { ReminderText(title, body) }
    fun Map<String, ReminderTextDto>.bySection() =
        AzkarSection.entries.mapNotNull { section -> this[section.key]?.model()?.let { section to it } }.toMap()
    return ReminderTexts(
        azkarStart = dto.azkarStart.bySection(),
        azkarUnfinished = dto.azkarUnfinished.bySection(),
        hadithOfDay = dto.hadithOfDay?.model(),
        streakAtRisk = dto.streakAtRisk.bySection(),
        readingLesson = dto.readingLesson?.model(),
    )
}
