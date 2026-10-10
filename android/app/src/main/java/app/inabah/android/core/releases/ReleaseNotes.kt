package app.inabah.android.core.releases

import app.inabah.android.core.content.ContentLanguagePriority
import app.inabah.android.core.content.ContentSource
import app.inabah.android.core.content.keyedByLanguage
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// data/versions.json (docs/06-data-structure.md, «История версий») — общий файл iOS и Android, тексты пишет пользователь.

@Serializable
internal data class VersionsFileDto(val versions: List<VersionDto> = emptyList())

@Serializable
internal data class VersionDto(
    val version: String = "",
    val date: String = "",
    val translations: Map<String, VersionTranslationDto> = emptyMap(),
)

@Serializable
internal data class VersionTranslationDto(
    val items: List<String> = emptyList(),
    val android: List<String> = emptyList(),
    val ios: List<String> = emptyList(),
)

/** Что нового в версии — на выбранном языке: общие пункты, за ними пункты Android. */
data class ReleaseNotes(val version: AppVersion, val date: LocalDate, val items: List<String>)

/**
 * История версий из `assets/data/versions.json`, новые сверху: разбор один раз (кэш под [Mutex]), чтение —
 * на [ioDispatcher]. Файла нет или он повреждён — пустой список и [onError]; версия с неверным номером или
 * датой, без пунктов на доступных языках — пропускается (ошибки данных ловит `check-data` до сборки).
 */
class ReleaseNotesSource(
    private val source: ContentSource,
    private val priority: ContentLanguagePriority,
    private val ioDispatcher: CoroutineDispatcher,
    private val onError: (message: String, error: Throwable?) -> Unit = { _, _ -> },
) {
    private val mutex = Mutex()
    private var cached: List<ReleaseNotes>? = null

    suspend fun all(): List<ReleaseNotes> = mutex.withLock {
        cached ?: read()?.also { cached = it } ?: emptyList()
    }

    /** Запись для [version]; нет — `null`. */
    suspend fun notes(version: AppVersion): ReleaseNotes? = all().firstOrNull { it.version == version }

    private suspend fun read(): List<ReleaseNotes>? = withContext(ioDispatcher) {
        val file = try {
            source.open(FILE).use { json.decodeFromString(VersionsFileDto.serializer(), it.bufferedReader().readText()) }
        } catch (error: IOException) {
            onError("Не прочитан $FILE", error)
            return@withContext null
        } catch (error: IllegalArgumentException) {
            // SerializationException — подкласс IllegalArgumentException.
            onError("Не разобран $FILE", error)
            return@withContext null
        }
        file.toReleaseNotes(priority)
    }

    private companion object {
        const val FILE = "data/versions.json"
        val json = Json { ignoreUnknownKeys = true }
    }
}

internal fun VersionsFileDto.toReleaseNotes(priority: ContentLanguagePriority): List<ReleaseNotes> =
    versions.mapNotNull { dto ->
        val version = AppVersion.parse(dto.version) ?: return@mapNotNull null
        val date = try {
            LocalDate.parse(dto.date)
        } catch (_: DateTimeParseException) {
            return@mapNotNull null
        }
        val translation = priority.select(dto.translations.keyedByLanguage())?.translation ?: return@mapNotNull null
        val items = (translation.items + translation.android).map(String::trim).filter(String::isNotEmpty)
        items.takeIf { it.isNotEmpty() }?.let { ReleaseNotes(version, date, it) }
    }
