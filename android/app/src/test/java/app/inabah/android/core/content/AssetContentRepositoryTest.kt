package app.inabah.android.core.content

import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.HadithCollection
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * «Контент в ассетах»: реальные JSON из `data/` в выходной папке задачи `copySharedContent`
 * (путь передаёт Gradle — `inabah.assetsDir`), плюс правила кэша репозитория.
 */
class AssetContentRepositoryTest {
    private val assetsDir = File(
        requireNotNull(System.getProperty("inabah.assetsDir")) { "Gradle не передал inabah.assetsDir" },
    )

    private fun repository(source: ContentSource = FileContentSource(assetsDir), dispatcher: CoroutineDispatcher) =
        AssetContentRepository(source, ContentLanguagePriority(listOf(ContentLanguage.Base)), dispatcher)

    @Test
    fun `Азкары раздела загружаются`() = runTest {
        val repository = repository(dispatcher = StandardTestDispatcher(testScheduler))
        for (section in AzkarSection.entries) {
            val azkar = repository.azkar(section)

            assertEquals((1..16).toList(), azkar.map { it.number }, "раздел $section")
            assertTrue(azkar.all { it.section == section })
            assertTrue(azkar.all { it.translation?.language == ContentLanguage.Base })
            assertTrue(azkar.all { it.repetitions >= 1 })
        }
    }

    @Test
    fun `Аудиофайл зикра лежит в ассетах`() = runTest {
        val repository = repository(dispatcher = StandardTestDispatcher(testScheduler))
        for (section in AzkarSection.entries) {
            val missing = repository.azkar(section)
                .filterNot { File(assetsDir, "audio/${it.audioFileName}").isFile }
                .map { it.audioFileName }

            assertEquals(emptyList(), missing, "нет аудио в разделе $section")
        }
    }

    @Test
    fun `Сборник хадисов загружается`() = runTest {
        val repository = repository(dispatcher = StandardTestDispatcher(testScheduler))
        val expected = mapOf(HadithCollection.Nawawi to 50, HadithCollection.Qudsi to 40, HadithCollection.Ajurri to 40)
        for ((collection, count) in expected) {
            val hadiths = repository.hadiths(collection)

            assertEquals((1..count).toList(), hadiths.map { it.number }, "сборник $collection")
            assertTrue(hadiths.all { it.translation?.language == ContentLanguage.Base })
            assertTrue(hadiths.all { it.arabic.isNotEmpty() })
        }
    }

    @Test
    fun `Служебные данные data pending не попадают в ассеты`() {
        assertTrue(File(assetsDir, "data/azkar.json").isFile, "ассеты не скопированы")
        assertFalse(File(assetsDir, "data/pending").exists())
        assertFalse(assetsDir.walk().any { it.name == "missing-texts.json" })
    }

    @Test
    fun `Одновременные запросы разбирают файл один раз`() = runTest {
        val source = CountingSource(FileContentSource(assetsDir))
        val repository = repository(source, StandardTestDispatcher(testScheduler))

        listOf(
            async { repository.azkar(AzkarSection.Morning) },
            async { repository.azkar(AzkarSection.Evening) },
            async { repository.azkar(AzkarSection.Morning) },
        ).awaitAll()

        assertEquals(1, source.opened.get())
    }

    @Test
    fun `Нет файла — ResourceMissing, неудача не кэшируется`() = runTest {
        val source = CountingSource { throw FileNotFoundException(it) }
        val repository = repository(source, StandardTestDispatcher(testScheduler))

        repeat(2) {
            val error = assertFailsWith<ContentError.ResourceMissing> { repository.hadiths(HadithCollection.Qudsi) }
            assertEquals("qudsi.json", error.file)
        }
        assertEquals(2, source.opened.get())
    }

    @Test
    fun `Повреждённый файл — DecodingFailed`() = runTest {
        val repository = repository({ "{ не json".byteInputStream() }, StandardTestDispatcher(testScheduler))

        val error = assertFailsWith<ContentError.DecodingFailed> { repository.azkar(AzkarSection.Morning) }

        assertEquals("azkar.json", error.file)
    }

    private class FileContentSource(private val root: File) : ContentSource {
        override fun open(path: String): InputStream = File(root, path).inputStream()
    }

    private class CountingSource(private val delegate: ContentSource) : ContentSource {
        val opened = AtomicInteger()

        override fun open(path: String): InputStream {
            opened.incrementAndGet()
            return delegate.open(path)
        }
    }
}
