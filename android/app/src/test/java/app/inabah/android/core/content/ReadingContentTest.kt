package app.inabah.android.core.content

import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.content.model.ReadingCourse
import app.inabah.android.core.content.model.ReadingQuestion
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** «Учимся читать»: `reading.json` из ассетов и ошибки данных. */
class ReadingContentTest {
    @Test
    fun `Курс из ассетов — 8 знаков, 28 букв и хамза по порядку, 10 шагов`() = runTest {
        val course = loadAssetCourse()

        assertEquals(listOf("fatha", "kasra", "damma", "sukun", "shadda", "fathatan", "kasratan", "dammatan"), course.marks.map { it.id })
        assertEquals((1..29).toList(), course.letters.map { it.number })
        assertEquals("ء", course.letters.last().letter)
        assertEquals((1..10).toList(), course.steps.map { it.number })
        assertEquals(setOf("ا", "د", "ذ", "ر", "ز", "و", "ء"), course.letters.filterNot { it.joinsNext }.map { it.letter }.toSet())
        assertTrue(course.steps.all { it.title.isNotBlank() && '—' !in it.title + it.subtitle })
    }

    @Test
    fun `Слоги и звук строятся из буквы и знака — у алифа хамза на подставке`() = runTest {
        val course = loadAssetCourse()
        val fatha = course.marks.first { it.id == "fatha" }
        val sukun = requireNotNull(course.sukun)
        val lam = course.letters.first { it.letter == "ل" }
        val alif = course.letters.first { it.letter == "ا" }

        assertEquals("لَ", lam.syllable(fatha))
        assertEquals("льа", lam.transliteration(fatha))
        assertEquals("ль", lam.transliteration(sukun), "с сукуном — звук буквы")
        assertEquals("ـلْ", lam.soundArabic(sukun))
        val kasra = course.marks.first { it.id == "kasra" }
        val hamza = course.letters.last()
        assertEquals("أَ", alif.syllable(fatha))
        assertEquals("إِ", alif.syllable(kasra))
        assertEquals("أْ", alif.soundArabic(sukun))
        assertTrue(alif.note != null, "у алифа - пояснение")
        assertEquals("ءَ", hamza.syllable(fatha))
        assertEquals("ءْ", hamza.soundArabic(sukun), "хамза ни с чем не соединяется - без татвиля")
    }

    @Test
    fun `Шаг 2 — знаки, весь алфавит с огласовками, правила и вопросы`() = runTest {
        val course = loadAssetCourse()
        val step = requireNotNull(course.step(2))
        assertEquals("Огласовки", step.title)

        val marks = step.blocks.filterIsInstance<ReadingBlock.Marks>().single()
        assertEquals(listOf("fatha", "kasra", "damma"), marks.marks.map { it.id })
        assertEquals("ب", marks.example.letter, "знаки — на первой букве таблицы")
        val table = step.blocks.filterIsInstance<ReadingBlock.Syllables>().single()
        assertEquals(listOf("fatha", "kasra", "damma"), table.marks.map { it.id }, "сукун и шадда — не столбцы")
        assertEquals(course.letters.dropLast(1), table.letters, "28 букв: хӏамза — в слогах алифа")
        assertEquals(12, table.sample, "на уроке — 12 случайных слогов, весь алфавит — отдельным экраном")
        val rules = step.blocks.filterIsInstance<ReadingBlock.Rule>()
        assertEquals(listOf(7, 1), rules.map { it.letters.size }, "твёрдые буквы и ро")
        assertTrue(rules.all { it.transliteration.isNotBlank() && it.text.isNotBlank() })
        assertTrue(step.quiz.isNotEmpty() && step.quiz.all { it is ReadingQuestion.Syllable })
    }

    @Test
    fun `Неизвестный знак в шаге — DecodingFailed, неизвестный тип блока пропускается`() = runTest {
        val broken = repository(minimalJson(stepMarks = "[\"unknown\"]"))
        val error = assertFailsWith<ContentError.DecodingFailed> { broken.reading() }
        assertEquals("reading.json", error.file)

        val future = repository(minimalJson(blocks = "[{\"type\":\"video\"},{\"type\":\"letters\"}]")).reading()
        assertEquals(listOf<ReadingBlock>(ReadingBlock.Letters(future.letters)), future.steps.single().blocks)
    }

    @Test
    fun `Нет файла — ResourceMissing, как и у репозитория по умолчанию`() = runTest {
        val missing = AssetContentRepository(
            { throw FileNotFoundException(it) },
            ContentLanguagePriority(listOf(ContentLanguage.Base)),
            StandardTestDispatcher(testScheduler),
        )
        assertFailsWith<ContentError.ResourceMissing> { missing.reading() }
        assertFailsWith<ContentError.ResourceMissing> { InMemoryContentRepository().reading() }
    }

    @Test
    fun `Шаг 2 — 60 слов группами по 15, каждое 6 раз, транскрипция только у первых 12`() = runTest {
        val words = requireNotNull(loadAssetCourse().step(2)?.words)

        assertEquals(60, words.items.size)
        assertEquals(6, words.repeat)
        assertEquals(listOf(15, 15, 15, 15), words.groups.map { it.size })
        assertTrue(words.items.take(12).all { it.transliteration != null })
        assertTrue(words.items.drop(12).all { it.transliteration == null })
        assertEquals("зарода", words.items.first().transliteration)
    }

    private fun TestScope.repository(json: String) = AssetContentRepository(
        { ByteArrayInputStream(json.toByteArray()) },
        ContentLanguagePriority(listOf(ContentLanguage.Base)),
        StandardTestDispatcher(testScheduler),
    )

    private fun minimalJson(stepMarks: String = "[\"fatha\"]", blocks: String = "[]") = """
        {
          "marks": [{ "id": "fatha", "sign": "َ", "translations": { "ru": { "name": "фатха" } } }],
          "letters": [{ "id": 1, "letter": "ب", "name": "بَاء", "translations": { "ru": { "name": "Ба" } } }],
          "steps": [{ "id": 1, "marks": $stepMarks, "translations": { "ru": { "title": "Шаг" } }, "blocks": $blocks }]
        }
    """.trimIndent()
}

/** Курс из настоящего `assets/data/reading.json` (путь передаёт Gradle). */
suspend fun TestScope.loadAssetCourse(): ReadingCourse {
    val assetsDir = File(requireNotNull(System.getProperty("inabah.assetsDir")) { "Gradle не передал inabah.assetsDir" })
    return AssetContentRepository(
        { File(assetsDir, it).inputStream() },
        ContentLanguagePriority(listOf(ContentLanguage.Base)),
        StandardTestDispatcher(testScheduler),
    ).reading()
}
