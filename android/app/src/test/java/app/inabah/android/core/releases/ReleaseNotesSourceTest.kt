package app.inabah.android.core.releases

import app.inabah.android.core.content.ContentLanguage
import app.inabah.android.core.content.ContentLanguagePriority
import app.inabah.android.core.content.ContentSource
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** История версий из `versions.json`: язык, пункты платформы, битые записи и файл. */
class ReleaseNotesSourceTest {
    private val priority = ContentLanguagePriority(listOf(ContentLanguage.Base))
    private val assetsDir = File(requireNotNull(System.getProperty("inabah.assetsDir")))

    private fun TestScope.source(
        priority: ContentLanguagePriority = this@ReleaseNotesSourceTest.priority,
        onError: (String) -> Unit = {},
        open: ContentSource,
    ) = ReleaseNotesSource(open, priority, StandardTestDispatcher(testScheduler)) { message, _ -> onError(message) }

    private fun TestScope.source(json: String, priority: ContentLanguagePriority = this@ReleaseNotesSourceTest.priority) =
        source(priority) { json.byteInputStream() }

    @Test
    fun `Настоящий versions json — версии сверху вниз, у каждой пункты`() = runTest {
        val notes = source { File(assetsDir, it).inputStream() }.all()

        assertTrue(notes.isNotEmpty())
        assertEquals(notes.sortedByDescending { it.version }, notes, "новые сверху")
        notes.forEach { assertTrue(it.items.isNotEmpty(), "${it.version}: нет пунктов") }
    }

    @Test
    fun `Общие пункты, за ними пункты Android, пункты iOS не показываются`() = runTest {
        val json = """{ "versions": [ { "version": "1.1.1", "date": "2026-10-12", "translations": { "ru": {
            "items": ["Общее"], "android": ["Только Android"], "ios": ["Только iOS"] } } } ] }"""

        val notes = assertNotNull(source(json).notes(AppVersion.parse("1.1.1")!!))

        assertEquals(listOf("Общее", "Только Android"), notes.items)
        assertEquals(LocalDate.of(2026, 10, 12), notes.date)
    }

    @Test
    fun `Язык — по приоритету, нет нужного — русский`() = runTest {
        val json = """{ "versions": [ { "version": "1.1.1", "date": "2026-10-12", "translations": {
            "ru": { "items": ["Новое"] }, "en": { "items": ["New"] } } } ] }"""
        val english = ContentLanguagePriority(listOf(ContentLanguage.of("en"), ContentLanguage.Base))
        val german = ContentLanguagePriority(listOf(ContentLanguage.of("de"), ContentLanguage.Base))

        assertEquals(listOf("New"), source(json, english).all().single().items)
        assertEquals(listOf("Новое"), source(json, german).all().single().items)
    }

    @Test
    fun `Неверный номер, дата или пустые пункты — версия пропускается, остальные на месте`() = runTest {
        val json = """{ "versions": [
            { "version": "1.3", "date": "2026-10-12", "translations": { "ru": { "items": ["a"] } } },
            { "version": "1.2.0", "date": "2026-02-30", "translations": { "ru": { "items": ["b"] } } },
            { "version": "1.1.2", "date": "2026-10-12", "translations": { "ru": { "items": ["  "], "ios": ["c"] } } },
            { "version": "1.1.1", "date": "2026-10-12", "translations": { "ru": { "items": [" Новое "] } } } ] }"""

        val notes = source(json).all()

        assertEquals(listOf("1.1.1"), notes.map { it.version.text })
        assertEquals(listOf("Новое"), notes.single().items, "пробелы по краям убраны")
    }

    @Test
    fun `Нет файла или он повреждён — пусто и сообщение`() = runTest {
        val errors = mutableListOf<String>()

        assertTrue(source(onError = { errors += it }) { throw FileNotFoundException(it) }.all().isEmpty())
        assertTrue(source(onError = { errors += it }) { "{ не json".byteInputStream() }.all().isEmpty())
        assertNull(source(onError = { errors += it }) { "{ не json".byteInputStream() }.notes(AppVersion.parse("1.1.1")!!))

        assertEquals(3, errors.size, "каждый сбой — в лог: $errors")
    }
}
