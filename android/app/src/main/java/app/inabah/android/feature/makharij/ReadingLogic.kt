package app.inabah.android.feature.makharij

import app.inabah.android.core.content.model.LetterForm
import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.content.model.ReadingCourse
import app.inabah.android.core.content.model.ReadingClosedSyllable
import app.inabah.android.core.content.model.ReadingLetter
import app.inabah.android.core.content.model.ReadingMark
import app.inabah.android.core.content.model.ReadingQuestion
import app.inabah.android.core.content.model.ReadingStep
import app.inabah.android.core.content.model.ReadingWord
import app.inabah.android.core.content.model.ReadingWords
import app.inabah.android.core.content.model.ZikrId
import app.inabah.android.core.content.model.closedSyllables
import app.inabah.android.core.settings.ReadingProgress
import app.inabah.android.core.settings.ZikrProgress
import kotlin.random.Random

// Правила «Учимся читать» без Compose (docs/08-screen-makharij.md): цепочка листа буквы,
// варианты ответа, текущий шаг.

/** Элемент цепочки листа буквы: название → слоги → закрытые слоги. */
data class LetterChainItem(
    val kind: Kind,
    val arabic: String,
    val transliteration: String,
    /** Запись; нет - элемент без ▶. */
    val audioFile: String?,
    /** Слог с ещё не пройденным знаком - пунктиром, без звука. */
    val isLearned: Boolean,
    /** Название знака слога (`фатха`, у закрытого слога - `сукун`); у названия - `null`. */
    val markName: String? = null,
) {
    enum class Kind { Name, Syllable, Closed }
}

/**
 * Цепочка буквы [letter] на шаге [stepNumber]: название, слоги всех знаков, для которых у буквы есть транскрипция
 * (по порядку знаков курса), затем закрытые слоги (`أَبْ أِبْ أُبْ`). Знак, не пройденный к шагу, -
 * [LetterChainItem.isLearned] `false`, а танвин до его шага не показывается.
 */
fun letterChain(course: ReadingCourse, letter: ReadingLetter, stepNumber: Int): List<LetterChainItem> {
    val learned = course.learnedMarks(stepNumber).mapTo(mutableSetOf()) { it.id }
    val name = LetterChainItem(
        LetterChainItem.Kind.Name, letter.arabicName, letter.nameTransliteration, letter.audio[KEY_NAME], true,
    )
    val syllables = course.marks.mapNotNull { mark ->
        val transliteration = letter.syllables[mark.id] ?: return@mapNotNull null
        val isLearned = mark.id in learned
        // Танвин - только с его шага: до него ещё 3 слога пунктиром загромождали лист (2026-10-16).
        if (mark.id in ReadingMark.TANWIN && !isLearned) return@mapNotNull null
        LetterChainItem(
            kind = LetterChainItem.Kind.Syllable,
            arabic = letter.syllable(mark),
            transliteration = transliteration,
            audioFile = letter.audio[mark.id]?.takeIf { isLearned },
            isLearned = isLearned,
            markName = mark.name,
        )
    }
    return listOf(name) + syllables + closedChain(course, letter, learned)
}

/**
 * Закрытые слоги буквы (решение пользователя 2026-10-19: согласная с сукуном одна не произносится - «ъаб ъиб ъуб»
 * вместо «б»): только когда сукун пройден (раньше непонятный знак путает, решение 2026-10-15); у алифа - те же, что
 * у хамзы (`أَأْ إِأْ أُأْ`: своего звука у алифа нет, он подставка хамзы), у و и ي - кроме мадда (أُوْ, إِيْ;
 * [ReadingLetter.closedVowels]). Нет в данных сукуна или алифа - пусто.
 */
private fun closedChain(course: ReadingCourse, letter: ReadingLetter, learned: Set<String>): List<LetterChainItem> {
    val sukun = course.sukun?.takeIf { it.id in learned } ?: return emptyList()
    val alif = course.alif ?: return emptyList()
    val vowels = course.marks.filter { it.id in ReadingLetter.CLOSED_VOWELS }
    return closedSyllables(letter, alif, vowels, sukun).map { closed ->
        LetterChainItem(
            kind = LetterChainItem.Kind.Closed,
            arabic = closed.arabic,
            transliteration = closed.transliteration,
            audioFile = closed.audio,
            isLearned = true,
            markName = sukun.name,
        )
    }
}

/** Запись названия буквы (`reading_NN_name.m4a`) - она звучит и в «Послушать алфавит»; нет - `null`. */
val ReadingLetter.nameAudio: String? get() = audio[KEY_NAME]

/** Записи цепочки листа буквы по порядку (название → слоги → закрытые слоги) из тех, что есть в сборке [available]. */
fun List<LetterChainItem>.playableFiles(available: Set<String>): List<String> =
    mapNotNull { item -> item.audioFile?.takeIf { it in available } }

private const val KEY_NAME = "name"
private const val OPTION_COUNT = 3

/** Вариант ответа: что показать на кнопке и верен ли он. */
data class QuizOption(val text: String, val isCorrect: Boolean)

/**
 * Варианты ответа в постоянном порядке (на экране они перемешаны - [quizRound]):
 * - слог - тот же слог с другими знаками, пройденными к шагу [stepNumber] (у которых у буквы есть
 *   транскрипция), по порядку знаков: «ба · би · бу»; верный - всегда среди них; у танвина варианты -
 *   танвины: «бан · бин · бун»;
 * - закрытый слог - с фатхьой, касрой и доммой алифа: «ъаб · ъиб · ъуб»;
 * - слово - варианты из данных (в их порядке);
 * - буква - она и соседние по алфавиту (у первой и последней - две следующие или предыдущие),
 *   соседние часто и похожи по форме: ب ت ث.
 */
fun quizOptions(course: ReadingCourse, question: ReadingQuestion, stepNumber: Int): List<QuizOption> =
    when (question) {
        is ReadingQuestion.Syllable -> {
            val letter = question.letter
            // Знак вопроса - всегда (сукун: транскрипция - звук буквы), остальные - со слогами; у танвина -
            // только другие танвины («бан · бин · бун»), иначе ответ угадывался по «н».
            val isTanwin = question.mark.id in ReadingMark.TANWIN
            val marks = course.learnedMarks(stepNumber)
                .filter { it.id in letter.syllables || it.id == question.mark.id }
                .filter { (it.id in ReadingMark.TANWIN) == isTanwin }
            val others = marks.filter { it.id != question.mark.id }.take(OPTION_COUNT - 1)
            marks.filter { it.id == question.mark.id || it in others }.map { mark ->
                QuizOption(letter.transliteration(mark).orEmpty(), mark.id == question.mark.id)
            }
        }
        // Слово: варианты - из данных, в их порядке.
        is ReadingQuestion.Word -> question.options.mapIndexed { index, text -> QuizOption(text, index == question.answer) }
        // Закрытый слог: тот же с другими огласовками алифа - «ъаб · ъиб · ъуб».
        is ReadingQuestion.ClosedSyllable -> ReadingQuestion.ClosedSyllable.VOWELS.map { id ->
            QuizOption(question.transliteration(id), id == question.vowel.id)
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

/** Вопрос захода в проверку и его варианты в том порядке, в каком они на экране. */
data class QuizItem(val question: ReadingQuestion, val options: List<QuizOption>)

/**
 * Заход в проверку (просьба пользователя 2026-10-19: каждый раз другие вопросы): [ReadingStep.quizCount]
 * случайных вопросов из запаса шага (меньше в запасе - все) в случайном порядке, варианты каждого перемешаны.
 * [seed] - одно на заход: тот же заход (поворот экрана) - те же вопросы и варианты.
 */
fun quizRound(course: ReadingCourse, step: ReadingStep, seed: Long): List<QuizItem> {
    val random = Random(seed)
    return step.quiz.shuffled(random).take(step.quizCount).map { question ->
        QuizItem(question, quizOptions(course, question, step.number).shuffled(random))
    }
}

/**
 * Текущий шаг - первый с уроком, ещё не пройденный; все пройдены - `null`. Шаги без урока
 * (содержимое ещё не написано) пропускаются: их нельзя пройти.
 */
fun currentStep(course: ReadingCourse, completed: Set<Int>): ReadingStep? =
    course.steps.firstOrNull { it.hasLesson && it.number !in completed }

/** Число шагов с номером - «M» в «пройдено N из M»; итог не считается. */
fun numberedStepCount(course: ReadingCourse): Int = course.steps.count { !it.isFinal }

/** Доля пройденных шагов с номером, 0…1; отметки шагов, которых нет в курсе, и итог не считаются. */
fun completedFraction(course: ReadingCourse, completed: Set<Int>): Double {
    val total = numberedStepCount(course)
    return if (total == 0) 0.0 else completedCount(course, completed).toDouble() / total
}

/** Число пройденных шагов с номером (без итога). */
fun completedCount(course: ReadingCourse, completed: Set<Int>): Int =
    course.steps.count { !it.isFinal && it.number in completed }

/** Курс пройден целиком - все шаги, включая итог. */
fun isCourseDone(course: ReadingCourse, completed: Set<Int>): Boolean =
    course.steps.isNotEmpty() && course.steps.all { it.number in completed }

/**
 * Ряд «Читаем слова»: [columns] плиток в ряд (3, 2 или 1 - во всю ширину); индекс слова - номер в шаге.
 * [isPair] - слово и словосочетание с ним (словосочетание шире).
 */
data class WordRow(val columns: Int, val words: List<IndexedValue<ReadingWord>>, val isPair: Boolean = false)

/**
 * Страницы «Читаем слова» (просьба пользователя 2026-10-16): [columnsOf] - сколько плиток с этим словом
 * влезает в ряд (экран меряет слово; 3, 2 или 1), словосочетание - всегда во всю ширину. Сначала короткие
 * (по 3), потом длинные (по 2), потом во всю ширину, внутри - порядок данных; страница - [rowsPerPage]
 * рядов. Неполных рядов нет: лишние слова очереди переходят в начало следующей, более широкой (в широкой
 * плитке слово тоже помещается). Индекс слова - его номер в данных: по нему хранится счёт прочтений.
 * [random] - перемешать слова внутри очередей (не в порядке книги, просьба пользователя 2026-10-17);
 * нет - порядок данных.
 */
fun wordPages(
    words: List<ReadingWord>,
    columnsOf: (ReadingWord) -> Int,
    rowsPerPage: Int,
    random: Random? = null,
): List<List<WordRow>> {
    val queues = (ReadingWords.MAX_COLUMNS downTo 1).associateWith { mutableListOf<IndexedValue<ReadingWord>>() }
    val ordered = words.withIndex().toList().let { if (random == null) it else it.shuffled(random) }
    for (word in ordered) {
        val columns = if (word.value.isPhrase) 1 else columnsOf(word.value).coerceIn(1, ReadingWords.MAX_COLUMNS)
        queues.getValue(columns) += word
    }
    val rows = mutableListOf<WordRow>()
    for (columns in ReadingWords.MAX_COLUMNS downTo 1) {
        val queue = queues.getValue(columns)
        val rest = queue.size % columns
        if (rest > 0) queues.getValue(columns - 1).addAll(0, queue.takeLast(rest))
        queue.dropLast(rest).chunked(columns).mapTo(rows) { WordRow(columns, it) }
    }
    return rows.chunked(rowsPerPage.coerceAtLeast(1))
}

/**
 * Страницы пар (шаг «Мадд перед артиклем»): порядок данных; слово, за которым словосочетание, - с ним в
 * одном ряду, остальное - во всю ширину. Страница - [rowsPerPage] рядов. [random] - перемешать пары между
 * собой; одиночные ряды остаются на своих местах: среди них аяты подряд (ат-Таквир 1-3).
 */
fun pairPages(words: List<ReadingWord>, rowsPerPage: Int, random: Random? = null): List<List<WordRow>> {
    val rows = mutableListOf<WordRow>()
    var i = 0
    while (i < words.size) {
        val next = words.getOrNull(i + 1)
        if (!words[i].isPhrase && next != null && next.isPhrase) {
            rows += WordRow(2, listOf(IndexedValue(i, words[i]), IndexedValue(i + 1, next)), isPair = true)
            i += 2
        } else {
            rows += WordRow(1, listOf(IndexedValue(i, words[i])))
            i += 1
        }
    }
    if (random != null) {
        val pairSlots = rows.indices.filter { rows[it].isPair }
        pairSlots.map { rows[it] }.shuffled(random).forEachIndexed { n, row -> rows[pairSlots[n]] = row }
    }
    return rows.chunked(rowsPerPage.coerceAtLeast(1))
}

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
 * Есть что сбросить у шага (настройки «Махраджа», 2026-10-19): пройден урок, прочитано хоть одно слово
 * или начат зикр итога.
 */
fun hasProgress(
    step: ReadingStep,
    lessonsDone: Set<Int>,
    wordCounts: Map<Int, List<Int>>,
    zikrs: Map<ZikrId, ZikrProgress>,
): Boolean =
    step.number in lessonsDone ||
        wordCounts[step.number].orEmpty().any { it > 0 } ||
        step.zikrs.any { block -> zikrs[block.zikr]?.let { it.isRead || it.words.isNotEmpty() } == true }

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
 * Случайные закрытые слоги для карточки урока (шаг «Сукун», решение пользователя 2026-10-16): [count] разных
 * слогов в порядке таблицы. Одинаковые при том же [seed] (экран хранит его).
 */
fun sampleClosedSyllables(items: List<ReadingClosedSyllable>, count: Int, seed: Int): List<ReadingClosedSyllable> {
    val picked = items.indices.shuffled(kotlin.random.Random(seed)).take(count).toSet()
    return items.filterIndexed { index, _ -> index in picked }
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

/** Зикр итога прочитан (решение пользователя 2026-10-17): все слова отмечены и нажато «Прочитал целиком». */
fun isZikrDone(zikr: ReadingBlock.Zikr, progress: ZikrProgress?): Boolean =
    progress != null && progress.isRead && zikr.words.indices.all { it in progress.words }

/** Итог «Читаем зикр» пройден - прочитаны все его зикры. */
fun allZikrsDone(step: ReadingStep, progress: Map<ZikrId, ZikrProgress>): Boolean =
    step.zikrs.isNotEmpty() && step.zikrs.all { zikr -> isZikrDone(zikr, progress[zikr.zikr]) }

/**
 * Урок итога с зикрами пройден, когда прочитаны все зикры (кнопки «Готово» у него нет): отметить урок.
 * `true` - урок стал пройденным сейчас (праздник); уже был отмечен или прочитаны не все - `false`.
 */
fun completeFinalIfRead(step: ReadingStep, progress: ReadingProgress): Boolean {
    if (step.number in progress.lessonsDone.value || !allZikrsDone(step, progress.zikrs.value)) return false
    progress.markLessonDone(step.number)
    return true
}

/**
 * Ряды «По словам» зикра: слова - по порядку зикра (не перемешиваются и не сортируются, как в «Читаем
 * слова»); в ряд - сколько влезает у каждого слова ряда ([columnsOf]: 3, 2 или 1); ряд во всю ширину -
 * `columns` = числу слов, без пустых мест.
 */
fun zikrRows(words: List<ReadingWord>, columnsOf: (ReadingWord) -> Int): List<WordRow> {
    val rows = mutableListOf<WordRow>()
    var row = mutableListOf<IndexedValue<ReadingWord>>()
    for (word in words.withIndex()) {
        val size = row.size + 1
        val fits = size <= ReadingWords.MAX_COLUMNS && (row + word).all { columnsOf(it.value) >= size }
        if (!fits && row.isNotEmpty()) {
            rows += WordRow(row.size, row)
            row = mutableListOf()
        }
        row += word
    }
    if (row.isNotEmpty()) rows += WordRow(row.size, row)
    return rows
}
