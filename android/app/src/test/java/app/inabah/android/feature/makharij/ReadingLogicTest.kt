package app.inabah.android.feature.makharij

import app.inabah.android.core.content.loadAssetCourse
import app.inabah.android.core.content.model.LetterForm
import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.content.model.ReadingQuestion
import app.inabah.android.core.content.model.ReadingWord
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** «Учимся читать»: цепочка листа буквы, варианты ответа, текущий шаг. */
class ReadingLogicTest {
    @Test
    fun `Цепочка — название и слоги, звук только с шага «Сукун», до шага 2 огласовки не пройдены`() = runTest {
        val course = loadAssetCourse()
        val lam = course.letters.first { it.letter == "ل" }
        val sukunStep = course.steps.first { "sukun" in it.markIds }.number

        val chain = letterChain(course, lam, stepNumber = 1)

        assertEquals(listOf(LetterChainItem.Kind.Name) + List(3) { LetterChainItem.Kind.Syllable }, chain.map { it.kind })
        assertEquals(listOf("льаам", "льа", "ли", "льу"), chain.map { it.transliteration })
        assertEquals(listOf(true, false, false, false), chain.map { it.isLearned })
        assertEquals(List(4) { true }, letterChain(course, lam, stepNumber = 2).map { it.isLearned })
        assertEquals(LetterChainItem.Kind.Sound, letterChain(course, lam, sukunStep).first().kind, "с шага «Сукун» - звук первым")
    }

    @Test
    fun `Варианты слога — тот же слог с пройденными знаками, по порядку`() = runTest {
        val course = loadAssetCourse()
        val question = course.step(2)!!.quiz.first() as ReadingQuestion.Syllable

        val options = quizOptions(course, question, stepNumber = 2)

        assertEquals(listOf("ба", "би", "бу"), options.map { it.text })
        assertEquals(listOf(question.letter.transliteration(question.mark)), options.filter { it.isCorrect }.map { it.text })
    }

    @Test
    fun `Варианты буквы — она и соседние, у первой и последней — с одной стороны`() = runTest {
        val course = loadAssetCourse()
        fun names(letter: String) =
            quizOptions(course, ReadingQuestion.LetterName(course.letters.first { it.letter == letter }), 1).map { it.text }

        assertEquals(listOf("Алиф", "Ба", "Та"), names("ب"))
        assertEquals(listOf("Алиф", "Ба", "Та"), names("ا"))
        assertEquals(course.letters.takeLast(3).map { it.name }, names("ي"))
        assertEquals(1, quizOptions(course, ReadingQuestion.LetterName(course.letters[5]), 1).count { it.isCorrect })
    }

    @Test
    fun `Текущий шаг — первый непройденный с уроком, все пройдены — нет`() = runTest {
        val course = loadAssetCourse()
        val withLessons = course.steps.filter { it.hasLesson }.map { it.number }

        assertEquals(1, currentStep(course, emptySet())?.number)
        assertEquals(2, currentStep(course, setOf(1))?.number)
        assertEquals(3, currentStep(course, setOf(1, 2))?.number)
        assertEquals(4, currentStep(course, setOf(1, 2, 3))?.number)
        assertEquals(5, currentStep(course, setOf(1, 2, 3, 4))?.number)
        assertEquals(6, currentStep(course, setOf(1, 2, 3, 4, 5))?.number)
        assertEquals(7, currentStep(course, setOf(1, 2, 3, 4, 5, 6))?.number)
        assertEquals(8, currentStep(course, (1..7).toSet())?.number)
        assertEquals(9, currentStep(course, (1..8).toSet())?.number)
        assertNull(currentStep(course, (1..9).toSet()), "шаги 10-11 без урока пропускаются")
        assertNull(currentStep(course, withLessons.toSet()))
        assertEquals(2.0 / 11, completedFraction(course, setOf(1, 2, 99)), "итог не считается")
    }

    @Test
    fun `Страницы слов - короткие по 3 впереди, длинные по 2, словосочетания во всю ширину, без неполных рядов`() {
        // «д» - длинное (по 2), «к» - короткое (по 3), с пробелом - словосочетание.
        val words = listOf("д1", "к1", "т т", "к2", "д2", "к3", "к4", "д3").map { ReadingWord(it, null) }
        val columnsOf = { word: ReadingWord -> if (word.arabic.startsWith("д")) 2 else 3 }

        val pages = wordPages(words, columnsOf, rowsPerPage = 2)

        // к4 - лишнее в очереди по 3 - уходит в начало очереди по 2.
        assertEquals(
            listOf(listOf(3 to listOf(1, 3, 5), 2 to listOf(6, 0)), listOf(2 to listOf(4, 7), 1 to listOf(2))),
            pages.map { page -> page.map { row -> row.columns to row.words.map { it.index } } },
        )
        // Нечётно длинных - последнее во всю ширину.
        val odd = listOf("д1", "д2", "д3").map { ReadingWord(it, null) }
        assertEquals(listOf(2, 1), wordPages(odd, columnsOf, 5).single().map { it.columns })
        assertEquals(1, wordPages(listOf(ReadingWord("т т", null)), { 3 }, 5).single().single().columns, "словосочетание - всегда 1")
        assertEquals(3, wordPages(words, { 7 }, 5).first().first().columns, "больше 3 в ряд не бывает")
    }

    @Test
    fun `Итог не считается в «N из M», курс пройден - только вместе с итогом`() = runTest {
        val course = loadAssetCourse()
        val all = course.steps.map { it.number }.toSet()
        val final = course.steps.single { it.isFinal }.number

        assertEquals(11, numberedStepCount(course))
        assertEquals(11, completedCount(course, all))
        assertEquals(1.0, completedFraction(course, all - final))
        assertFalse(isCourseDone(course, all - final))
        assertTrue(isCourseDone(course, all))
    }

    @Test
    fun `Вопрос с сукуном — верный вариант есть, его транскрипция — звук буквы`() = runTest {
        val course = loadAssetCourse()
        val ba = course.letters.first { it.letter == "ب" }
        val question = ReadingQuestion.Syllable(ba, requireNotNull(course.sukun))

        val sukunStep = course.steps.first { "sukun" in it.markIds }.number

        val options = quizOptions(course, question, sukunStep)

        assertEquals(listOf("б"), options.filter { it.isCorrect }.map { it.text })
        assertEquals(3, options.size)
    }

    @Test
    fun `Закрытый слог - варианты с фатхьой, касрой и доммой алифа, верный - своя огласовка`() = runTest {
        val course = loadAssetCourse()
        val step = requireNotNull(course.step(7))
        val questions = step.quiz.filterIsInstance<ReadingQuestion.ClosedSyllable>()
        val ba = questions.first { it.letter.letter == "ب" }

        assertEquals(listOf("ъаб", "ъиб", "ъуб"), quizOptions(course, ba, step.number).map { it.text })
        assertEquals(listOf("ъиб"), quizOptions(course, ba, step.number).filter { it.isCorrect }.map { it.text })
        assertEquals(setOf(0, 1, 2), questions.map { q -> quizOptions(course, q, step.number).indexOfFirst { it.isCorrect } }.toSet(),
            "верный - не всегда на одном месте")
    }

    @Test
    fun `Шаг со словами пройден, только когда пройдены урок и все слова, следующий до этого закрыт`() = runTest {
        val course = loadAssetCourse()
        val step2 = course.step(2)!!
        val words = requireNotNull(step2.words)
        val all = List(words.items.size) { words.repeat }
        val almost = all.toMutableList().also { it[it.lastIndex] = words.repeat - 1 }

        assertEquals(setOf(1), completedSteps(course, setOf(1, 2), mapOf(2 to almost)), "одно слово не дочитано")
        assertEquals(setOf(1), completedSteps(course, setOf(1), mapOf(2 to all)), "урок не пройден")
        assertEquals(setOf(1, 2), completedSteps(course, setOf(1, 2), mapOf(2 to all)))

        val step3 = course.step(3)!!
        assertEquals(false, isUnlocked(course, setOf(1), step3))
        assertEquals(true, isUnlocked(course, setOf(1, 2), step3))
        assertEquals(true, isUnlocked(course, emptySet(), course.step(1)!!))
        assertEquals(false, isUnlocked(course, emptySet(), step2), "шаг 2 - после шага 1")
    }

    @Test
    fun `Случайные слоги — разные буквы, знаки из таблицы, тот же набор при том же seed`() = runTest {
        val table = loadAssetCourse().step(2)!!.blocks
            .filterIsInstance<app.inabah.android.core.content.model.ReadingBlock.Syllables>().single()

        val sample = sampleSyllables(table.letters, table.marks, 12, seed = 7)

        assertEquals(12, sample.map { it.first }.distinct().size)
        assertEquals(true, sample.all { it.second in table.marks })
        assertEquals(sample, sampleSyllables(table.letters, table.marks, 12, seed = 7))
    }

    @Test
    fun `Буква по форме — варианты из её карточки похожего написания`() = runTest {
        val course = loadAssetCourse()
        val questions = course.step(3)!!.quiz.filterIsInstance<ReadingQuestion.LetterInForm>()
        val tha = questions.first { it.letter.letter == "ث" }
        val khaa = questions.first { it.letter.letter == "خ" }

        assertEquals(listOf("Та", "Са", "Нун"), quizOptions(course, tha, 3).map { it.text })
        assertEquals(listOf("Джим", "Хьа", "Хо"), quizOptions(course, khaa, 3).map { it.text }, "карточка ج ح خ")
        assertEquals(1, quizOptions(course, khaa, 3).count { it.isCorrect })
    }

    @Test
    fun `Буква в слове-примере — диапазон буквы со знаком по её месту`() {
        assertEquals(0..1, letterRange("بَحَثَ", "ب", LetterForm.Initial))
        assertEquals(2..3, letterRange("جَبُنَ", "ب", LetterForm.Medial))
        assertEquals(4..5, letterRange("ثَبَتَ", "ت", LetterForm.Final))
        assertEquals(2..3, letterRange("لِيَرِثَ", "ي", LetterForm.Medial))
        assertNull(letterRange("بَحَثَ", "ب", LetterForm.Final))
    }

    @Test
    fun `Имя записи слова-примера — шаг, номер буквы и место`() = runTest {
        val ba = loadAssetCourse().letters.first { it.letter == "ب" }

        assertEquals("reading_s03_02_medial.m4a", formExampleAudio(3, ba, LetterForm.Medial))
    }

    @Test
    fun `Случайные формулы - разные, в порядке таблицы, те же при том же зерне`() = runTest {
        val items = requireNotNull(loadAssetCourse().step(7)).blocks
            .filterIsInstance<ReadingBlock.Formulas>().first { it.sample != null }.items
        val sample = sampleFormulas(items, 6, seed = 7)

        assertEquals(6, sample.toSet().size)
        assertEquals(sample, sample.sortedBy { items.indexOf(it) })
        assertEquals(sample, sampleFormulas(items, 6, seed = 7))
        assertEquals(items, sampleFormulas(items, 100, seed = 1), "больше, чем есть - все")
    }

    @Test
    fun `Вопрос с танвином - варианты только танвины, верный один`() = runTest {
        val course = loadAssetCourse()
        val step = course.steps.first { "fathatan" in it.markIds }
        val ba = course.letters.first { it.letter == "ب" }
        val kasratan = course.marks.first { it.id == "kasratan" }

        val options = quizOptions(course, ReadingQuestion.Syllable(ba, kasratan), step.number)

        assertEquals(listOf("бан", "бин", "бун"), options.map { it.text })
        assertEquals(listOf("бин"), options.filter { it.isCorrect }.map { it.text })
        // Лист буквы: танвин - только с его шага, и сразу не пунктиром.
        assertEquals(listOf("бан", "бин", "бун"), letterChain(course, ba, step.number).takeLast(3).map { it.transliteration })
        assertTrue(letterChain(course, ba, step.number).takeLast(3).all { it.isLearned })
        assertTrue(letterChain(course, ba, step.number - 1).none { it.transliteration == "бан" })
    }

    @Test
    fun `Вопрос со словом - варианты из данных в их порядке, верный - по answer, место верного меняется`() = runTest {
        val course = loadAssetCourse()
        val step = requireNotNull(course.step(9))
        val words = step.quiz.filterIsInstance<ReadingQuestion.Word>()
        val first = words.first()

        assertEquals(first.options, quizOptions(course, first, step.number).map { it.text })
        assertEquals(listOf(first.options[first.answer]), quizOptions(course, first, step.number).filter { it.isCorrect }.map { it.text })
        assertEquals(setOf(0, 1, 2), words.map { it.answer }.toSet())
    }
}
