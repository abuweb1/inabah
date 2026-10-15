package app.inabah.android.core.content.model

// «Учимся читать» (раздел «Махрадж», data/reading.json, docs/06-data-structure.md) - уже на
// выбранном языке. Слоги и звук буквы не хранятся: строятся из буквы и знака.

/** Курс целиком: знаки, 28 букв и хамза, 10 шагов. */
data class ReadingCourse(
    val marks: List<ReadingMark>,
    val letters: List<ReadingLetter>,
    val steps: List<ReadingStep>,
) {
    fun step(number: Int): ReadingStep? = steps.firstOrNull { it.number == number }

    /** Знаки, пройденные к шагу [stepNumber] включительно, в порядке [marks]. */
    fun learnedMarks(stepNumber: Int): List<ReadingMark> {
        val learned = steps.filter { it.number <= stepNumber }.flatMapTo(mutableSetOf()) { it.markIds }
        return marks.filter { it.id in learned }
    }

    /** Звук буквы - она же с сукуном; сукуна в данных нет - `null`. */
    val sukun: ReadingMark? get() = marks.firstOrNull { it.id == ReadingMark.SUKUN }
}

/** Знак огласовки; [id] - слово латиницей (`fatha`), по нему ссылаются шаги и слоги. */
data class ReadingMark(
    val id: String,
    /** Сам знак (U+064B…U+0652). */
    val sign: String,
    val name: String,
    /** «над буквой» / «под буквой». */
    val where: String,
    /** Звук: «а», «ан», «без гласного». */
    val sound: String,
) {
    companion object {
        const val SUKUN = "sukun"
    }
}

data class ReadingLetter(
    /** Номер в алфавите, 1…29 (хамза - последняя). */
    val number: Int,
    /** Буква без огласовки. */
    val letter: String,
    /** Арабское название с огласовками (`لَام`). */
    val arabicName: String,
    /** `false` - не соединяется со следующей (ا د ذ ر ز و ء). */
    val joinsNext: Boolean,
    /** `false` - не соединяется и с предыдущей (хамза). */
    val joinsPrevious: Boolean,
    /** Подставка слога по знакам: у алифа - хамза на алифе (`أ`, с касрой `إ`); нет знака - сама буква. */
    val syllableBases: Map<String, String>,
    /** «Лям». */
    val name: String,
    /** Транскрипция названия: «льаам». */
    val nameTransliteration: String,
    /** Транскрипция звука (буква с сукуном): «ль». */
    val sound: String,
    /** Транскрипция слогов по знакам: `fatha` → «льа». */
    val syllables: Map<String, String>,
    /** Записи: `sound`, `name`, `<знак>` → имя файла; записи нет - ключа нет. */
    val audio: Map<String, String>,
    /** Пояснение в листе буквы (алиф не бывает сам по себе); нет - `null`. */
    val note: String? = null,
) {
    private fun base(mark: ReadingMark): String = syllableBases[mark.id] ?: letter

    /** Слог: буква (или её подставка) и знак (`لَ`, у алифа `إِ`). */
    fun syllable(mark: ReadingMark): String = base(mark) + mark.sign

    /** Транскрипция слога; с сукуном - звук буквы; нет в данных - `null`. */
    fun transliteration(mark: ReadingMark): String? =
        if (mark.id == ReadingMark.SUKUN) sound else syllables[mark.id]

    /**
     * Звук буквы на письме: с сукуном и татвилем перед ней (`ـلْ`, как в конце слова); без татвиля -
     * у подставки (алиф → `أْ`) и у буквы, которая ни с чем не соединяется (хамза → `ءْ`).
     */
    fun soundArabic(sukun: ReadingMark): String {
        val base = base(sukun)
        return if (base == letter && joinsPrevious) TATWEEL + letter + sukun.sign else base + sukun.sign
    }

    companion object {
        const val TATWEEL = "ـ"
    }
}

data class ReadingStep(
    /** 1…10. */
    val number: Int,
    /** Знаки, которые шаг вводит. */
    val markIds: List<String>,
    /** Пример справа в списке шагов; `null` - без примера. */
    val example: String?,
    val title: String,
    val subtitle: String,
    val blocks: List<ReadingBlock>,
    val quiz: List<ReadingQuestion>,
    /** «Читаем слова»; `null` - у шага слов нет. */
    val words: ReadingWords? = null,
) {
    /** Пустые шаги - «Урок готовится». */
    val hasLesson: Boolean get() = blocks.isNotEmpty()
}

/**
 * «Читаем слова» (решение пользователя 2026-10-15): слова только из букв, которые в слове не меняют
 * форму, группами по [groupSize]; каждое прочитать [repeat] раз. Транскрипция - только у первой группы.
 */
data class ReadingWords(val groupSize: Int, val repeat: Int, val items: List<ReadingWord>) {
    val groups: List<List<ReadingWord>> get() = items.chunked(groupSize.coerceAtLeast(1))
}

data class ReadingWord(val arabic: String, val transliteration: String?)

/** Блок урока; неизвестные типы данных сюда не попадают. */
sealed interface ReadingBlock {
    data class Text(val text: String) : ReadingBlock

    /** Знаки с названием и звуком, на примере [example]. */
    data class Marks(val marks: List<ReadingMark>, val example: ReadingLetter) : ReadingBlock

    /** Сетка букв; нажатие - лист буквы. */
    data class Letters(val letters: List<ReadingLetter>) : ReadingBlock

    /** Таблица слогов: буквы × знаки, пройденные к шагу (у которых у букв есть транскрипция). */
    data class Syllables(
        val letters: List<ReadingLetter>,
        val marks: List<ReadingMark>,
        /** На уроке - столько случайных слогов и «Посмотреть весь алфавит»; `null` - вся таблица на уроке. */
        val sample: Int? = null,
    ) : ReadingBlock

    /** Правило: арабское название и его транскрипция, пояснение, буквы правила. */
    data class Rule(
        val arabic: String,
        val transliteration: String,
        val text: String,
        val letters: List<ReadingLetter>,
    ) : ReadingBlock
}

/** Вопрос проверки; варианты ответа строит экран проверки. */
sealed interface ReadingQuestion {
    val letter: ReadingLetter

    /** «Как читается этот слог?». */
    data class Syllable(override val letter: ReadingLetter, val mark: ReadingMark) : ReadingQuestion

    /** «Какая это буква?». */
    data class LetterName(override val letter: ReadingLetter) : ReadingQuestion
}
