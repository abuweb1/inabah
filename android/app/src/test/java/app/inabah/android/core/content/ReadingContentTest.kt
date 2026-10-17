package app.inabah.android.core.content

import app.inabah.android.core.content.model.LetterForm
import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.content.model.ReadingCourse
import app.inabah.android.core.content.model.ReadingMark
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
    fun `Курс из ассетов — 8 знаков, 28 букв и хамза по порядку, 12 шагов и итог`() = runTest {
        val course = loadAssetCourse()

        assertEquals(listOf("fatha", "kasra", "damma", "sukun", "shadda", "fathatan", "kasratan", "dammatan"), course.marks.map { it.id })
        assertEquals((1..29).toList(), course.letters.map { it.number })
        assertEquals("ء", course.letters.last().letter)
        assertEquals((1..13).toList(), course.steps.map { it.number })
        assertEquals(listOf(13), course.steps.filter { it.isFinal }.map { it.number }, "итог - только последний")
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
        val fathatan = course.marks.first { it.id == "fathatan" }
        assertEquals("لًا", lam.syllable(fathatan), "после танвина фатхьи - алиф, он не читается")
        assertEquals("أً", alif.syllable(fathatan), "у алифа второго алифа нет")
        assertEquals("льан", lam.transliteration(fathatan))
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
    fun `Шаг 2 - 60 слов, каждое 6 раз, страница - 5 рядов, транскрипция у всех слов`() = runTest {
        val words = requireNotNull(loadAssetCourse().step(2)?.words)

        assertEquals(60, words.items.size)
        assertEquals(6, words.repeat)
        assertEquals(5, words.rowsPerPage, "15 коротких слов по 3 в ряд")
        assertTrue(loadAssetCourse().steps.mapNotNull { it.words }.all { w -> w.items.all { it.transliteration != null } },
            "транскрипция у всех слов, на экране - только на первой странице (2026-10-16)")
        assertEquals("заъама", words.items.first().transliteration, "порядок слов перемешан - решение пользователя 2026-10-16")
    }

    @Test
    fun `Формы букв строятся из буквы — у несоединяемых и хамзы без лишних хвостов`() = runTest {
        val letters = loadAssetCourse().letters.associateBy { it.letter }
        val ba = letters.getValue("ب")
        val ra = letters.getValue("ر")
        val hamza = letters.getValue("ء")

        assertEquals(listOf("ب", "بـ", "ـبـ", "ـب"), LetterForm.entries.map { ba.form(it) })
        assertEquals(listOf("ر", "ر", "ـر", "ـر"), LetterForm.entries.map { ra.form(it) })
        assertEquals(List(4) { "ء" }, LetterForm.entries.map { hamza.form(it) })
    }

    @Test
    fun `Шаг 3 — формы группы 1 карточками, пояснение по ⓘ, 8 вопросов по форме, 60 слов`() = runTest {
        val step = requireNotNull(loadAssetCourse().step(3))

        val forms = step.blocks.filterIsInstance<ReadingBlock.Forms>().single()
        assertEquals(listOf("ب ت ث ن ي", "ج ح خ"), forms.groups.map { group -> group.joinToString(" ") { it.letter } })
        assertEquals(8, forms.examples.size, "слова-примеры у каждой буквы группы")
        assertEquals(listOf("ب", "ن", "ج"), forms.exampleCard.map { it.letter })
        assertTrue(step.blocks.filterIsInstance<ReadingBlock.Text>().single().info != null)
        assertEquals(8, step.quiz.filterIsInstance<ReadingQuestion.LetterInForm>().size)
        assertEquals(60, step.words?.items?.size)
    }

    @Test
    fun `Шаг 6 — формы группы 4 без проверки, формулы мадда, слова-экзамен`() = runTest {
        val step = requireNotNull(loadAssetCourse().step(6))

        val forms = step.blocks.filterIsInstance<ReadingBlock.Forms>().single()
        assertEquals(listOf("د ذ ر ز", "و ا"), forms.groups.map { group -> group.joinToString(" ") { it.letter } })
        assertTrue(step.quiz.isEmpty(), "формы группы 4 без проверки - решение пользователя")
        val (madd, small) = step.blocks.filterIsInstance<ReadingBlock.Formulas>()
        assertTrue(step.blocks.indexOf(small) < step.blocks.indexOf(forms), "мадд - до форм: в примерах форм есть буквы мадда")
        assertEquals(1, madd.columns, "формулы - по одной в строку (решение пользователя)")
        assertEquals(listOf("بَا", "بِي", "بُو"), madd.items.map { it.result })
        assertEquals(listOf("баа", "бии", "буу"), madd.items.map { it.transliteration })
        assertEquals(listOf(listOf("بَ", "ا")), madd.items.take(1).map { it.parts })
        assertEquals(1, small.columns)
        assertEquals("تَشَـٰبَهَ", small.items.first().parts.single(), "маленький алиф - после фатхьи на соединительной черте")
        assertEquals(3, step.words?.repeat)
    }

    @Test
    fun `Шаг 7 - сукун, закрытые слоги формулами, вопросы со звуком буквы, 60 слов`() = runTest {
        val step = requireNotNull(loadAssetCourse().step(7))

        assertEquals(listOf("sukun"), step.markIds)
        val (pair, table, diphthongs) = step.blocks.filterIsInstance<ReadingBlock.Formulas>()
        assertEquals(listOf("заз", "мин", "къуль"), pair.items.map { it.transliteration })
        assertEquals(75, table.items.size, "25 букв (без ا ء و ي) × фатхьа, касра, домма")
        assertEquals(6, table.sample, "на уроке - 6 случайных, весь алфавит - по кнопке")
        assertEquals("ъаб", table.items.first().transliteration)
        assertEquals(listOf("ъав", "ъай"), diphthongs.items.map { it.transliteration })
        val closed = step.quiz.filterIsInstance<ReadingQuestion.ClosedSyllable>()
        assertEquals(step.quiz.size, closed.size, "проверка - закрытые слоги, как в «Весь алфавит»")
        assertEquals("إِبْ", closed.first().arabic)
        assertEquals("ъиб", closed.first().transliteration("kasra"))
        val words = requireNotNull(step.words)
        assertEquals(60, words.items.size)
        assertEquals(6, words.repeat)
        assertEquals(6, words.items.count { it.isPhrase })
    }

    @Test
    fun `Шаг 8 - танвин, знаки, равенства, слоги только с танвином, 60 слов`() = runTest {
        val step = requireNotNull(loadAssetCourse().step(8))

        assertEquals(listOf("fathatan", "kasratan", "dammatan"), step.markIds)
        val marks = step.blocks.filterIsInstance<ReadingBlock.Marks>().single()
        assertEquals("ب", marks.example.letter)
        val equal = step.blocks.filterIsInstance<ReadingBlock.Formulas>().single()
        assertEquals(listOf("بًا", "بٍ", "بٌ"), equal.items.map { it.result })
        assertEquals(listOf("бан", "бин", "бун"), equal.items.map { it.transliteration })
        val table = step.blocks.filterIsInstance<ReadingBlock.Syllables>().single()
        assertEquals(listOf("fathatan", "kasratan", "dammatan"), table.marks.map { it.id }, "столбцы - только танвин")
        assertEquals(28, table.letters.size)
        assertEquals(12, table.sample)
        assertTrue(step.quiz.all { it is ReadingQuestion.Syllable && it.mark.id in ReadingMark.TANWIN })
        val words = requireNotNull(step.words)
        assertEquals(60, words.items.size)
        assertEquals(6, words.repeat)
    }

    @Test
    fun `Шаг 9 - шадда, равенства книги, вопросы со словом, 60 слов`() = runTest {
        val step = requireNotNull(loadAssetCourse().step(9))

        assertEquals(listOf("shadda"), step.markIds)
        val equal = step.blocks.filterIsInstance<ReadingBlock.Formulas>().single()
        assertEquals(9, equal.items.size)
        assertEquals(listOf("ъабба", "джиддан", "роббун"), listOf(0, 6, 8).map { equal.items[it].transliteration })
        val words = step.quiz.filterIsInstance<ReadingQuestion.Word>()
        assertEquals(step.quiz.size, words.size)
        assertEquals("شَكُّ", words.first().arabic)
        assertEquals("шакку", words.first().options[words.first().answer])
        assertTrue(words.all { it.explanation != null && it.options.size == 3 })
        assertEquals(60, step.words?.items?.size)
        assertEquals("мухьаммадун", step.words?.items?.first { it.arabic == "مُحَمَّدٌ" }?.transliteration)
    }

    @Test
    fun `Шаг 10 - артикль, как пишется и как читается, лунные и солнечные буквы справкой, вопросы со словом`() = runTest {
        val course = loadAssetCourse()
        val step = requireNotNull(course.step(10))

        assertEquals("Определённый артикль", step.title)
        assertEquals("Мадд перед артиклем", course.step(11)?.title, "страницы 40-41 - отдельным шагом (решение пользователя)")
        val read = step.blocks.filterIsInstance<ReadingBlock.Formulas>().flatMap { it.items }
        assertEquals("йавми-ддиини", read.first { it.result == "يَوْمِدِّينِ" }.transliteration, "в середине не читаются ни ٱ, ни лям перед солнечной")
        // Начало чтения: хӏамзату-львасли - как пишется, «ъ» - в транскрипции, без «=» (решение пользователя).
        val start = read.first { it.result == "ٱلْحَمْدُ" && it.parts.isEmpty() }
        assertEquals("ъальхьамду", start.transliteration)
        assertTrue(step.blocks.none { it is ReadingBlock.Rule }, "солнечные и лунные буквы - не на уроке (решение пользователя)")
        val extra = requireNotNull(step.extra)
        assertEquals("Солнечные и лунные буквы", extra.title)
        assertEquals(listOf(14, 14), extra.blocks.filterIsInstance<ReadingBlock.Rule>().map { it.letters.size })
        assertTrue(step.quiz.isNotEmpty() && step.quiz.all { it is ReadingQuestion.Word })
        assertEquals(60, step.words?.items?.size)
    }

    @Test
    fun `Шаг 11 - мадд перед артиклем, буква мадда не тянется, пары слов`() = runTest {
        val step = requireNotNull(loadAssetCourse().step(11))

        val read = step.blocks.filterIsInstance<ReadingBlock.Formulas>().single().items
        assertEquals("фи-лькъубуури", read.first().transliteration)
        val words = requireNotNull(step.words)
        assertTrue(words.pairs, "пары страницы 41 - рядом (решение пользователя)")
        assertEquals("маа", words.items.first { it.arabic == "مَا" }.transliteration, "отдельно - долго")
        assertEquals("ма-лькъоориӏату", words.items.first { it.arabic == "مَا ٱلْقَارِعَةُ" }.transliteration, "перед артиклем - коротко, та марбута - как та")
        assertTrue(step.quiz.all { it is ReadingQuestion.Word })
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
