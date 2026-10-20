package app.inabah.android.core.content

import app.inabah.android.core.content.dto.ReadingBlockDto
import app.inabah.android.core.content.dto.ReadingFileDto
import app.inabah.android.core.content.dto.ReadingQuizDto
import app.inabah.android.core.content.dto.ReadingStepDto
import app.inabah.android.core.content.dto.ReadingWordDto
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.FormExamples
import app.inabah.android.core.content.model.LetterForm
import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.content.model.ReadingCourse
import app.inabah.android.core.content.model.ReadingExtra
import app.inabah.android.core.content.model.ReadingFormula
import app.inabah.android.core.content.model.ReadingJoin
import app.inabah.android.core.content.model.ReadingLetter
import app.inabah.android.core.content.model.ReadingMark
import app.inabah.android.core.content.model.ReadingQuestion
import app.inabah.android.core.content.model.ReadingStep
import app.inabah.android.core.content.model.ReadingWord
import app.inabah.android.core.content.model.ReadingWords
import app.inabah.android.core.content.model.ZikrId
import app.inabah.android.core.content.model.closedSyllables

// reading.json → курс на выбранном языке. Ссылки на неизвестные знак или букву, запись без
// перевода - ошибка данных (IllegalArgumentException; их ловит check-data, а репозиторий
// превращает в ContentError.DecodingFailed). Неизвестные типы блоков и вопросов пропускаются -
// их добавят будущие версии данных.

private const val EXAMPLE_LETTER = "ب"
private const val FORM_EXAMPLES = 3

/** Места слов-примеров по порядку `examples`. */
private val PLACED_FORMS = listOf(LetterForm.Initial, LetterForm.Medial, LetterForm.Final)
private const val MAX_FORMULA_COLUMNS = 3

internal fun ReadingFileDto.toReadingCourse(priority: ContentLanguagePriority): ReadingCourse {
    val marks = marks.map { dto ->
        val text = priority.require(dto.translations, "знак ${dto.id}")
        ReadingMark(id = dto.id, sign = dto.sign, name = text.name, where = text.where, sound = text.sound)
    }
    val letters = letters.map { dto ->
        val text = priority.require(dto.translations, "буква ${dto.letter}")
        ReadingLetter(
            number = dto.id,
            letter = dto.letter,
            arabicName = dto.name,
            joinsNext = dto.joinsNext,
            joinsPrevious = dto.joinsPrevious,
            syllableBases = dto.syllableBases,
            name = text.name,
            nameTransliteration = text.nameTranslit,
            sound = text.sound,
            syllables = text.syllables,
            audio = dto.audio.mapNotNull { (key, file) -> file?.let { key to it } }.toMap(),
            note = text.note?.trim()?.ifEmpty { null },
            closedBase = dto.closedBase,
            closedVowels = dto.closedVowels ?: ReadingLetter.CLOSED_VOWELS,
        )
    }
    val markById = marks.associateBy { it.id }
    val letterByChar = letters.associateBy { it.letter }
    fun mark(id: String) = requireNotNull(markById[id]) { "reading.json: неизвестный знак $id" }
    fun letter(char: String) = requireNotNull(letterByChar[char]) { "reading.json: неизвестная буква $char" }

    val learned = mutableSetOf<String>()
    // Карточки форм пройденных шагов: свой шаг - первым (экзамен шага 12 спрашивает формы прошлых шагов).
    val learnedFormGroups = mutableListOf<List<String>>()
    val steps = steps.map { dto ->
        dto.marks.forEach(::mark)
        learned += dto.marks
        learnedFormGroups.addAll(0, dto.blocks.filter { it.type == "forms" }.flatMap { it.groups.orEmpty() })
        val learnedMarks = marks.filter { it.id in learned }
        val text = priority.require(dto.translations, "шаг ${dto.id}")
        ReadingStep(
            number = dto.id,
            markIds = dto.marks,
            example = dto.example,
            title = text.title,
            subtitle = text.subtitle,
            blocks = dto.blocks.mapNotNull { block ->
                block.toBlock(dto, learnedMarks, priority, ::mark, ::letter, letters)
            },
            extra = dto.extra?.let { extra ->
                ReadingExtra(
                    title = priority.require(extra.translations, "шаг ${dto.id}, справка").title,
                    blocks = extra.blocks.mapNotNull { block -> block.toBlock(dto, learnedMarks, priority, ::mark, ::letter, letters) },
                )
            },
            quiz = dto.quiz.mapNotNull { quiz ->
                // Варианты «буквы по форме» - буквы её карточки в таблице форм этого или прошлого шага.
                quiz.toQuestion(priority, ::mark, ::letter) { char -> learnedFormGroups.firstOrNull { char in it }.orEmpty().map(::letter) }
            },
            quizCount = dto.quizCount ?: ReadingStep.QUIZ_ROUND,
            words = dto.words?.let { words ->
                ReadingWords(
                    groupSize = words.groupSize,
                    repeat = words.repeat,
                    pairs = words.pairs,
                    items = words.items.map { item ->
                        ReadingWord(
                            arabic = item.arabic,
                            transliteration = priority.select(item.translations.keyedByLanguage())
                                ?.translation?.translit?.trim()?.ifEmpty { null },
                        )
                    },
                )
            },
            isFinal = dto.final,
        )
    }
    return ReadingCourse(marks = marks, letters = letters, steps = steps)
}

private fun ReadingBlockDto.toBlock(
    step: ReadingStepDto,
    learnedMarks: List<ReadingMark>,
    priority: ContentLanguagePriority,
    mark: (String) -> ReadingMark,
    letter: (String) -> ReadingLetter,
    alphabet: List<ReadingLetter>,
): ReadingBlock? = when (type) {
    "text" -> priority.require(translations, "шаг ${step.id}, текст").let {
        ReadingBlock.Text(it.text, it.info.trim().ifEmpty { null })
    }
    "forms" -> ReadingBlock.Forms(
        groups = groups.orEmpty().map { group -> group.map(letter) },
        examples = examples.mapNotNull { (char, words) ->
            if (words.size != FORM_EXAMPLES) return@mapNotNull null
            // Транскрипция - необязательна: нет перевода или не 3 строки - слова без подписи.
            val transliteration = priority.select(translations.keyedByLanguage())?.translation?.examples?.get(char)
                ?.takeIf { it.size == FORM_EXAMPLES }
                ?.let { PLACED_FORMS.zip(it.map(String::trim)).filter { (_, text) -> text.isNotEmpty() }.toMap() }
                .orEmpty()
            letter(char) to FormExamples(words[0], words[1], words[2], transliteration)
        }.toMap(),
        exampleCard = exampleCard.map(letter),
    )
    // Знаки - на первой букве таблицы слогов шага без подставки (у алифа знак - на хамзе), иначе на «ب».
    "marks" -> ReadingBlock.Marks(
        marks = marks.map(mark),
        example = step.blocks.firstOrNull { it.type == "syllables" }?.letters.orEmpty()
            .map(letter)
            .firstOrNull { it.syllableBases.isEmpty() }
            ?: letter(EXAMPLE_LETTER),
    )
    "letters" -> ReadingBlock.Letters(letters?.map(letter) ?: alphabet)
    "syllables" -> {
        // Без букв - весь алфавит.
        val rows = letters?.map(letter) ?: alphabet
        // Столбцы - знаки блока (танвин) или пройденные знаки; только те, у которых у всех букв есть
        // транскрипция слога (сукун и шадда - нет).
        val columns = if (marks.isNotEmpty()) marks.map(mark) else learnedMarks
        ReadingBlock.Syllables(rows, columns.filter { m -> rows.all { m.id in it.syllables } }, sample)
    }
    "rule" -> {
        val text = priority.require(translations, "шаг ${step.id}, правило")
        ReadingBlock.Rule(
            arabic = requireNotNull(arabic) { "reading.json: правило без arabic" },
            transliteration = text.translit,
            text = text.text,
            letters = letters.orEmpty().map(letter),
        )
    }
    "topic" -> {
        val text = priority.require(translations, "шаг ${step.id}, тема")
        ReadingBlock.Topic(
            summary = text.text,
            example = arabic,
            extra = ReadingExtra(
                title = text.title,
                blocks = blocks.mapNotNull { it.toBlock(step, learnedMarks, priority, mark, letter, alphabet) },
            ),
        )
    }
    "formulas" -> ReadingBlock.Formulas(
        columns = columns.coerceIn(1, MAX_FORMULA_COLUMNS),
        items = items.map { item ->
            ReadingFormula(
                parts = item.parts,
                result = item.result,
                transliteration = priority.select(item.translations.keyedByLanguage())
                    ?.translation?.translit?.trim()?.ifEmpty { null },
                audio = item.audio?.trim()?.ifEmpty { null },
            )
        },
    )
    // Закрытые слоги (шаг «Сукун», 2026-10-19): ряд на букву, столбцы - огласовки алифа; без букв - весь алфавит.
    "closedSyllables" -> {
        val vowels = ReadingLetter.CLOSED_VOWELS.map(mark)
        val alif = letter(ReadingLetter.ALIF)
        val sukun = mark(ReadingMark.SUKUN)
        val rows = (letters?.map(letter) ?: alphabet).map { row ->
            val cells = closedSyllables(row, alif, vowels, sukun)
            vowels.map { vowel -> cells.firstOrNull { it.vowel == vowel } }
        }
        ReadingBlock.ClosedSyllables(vowels, rows, sample)
    }
    "zikr" -> {
        val text = priority.require(translations, "шаг ${step.id}, зикр")
        val zikrSection = requireNotNull(AzkarSection.entries.firstOrNull { it.key == section }) { "reading.json: зикр - неизвестный раздел $section" }
        ReadingBlock.Zikr(
            zikr = ZikrId(zikrSection, requireNotNull(id) { "reading.json: зикр без id" }),
            audio = audio,
            arabic = requireNotNull(arabic) { "reading.json: зикр без arabic" },
            words = words.map { ReadingWord(it.arabic, it.translation(priority)?.translit?.trim()?.ifEmpty { null }) },
            joins = joins.map { join ->
                val joinText = priority.require(join.translations, "шаг ${step.id}, зикр, ${join.arabic}")
                ReadingJoin(join.arabic, joinText.translit, joinText.note)
            },
            summary = text.text,
            transliteration = text.translit,
        )
    }
    else -> null
}

private fun ReadingWordDto.translation(priority: ContentLanguagePriority) =
    priority.select(translations.keyedByLanguage())?.translation

private fun ReadingQuizDto.toQuestion(
    priority: ContentLanguagePriority,
    findMark: (String) -> ReadingMark,
    findLetter: (String) -> ReadingLetter,
    formGroup: (String) -> List<ReadingLetter>,
): ReadingQuestion? = when (type) {
    "letterForm" -> ReadingQuestion.LetterInForm(
        letter = findLetter(letter),
        form = requireNotNull(form?.let(LetterForm::of)) { "reading.json: неизвестная форма $form" },
        // Варианты - буквы карточки; буквы нет в таблицах форм - вопрос с одним вариантом, ошибка данных.
        group = formGroup(letter).also { require(it.size > 1) { "reading.json: «letterForm» $letter - нет карточки в таблицах форм пройденных шагов" } },
    )
    "syllable" -> ReadingQuestion.Syllable(
        findLetter(letter),
        findMark(requireNotNull(mark) { "reading.json: вопрос «syllable» без знака" }),
    )
    "letterName" -> ReadingQuestion.LetterName(findLetter(letter))
    "word" -> {
        val text = priority.require(translations, "вопрос «word» ${arabic.orEmpty()}")
        require(answer in text.options.indices) { "reading.json: вопрос «word» - answer вне вариантов" }
        ReadingQuestion.Word(
            arabic = requireNotNull(arabic) { "reading.json: вопрос «word» без arabic" },
            options = text.options,
            answer = answer,
            explanation = text.explain.trim().ifEmpty { null },
            prompt = text.question.trim().ifEmpty { null },
        )
    }
    // Закрытый слог: алиф-хамза с огласовкой [mark] и буква с сукуном (`أِبْ`).
    "closedSyllable" -> ReadingQuestion.ClosedSyllable(
        letter = findLetter(letter),
        vowel = findMark(requireNotNull(mark) { "reading.json: вопрос «closedSyllable» без огласовки" }),
        alif = findLetter(ReadingLetter.ALIF),
        sukun = findMark(ReadingMark.SUKUN),
    )
    else -> null
}

private fun <T> ContentLanguagePriority.require(translations: Map<String, T>, what: String): T =
    requireNotNull(select(translations.keyedByLanguage())) { "reading.json: нет перевода - $what" }.translation
