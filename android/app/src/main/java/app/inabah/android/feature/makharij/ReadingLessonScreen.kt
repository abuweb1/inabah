package app.inabah.android.feature.makharij

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.audio.ClipPlayback
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.LetterForm
import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.content.model.ReadingClosedSyllable
import app.inabah.android.core.content.model.ReadingFormula
import app.inabah.android.core.content.model.ReadingGuide
import app.inabah.android.core.content.model.ReadingLetter
import app.inabah.android.core.content.model.ReadingMark
import app.inabah.android.core.content.model.ReadingStep
import app.inabah.android.core.content.model.ZikrId
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.AnchoredPopover
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.BareIconButton
import app.inabah.android.core.designsystem.components.NoticeCard
import app.inabah.android.core.designsystem.components.ParchmentPanel
import app.inabah.android.core.designsystem.components.PopoverPlacement
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.formatting.toArabicIndicDigits
import app.inabah.android.core.settings.ReadingProgress
import app.inabah.android.core.settings.ReadingSettings
import app.inabah.android.core.settings.ZikrProgress
import kotlin.random.Random
import kotlinx.coroutines.delay

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
    /** Справка к уроку отдельным экраном: `null` - [ReadingStep.extra], номер - тема урока. */
    onOpenExtra: (Int?) -> Unit = {},
    /** Зикр итога «Читаем зикр» по номеру. */
    onOpenZikr: (Int) -> Unit = {},
    /** Записи (`ClipPlayer`): слова-примеры форм, лист буквы, «Послушать алфавит». */
    clips: ClipPlayback = ClipPlayback.None,
    /** Руководство по произношению ([ReadingGuide]) - кнопкой урока и из листа буквы. */
    onOpenGuide: (String) -> Unit = {},
) {
    val hidden by settings.transliterationHidden.collectAsStateWithLifecycle()
    val zikrs by progress.zikrs.collectAsStateWithLifecycle()
    // Открытый лист - номер буквы: переживает поворот и смерть процесса.
    var sheetLetter by rememberSaveable { mutableStateOf<Int?>(null) }
    val course = (store.course.collectAsStateWithLifecycle().value as? Loadable.Loaded)?.value
    val step = course?.step(stepNumber)
    // «Послушать алфавит» - только у шага с сеткой букв.
    val alphabet = step?.blocks?.filterIsInstance<ReadingBlock.Letters>()?.firstOrNull()?.letters
    val playback = rememberAlphabetPlayback()
    val available by clips.available.collectAsStateWithLifecycle()
    val playingClip by clips.playing.collectAsStateWithLifecycle()
    // Звучит название буквы (`reading_NN_name.m4a`), следующая - по окончании записи; записи нет - буква
    // подсвечивается без звука.
    val alphabetItems = remember(alphabet) { alphabet?.map { AlphabetItem(it.name, it.nameAudio) } }
    if (alphabetItems != null) AlphabetPlaybackEffect(playback, alphabetItems, clips, sheetOpen = sheetLetter != null)
    ReadingScaffold(
        store = store,
        title = step?.let { stepTitle(it) }
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
        // Первое открытие урока - волна по нажимаемым ячейкам и подсказка (живые ячейки, 2026-10-20).
        val intro = rememberTapIntro("lesson.$stepNumber", settings)
        // Открыли лист буквы (и через TalkBack, без касаний) - подсказка над ним не остаётся.
        LaunchedEffect(sheetLetter) { if (sheetLetter != null) intro.tappedCell() }
        TapIntroHost(intro, Modifier.fillMaxSize()) {
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
                            if (alphabetItems != null) AlphabetPlayerHost(playback, alphabetItems)
                        },
                        onLetter = {
                            // Лист буквы звучит сам - алфавит уступает.
                            playback.pause()
                            sheetLetter = it.number
                        },
                        onOpenWords = onOpenWords,
                        onOpenAlphabet = onOpenAlphabet,
                        onOpenLetterForms = onOpenLetterForms,
                        onOpenExtra = onOpenExtra,
                        guides = loaded.guides,
                        onOpenGuide = onOpenGuide,
                        zikrs = zikrs,
                        onOpenZikr = onOpenZikr,
                        clips = available,
                        playingClip = playingClip,
                        onPlayClip = clips::play,
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
                clips = clips,
                onOpenGuide = onOpenGuide,
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
    onOpenExtra: (Int?) -> Unit,
    /** Руководства курса: их буквы на уроке помечены, у урока с ними - кнопки. */
    guides: List<ReadingGuide>,
    onOpenGuide: (String) -> Unit,
    zikrs: Map<ZikrId, ZikrProgress>,
    onOpenZikr: (Int) -> Unit,
    clips: Set<String>,
    /** Звучащая запись курса (слово-пример, закрытый слог) - подсвечена; `null` - ничего не звучит. */
    playingClip: String?,
    onPlayClip: (String) -> Unit,
    onFinish: () -> Unit,
    contentPadding: PaddingValues,
) {
    val marked = remember(guides) { guides.flatMapTo(mutableSetOf()) { it.letters } }
    // Руководства, буквы которых есть в сетке или таблице форм урока (шаг «Буквы», шаги форм).
    val stepGuides = remember(step, guides) {
        val shown = step.blocks.flatMapTo(mutableSetOf()) { block ->
            when (block) {
                is ReadingBlock.Letters -> block.letters
                is ReadingBlock.Forms -> block.letters
                else -> emptyList()
            }
        }
        guides.filter { guide -> guide.letters.any { it in shown } }
    }
    Box(Modifier.fillMaxSize()) {
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
                is ReadingBlock.Forms -> FormsTable(block, step.number, clips, playingClip, onPlayClip, marked) { onOpenLetterForms(it.letter) }
                    is ReadingBlock.Marks -> MarksBlock(block.marks, block.example)
                    is ReadingBlock.Letters -> {
                        LettersGrid(block.letters, onLetter, playing = playing, available = clips, marked = marked)
                        if (block.letters.any { it in marked }) GuideMarkCaption()
                    }
                    is ReadingBlock.Syllables -> if (block.sample != null) {
                        SampleSyllables(block, block.sample, hidden, onLetter, onOpenAlphabet)
                    } else {
                        SyllablesTable(block.letters, block.marks, hidden, onLetter)
                    }
                    is ReadingBlock.Rule -> RuleBlock(block, onLetter)
                    is ReadingBlock.Topic -> TopicCard(block) { onOpenExtra(step.topics.indexOf(block)) }
                    is ReadingBlock.Zikr -> {
                        val index = step.zikrs.indexOf(block)
                        ZikrCard(block, index, zikrs[block.zikr]) { onOpenZikr(index) }
                    }
                    is ReadingBlock.Formulas -> FormulasBlock(block.items, block.columns, hidden, clips, playingClip, onPlayClip)
                    is ReadingBlock.ClosedSyllables -> if (block.sample != null) {
                        SampleClosedSyllables(block, block.sample, hidden, onLetter, onOpenAlphabet)
                    } else {
                        ClosedSyllablesTable(block, hidden, onLetter = onLetter)
                    }
                    // Рисунки - только в руководстве (ReadingGuideScreen).
                    is ReadingBlock.Image -> Unit
                }
            }
            // Справка (солнечные и лунные буквы) - для желающих, отдельным экраном (решение пользователя 2026-10-16).
            step.extra?.let { ReadingOutlineButton(it.title, onClick = { onOpenExtra(null) }) }
            // Особое произношение (ث ذ ظ ض, решение пользователя 2026-10-21) - перед «Проверить себя».
            for (guide in stepGuides) ReadingOutlineButton(guide.title, onClick = { onOpenGuide(guide.id) })
            // Итог с зикрами - без «Готово»: пройден, когда прочитаны все зикры (экран зикра отмечает сам).
            if (step.zikrs.isEmpty()) {
                Spacer(Modifier.height(Spacing.s))
                ReadingButton(
                    text = stringResource(if (step.quiz.isEmpty()) R.string.makharij_lesson_done else R.string.makharij_lesson_check),
                    onClick = onFinish,
                )
            }
            // «Читаем слова» - в конце урока (и после проверки); шаг пройден, когда прочитаны все слова.
            if (step.words != null) {
                ReadingOutlineButton(stringResource(R.string.makharij_words_open), onClick = onOpenWords)
            }
        }
        // Плеер алфавита - поверх урока, сверху (выезжает и уезжает сам, содержимое не сдвигает).
        Box(Modifier.padding(horizontal = Spacing.xl)) { playerBar() }
    }
}

/**
 * Тема урока (шаг «Особые случаи», 2026-10-17): название, краткое описание, пример справа и ›;
 * нажатие - экран темы.
 */
@Composable
private fun TopicCard(topic: ReadingBlock.Topic, onClick: () -> Unit) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .pressFeedback(interaction, scale = PressFeedback.WIDE_SCALE, opacity = PressFeedback.WIDE_OPACITY)
            .surface(colors.tile, Radius.card, border = colors.tileBorder)
            .padding(start = Spacing.xl, end = Spacing.m, top = Spacing.m, bottom = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                withArabicRuns(topic.extra.title),
                color = palette.onAccent,
                style = InabahType.body.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                withArabicRuns(topic.summary),
                color = palette.onAccentSecondary,
                style = InabahType.contentTranslation,
            )
        }
        topic.example?.let { ArabicText(it, TOPIC_EXAMPLE_SIZE, colors.tint, bold = true, maxLines = 1) }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.tint)
    }
}

/**
 * Зикр итога (2026-10-17): номер (прочитан - ✓ на зелёном), начало зикра, сколько слов и что в нём
 * встретится; нажатие - экран зикра.
 */
@Composable
private fun ZikrCard(zikr: ReadingBlock.Zikr, index: Int, state: ZikrProgress?, onClick: () -> Unit) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val interaction = remember { MutableInteractionSource() }
    val done = isZikrDone(zikr, state)
    val doneLabel = stringResource(R.string.makharij_zikr_done)
    val count = pluralStringResource(R.plurals.makharij_zikr_word_count, zikr.words.size, zikr.words.size)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .pressFeedback(interaction, scale = PressFeedback.WIDE_SCALE, opacity = PressFeedback.WIDE_OPACITY)
            .surface(colors.tile, Radius.card, border = colors.tileBorder)
            .padding(start = Spacing.m, end = Spacing.m, top = Spacing.m, bottom = Spacing.m)
            .semantics(mergeDescendants = true) { if (done) stateDescription = doneLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            Modifier
                .size(ZikrNumberSize)
                .surface(SolidColor(if (done) palette.success else colors.tile), CircleShape, border = if (done) palette.success else colors.tint)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(ZikrCheckSize))
            } else {
                ArabicText((index + 1).toArabicIndicDigits(), ZIKR_NUMBER_SIZE, colors.tint, bold = true)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            ArabicText(zikr.arabic, TOPIC_EXAMPLE_SIZE, palette.onAccent, bold = true, maxLines = 1)
            Text(
                withArabicRuns("$count · ${zikr.summary}"),
                color = palette.onAccentSecondary,
                style = InabahType.contentTranslation,
            )
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.tint)
    }
}

/** Абзац объяснения - категория «перевод» (растёт с «Размером текста»). */
@Composable
internal fun TextBlock(text: String, info: String?) = InfoTextCard(
    text = text,
    info = info?.let { full ->
        @Composable {
            Text(
                full,
                color = InabahTheme.palette.onAccent,
                style = InabahType.contentTranslation,
                modifier = Modifier.padding(Spacing.xl),
            )
        }
    },
)

/**
 * Короткий текст на карточке раздела и ⓘ (если есть [info]) - всплывающее окно с [info]: полный текст
 * урока или хадис в «Читаем слова» (2026-10-16).
 */
@Composable
internal fun InfoTextCard(text: String, info: (@Composable () -> Unit)?) {
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

/** ⓘ и всплывающее окно с [content] - как подтверждение сброса в настройках. */
@Composable
private fun InfoButton(content: @Composable () -> Unit) {
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
            Box(Modifier.widthIn(max = InfoMaxWidth)) { content() }
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
    playingClip: String?,
    onPlayClip: (String) -> Unit,
    /** Буквы с руководством по произношению - точка у отдельной формы. */
    marked: Set<ReadingLetter>,
    onLetter: (ReadingLetter) -> Unit,
) {
    val groups = block.groups
    val palette = InabahTheme.palette
    val colors = readingColors
    val intro = LocalTapIntro.current
    val hint = stringResource(R.string.makharij_hint_letter)
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
                                    val cellId = rememberTapCellId()
                                    val place = stringResource(form.place)
                                    val isMarked = form == LetterForm.Isolated && letter in marked
                                    val special = if (isMarked) ", ${stringResource(R.string.makharij_guide_marked_description)}" else ""
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .liveCell(intro, cellId, hint, colors.highlightBorder)
                                            .clickable(interaction, indication = cellRipple(), role = Role.Button) { onLetter(letter) }
                                            .pressFeedback(interaction, scale = PressFeedback.ICON_SCALE)
                                            .surface(colors.parchmentCell, Radius.box, border = colors.parchmentCellBorder)
                                            .padding(top = Spacing.xs)
                                            .clearAndSetSemantics { contentDescription = "${letter.name}, $place$special" },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        ArabicText(letter.form(form), FORM_SIZE, palette.parchmentText, bold = true, maxLines = 1)
                                        if (isMarked) GuideMark(palette.parchmentMark, Modifier.align(AbsoluteAlignment.TopRight))
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
                                val cellId = rememberTapCellId()
                                Box(
                                    Modifier
                                        .weight(ISOLATED_WEIGHT)
                                        .fillMaxHeight()
                                        .liveCell(intro, cellId, hint, colors.highlightBorder)
                                        .clickable(interaction, indication = cellRipple(), role = Role.Button) { onLetter(letter) }
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
                                        isPlaying = playingClip == formExampleAudio(stepNumber, letter, form),
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
internal fun RuleBlock(rule: ReadingBlock.Rule, onLetter: (ReadingLetter) -> Unit) {
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
    /** Записи в сборке: у буквы с записью - 🔈 в углу плитки (набросок 1A). */
    available: Set<String> = emptySet(),
    /** Буквы с руководством по произношению - точка в правом верхнем углу (🔈 - в левом нижнем). */
    marked: Set<ReadingLetter> = emptySet(),
) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val tileHeight = with(LocalDensity.current) { LETTER_TILE_HEIGHT.sp.toDp() }
    val intro = LocalTapIntro.current
    val hint = stringResource(R.string.makharij_hint_letter)
    val special = stringResource(R.string.makharij_guide_marked_description)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            for (row in letters.chunked(columns)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    for (letter in row) {
                        val interaction = remember { MutableInteractionSource() }
                        val cellId = rememberTapCellId()
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
                                .height(tileHeight),
                        ) {
                            // Плитка обрезана по своей форме (вспышка), надпись над ней - снаружи обрезки.
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .liveCell(intro, cellId, hint, colors.highlightBorder)
                                    .clickable(interaction, indication = cellRipple(), role = Role.Button) { onLetter(letter) }
                                    .pressFeedback(interaction, scale = PressFeedback.ICON_SCALE)
                                    .surface(
                                        if (letter == playing) colors.highlight else colors.tile,
                                        Radius.box,
                                        border = if (letter == playing) colors.highlightBorder else colors.tileBorder,
                                        lineWidth = ReadingTileBorder,
                                    )
                                    .clearAndSetSemantics { contentDescription = if (letter in marked) "${letter.name}, $special" else letter.name },
                                contentAlignment = Alignment.Center,
                            ) {
                                ArabicText(letter.letter, LETTER_SIZE, palette.onAccent, maxLines = 1)
                                if (letter in marked) GuideMark(colors.tint, Modifier.align(AbsoluteAlignment.TopRight))
                                if (letter.audio.values.any { it in available }) {
                                    Icon(
                                        painterResource(R.drawable.ic_volume_up),
                                        contentDescription = null,
                                        tint = palette.onAccentTertiary,
                                        modifier = Modifier
                                            .align(AbsoluteAlignment.BottomLeft)
                                            .padding(Spacing.xxs)
                                            .size(SpeakerSize),
                                    )
                                }
                            }
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

/** Точка буквы с руководством по произношению - в углу плитки. */
@Composable
private fun GuideMark(color: Color, modifier: Modifier = Modifier) = Box(
    modifier
        .padding(Spacing.xs)
        .size(Size.statusDot)
        .background(color, CircleShape),
)

/** Под сеткой: что значит точка (кнопка руководства - ниже, перед «Проверить себя»). */
@Composable
private fun GuideMarkCaption() {
    val colors = readingColors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Box(Modifier.size(Size.statusDot).background(colors.tint, CircleShape))
        Text(
            stringResource(R.string.makharij_guide_marked),
            color = InabahTheme.palette.onAccentSecondary,
            style = InabahType.footnote,
        )
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
 * Формулы (мадд, 2026-10-15): одна карточка пергамента, ячейки по [ReadingBlock.Formulas.columns] в ряд
 * справа налево; в ячейке «بَ + ا = بَا» - добавленные части выделены (буква мадда), под ней транскрипция
 * итога (прячется «глазом»). Одна часть - равенство «تَشَٰبَهَ = تَشَابَهَ», без выделения.
 * Есть запись итога ([ReadingFormula.audio] в [clips], примеры шадды, 2026-10-20) - 🔊 в углу, нажатие
 * проигрывает её, пока звучит ([playingClip]) - подсвечена; нет - ячейка не нажимается.
 */
@Composable
internal fun FormulasBlock(
    items: List<ReadingFormula>,
    columns: Int,
    hidden: Boolean,
    clips: Set<String> = emptySet(),
    playingClip: String? = null,
    onPlay: (String) -> Unit = {},
) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val haptics = LocalHapticFeedback.current
    val listen = stringResource(R.string.makharij_word_listen)
    val hint = stringResource(R.string.makharij_hint_listen)
    val intro = LocalTapIntro.current

    ParchmentPanel(topCornerRadius = Radius.card, bottomCornerRadius = Radius.card) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                for (row in items.chunked(columns)) {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        for (formula in row) {
                            val parts = remember(formula, colors.letterHighlight) {
                                buildAnnotatedString {
                                    formula.parts.forEachIndexed { index, part ->
                                        if (index > 0) {
                                            append(" + ")
                                            withStyle(SpanStyle(color = colors.letterHighlight)) { append(part) }
                                        } else append(part)
                                    }
                                }
                            }
                            // Без частей - просто слово с транскрипцией (примеры «льам твёрдый / мягкий»).
                            val result = if (formula.parts.isEmpty()) formula.result else "= ${formula.result}"
                            val text = parts + AnnotatedString(" $result")
                            val isPlaying = formula.audio != null && formula.audio == playingClip
                            val play = formula.audio?.takeIf { it in clips }?.let { file ->
                                {
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    onPlay(file)
                                }
                            }
                            val interaction = remember { MutableInteractionSource() }
                            val cellId = rememberTapCellId()
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .then(
                                        if (play != null) {
                                            Modifier
                                                .liveCell(intro, cellId, hint, colors.highlightBorder)
                                                .clickable(interaction, indication = cellRipple(), role = Role.Button, onClick = play)
                                                .pressFeedback(interaction, scale = PressFeedback.ICON_SCALE)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .parchmentCellSurface(colors, isPlaying)
                                    .clearAndSetSemantics {
                                        contentDescription = listOfNotNull(text.text, formula.transliteration).joinToString(", ")
                                        if (play != null) onClick(listen) { play(); true }
                                    },
                            ) {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = Spacing.xxs, vertical = Spacing.xs),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                if (columns == 1) {
                                    // Длинная формула (артикль: «как пишется = как читается») переносится по пробелу.
                                    ArabicText(text, FORMULA_WIDE_SIZE, palette.parchmentText, bold = true, textAlign = TextAlign.Center, maxLines = WIDE_FORMULA_LINES)
                                } else {
                                    // Узкая ячейка: части - строкой выше, итог - крупно под ними (в одну строку не влезало);
                                    // слово без частей (исключения хӏамзату-львасли) - только оно.
                                    if (formula.parts.isNotEmpty()) {
                                        ArabicText(parts, FORMULA_PARTS_SIZE, palette.parchmentText, bold = true, textAlign = TextAlign.Center, maxLines = 1)
                                    }
                                    ArabicText(result, FORMULA_SIZE, palette.parchmentText, bold = true, textAlign = TextAlign.Center, maxLines = 1)
                                }
                                formula.transliteration?.let {
                                    Text(
                                        it,
                                        color = colors.parchmentCaption,
                                        style = InabahType.caption,
                                        modifier = Modifier.alpha(transliterationAlpha(hidden)),
                                    )
                                }
                            }
                            if (play != null) {
                                Icon(
                                    painterResource(R.drawable.ic_volume_up),
                                    contentDescription = null,
                                    tint = colors.parchmentCaption,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(Spacing.xxs)
                                        .size(SpeakerSize),
                                )
                            }
                            }
                        }
                        // Неполный последний ряд формул - с пустыми местами, ряд слов (8 исключений: 3, 3, 2) -
                        // во всю ширину, как плитки «Читаем слова».
                        if (row.any { it.parts.isNotEmpty() }) repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/**
 * Карточка случайных закрытых слогов (решение пользователя 2026-10-16, как слоги шага «Огласовки»; с 2026-10-19 -
 * без формул, просто слог: «ъахь · ъиб · ъук») и под ней «Посмотреть весь алфавит» - вся таблица отдельным экраном
 * (одна надпись на всех шагах, решение пользователя 2026-10-20).
 * Набор - при каждом открытии урока свой, переживает поворот.
 */
@Composable
private fun SampleClosedSyllables(
    block: ReadingBlock.ClosedSyllables,
    count: Int,
    hidden: Boolean,
    onLetter: (ReadingLetter) -> Unit,
    onOpenAlphabet: () -> Unit,
) {
    val seed = rememberSaveable { Random.nextInt() }
    val sample = remember(block, count, seed) { sampleClosedSyllables(block.all, count, seed) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        ParchmentPanel(topCornerRadius = Radius.card, bottomCornerRadius = Radius.card) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    for (row in sample.chunked(SAMPLE_COLUMNS)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            for (cell in row) ClosedSyllableCell(cell, hidden, isPlaying = false, onLetter, Modifier.weight(1f))
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
 * Все закрытые слоги («Весь алфавит» сукуна): по [SYLLABLE_ROWS_PER_CARD] буквы на карточку, как таблица слогов;
 * ряд - буква, столбцы - фатхьа, касра, домма справа налево; нет слога (мадд у و ي) - пустое место.
 * [playing] - слог, звучащий в «Послушать алфавит» таблицы (подсвечен, прокрутка к нему).
 */
@Composable
internal fun ClosedSyllablesTable(
    block: ReadingBlock.ClosedSyllables,
    hidden: Boolean,
    playing: TableSyllable? = null,
    onLetter: (ReadingLetter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        for (chunk in block.rows.chunked(SYLLABLE_ROWS_PER_CARD)) {
            ParchmentPanel(topCornerRadius = Radius.card, bottomCornerRadius = Radius.card) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        for (row in chunk) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                for (cell in row) {
                                    if (cell != null) {
                                        val isPlaying = playing != null && playing.letter == cell.letter && playing.mark == cell.vowel
                                        ClosedSyllableCell(cell, hidden, isPlaying, onLetter, Modifier.weight(1f))
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ячейка закрытого слога на пергаменте: слог и транскрипция под ним; нажатие - лист буквы, как у слога
 * (решение пользователя 2026-10-20: на шаге «Сукун» слоги без записей не нажимались; звук - в цепочке листа).
 * [isPlaying] - звучит в «Послушать алфавит» таблицы: подсвечена, прокрутка к ней.
 */
@Composable
private fun ClosedSyllableCell(
    cell: ReadingClosedSyllable,
    hidden: Boolean,
    isPlaying: Boolean,
    onLetter: (ReadingLetter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val interaction = remember { MutableInteractionSource() }
    val cellId = rememberTapCellId()
    Column(
        modifier
            .keepInViewWhen(isPlaying)
            .liveCell(LocalTapIntro.current, cellId, stringResource(R.string.makharij_hint_syllable), colors.highlightBorder)
            .clickable(interaction, indication = cellRipple(), role = Role.Button) { onLetter(cell.letter) }
            .pressFeedback(interaction, scale = PressFeedback.ICON_SCALE)
            .parchmentCellSurface(colors, isPlaying)
            // Сверху - место под знак над хамзой на алифе (أُ касалась края ячейки).
            .padding(vertical = Spacing.xs)
            .clearAndSetSemantics { contentDescription = "${cell.letter.name}, ${cell.transliteration}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArabicText(cell.arabic, SYLLABLE_SIZE, palette.parchmentText, bold = true)
        Text(
            cell.transliteration,
            color = colors.parchmentCaption,
            style = InabahType.caption,
            modifier = Modifier.padding(top = Spacing.xs).alpha(transliterationAlpha(hidden)),
        )
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
    /** Слог, звучащий в «Послушать алфавит» таблицы (подсвечен, прокрутка к нему); `null` - не звучит. */
    playing: TableSyllable? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        for (chunk in letters.chunked(SYLLABLE_ROWS_PER_CARD)) {
            ParchmentPanel(topCornerRadius = Radius.card, bottomCornerRadius = Radius.card) {
                SyllableRows(chunk, marks, hidden, onLetter, playing)
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
    playing: TableSyllable?,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            for (letter in letters) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    for (mark in marks) {
                        SyllableCell(letter, mark, hidden, onLetter, Modifier.weight(1f), isPlaying = playing?.letter == letter && playing.mark == mark)
                    }
                }
            }
        }
    }
}

private const val LETTER_COLUMNS = 5
private const val WIDE_FORMULA_LINES = 2
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
private const val PLAYING_SCALE = 1.2f
private const val SYLLABLE_ROWS_PER_CARD = 4
private const val SAMPLE_COLUMNS = 3
private const val FORMULA_SIZE = 32f
private const val FORMULA_PARTS_SIZE = 22f
private const val FORMULA_WIDE_SIZE = 32f
/** Номер зикра итога в круге. */
private val ZikrNumberSize = 36.dp
private val ZikrCheckSize = 18.dp
private const val ZIKR_NUMBER_SIZE = 22f
/** Пример на карточке темы урока. */
private const val TOPIC_EXAMPLE_SIZE = 28f
private const val RULE_LETTER_COLUMNS = 7
private const val RULE_TITLE_SIZE = 26f
/** Плитка буквы 64, в sp - растёт с шагом интерфейса. */
private const val LETTER_TILE_HEIGHT = 64f
private const val LETTER_SIZE = 30f
private const val MARK_SYLLABLE_SIZE = 36f
private const val SYLLABLE_SIZE = 32f

/**
 * Ячейка слога на пергаменте: слог и транскрипция под ним; нажатие - лист буквы. [isPlaying] - звучит в
 * «Послушать алфавит» таблицы: подсвечена, прокрутка к ней.
 */
@Composable
private fun SyllableCell(
    letter: ReadingLetter,
    mark: ReadingMark,
    hidden: Boolean,
    onLetter: (ReadingLetter) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val transliteration = letter.transliteration(mark).orEmpty()
    val interaction = remember { MutableInteractionSource() }
    val cellId = rememberTapCellId()
    Column(
        modifier
            .keepInViewWhen(isPlaying)
            .liveCell(LocalTapIntro.current, cellId, stringResource(R.string.makharij_hint_syllable), colors.highlightBorder)
            .clickable(interaction, indication = cellRipple(), role = Role.Button) { onLetter(letter) }
            .pressFeedback(interaction, scale = PressFeedback.ICON_SCALE)
            .parchmentCellSurface(colors, isPlaying)
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
            // Сверху - место под знак под буквой: касратан у ع уходил на подпись (2026-10-16).
            modifier = Modifier.padding(top = Spacing.xs).alpha(transliterationAlpha(hidden)),
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

/** Ячейка на пергаменте (слог, формула): звучит - заливка и рамка выделения пергамента, рамка толще. */
private fun Modifier.parchmentCellSurface(colors: ReadingColors, isPlaying: Boolean): Modifier = surface(
    if (isPlaying) colors.parchmentPlaying else colors.parchmentCell,
    Radius.box,
    border = if (isPlaying) colors.parchmentPlayingBorder else colors.parchmentCellBorder,
    lineWidth = if (isPlaying) ReadingTileBorder else Size.hairline,
)
