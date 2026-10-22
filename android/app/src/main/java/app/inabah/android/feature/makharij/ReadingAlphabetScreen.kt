package app.inabah.android.feature.makharij

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.audio.ClipPlayback
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.content.model.ReadingLetter
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.settings.ReadingSettings

/**
 * «Весь алфавит» с огласовками (решение пользователя 2026-10-15): таблица шага карточками по 4 буквы,
 * глаз, нажатие - лист буквы. Открывается кнопкой под карточкой случайных слогов урока. У шага без
 * таблицы слогов (сукун, 2026-10-16) - таблица [ReadingBlock.ClosedSyllables] (слоги без формул, 2026-10-19), «закрытые слоги».
 * «Послушать алфавит» (решение пользователя 2026-10-20, как на шаге «Буквы»): ▶ в шапке, панель поверх таблицы,
 * слоги звучат ряд за рядом справа налево, звучащий подсвечен.
 */
@Composable
fun ReadingAlphabetScreen(
    stepNumber: Int,
    store: ReadingStore,
    settings: ReadingSettings,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Записи листа буквы и «Послушать алфавит» (`ClipPlayer`). */
    clips: ClipPlayback = ClipPlayback.None,
    /** Руководство по произношению буквы - из её листа. */
    onOpenGuide: (String) -> Unit = {},
) {
    val hidden by settings.transliterationHidden.collectAsStateWithLifecycle()
    var sheetLetter by rememberSaveable { mutableStateOf<Int?>(null) }
    // Подзаголовок - до загрузки курса «с огласовками»; у шага без таблицы слогов - «закрытые слоги».
    val course by store.course.collectAsStateWithLifecycle()
    val step = (course as? Loadable.Loaded)?.value?.step(stepNumber)
    val table = step?.blocks?.filterIsInstance<ReadingBlock.Syllables>()?.firstOrNull()
    val closed = step?.blocks?.filterIsInstance<ReadingBlock.ClosedSyllables>()?.firstOrNull()
    val subtitle = if (step?.blocks?.none { it is ReadingBlock.Syllables } == true) R.string.makharij_alphabet_formulas_subtitle
    else R.string.makharij_alphabet_table_subtitle
    val order = remember(table, closed) {
        when {
            table != null -> syllablesPlayOrder(table)
            closed != null -> closedSyllablesPlayOrder(closed)
            else -> emptyList()
        }
    }
    val items = remember(order) { order.map { AlphabetItem("${it.letter.name} · ${it.mark.name}", it.audio) } }
    val playback = rememberAlphabetPlayback()
    if (items.isNotEmpty()) AlphabetPlaybackEffect(playback, items, clips, sheetOpen = sheetLetter != null)
    val playing = playback.index?.let(order::getOrNull)
    val onLetter: (ReadingLetter) -> Unit = { letter ->
        // Лист буквы звучит сам - алфавит уступает.
        playback.pause()
        sheetLetter = letter.number
    }
    ReadingScaffold(
        store = store,
        title = stringResource(R.string.makharij_alphabet_table_title),
        subtitle = stringResource(subtitle),
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (items.isNotEmpty()) AlphabetPlayButton(playback)
            TransliterationToggle(hidden) { settings.setTransliterationHidden(!hidden) }
        },
    ) { course ->
        // Открыт лист - таблица за ним для TalkBack скрыта.
        val behindSheet = if (sheetLetter != null) Modifier.clearAndSetSemantics {} else Modifier
        val intro = rememberTapIntro("alphabet.$stepNumber", settings)
        LaunchedEffect(sheetLetter) { if (sheetLetter != null) intro.tappedCell() }
        // Панель плеера сверху закрыла бы первый ряд (выше начала не прокрутить): таблица съезжает под неё вместе
        // с выездом панели - сдвигом отрисовки, без переверстки; снизу всегда запас на этот сдвиг.
        val barShift by animateFloatAsState(if (playback.isActive) 1f else 0f, Motion.collapse(), label = "barShift")
        val barSpace = with(LocalDensity.current) { PlayerBarSpace.toPx() }
        TapIntroHost(intro, Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .then(behindSheet)
                    .verticalScroll(rememberScrollState())
                    .graphicsLayer { translationY = barShift * barSpace }
                    .padding(horizontal = Spacing.xl)
                    .padding(top = Spacing.s, bottom = Spacing.xl + PlayerBarSpace + contentPadding.calculateBottomPadding()),
            ) {
                when {
                    table != null -> SyllablesTable(table.letters, table.marks, hidden, onLetter, playing)
                    closed != null -> ClosedSyllablesTable(closed, hidden, playing, onLetter)
                }
            }
            // Поверх таблицы, сверху: выезжает и уезжает сама, таблицу не сдвигает.
            if (items.isNotEmpty()) {
                AlphabetPlayerHost(
                    playback,
                    items,
                    Modifier.padding(horizontal = Spacing.xl),
                    previousLabel = R.string.makharij_alphabet_previous_syllable,
                    nextLabel = R.string.makharij_alphabet_next_syllable,
                )
            }
            LetterSheet(
                course = course,
                letter = sheetLetter?.let { number -> course.letters.firstOrNull { it.number == number } },
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

/** Сколько занимает панель плеера с отступом под ней: кнопки панели и её поля. */
private val PlayerBarSpace = Size.visibleTapTarget + Spacing.s * 3
