package app.inabah.android.feature.makharij

import app.inabah.android.core.content.model.LetterForm
import app.inabah.android.core.content.model.ReadingCourse
import app.inabah.android.core.content.model.ReadingLetter
import app.inabah.android.core.content.model.ReadingMark
import app.inabah.android.core.content.model.ReadingQuestion
import app.inabah.android.core.content.model.ReadingStep

// Правила «Учимся читать» без Compose (docs/08-screen-makharij.md): цепочка листа буквы,
// варианты ответа, текущий шаг.

/** Элемент цепочки листа буквы: звук → название → слоги. */
data class LetterChainItem(
    val kind: Kind,
    val arabic: String,
    val transliteration: String,
    /** Запись; нет - элемент без ▶. */
    val audioFile: String?,
    /** Слог с ещё не пройденным знаком - пунктиром, без звука. */
    val isLearned: Boolean,
    /** Название знака слога (`фатха`); у звука и названия - `null`. */
    val markName: String? = null,
) {
    enum class Kind { Sound, Name, Syllable }
}

/**
 * Цепочка буквы [letter] на шаге [stepNumber]: звук (с сукуном), название, затем слоги всех знаков,
 * для которых у буквы есть транскрипция (по порядку знаков курса); знак, не пройденный к шагу, -
 * [LetterChainItem.isLearned] `false`. Сукуна в данных нет - без звука.
 */
fun letterChain(course: ReadingCourse, letter: ReadingLetter, stepNumber: Int): List<LetterChainItem> {
    val learned = course.learnedMarks(stepNumber).mapTo(mutableSetOf()) { it.id }
    val sound = course.sukun?.let { sukun ->
        LetterChainItem(LetterChainItem.Kind.Sound, letter.soundArabic(sukun), letter.sound, letter.audio[KEY_SOUND], true)
    }
    val name = LetterChainItem(
        LetterChainItem.Kind.Name, letter.arabicName, letter.nameTransliteration, letter.audio[KEY_NAME], true,
    )
    val syllables = course.marks.mapNotNull { mark ->
        val transliteration = letter.syllables[mark.id] ?: return@mapNotNull null
        val isLearned = mark.id in learned
        LetterChainItem(
            kind = LetterChainItem.Kind.Syllable,
            arabic = letter.syllable(mark),
            transliteration = transliteration,
            audioFile = letter.audio[mark.id]?.takeIf { isLearned },
            isLearned = isLearned,
            markName = mark.name,
        )
    }
    return listOfNotNull(sound, name) + syllables
}

private const val KEY_SOUND = "sound"
private const val KEY_NAME = "name"
private const val OPTION_COUNT = 3

/** Вариант ответа: что показать на кнопке и верен ли он. */
data class QuizOption(val text: String, val isCorrect: Boolean)

/**
 * Варианты ответа по порядку (без случайности - одинаково при каждом открытии):
 * - слог - тот же слог с другими знаками, пройденными к шагу [stepNumber] (у которых у буквы есть
 *   транскрипция), по порядку знаков: «ба · би · бу»; верный - всегда среди них;
 * - буква - она и соседние по алфавиту (у первой и последней - две следующие или предыдущие),
 *   соседние часто и похожи по форме: ب ت ث.
 */
fun quizOptions(course: ReadingCourse, question: ReadingQuestion, stepNumber: Int): List<QuizOption> =
    when (question) {
        is ReadingQuestion.Syllable -> {
            val letter = question.letter
            // Знак вопроса - всегда (сукун: транскрипция - звук буквы), остальные - со слогами.
            val marks = course.learnedMarks(stepNumber).filter { it.id in letter.syllables || it.id == question.mark.id }
            val others = marks.filter { it.id != question.mark.id }.take(OPTION_COUNT - 1)
            marks.filter { it.id == question.mark.id || it in others }.map { mark ->
                QuizOption(letter.transliteration(mark).orEmpty(), mark.id == question.mark.id)
            }
        }
        // Буквы карточки похожего написания: она и соседние (в карточке из 3 - все).
        is ReadingQuestion.LetterInForm -> {
            val group = question.group
            val index = group.indexOf(question.letter).coerceAtLeast(0)
            val start = (index - 1).coerceIn(0, (group.size - OPTION_COUNT).coerceAtLeast(0))
            group.drop(start).take(OPTION_COUNT).map { QuizOption(it.name, it == question.letter) }
        }
        is ReadingQuestion.LetterName -> {
            val letters = course.letters
            val index = letters.indexOf(question.letter)
            val start = (index - 1).coerceIn(0, (letters.size - OPTION_COUNT).coerceAtLeast(0))
            letters.drop(start).take(OPTION_COUNT).map { QuizOption(it.name, it == question.letter) }
        }
    }

/**
 * Текущий шаг - первый с уроком, ещё не пройденный; все пройдены - `null`. Шаги без урока
 * (содержимое ещё не написано) пропускаются: их нельзя пройти.
 */
fun currentStep(course: ReadingCourse, completed: Set<Int>): ReadingStep? =
    course.steps.firstOrNull { it.hasLesson && it.number !in completed }

/** Доля пройденных шагов курса, 0…1; отметки шагов, которых нет в курсе, не считаются. */
fun completedFraction(course: ReadingCourse, completed: Set<Int>): Double =
    if (course.steps.isEmpty()) 0.0 else course.steps.count { it.number in completed }.toDouble() / course.steps.size

/** Число пройденных шагов курса. */
fun completedCount(course: ReadingCourse, completed: Set<Int>): Int = course.steps.count { it.number in completed }

/** Все слова шага прочитаны нужное число раз (слов нет - да). */
fun wordsDone(step: ReadingStep, counts: List<Int>?): Boolean {
    val words = step.words ?: return true
    return words.items.indices.all { (counts?.getOrNull(it) ?: 0) >= words.repeat }
}

/** Сколько слов шага прочитано нужное число раз. */
fun wordsReadCount(step: ReadingStep, counts: List<Int>?): Int {
    val words = step.words ?: return 0
    return words.items.indices.count { (counts?.getOrNull(it) ?: 0) >= words.repeat }
}

/** Пройденные шаги: урок пройден и все слова прочитаны (решение пользователя 2026-10-15). */
fun completedSteps(course: ReadingCourse, lessonsDone: Set<Int>, wordCounts: Map<Int, List<Int>>): Set<Int> =
    course.steps.filter { it.number in lessonsDone && wordsDone(it, wordCounts[it.number]) }.mapTo(mutableSetOf()) { it.number }

/**
 * Шаг открыт, если пройдены все шаги с уроком перед ним: пока не прочитаны все слова, следующий шаг
 * недоступен (решение пользователя 2026-10-15). Шаги без урока не держат следующие.
 */
fun isUnlocked(course: ReadingCourse, completed: Set<Int>, step: ReadingStep): Boolean =
    course.steps.none { it.number < step.number && it.hasLesson && it.number !in completed }

/**
 * Случайные слоги для карточки урока («ъа · би · тӏы · ро», решение пользователя 2026-10-15): [count]
 * разных букв, у каждой - случайный знак из [marks]. Одинаковые при том же [seed] (экран хранит его).
 */
fun sampleSyllables(
    letters: List<ReadingLetter>,
    marks: List<ReadingMark>,
    count: Int,
    seed: Int,
): List<Pair<ReadingLetter, ReadingMark>> {
    if (marks.isEmpty()) return emptyList()
    val random = kotlin.random.Random(seed)
    return letters.shuffled(random).take(count).map { it to marks.random(random) }
}

/**
 * Где в слове подсветить букву [letter] формы [form]: диапазон символов буквы вместе с её знаками.
 * Начало - первая буква, конец - последняя, середина - первая такая буква не с краю. Нет - `null`.
 */
fun letterRange(word: String, letter: String, form: LetterForm): IntRange? {
    // Буквы слова: позиция и длина вместе со следующими за буквой знаками огласовки.
    val units = mutableListOf<IntRange>()
    var i = 0
    while (i < word.length) {
        var end = i + 1
        while (end < word.length && word[end] in HARAKAT) end++
        units += i until end
        i = end
    }
    val matches = units.withIndex().filter { word.substring(it.value.first, it.value.first + 1) == letter }
    return when (form) {
        LetterForm.Initial -> matches.firstOrNull { it.index == 0 }
        LetterForm.Final -> matches.lastOrNull { it.index == units.lastIndex }
        LetterForm.Medial -> matches.firstOrNull { it.index != 0 && it.index != units.lastIndex }
        LetterForm.Isolated -> null
    }?.value
}

/** Знаки огласовки (U+064B…U+0652) и кинжальный алиф. */
private val HARAKAT = ('\u064B'..'\u0652').toSet() + '\u0670'

/**
 * Запись слова-примера формы буквы (docs/process/audio.md, раздел 5): `reading_s03_02_medial.m4a` -
 * шаг 3, буква 2 (ب), слово, где она в середине.
 */
fun formExampleAudio(step: Int, letter: ReadingLetter, form: LetterForm): String =
    "reading_s%02d_%02d_%s.m4a".format(step, letter.number, form.id)
