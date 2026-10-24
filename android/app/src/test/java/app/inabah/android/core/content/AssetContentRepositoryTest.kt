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
        // Пополнены 2026-10-24 из файла пользователя: утро 16 → 21, вечер 16 → 22 (docs/analysis/2026-10-24-azkar-new-file.md).
        val expected = mapOf(AzkarSection.Morning to 21, AzkarSection.Evening to 22)
        for (section in AzkarSection.entries) {
            val azkar = repository.azkar(section)

            assertEquals((1..expected.getValue(section)).toList(), azkar.map { it.number }, "раздел $section")
            assertTrue(azkar.all { it.section == section })
            assertTrue(azkar.all { it.translation?.language == ContentLanguage.Base })
            assertTrue(azkar.all { it.repetitions >= 1 })
        }
    }

    // Свои записи (2026-10-12, docs/process/audio.md): у первых 16 зикров раздела — файл в assets/audio/;
    // зикры, добавленные 2026-10-24, пока без записи (кнопка ▶ - «Аудио скоро»).
    @Test
    fun `Записи азкаров - у первых 16 зикров есть файл в ассетах, новые без записи, 20 файлов азкаров, все m4a`() = runTest {
        val repository = repository(dispatcher = StandardTestDispatcher(testScheduler))
        val bundled = File(assetsDir, "audio").listFiles().orEmpty().map { it.name }.toSet()
        for (section in AzkarSection.entries) {
            val (recorded, added) = repository.azkar(section).partition { it.number <= RECORDED_AZKAR }
            for (zikr in recorded) {
                assertTrue(zikr.audioFileName in bundled, "$section ${zikr.number}: нет файла ${zikr.audioFileName}")
            }
            assertTrue(added.none { it.hasAudio }, "$section: у новых зикров записи ещё нет")
        }
        // Записи курса «Учимся читать» (reading_*) лежат рядом - считаются отдельно.
        val azkar = bundled.filter { it.startsWith("morning_") || it.startsWith("evening_") }
        assertEquals(20, azkar.size)
        assertTrue(bundled.all { it.endsWith(".m4a") }, "только .m4a: $bundled")
    }

    // Вечерний зикр, совпадающий с утренним, берёт его запись — только если арабский тот же (без огласовок:
    // у утреннего и вечернего 7 знаки в «اللَّهُمَّ» набраны в другом порядке).
    @Test
    fun `Общая запись утреннего и вечернего — только при одинаковом арабском`() = runTest {
        val repository = repository(dispatcher = StandardTestDispatcher(testScheduler))
        val all = AzkarSection.entries.flatMap { repository.azkar(it) }
        val shared = all.filter { it.hasAudio }.groupBy { it.audioFileName }.filterValues { it.size > 1 }

        assertEquals(12, shared.size)
        for ((file, azkar) in shared) {
            assertEquals(1, azkar.map { bareArabic(it.arabic) }.toSet().size, "$file: разный арабский текст")
        }
    }

    private companion object {
        /** Зикров со своими записями в каждом разделе (2026-10-12). */
        const val RECORDED_AZKAR = 16
    }

    /** Арабский без огласовок и пробелов — для сравнения текстов. */
    private fun bareArabic(text: String): String =
        text.filterNot { it in '\u064B'..'\u0652' || it == '\u0670' || it.isWhitespace() }

    @Test
    fun `Сборник хадисов загружается`() = runTest {
        val repository = repository(dispatcher = StandardTestDispatcher(testScheduler))
        val expected = mapOf(HadithCollection.Nawawi to 50, HadithCollection.Qudsi to 42, HadithCollection.Ajurri to 40)
        for ((collection, count) in expected) {
            val hadiths = repository.hadiths(collection)

            assertEquals((1..count).toList(), hadiths.map { it.number }, "сборник $collection")
            assertTrue(hadiths.all { it.translation?.language == ContentLanguage.Base })
            assertTrue(hadiths.all { it.arabic.isNotEmpty() })
        }
    }

    @Test
    fun `О сборнике из настоящего collections json — у своей подборки къудси составителя нет`() = runTest {
        val repository = repository(FileContentSource(assetsDir), StandardTestDispatcher(testScheduler))

        for (collection in HadithCollection.entries) {
            val info = repository.collectionInfo(collection)
            assertTrue(info.about.isNotEmpty(), "нет «О книге» у $collection")
            assertTrue(info.authors.all { it.name.isNotBlank() })
        }
        assertEquals(1, repository.collectionInfo(HadithCollection.Nawawi).authors.size)
        assertEquals(1, repository.collectionInfo(HadithCollection.Ajurri).authors.size)
        assertTrue(repository.collectionInfo(HadithCollection.Qudsi).authors.isEmpty())
    }

    @Test
    fun `О сборнике — нет файла — ResourceMissing, неудача не кэшируется`() = runTest {
        val source = CountingSource { throw FileNotFoundException(it) }
        val repository = repository(source, StandardTestDispatcher(testScheduler))

        repeat(2) {
            val error = assertFailsWith<ContentError.ResourceMissing> { repository.collectionInfo(HadithCollection.Nawawi) }
            assertEquals("collections.json", error.file)
        }
        assertEquals(2, source.opened.get())
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
