package app.inabah.android.feature.makharij

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.settings.ReadingSettings

/**
 * «Весь алфавит» с огласовками (решение пользователя 2026-10-15): таблица шага карточками по 4 буквы,
 * глаз, нажатие - лист буквы. Открывается кнопкой под карточкой случайных слогов урока.
 */
@Composable
fun ReadingAlphabetScreen(
    stepNumber: Int,
    store: ReadingStore,
    settings: ReadingSettings,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val hidden by settings.transliterationHidden.collectAsStateWithLifecycle()
    var sheetLetter by rememberSaveable { mutableStateOf<Int?>(null) }
    ReadingScaffold(
        store = store,
        title = stringResource(R.string.makharij_alphabet_table_title),
        subtitle = stringResource(R.string.makharij_alphabet_table_subtitle),
        onBack = onBack,
        modifier = modifier,
        actions = { TransliterationToggle(hidden) { settings.setTransliterationHidden(!hidden) } },
    ) { course ->
        val table = course.step(stepNumber)?.blocks?.filterIsInstance<ReadingBlock.Syllables>()?.firstOrNull()
        // Открыт лист - таблица за ним для TalkBack скрыта.
        val behindSheet = if (sheetLetter != null) Modifier.clearAndSetSemantics {} else Modifier
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .then(behindSheet)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.xl)
                    .padding(top = Spacing.s, bottom = Spacing.xl + contentPadding.calculateBottomPadding()),
            ) {
                table?.let { SyllablesTable(it.letters, it.marks, hidden, onLetter = { letter -> sheetLetter = letter.number }) }
            }
            LetterSheet(
                course = course,
                letter = sheetLetter?.let { number -> course.letters.firstOrNull { it.number == number } },
                stepNumber = stepNumber,
                transliterationHidden = hidden,
                onDismiss = { sheetLetter = null },
                contentPadding = contentPadding,
            )
        }
    }
}
