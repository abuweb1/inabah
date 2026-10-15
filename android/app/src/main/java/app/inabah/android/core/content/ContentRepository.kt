package app.inabah.android.core.content

import app.inabah.android.core.content.dto.AzkarFileDto
import app.inabah.android.core.content.dto.CollectionInfoDto
import app.inabah.android.core.content.dto.HadithDto
import app.inabah.android.core.content.dto.ReadingFileDto
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.CollectionInfo
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.ReadingCourse
import app.inabah.android.core.content.model.Zikr
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Источник азкаров, хадисов и сведений о сборниках. Ошибки — [ContentError]. */
interface ContentRepository {
    suspend fun azkar(section: AzkarSection): List<Zikr>
    suspend fun hadiths(collection: HadithCollection): List<Hadith>

    /** «О сборнике»; сборника нет в файле — [CollectionInfo.Empty]. */
    suspend fun collectionInfo(collection: HadithCollection): CollectionInfo

    /**
     * «Учимся читать» (`data/reading.json`). Реализация по умолчанию - «файла нет»: тестовым
     * репозиториям азкаров и хадисов курс не нужен.
     */
    suspend fun reading(): ReadingCourse = throw ContentError.ResourceMissing(READING_FILE)
}

internal const val READING_FILE = "reading.json"

/** Открывает файл по пути внутри ассетов (`data/azkar.json`); нет файла — [FileNotFoundException]. */
fun interface ContentSource {
    @Throws(IOException::class)
    fun open(path: String): InputStream
}

/**
 * Контент из `assets/data/` (iOS `BundleContentRepository`). Файл разбирается один раз:
 * кэш под [Mutex], одновременные запросы ждут первый разбор. Неудачный разбор не кэшируется —
 * следующий запрос пробует снова.
 */
class AssetContentRepository(
    private val source: ContentSource,
    private val priority: ContentLanguagePriority,
    private val ioDispatcher: CoroutineDispatcher,
) : ContentRepository {
    private val mutex = Mutex()
    private var azkarFile: AzkarFileDto? = null
    private val hadithFiles = mutableMapOf<HadithCollection, List<HadithDto>>()
    private var collectionsFile: Map<String, CollectionInfoDto>? = null
    private var readingCourse: ReadingCourse? = null

    override suspend fun azkar(section: AzkarSection): List<Zikr> {
        val file = mutex.withLock {
            azkarFile ?: decode(AZKAR_FILE, AzkarFileDto.serializer()).also { azkarFile = it }
        }
        val items = when (section) {
            AzkarSection.Morning -> file.morning
            AzkarSection.Evening -> file.evening
        }
        return items.map { it.toZikr(section, priority) }
    }

    override suspend fun hadiths(collection: HadithCollection): List<Hadith> {
        val items = mutex.withLock {
            hadithFiles[collection]
                ?: decode("${collection.key}.json", ListSerializer(HadithDto.serializer()))
                    .also { hadithFiles[collection] = it }
        }
        return items.map { it.toHadith(collection, priority) }
    }

    override suspend fun collectionInfo(collection: HadithCollection): CollectionInfo {
        val file = mutex.withLock {
            collectionsFile
                ?: decode(COLLECTIONS_FILE, MapSerializer(String.serializer(), CollectionInfoDto.serializer()))
                    .also { collectionsFile = it }
        }
        return file[collection.key]?.toCollectionInfo(priority) ?: CollectionInfo.Empty
    }

    override suspend fun reading(): ReadingCourse = mutex.withLock {
        readingCourse ?: decode(READING_FILE, ReadingFileDto.serializer()).let { file ->
            try {
                file.toReadingCourse(priority)
            } catch (error: IllegalArgumentException) {
                // Ссылка на неизвестный знак или букву, запись без перевода.
                throw ContentError.DecodingFailed(READING_FILE, error)
            }
        }.also { readingCourse = it }
    }

    private suspend fun <T> decode(file: String, deserializer: DeserializationStrategy<T>): T =
        withContext(ioDispatcher) {
            val text = try {
                source.open("$DATA_DIR/$file").use { it.bufferedReader().readText() }
            } catch (_: FileNotFoundException) {
                throw ContentError.ResourceMissing(file)
            } catch (error: IOException) {
                throw ContentError.Unknown("Не удалось прочитать $file", error)
            }
            try {
                json.decodeFromString(deserializer, text)
            } catch (error: IllegalArgumentException) {
                // SerializationException — подкласс IllegalArgumentException.
                throw ContentError.DecodingFailed(file, error)
            }
        }

    private companion object {
        const val DATA_DIR = "data"
        const val AZKAR_FILE = "azkar.json"
        const val COLLECTIONS_FILE = "collections.json"

        val json = Json { ignoreUnknownKeys = true }
    }
}
