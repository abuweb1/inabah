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
import kotlinx.coroutines.launch

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
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Записи в сборке и их проигрывание (`ClipPlayer`). */
    clips: Set<String> = emptySet(),
    onPlayClip: (String) -> Unit = {},
) {
    val course = (store.course.collectAsStateWithLifecycle().value as? Loadable.Loaded)?.value
    val forms = course?.step(stepNumber)?.blocks?.filterIsInstance<ReadingBlock.Forms>()?.firstOrNull()
    val letters = forms?.letters.orEmpty()
    val start = letters.indexOfFirst { it.letter == letter }.coerceAtLeast(0)
    val pager = rememberPagerState(start) { letters.size }
    val scope = rememberCoroutineScope()
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
    ) { _ ->
        if (forms == null || letters.isEmpty()) return@ReadingScaffold
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
                            clips = clips,
                            onPlayClip = onPlayClip,
                            contentPadding = contentPadding,
                        )
                    }
                }
            }
        }
    }
}

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

/** Страница буквы: крупно, три формы с подписями, слова-примеры. */
@Composable
private fun LetterFormsPage(
    stepNumber: Int,
    letter: ReadingLetter,
    examples: FormExamples?,
    clips: Set<String>,
    onPlayClip: (String) -> Unit,
    contentPadding: PaddingValues,
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
                FormRow(placed) { form ->
                    ArabicText(letter.form(form), FORM_LETTER, palette.parchmentText, bold = true, maxLines = 1)
                }
                if (examples != null) {
                    // Слова справа налево под своими формами; есть запись - нажатие проигрывает.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            for (form in placed) {
                                ExampleWordCell(
                                    word = examples.word(form).orEmpty(),
                                    letter = letter,
                                    form = form,
                                    audioFile = formExampleAudio(stepNumber, letter, form),
                                    clips = clips,
                                    onPlay = onPlayClip,
                                    size = EXAMPLE_WORD,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
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
private const val EXAMPLE_WORD = 26f
