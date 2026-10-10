package app.inabah.android.core.reminders

import app.inabah.android.core.content.ContentLanguage
import app.inabah.android.core.content.ContentLanguagePriority
import app.inabah.android.core.content.ContentSource
import app.inabah.android.core.content.model.AzkarSection
import java.io.File
import java.io.FileNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Тексты напоминаний из `reminders.json` и подстановки. */
class ReminderTextsTest {
    private val priority = ContentLanguagePriority(listOf(ContentLanguage.Base))
    private val assetsDir = File(requireNotNull(System.getProperty("inabah.assetsDir")))

    @Test
    fun `Подстановки заменяются, отсутствующие в тексте — не мешают`() {
        val text = ReminderText("Хадис дня · {collection}, № {number}", "{text}")

        assertEquals(
            ReminderText("Хадис дня · 40 хадисов ан-Навави, № 5", "Перевод"),
            text.fill(mapOf("collection" to "40 хадисов ан-Навави", "number" to "5", "text" to "Перевод")),
        )
        assertEquals(ReminderText("Утренние азкары", "Лучшее время"), ReminderText("Утренние азкары", "Лучшее время").fill(mapOf("end" to "12:00")))
    }

    @Test
    fun `Настоящий reminders json — все виды и разделы на русском`() = runTest {
        val texts = assertNotNull(ReminderTextsSource({ File(assetsDir, it).inputStream() }, priority, StandardTestDispatcher(testScheduler)).texts())

        for (kind in ReminderKind.entries) assertNotNull(texts.forKind(kind), "нет текста для $kind")
        assertEquals(AzkarSection.entries.toSet(), texts.azkarStart.keys)
    }

    @Test
    fun `Нет файла, он повреждён или нет перевода на доступных языках — null и сообщение`() = runTest {
        val errors = mutableListOf<String>()
        fun source(open: ContentSource) = ReminderTextsSource(open, priority, StandardTestDispatcher(testScheduler)) { message, _ -> errors += message }

        assertNull(source { throw FileNotFoundException(it) }.texts())
        assertNull(source { "{ не json".byteInputStream() }.texts())
        val english = """{ "translations": { "en": { "hadithOfDay": { "title": "Hadith", "body": "{text}" } } } }"""
        assertNull(source { english.byteInputStream() }.texts())

        assertEquals(3, errors.size, "каждый сбой — в лог (аудит 2026-10-10, К5): $errors")
    }
}
