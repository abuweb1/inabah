package app.inabah.android.feature.makharij

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.FormExamples
import app.inabah.android.core.content.model.LetterForm
import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.content.model.ReadingGuide
import app.inabah.android.core.content.model.ReadingLetterNote
import app.inabah.android.core.content.model.ReadingLetter
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.ParchmentPanel
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.settings.ReadingSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect

/**
 * Карточка буквы (вариант B, просьба пользователя 2026-10-15: новичку одной таблицы мало): буква крупно,
 * три её формы с подписями и слова, где она в начале, в середине и в конце (подсвечена). Буквы группы
 * листаются свайпом, сверху - ряд букв группы (нажатие - перейти). Открывается из таблицы форм.
 */
@Composable
fun ReadingLetterFormsScreen(
    stepNumber: Int,
    letter: String,
    store: ReadingStore,
    settings: ReadingSettings,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Записи в сборке, звучащая запись и их проигрывание (`ClipPlayer`). */
    clips: Set<String> = emptySet(),
    playingClip: String? = null,
    onPlayClip: (String) -> Unit = {},
    /** Руководство по произношению буквы (ث ذ ظ ض, 2026-10-21) - кнопкой под её формами. */
    onOpenGuide: (String) -> Unit = {},
) {
    val course = (store.course.collectAsStateWithLifecycle().value as? Loadable.Loaded)?.value
    val forms = course?.step(stepNumber)?.blocks?.filterIsInstance<ReadingBlock.Forms>()?.firstOrNull()
    val letters = forms?.letters.orEmpty()
    val start = letters.indexOfFirst { it.letter == letter }.coerceAtLeast(0)
    val pager = rememberPagerState(start) { letters.size }
    val scope = rememberCoroutineScope()
    // Подсказка (просьба пользователя 2026-10-19): транскрипция слов-примеров на миг, как в «Читаем слова».
    var peek by remember { mutableStateOf(false) }
    var peekRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(peekRequest) {
        if (peekRequest == 0) return@LaunchedEffect
        delay(TRANSLITERATION_HINT_MILLIS)
        peek = false
    }
    // Увеличенное слово-пример (долгое нажатие, как в «Читаем слова»).
    var zoom by remember { mutableStateOf<ExampleZoom?>(null) }
    // Курс мог загрузиться позже первого показа (тогда страница - 0): один раз встать на нажатую букву.
    var positioned by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(letters.size) {
        if (letters.isNotEmpty() && !positioned) {
            pager.scrollToPage(start)
            positioned = true
        }
    }
    ReadingScaffold(
        store = store,
        title = letters.getOrNull(pager.currentPage)?.name ?: stringResource(R.string.makharij_forms_title),
        subtitle = if (letters.isNotEmpty()) stringResource(R.string.makharij_forms_position, pager.currentPage + 1, letters.size) else null,
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (forms?.examples?.values?.any { it.transliterations.isNotEmpty() } == true) {
                TransliterationHint {
                    peek = true
                    peekRequest++
                }
            }
        },
    ) { _ ->
        if (forms == null || letters.isEmpty()) return@ReadingScaffold
        // Слова-примеры увеличиваются - первое открытие: волна, «зов» и подсказка «Удерживайте…» (2026-10-20).
        val intro = rememberTapIntro("letterCard", settings, zoomable = true)
        TapIntroHost(intro, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                GroupStrip(letters, pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
                // Листать - в ту же сторону, что стоят буквы в ряду сверху: справа налево.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HorizontalPager(state = pager, modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) { page ->
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            LetterFormsPage(
                                stepNumber = stepNumber,
                                letter = letters[page],
                                examples = forms.examples[letters[page]],
                                variants = forms.variants[letters[page]],
                                note = forms.notes[letters[page]],
                                clips = clips,
                                onPlayClip = onPlayClip,
                                showTransliteration = peek,
                                zoomed = zoom?.takeIf { it.letter == letters[page] }?.form,
                                onZoom = { form, bounds ->
                                    zoom = ExampleZoom(letters[page], form, bounds)
                                    settings.markZoomLearned()
                                },
                                playingClip = playingClip,
                                contentPadding = contentPadding,
                                guide = course?.guideFor(letters[page]),
                                onOpenGuide = onOpenGuide,
                            )
                        }
                    }
                }
            }
            zoom?.let { current ->
                val examples = forms.examples[current.letter] ?: return@let
                TileZoomOverlay(
                    key = current,
                    from = current.from,
                    fromTextSize = EXAMPLE_WORD,
                    textSize = ZOOM_EXAMPLE_WORD,
                    onDismiss = { zoom = null },
                ) { scale, tileModifier ->
                    ExampleWordCell(
                        word = examples.word(current.form).orEmpty(),
                        letter = current.letter,
                        form = current.form,
                        audioFile = formExampleAudio(stepNumber, current.letter, current.form),
                        clips = clips,
                        onPlay = onPlayClip,
                        size = ZOOM_EXAMPLE_WORD,
                        transliteration = examples.transliteration(current.form),
                        showTransliteration = peek,
                        zoomed = true,
                        contentScale = scale,
                        modifier = tileModifier,
                        isPlaying = playingClip == formExampleAudio(stepNumber, current.letter, current.form),
                    )
                }
            }
        }
    }
}

/** Увеличенное слово-пример: буква, место слова и где стояла ячейка (в координатах корня). */
private data class ExampleZoom(val letter: ReadingLetter, val form: LetterForm, val from: Rect)

/** Ряд букв группы справа налево; текущая выделена, нажатие - перейти. */
@Composable
private fun GroupStrip(letters: List<ReadingLetter>, current: Int, onSelect: (Int) -> Unit) {
    val palette = InabahTheme.palette
    val colors = readingColors
    // Зона касания - не меньше 48 dp; 8 букв в ширину не помещаются - ряд прокручивается.
    val chip = maxOf(with(LocalDensity.current) { STRIP_CHIP.sp.toDp() }, Size.minTapTarget)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
        ) {
            letters.forEachIndexed { index, letter ->
                val isCurrent = index == current
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .size(chip)
                        .clickable(interaction, indication = null, role = Role.Tab) { onSelect(index) }
                        .pressFeedback(interaction, scale = PressFeedback.ICON_SCALE)
                        .surface(
                            if (isCurrent) colors.highlight else colors.tile,
                            Radius.control,
                            border = if (isCurrent) colors.highlightBorder else colors.tileBorder,
                            lineWidth = ReadingTileBorder,
                        )
                        .clearAndSetSemantics {
                            contentDescription = letter.name
                            selected = isCurrent
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    ArabicText(letter.letter, STRIP_LETTER, palette.onAccent, maxLines = 1)
                }
            }
        }
    }
}

/** Страница буквы: крупно, три формы с подписями, слова-примеры (долгое нажатие - увеличить). */
@Composable
private fun LetterFormsPage(
    stepNumber: Int,
    letter: ReadingLetter,
    examples: FormExamples?,
    /** Написания буквы вне групп (хӏамза) - вместо ряда форм. */
    variants: List<String>?,
    /** Текст под пергаментом (хӏамза: подставка не читается). */
    note: ReadingLetterNote?,
    clips: Set<String>,
    onPlayClip: (String) -> Unit,
    showTransliteration: Boolean,
    /** Место слова, увеличенного сейчас: его ячейка на месте не видна (она «вылетела» в центр). */
    zoomed: LetterForm?,
    onZoom: (LetterForm, Rect) -> Unit,
    contentPadding: PaddingValues,
    playingClip: String? = null,
    guide: ReadingGuide? = null,
    onOpenGuide: (String) -> Unit = {},
) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val placed = listOf(LetterForm.Initial, LetterForm.Medial, LetterForm.Final)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl)
            .padding(top = Spacing.s, bottom = Spacing.xl + contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ParchmentPanel(topCornerRadius = Radius.card, bottomCornerRadius = Radius.card) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                ArabicText(
                    letter.letter, BIG_LETTER, palette.parchmentText, bold = true, maxLines = 1,
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = letter.name },
                    textAlign = TextAlign.Center,
                )
                if (variants != null) {
                    LetterVariants(
                        variants,
                        VARIANT_LETTER,
                        palette.parchmentText,
                        Modifier
                            .fillMaxWidth()
                            .surface(colors.parchmentCell, Radius.box, border = colors.parchmentCellBorder)
                            .padding(horizontal = Spacing.s)
                            .padding(top = Spacing.xs),
                    )
                } else {
                    FormRow(placed) { form ->
                        ArabicText(letter.form(form), FORM_LETTER, palette.parchmentText, bold = true, maxLines = 1)
                    }
                }
                if (examples != null) {
                    // Слова справа налево под своими формами; есть запись - нажатие проигрывает.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            for (form in placed) {
                                // Ячейка - слева направо, как увеличенная (значок 🔊 - в том же углу при полёте).
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    ExampleWordCell(
                                        word = examples.word(form).orEmpty(),
                                        letter = letter,
                                        form = form,
                                        audioFile = formExampleAudio(stepNumber, letter, form),
                                        clips = clips,
                                        onPlay = onPlayClip,
                                        size = EXAMPLE_WORD,
                                        transliteration = examples.transliteration(form),
                                        showTransliteration = showTransliteration,
                                        onZoom = { bounds -> onZoom(form, bounds) },
                                        isPlaying = playingClip == formExampleAudio(stepNumber, letter, form),
                                        modifier = Modifier.weight(1f).alpha(if (form == zoomed) 0f else 1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        note?.let { TextBlock(it.text, it.info) }
        guide?.let { ReadingOutlineButton(it.title, onClick = { onOpenGuide(it.id) }) }
    }
}

/** Три ячейки справа налево (начало, середина, конец) с подписью места под каждой. */
@Composable
private fun FormRow(forms: List<LetterForm>, content: @Composable (LetterForm) -> Unit) {
    val colors = readingColors
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            for (form in forms) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val place = stringResource(form.column)
                    Column(
                        Modifier
                            .weight(1f)
                            .surface(colors.parchmentCell, Radius.box, border = colors.parchmentCellBorder)
                            .padding(top = Spacing.xs, bottom = Spacing.xs)
                            .semantics(mergeDescendants = true) {},
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        content(form)
                        Text(place, color = colors.parchmentCaption, style = InabahType.caption2)
                    }
                }
            }
        }
    }
}

private const val STRIP_CHIP = 34f
private const val STRIP_LETTER = 20f
private const val BIG_LETTER = 84f
private const val FORM_LETTER = 34f
/** Написания хӏамзы в ряд (13 знаков) - мельче форм буквы. */
private const val VARIANT_LETTER = 26f
private const val EXAMPLE_WORD = 26f
/** Увеличенное слово-пример - как слово в «Читаем слова». */
private const val ZOOM_EXAMPLE_WORD = 60f
