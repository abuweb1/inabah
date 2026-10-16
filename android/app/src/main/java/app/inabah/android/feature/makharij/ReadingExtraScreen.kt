package app.inabah.android.feature.makharij

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.settings.ReadingSettings

/**
 * Справка к уроку (решение пользователя 2026-10-16): солнечные и лунные буквы и подобная общая
 * информация - отдельным экраном по кнопке под уроком. Навбар - название справки и шаг; блоки - как на
 * уроке (текст, правило, формулы); нажатие на букву - лист буквы.
 */
@Composable
fun ReadingExtraScreen(
    stepNumber: Int,
    store: ReadingStore,
    settings: ReadingSettings,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val hidden by settings.transliterationHidden.collectAsStateWithLifecycle()
    var sheetLetter by rememberSaveable { mutableStateOf<Int?>(null) }
    val step = (store.course.collectAsStateWithLifecycle().value as? Loadable.Loaded)?.value?.step(stepNumber)
    ReadingScaffold(
        store = store,
        title = step?.extra?.title.orEmpty(),
        subtitle = step?.let { stepTitle(it) },
        onBack = onBack,
        modifier = modifier,
    ) { course ->
        val blocks = course.step(stepNumber)?.extra?.blocks.orEmpty()
        val behindSheet = if (sheetLetter != null) Modifier.clearAndSetSemantics {} else Modifier
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .then(behindSheet)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.xl)
                    .padding(top = Spacing.s, bottom = Spacing.xl + contentPadding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                for (block in blocks) {
                    when (block) {
                        is ReadingBlock.Text -> TextBlock(block.text, block.info)
                        is ReadingBlock.Rule -> RuleBlock(block) { sheetLetter = it.number }
                        is ReadingBlock.Formulas -> FormulasBlock(block.items, block.columns, hidden)
                        // В справке пока только текст, правила и формулы.
                        else -> Unit
                    }
                }
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
