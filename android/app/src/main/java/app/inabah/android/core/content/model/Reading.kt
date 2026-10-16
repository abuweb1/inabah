package app.inabah.android.core.content.model

// «Учимся читать» (раздел «Махрадж», data/reading.json, docs/06-data-structure.md) - уже на
// выбранном языке. Слоги и звук буквы не хранятся: строятся из буквы и знака.

/** Курс целиком: знаки, 28 букв и хамза, шаги. */
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
        const val FATHATAN = "fathatan"

        /** Танвин: фатхьатан, касратан, домматан. */
        val TANWIN = setOf(FATHATAN, "kasratan", "dammatan")
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
    /** «Льам». */
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

    /**
     * Форма буквы в слове, с татвилем на месте соседних букв: начало `بـ`, середина `ـبـ`, конец `ـب`.
     * Не соединяется со следующей (ا د ذ ر ز و) - без правого хвоста; хамза ни с чем - всегда одна буква.
     */
    fun form(form: LetterForm): String {
        val before = if (joinsPrevious) TATWEEL else ""
        val after = if (joinsNext) TATWEEL else ""
        return when (form) {
            LetterForm.Isolated -> letter
            LetterForm.Initial -> letter + after
            LetterForm.Medial -> before + letter + after
            LetterForm.Final -> before + letter
        }
    }

    /**
     * Слог: буква (или её подставка) и знак (`لَ`, у алифа `إِ`). С танвином фатхьи - и алиф, который не
     * читается (`بًا`, стр. 34 книги); у алифа - без него (`أً`): там алиф уже есть.
     */
    fun syllable(mark: ReadingMark): String {
        val base = base(mark)
        val silentAlif = if (mark.id == ReadingMark.FATHATAN && base != SEAT_ALIF) ALIF else ""
        return base + mark.sign + silentAlif
    }

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
        private const val ALIF = "ا"
        private const val SEAT_ALIF = "أ"
    }
}

/** Форма буквы по месту в слове; [id] - как в `reading.json`. */
enum class LetterForm(val id: String) {
    Isolated("isolated"),
    Initial("initial"),
    Medial("medial"),
    Final("final"),
    ;

    companion object {
        fun of(id: String): LetterForm? = entries.firstOrNull { it.id == id }
    }
}

/** Слова-примеры буквы: она в начале, в середине и в конце слова. */
data class FormExamples(val initial: String, val medial: String, val final: String) {
    fun word(form: LetterForm): String? = when (form) {
        LetterForm.Initial -> initial
        LetterForm.Medial -> medial
        LetterForm.Final -> final
        LetterForm.Isolated -> null
    }
}

data class ReadingStep(
    /** 1, 2, 3… по порядку; ключ прогресса. У итога тоже есть, но не показывается. */
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
    /**
     * Итог курса («Читаем зикр», решение пользователя 2026-10-15): последний, вместо номера - ✦,
     * в «пройдено N из M» не считается; открывается, как и шаги, после всех предыдущих.
     */
    val isFinal: Boolean = false,
    /** Справка к уроку отдельным экраном (кнопка под уроком); нет - `null`. */
    val extra: ReadingExtra? = null,
) {
    /** Пустые шаги - «Урок готовится». */
    val hasLesson: Boolean get() = blocks.isNotEmpty()
}

/**
 * Справка к уроку (решение пользователя 2026-10-16: солнечные и лунные буквы - общая информация, читать
 * учиться без неё можно): [title] - на кнопке и в навбаре своего экрана, [blocks] - его содержимое.
 */
data class ReadingExtra(val title: String, val blocks: List<ReadingBlock>)

/**
 * «Читаем слова» (решение пользователя 2026-10-15): каждое слово прочитать [repeat] раз. Страницы строит
 * экран (`wordPages`, 2026-10-16): [groupSize] - слов на странице из коротких слов (15 = 5 рядов по 3).
 * Транскрипция - у всех слов, на экране - только на первой странице (дальше - подсказкой).
 */
data class ReadingWords(
    val groupSize: Int,
    val repeat: Int,
    val items: List<ReadingWord>,
    /**
     * Пары (шаг «Мадд перед артиклем», решение пользователя 2026-10-16): слово и за ним словосочетание с
     * ним - в одном ряду, в порядке данных (по ширине не сортируются): «маа» и «ма лькъоориӏату».
     */
    val pairs: Boolean = false,
) {
    /** Рядов на странице: [groupSize] коротких слов по [MAX_COLUMNS] в ряд. */
    val rowsPerPage: Int get() = ((groupSize + MAX_COLUMNS - 1) / MAX_COLUMNS).coerceAtLeast(1)

    companion object {
        /** Больше всего плиток в ряду - у коротких слов. */
        const val MAX_COLUMNS = 3
    }
}

data class ReadingWord(val arabic: String, val transliteration: String?) {
    /** Несколько слов через пробел («لَهُۥ بَنِينَ») - на экране строкой во всю ширину. */
    val isPhrase: Boolean get() = ' ' in arabic
}

/** Формула блока [ReadingBlock.Formulas]: части, итог и его транскрипция (`null` - без подписи). */
data class ReadingFormula(val parts: List<String>, val result: String, val transliteration: String?)

/** Блок урока; неизвестные типы данных сюда не попадают. */
sealed interface ReadingBlock {
    /** Короткий текст; [info] - полный, во всплывающем окне по ⓘ. */
    data class Text(val text: String, val info: String? = null) : ReadingBlock

    /**
     * Таблица форм: карточки по группам похожего написания, ряд - буква, столбцы - 4 формы; [examples] -
     * слова, где буква в начале, в середине и в конце; [exampleCard] - буквы третьей карточки «в словах».
     */
    data class Forms(
        val groups: List<List<ReadingLetter>>,
        val examples: Map<ReadingLetter, FormExamples> = emptyMap(),
        val exampleCard: List<ReadingLetter> = emptyList(),
    ) : ReadingBlock {
        val letters: List<ReadingLetter> get() = groups.flatten()
    }

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

    /**
     * Формулы на пергаменте (мадд, 2026-10-15): части через «+» и итог после «=», справа налево -
     * «بَ + ا = بَا»; одна часть - равенство («تَشَٰبَهَ = تَشَابَهَ»). [columns] - формул в ряду.
     * [sample] - на уроке столько случайных формул и «Прочтите весь алфавит», все - отдельным экраном
     * (закрытые слоги сукуна, решение пользователя 2026-10-16); `null` - все на уроке.
     */
    data class Formulas(val columns: Int, val items: List<ReadingFormula>, val sample: Int? = null) : ReadingBlock

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

    /** «Как читается этот слог?». */
    data class Syllable(val letter: ReadingLetter, val mark: ReadingMark) : ReadingQuestion

    /**
     * «Как читается этот слог?» - закрытый слог шага «Сукун» (просьба пользователя 2026-10-16, как в
     * «Весь алфавит»): алиф-хамза с [vowel] и [letter] с сукуном - `أِبْ`. [alif] - буква алиф курса
     * (её подставки и слоги), [sukun] - знак.
     */
    data class ClosedSyllable(
        val letter: ReadingLetter,
        val vowel: ReadingMark,
        val alif: ReadingLetter,
        val sukun: ReadingMark,
    ) : ReadingQuestion {
        val arabic: String get() = alif.syllable(vowel) + letter.letter + sukun.sign

        /** Транскрипция слога с огласовкой [vowelId]: «ъиб». */
        fun transliteration(vowelId: String): String = alif.syllables[vowelId].orEmpty() + letter.sound

        companion object {
            /** Огласовки алифа в вариантах ответа, по порядку. */
            val VOWELS = listOf("fatha", "kasra", "damma")
        }
    }

    /**
     * «Как читается это слово?» (шаг «Шадда», 2026-10-16): слово и варианты транскрипции из данных
     * (в показанном порядке), [answer] - номер верного, [explanation] - почему так (нет - «Читается «…».»).
     */
    data class Word(
        val arabic: String,
        val options: List<String>,
        val answer: Int,
        val explanation: String?,
    ) : ReadingQuestion

    /** «Какая это буква?». */
    data class LetterName(val letter: ReadingLetter) : ReadingQuestion

    /** «Какая это буква?» по одной форме; варианты - буквы её группы похожего написания ([group]). */
    data class LetterInForm(
        val letter: ReadingLetter,
        val form: LetterForm,
        val group: List<ReadingLetter>,
    ) : ReadingQuestion
}
