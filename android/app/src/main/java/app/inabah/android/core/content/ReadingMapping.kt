package app.inabah.android.core.content

import app.inabah.android.core.content.dto.ReadingBlockDto
import app.inabah.android.core.content.dto.ReadingFileDto
import app.inabah.android.core.content.dto.ReadingQuizDto
import app.inabah.android.core.content.dto.ReadingStepDto
import app.inabah.android.core.content.model.FormExamples
import app.inabah.android.core.content.model.LetterForm
import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.content.model.ReadingCourse
import app.inabah.android.core.content.model.ReadingFormula
import app.inabah.android.core.content.model.ReadingLetter
import app.inabah.android.core.content.model.ReadingMark
import app.inabah.android.core.content.model.ReadingQuestion
import app.inabah.android.core.content.model.ReadingStep
import app.inabah.android.core.content.model.ReadingWord
import app.inabah.android.core.content.model.ReadingWords

// reading.json → курс на выбранном языке. Ссылки на неизвестные знак или букву, запись без
// перевода - ошибка данных (IllegalArgumentException; их ловит check-data, а репозиторий
// превращает в ContentError.DecodingFailed). Неизвестные типы блоков и вопросов пропускаются -
// их добавят будущие версии данных.

private const val EXAMPLE_LETTER = "ب"
private const val FORM_EXAMPLES = 3
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
        )
    }
    val markById = marks.associateBy { it.id }
    val letterByChar = letters.associateBy { it.letter }
    fun mark(id: String) = requireNotNull(markById[id]) { "reading.json: неизвестный знак $id" }
    fun letter(char: String) = requireNotNull(letterByChar[char]) { "reading.json: неизвестная буква $char" }

    val learned = mutableSetOf<String>()
    val steps = steps.map { dto ->
        dto.marks.forEach(::mark)
        learned += dto.marks
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
            quiz = dto.quiz.mapNotNull { quiz ->
                // Варианты «буквы по форме» - буквы её карточки в таблице форм шага.
                val formGroups = dto.blocks.filter { it.type == "forms" }.flatMap { it.groups.orEmpty() }
                quiz.toQuestion(::mark, ::letter) { char -> formGroups.firstOrNull { char in it }.orEmpty().map(::letter) }
            },
            words = dto.words?.let { words ->
                ReadingWords(
                    groupSize = words.groupSize,
                    repeat = words.repeat,
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
            letter(char) to FormExamples(words[0], words[1], words[2])
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
    "formulas" -> ReadingBlock.Formulas(
        columns = columns.coerceIn(1, MAX_FORMULA_COLUMNS),
        items = items.map { item ->
            ReadingFormula(
                parts = item.parts,
                result = item.result,
                transliteration = priority.select(item.translations.keyedByLanguage())
                    ?.translation?.translit?.trim()?.ifEmpty { null },
            )
        },
        sample = sample,
    )
    else -> null
}

private fun ReadingQuizDto.toQuestion(
    findMark: (String) -> ReadingMark,
    findLetter: (String) -> ReadingLetter,
    formGroup: (String) -> List<ReadingLetter>,
): ReadingQuestion? = when (type) {
    "letterForm" -> ReadingQuestion.LetterInForm(
        letter = findLetter(letter),
        form = requireNotNull(form?.let(LetterForm::of)) { "reading.json: неизвестная форма $form" },
        group = formGroup(letter).ifEmpty { listOf(findLetter(letter)) },
    )
    "syllable" -> ReadingQuestion.Syllable(
        findLetter(letter),
        findMark(requireNotNull(mark) { "reading.json: вопрос «syllable» без знака" }),
    )
    "letterName" -> ReadingQuestion.LetterName(findLetter(letter))
    else -> null
}

private fun <T> ContentLanguagePriority.require(translations: Map<String, T>, what: String): T =
    requireNotNull(select(translations.keyedByLanguage())) { "reading.json: нет перевода - $what" }.translation
