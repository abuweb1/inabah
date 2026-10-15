package app.inabah.android.feature.makharij

import app.inabah.android.core.content.loadAssetCourse
import app.inabah.android.core.content.model.LetterForm
import app.inabah.android.core.content.model.ReadingQuestion
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** «Учимся читать»: цепочка листа буквы, варианты ответа, текущий шаг. */
class ReadingLogicTest {
    @Test
    fun `Цепочка — звук, название, слоги, до шага 2 огласовки не пройдены`() = runTest {
        val course = loadAssetCourse()
        val lam = course.letters.first { it.letter == "ل" }

        val chain = letterChain(course, lam, stepNumber = 1)

        assertEquals(
            listOf(LetterChainItem.Kind.Sound, LetterChainItem.Kind.Name) + List(3) { LetterChainItem.Kind.Syllable },
            chain.map { it.kind },
        )
        assertEquals(listOf("ль", "льаам", "льа", "ли", "льу"), chain.map { it.transliteration })
        assertEquals(listOf(true, true, false, false, false), chain.map { it.isLearned })
        assertEquals(List(5) { true }, letterChain(course, lam, stepNumber = 2).map { it.isLearned })
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
        assertNull(currentStep(course, setOf(1, 2, 3)), "шаги 4-10 без урока пропускаются")
        assertNull(currentStep(course, withLessons.toSet()))
        assertEquals(0.2, completedFraction(course, setOf(1, 2, 99)))
    }

    @Test
    fun `Вопрос с сукуном — верный вариант есть, его транскрипция — звук буквы`() = runTest {
        val course = loadAssetCourse()
        val ba = course.letters.first { it.letter == "ب" }
        val question = ReadingQuestion.Syllable(ba, requireNotNull(course.sukun))

        val options = quizOptions(course, question, stepNumber = 5)

        assertEquals(listOf("б"), options.filter { it.isCorrect }.map { it.text })
        assertEquals(3, options.size)
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
}
