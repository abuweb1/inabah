package app.inabah.android.feature.makharij

import androidx.compose.foundation.layout.wrapContentSize
import app.inabah.android.core.designsystem.Motion
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.res.painterResource
import app.inabah.android.core.designsystem.components.BareIconButton
import app.inabah.android.core.designsystem.components.PopoverPlacement
import app.inabah.android.core.designsystem.components.AnchoredPopover
import app.inabah.android.core.content.model.LetterForm
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import kotlinx.coroutines.delay
import kotlin.random.Random
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.content.model.ReadingLetter
import app.inabah.android.core.content.model.ReadingMark
import app.inabah.android.core.content.model.ReadingStep
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.NoticeCard
import app.inabah.android.core.designsystem.components.ParchmentPanel
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.settings.ReadingProgress
import app.inabah.android.core.settings.ReadingSettings

/**
 * Урок шага (наброски `docs/design/makharij-reading.html`, «Огласовки»; `makharij-audio-themes.html`,
 * сетка букв): блоки сверху вниз, внизу - «Проверить себя» (есть вопросы) или «Готово» (вопросов нет -
 * шаг отмечается пройденным). Нажатие на букву или слог - лист буквы снизу. Шаг без содержания -
 * «Урок готовится».
 */
@Composable
fun ReadingLessonScreen(
    stepNumber: Int,
    store: ReadingStore,
    progress: ReadingProgress,
    settings: ReadingSettings,
    onBack: () -> Unit,
    onOpenQuiz: () -> Unit,
    onOpenWords: () -> Unit,
    onOpenAlphabet: () -> Unit,
    onOpenLetterForms: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Записи в сборке и их проигрывание (`ClipPlayer`): слова-примеры форм. */
    clips: Set<String> = emptySet(),
    onPlayClip: (String) -> Unit = {},
) {
    val hidden by settings.transliterationHidden.collectAsStateWithLifecycle()
    // Открытый лист - номер буквы: переживает поворот и смерть процесса.
    var sheetLetter by rememberSaveable { mutableStateOf<Int?>(null) }
    val course = (store.course.collectAsStateWithLifecycle().value as? Loadable.Loaded)?.value
    val step = course?.step(stepNumber)
    // «Послушать алфавит» - только у шага с сеткой букв.
    val alphabet = step?.blocks?.filterIsInstance<ReadingBlock.Letters>()?.firstOrNull()?.letters
    val playback = rememberAlphabetPlayback()
    // Записей пока нет: буква подсвечивается на время LETTER_MILLIS. С записями - по окончании звука.
    if (alphabet != null) {
        LaunchedEffect(playback.index, playback.isPaused) {
            if (playback.isActive && !playback.isPaused) {
                delay(LETTER_MILLIS)
                playback.next(alphabet.size)
            }
        }
    }
    ReadingScaffold(
        store = store,
        title = step?.let { stringResource(R.string.makharij_step_title, it.number, it.title) }
            ?: stringResource(R.string.makharij_reading_title),
        subtitle = step?.subtitle,
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (alphabet != null) AlphabetPlayButton(playback)
            TransliterationToggle(hidden) { settings.setTransliterationHidden(!hidden) }
        },
    ) { loaded ->
        val current = loaded.step(stepNumber)
        // Открыт лист - урок за ним для TalkBack скрыт (как экран под «Что нового»).
        val behindSheet = if (sheetLetter != null) Modifier.clearAndSetSemantics {} else Modifier
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(behindSheet)) {
                if (current == null || !current.hasLesson) {
                    NoticeCard(
                        icon = R.drawable.ic_hourglass,
                        title = stringResource(R.string.makharij_lesson_soon_title),
                        message = stringResource(R.string.makharij_lesson_soon_message),
                        modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
                    )
                } else {
                    LessonContent(
                        step = current,
                        hidden = hidden,
                        playing = alphabet?.let { letters -> playback.index?.let(letters::getOrNull) },
                        playerBar = {
                            if (alphabet != null) AlphabetPlayerBar(playback, alphabet)
                        },
                        onLetter = { sheetLetter = it.number },
                        onOpenWords = onOpenWords,
                        onOpenAlphabet = onOpenAlphabet,
                        onOpenLetterForms = onOpenLetterForms,
                        clips = clips,
                        onPlayClip = onPlayClip,
                        onFinish = {
                            if (current.quiz.isEmpty()) {
                                progress.markLessonDone(current.number)
                                onBack()
                            } else {
                                onOpenQuiz()
                            }
                        },
                        contentPadding = contentPadding,
                    )
                }
            }
            val letter = sheetLetter?.let { number -> loaded.letters.firstOrNull { it.number == number } }
            LetterSheet(
                course = loaded,
                letter = letter,
                stepNumber = stepNumber,
                transliterationHidden = hidden,
                onDismiss = { sheetLetter = null },
                contentPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun LessonContent(
    step: ReadingStep,
    hidden: Boolean,
    /** Звучащая буква «Послушать алфавит» - подсвечена в сетке. */
    playing: ReadingLetter?,
    /** Панель «Послушать алфавит» - над уроком, не прокручивается. */
    playerBar: @Composable () -> Unit,
    onLetter: (ReadingLetter) -> Unit,
    onOpenWords: () -> Unit,
    onOpenAlphabet: () -> Unit,
    onOpenLetterForms: (String) -> Unit,
    clips: Set<String>,
    onPlayClip: (String) -> Unit,
    onFinish: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = Spacing.xl)) { playerBar() }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.s, bottom = Spacing.xl + contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            step.example?.let { LessonHero(it) }
            for (block in step.blocks) {
                when (block) {
                    is ReadingBlock.Text -> TextBlock(block.text, block.info)
                // Нажатие на букву таблицы - экран буквы (вариант B), не лист: новичку нужна вся картина.
                is ReadingBlock.Forms -> FormsTable(block, step.number, clips, onPlayClip) { onOpenLetterForms(it.letter) }
                    is ReadingBlock.Marks -> MarksBlock(block.marks, block.example)
                    is ReadingBlock.Letters -> LettersGrid(block.letters, onLetter, playing = playing)
                    is ReadingBlock.Syllables -> if (block.sample != null) {
                        SampleSyllables(block, block.sample, hidden, onLetter, onOpenAlphabet)
                    } else {
                        SyllablesTable(block.letters, block.marks, hidden, onLetter)
                    }
                    is ReadingBlock.Rule -> RuleBlock(block, onLetter)
                }
            }
            Spacer(Modifier.height(Spacing.s))
            ReadingButton(
                text = stringResource(if (step.quiz.isEmpty()) R.string.makharij_lesson_done else R.string.makharij_lesson_check),
                onClick = onFinish,
            )
            // «Читаем слова» - в конце урока (и после проверки); шаг пройден, когда прочитаны все слова.
            if (step.words != null) {
                ReadingOutlineButton(stringResource(R.string.makharij_words_open), onClick = onOpenWords)
            }
        }
    }
}

/** Абзац объяснения - категория «перевод» (растёт с «Размером текста»). */
@Composable
private fun TextBlock(text: String, info: String?) {
    val colors = readingColors
    Row(
        Modifier
            .fillMaxWidth()
            .surface(colors.tile, Radius.card, border = colors.tileBorder)
            .padding(start = Spacing.xl, end = if (info != null) Spacing.xs else Spacing.xl, top = Spacing.m, bottom = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            withArabicRuns(text),
            color = InabahTheme.palette.onAccent,
            style = InabahType.contentTranslation,
            modifier = Modifier.weight(1f),
        )
        // Полный текст - по ⓘ во всплывающем окне, чтобы не загружать экран (решение пользователя 2026-10-15).
        info?.let { InfoButton(it) }
    }
}

/** ⓘ и всплывающее окно с текстом - как подтверждение сброса в настройках. */
@Composable
private fun InfoButton(info: String) {
    val colors = readingColors
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        BareIconButton(
            onClick = { expanded = true },
            icon = painterResource(R.drawable.ic_info),
            contentDescription = stringResource(R.string.makharij_info_more),
            foreground = colors.tint,
        )
        AnchoredPopover(expanded, PopoverPlacement.BelowEnd, colors.tint, onDismiss = { expanded = false }) {
            Text(
                info,
                color = InabahTheme.palette.onAccent,
                style = InabahType.contentTranslation,
                modifier = Modifier.widthIn(max = InfoMaxWidth).padding(Spacing.xl),
            )
        }
    }
}

/**
 * Таблица форм (вариант A, решение пользователя 2026-10-15): карточки пергамента по группам похожего
 * написания (ب ت ث ن ي, ج ح خ), ряд - буква, столбцы справа налево - отдельно, в начале, в середине,
 * в конце. Формы строит буква сама. Нажатие - лист буквы.
 */
@Composable
private fun FormsTable(
    block: ReadingBlock.Forms,
    stepNumber: Int,
    clips: Set<String>,
    onPlayClip: (String) -> Unit,
    onLetter: (ReadingLetter) -> Unit,
) {
    val groups = block.groups
    val palette = InabahTheme.palette
    val colors = readingColors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        groups.forEachIndexed { index, group ->
            ParchmentPanel(topCornerRadius = Radius.card, bottomCornerRadius = Radius.card) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        // Подписи столбцов - только над первой карточкой.
                        if (index == 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                for (form in LetterForm.entries) {
                                    Text(
                                        stringResource(form.column),
                                        color = colors.parchmentCaption,
                                        style = InabahType.caption2,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        for (letter in group) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                for (form in LetterForm.entries) {
                                    val interaction = remember { MutableInteractionSource() }
                                    val place = stringResource(form.place)
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .clickable(interaction, indication = null, role = Role.Button) { onLetter(letter) }
                                            .pressFeedback(interaction, scale = PressFeedback.ICON_SCALE)
                                            .surface(colors.parchmentCell, Radius.box, border = colors.parchmentCellBorder)
                                            .padding(top = Spacing.xs)
                                            .clearAndSetSemantics { contentDescription = "${letter.name}, $place" },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        ArabicText(letter.form(form), FORM_SIZE, palette.parchmentText, bold = true, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Третья карточка - «в словах» (просьба пользователя 2026-10-15): буква и слова, где она
        // в начале, в середине и в конце, в тех же столбцах; буква в слове подсвечена.
        if (block.exampleCard.isNotEmpty()) {
            ParchmentPanel(topCornerRadius = Radius.card, bottomCornerRadius = Radius.card) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        for (letter in block.exampleCard) {
                            val examples = block.examples[letter] ?: continue
                            // Отдельная буква - узкая ячейка, слова - шире и крупнее; ряд одной высоты.
                            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                val interaction = remember { MutableInteractionSource() }
                                Box(
                                    Modifier
                                        .weight(ISOLATED_WEIGHT)
                                        .fillMaxHeight()
                                        .clickable(interaction, indication = null, role = Role.Button) { onLetter(letter) }
                                        .pressFeedback(interaction, scale = PressFeedback.ICON_SCALE)
                                        .surface(colors.parchmentCell, Radius.box, border = colors.parchmentCellBorder)
                                        .clearAndSetSemantics { contentDescription = letter.name },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ArabicText(letter.letter, FORM_SIZE, palette.parchmentText, bold = true, maxLines = 1)
                                }
                                for (form in PLACED_FORMS) {
                                    ExampleWordCell(
                                        word = examples.word(form).orEmpty(),
                                        letter = letter,
                                        form = form,
                                        audioFile = formExampleAudio(stepNumber, letter, form),
                                        clips = clips,
                                        onPlay = onPlayClip,
                                        size = FORM_WORD_SIZE,
                                        modifier = Modifier.weight(WORD_WEIGHT).fillMaxHeight(),
                                        onClickWithoutAudio = { onLetter(letter) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Знаки на одной букве: слог, название, «над буквой · «а»». */
@Composable
private fun MarksBlock(marks: List<ReadingMark>, example: ReadingLetter) {
    val palette = InabahTheme.palette
    val colors = readingColors
    // Справа налево: первый знак - справа, как читается.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            for (mark in marks) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(
                        Modifier
                            .weight(1f)
                            .surface(colors.tile, Radius.box, border = colors.tileBorder, lineWidth = ReadingTileBorder)
                            .padding(horizontal = Spacing.xxs, vertical = Spacing.s),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ArabicText(example.syllable(mark), MARK_SYLLABLE_SIZE, palette.onAccent, textAlign = TextAlign.Center)
                        Text(mark.name, color = palette.onAccent, style = InabahType.footnote.copy(fontWeight = FontWeight.Bold))
                        Text(
                            stringResource(R.string.makharij_mark_caption, mark.where, mark.sound),
                            color = palette.onAccentSecondary,
                            style = InabahType.caption2,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Правило: арабское название, его транскрипция (видна всегда - это имя правила, не упражнение),
 * пояснение и буквы правила в ряд справа налево (нажатие - лист буквы).
 */
@Composable
private fun RuleBlock(rule: ReadingBlock.Rule, onLetter: (ReadingLetter) -> Unit) {
    val palette = InabahTheme.palette
    val colors = readingColors
    Column(
        Modifier
            .fillMaxWidth()
            .surface(colors.tile, Radius.card, border = colors.tileBorder)
            .padding(horizontal = Spacing.xl, vertical = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ArabicText(rule.arabic, RULE_TITLE_SIZE, palette.onAccent, textAlign = TextAlign.Center)
        Text(
            rule.transliteration,
            color = colors.tint,
            style = InabahType.subheadline.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
        )
        val ruleText: @Composable (Modifier) -> Unit = { textModifier ->
            Text(
                withArabicRuns(rule.text),
                color = palette.onAccentStrong,
                style = InabahType.contentTranslation,
                modifier = textModifier.padding(top = Spacing.xxs),
            )
        }
        if (rule.letters.size == 1) {
            // Одна буква (ро) - в ряд с текстом, не отдельной строкой (просьба пользователя 2026-10-15).
            val tileWidth = with(LocalDensity.current) { SINGLE_LETTER_TILE.sp.toDp() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                ruleText(Modifier.weight(1f))
                Box(Modifier.width(tileWidth)) { LettersGrid(rule.letters, onLetter, columns = 1) }
            }
        } else {
            ruleText(Modifier.fillMaxWidth())
        }
        if (rule.letters.size > 1) {
            Spacer(Modifier.height(Spacing.xs))
            // Плитки не шире, чем в сетке алфавита: одна буква - обычная плитка справа.
            LettersGrid(rule.letters, onLetter, columns = rule.letters.size.coerceIn(LETTER_COLUMNS, RULE_LETTER_COLUMNS))
        }
    }
}

/** Сетка букв справа налево, по 5 в ряд; нажатие - лист буквы. */
@Composable
private fun LettersGrid(
    letters: List<ReadingLetter>,
    onLetter: (ReadingLetter) -> Unit,
    columns: Int = LETTER_COLUMNS,
    playing: ReadingLetter? = null,
) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val tileHeight = with(LocalDensity.current) { LETTER_TILE_HEIGHT.sp.toDp() }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            for (row in letters.chunked(columns)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    for (letter in row) {
                        val interaction = remember { MutableInteractionSource() }
                        // Звучащая буква «Послушать алфавит» - крупнее и поверх соседей (просьба пользователя).
                        val isPlaying = letter == playing
                        val scale by animateFloatAsState(if (isPlaying) PLAYING_SCALE else 1f, Motion.collapse(), label = "playingScale")
                        Box(
                            Modifier
                                .weight(1f)
                                .zIndex(if (isPlaying) 1f else 0f)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .height(tileHeight)
                                .clickable(interaction, indication = null, role = Role.Button) { onLetter(letter) }
                                .pressFeedback(interaction, scale = PressFeedback.ICON_SCALE)
                                .surface(
                                    if (letter == playing) colors.highlight else colors.tile,
                                    Radius.box,
                                    border = if (letter == playing) colors.highlightBorder else colors.tileBorder,
                                    lineWidth = ReadingTileBorder,
                                )
                                .clearAndSetSemantics { contentDescription = letter.name },
                            contentAlignment = Alignment.Center,
                        ) {
                            ArabicText(letter.letter, LETTER_SIZE, palette.onAccent, maxLines = 1)
                            // Звучащая буква - название над ней в своей рамке (просьба пользователя 2026-10-15):
                            // сдвиг отрисовки вверх на свою высоту, раскладка ряда не меняется.
                            if (isPlaying) PlayingNameBubble(letter.name, Modifier.align(Alignment.TopCenter))
                        }
                    }
                    // Неполный ряд - пустые места, чтобы плитки были одной ширины.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Карточка случайных слогов (разные буквы с разными огласовками: «ъа · би · тӏы · ро») и под ней
 * «Посмотреть весь алфавит» - вся таблица отдельным экраном (решение пользователя 2026-10-15: на уроке
 * её приходилось долго листать). Набор - при каждом открытии урока свой, переживает поворот.
 */
@Composable
private fun SampleSyllables(
    block: ReadingBlock.Syllables,
    count: Int,
    hidden: Boolean,
    onLetter: (ReadingLetter) -> Unit,
    onOpenAlphabet: () -> Unit,
) {
    val seed = rememberSaveable { Random.nextInt() }
    val sample = remember(block, count, seed) { sampleSyllables(block.letters, block.marks, count, seed) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        ParchmentPanel(topCornerRadius = Radius.card, bottomCornerRadius = Radius.card) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    for (row in sample.chunked(SAMPLE_COLUMNS)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            for ((letter, mark) in row) {
                                SyllableCell(letter, mark, hidden, onLetter, Modifier.weight(1f))
                            }
                            repeat(SAMPLE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        ReadingOutlineButton(stringResource(R.string.makharij_alphabet_table_open), onClick = onOpenAlphabet)
    }
}

/**
 * Таблица слогов: по [SYLLABLE_ROWS_PER_CARD] буквы на карточку пергамента (одна длинная доска
 * растягивала градиент - ячейки терялись, просьба пользователя 2026-10-15). Ряд - буква, столбцы -
 * знаки справа налево; ячейка - свой тон и рамка, под слогом - транскрипция.
 */
@Composable
internal fun SyllablesTable(
    letters: List<ReadingLetter>,
    marks: List<ReadingMark>,
    hidden: Boolean,
    onLetter: (ReadingLetter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        for (chunk in letters.chunked(SYLLABLE_ROWS_PER_CARD)) {
            ParchmentPanel(topCornerRadius = Radius.card, bottomCornerRadius = Radius.card) {
                SyllableRows(chunk, marks, hidden, onLetter)
            }
        }
    }
}

@Composable
private fun SyllableRows(
    letters: List<ReadingLetter>,
    marks: List<ReadingMark>,
    hidden: Boolean,
    onLetter: (ReadingLetter) -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            for (letter in letters) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    for (mark in marks) {
                        SyllableCell(letter, mark, hidden, onLetter, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private const val LETTER_COLUMNS = 5
private const val FORM_SIZE = 28f
private const val FORM_WORD_SIZE = 26f
private const val ISOLATED_WEIGHT = 0.6f
private const val WORD_WEIGHT = 1.15f
private val PLACED_FORMS = listOf(LetterForm.Initial, LetterForm.Medial, LetterForm.Final)
private val InfoMaxWidth = 300.dp
private const val SINGLE_LETTER_TILE = 64f
private const val HERO_SIZE = 46f
private const val HERO_STAGGER_MILLIS = 350
private const val HERO_APPEAR_MILLIS = 500
/** «Послушать алфавит» без записей - сколько подсвечена буква. */
private const val LETTER_MILLIS = 2_000L
private const val PLAYING_SCALE = 1.2f
private const val SYLLABLE_ROWS_PER_CARD = 4
private const val SAMPLE_COLUMNS = 3
private const val RULE_LETTER_COLUMNS = 7
private const val RULE_TITLE_SIZE = 26f
/** Плитка буквы 64, в sp - растёт с шагом интерфейса. */
private const val LETTER_TILE_HEIGHT = 64f
private const val LETTER_SIZE = 30f
private const val MARK_SYLLABLE_SIZE = 36f
private const val SYLLABLE_SIZE = 32f

/** Ячейка слога на пергаменте: слог и транскрипция под ним; нажатие - лист буквы. */
@Composable
private fun SyllableCell(
    letter: ReadingLetter,
    mark: ReadingMark,
    hidden: Boolean,
    onLetter: (ReadingLetter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val transliteration = letter.transliteration(mark).orEmpty()
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .clickable(interaction, indication = null, role = Role.Button) { onLetter(letter) }
            .pressFeedback(interaction, scale = PressFeedback.ICON_SCALE)
            .surface(colors.parchmentCell, Radius.box, border = colors.parchmentCellBorder)
            // Сверху - место под знак над хамзой на алифе (أُ касалась края ячейки).
            .padding(top = Spacing.xs, bottom = Spacing.xs)
            .clearAndSetSemantics { contentDescription = "${letter.name}, $transliteration" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArabicText(letter.syllable(mark), SYLLABLE_SIZE, palette.parchmentText, bold = true)
        Text(
            transliteration,
            color = colors.parchmentCaption,
            style = InabahType.caption,
            modifier = Modifier.alpha(transliterationAlpha(hidden)),
        )
    }
}

/**
 * «Лицо» урока (просьба пользователя 2026-10-15: живость): пример шага крупно на пергаменте (بَ بِ بُ),
 * части появляются по очереди справа налево - как пишутся. При каждом открытии урока заново.
 */
@Composable
private fun LessonHero(example: String) {
    val palette = InabahTheme.palette
    val parts = remember(example) { example.split(' ').filter { it.isNotBlank() } }
    ParchmentPanel(topCornerRadius = Radius.card, bottomCornerRadius = Radius.card) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(
                Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = example },
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                parts.forEachIndexed { index, part ->
                    val appear = remember(example) { Animatable(0f) }
                    LaunchedEffect(example) {
                        delay(index * HERO_STAGGER_MILLIS.toLong())
                        appear.animateTo(1f, tween(HERO_APPEAR_MILLIS, easing = FastOutSlowInEasing))
                    }
                    ArabicText(
                        part,
                        HERO_SIZE,
                        palette.parchmentText,
                        bold = true,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer {
                            alpha = appear.value
                            val scale = HERO_START_SCALE + (1 - HERO_START_SCALE) * appear.value
                            scaleX = scale
                            scaleY = scale
                            translationY = (1 - appear.value) * HERO_RISE.toPx()
                        },
                    )
                }
            }
        }
    }
}

private const val HERO_START_SCALE = 0.6f
private val HERO_RISE = 12.dp

/** Подсказка над звучащей буквой «Послушать алфавит»: название в рамке цвета раздела, над плиткой. */
@Composable
private fun PlayingNameBubble(name: String, modifier: Modifier = Modifier) {
    val colors = readingColors
    val appear = remember(name) { Animatable(0f) }
    LaunchedEffect(name) { appear.animateTo(1f, tween(BUBBLE_APPEAR_MILLIS)) }
    val gap = with(LocalDensity.current) { Spacing.xs.toPx() }
    Text(
        name,
        color = InabahTheme.palette.onAccent,
        style = InabahType.title3.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .wrapContentSize(unbounded = true)
            .graphicsLayer {
                alpha = appear.value
                translationY = -size.height - gap - (1 - appear.value) * gap
            }
            .surface(colors.sheet, Radius.box, border = colors.highlightBorder, lineWidth = ReadingTileBorder)
            .padding(horizontal = Spacing.m, vertical = Spacing.xs)
            .clearAndSetSemantics {},
    )
}

private const val BUBBLE_APPEAR_MILLIS = 250

