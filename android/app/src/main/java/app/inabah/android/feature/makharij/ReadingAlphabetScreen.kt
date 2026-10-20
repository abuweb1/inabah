package app.inabah.android.feature.makharij

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.audio.ClipPlayback
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.settings.ReadingSettings

/**
 * «Весь алфавит» с огласовками (решение пользователя 2026-10-15): таблица шага карточками по 4 буквы,
 * глаз, нажатие - лист буквы. Открывается кнопкой под карточкой случайных слогов урока. У шага без
 * таблицы слогов (сукун, 2026-10-16) - таблица [ReadingBlock.ClosedSyllables] (слоги без формул, 2026-10-19), «закрытые слоги».
 */
@Composable
fun ReadingAlphabetScreen(
    stepNumber: Int,
    store: ReadingStore,
    settings: ReadingSettings,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Записи листа буквы и закрытых слогов (`ClipPlayer`). */
    clips: ClipPlayback = ClipPlayback.None,
) {
    val hidden by settings.transliterationHidden.collectAsStateWithLifecycle()
    val available by clips.available.collectAsStateWithLifecycle()
    val playingClip by clips.playing.collectAsStateWithLifecycle()
    var sheetLetter by rememberSaveable { mutableStateOf<Int?>(null) }
    // Подзаголовок - до загрузки курса «с огласовками»; у шага без таблицы слогов - «закрытые слоги».
    val course by store.course.collectAsStateWithLifecycle()
    val step = (course as? Loadable.Loaded)?.value?.step(stepNumber)
    val subtitle = if (step?.blocks?.none { it is ReadingBlock.Syllables } == true) R.string.makharij_alphabet_formulas_subtitle
    else R.string.makharij_alphabet_table_subtitle
    ReadingScaffold(
        store = store,
        title = stringResource(R.string.makharij_alphabet_table_title),
        subtitle = stringResource(subtitle),
        onBack = onBack,
        modifier = modifier,
        actions = { TransliterationToggle(hidden) { settings.setTransliterationHidden(!hidden) } },
    ) { course ->
        val table = course.step(stepNumber)?.blocks?.filterIsInstance<ReadingBlock.Syllables>()?.firstOrNull()
        // Открыт лист - таблица за ним для TalkBack скрыта.
        val behindSheet = if (sheetLetter != null) Modifier.clearAndSetSemantics {} else Modifier
        val intro = rememberTapIntro("alphabet.$stepNumber", settings)
        LaunchedEffect(sheetLetter) { if (sheetLetter != null) intro.tappedCell() }
        TapIntroHost(intro, Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .then(behindSheet)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.xl)
                    .padding(top = Spacing.s, bottom = Spacing.xl + contentPadding.calculateBottomPadding()),
            ) {
                val closed = course.step(stepNumber)?.blocks?.filterIsInstance<ReadingBlock.ClosedSyllables>()?.firstOrNull()
                when {
                    table != null -> SyllablesTable(table.letters, table.marks, hidden, onLetter = { letter -> sheetLetter = letter.number })
                    closed != null -> ClosedSyllablesTable(closed, hidden, available, playingClip, clips::play)
                }
            }
            LetterSheet(
                course = course,
                letter = sheetLetter?.let { number -> course.letters.firstOrNull { it.number == number } },
                stepNumber = stepNumber,
                transliterationHidden = hidden,
                onDismiss = { sheetLetter = null },
                contentPadding = contentPadding,
                clips = clips,
            )
        }
    }
}
