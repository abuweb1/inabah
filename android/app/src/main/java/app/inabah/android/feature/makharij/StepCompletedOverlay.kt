package app.inabah.android.feature.makharij

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.inabah.android.R
import app.inabah.android.core.content.model.ReadingStep
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.FeaturedVerse
import app.inabah.android.core.designsystem.components.FeaturedVerseBox

// Арабский контент окна - не переводится.
private const val TITLE_ARABIC = "مَا شَاءَ اللَّهُ"
private const val VERSE_ARABIC = "وَقُل رَّبِّ زِدْنِي عِلْمًا"

/**
 * «Шаг пройден» - праздник в конце шага (просьба пользователя 2026-10-15: живость; аят - по-арабски
 * с переводом, как у азкаров): «مَا شَاءَ اللَّهُ» дышит, вокруг - вспышки ✦, «Шаг N пройден», сколько
 * слов прочитано, аят Тӏа Хӏа 20:114 и кнопки - следующий шаг (если открыт и готов) или список.
 * Слой поверх экрана: касания вниз не проходят, «Назад» - к списку.
 */
@Composable
fun StepCompletedOverlay(
    step: ReadingStep,
    next: ReadingStep?,
    onOpenStep: (Int) -> Unit,
    onOpenSteps: () -> Unit,
    contentPadding: PaddingValues,
) {
    val palette = InabahTheme.palette
    val gradients = InabahTheme.gradients
    BackHandler(onBack = onOpenSteps)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Касания не проходят к экрану под окном.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .background(gradients.makharijBackground),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .fillMaxWidth()
                .padding(horizontal = Spacing.xxl, vertical = Spacing.section)
                .padding(bottom = contentPadding.calculateBottomPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
        ) {
            val title = stringResource(R.string.azkar_completion_title)
            Box(contentAlignment = Alignment.Center) {
                // Две волны звёздочек: сразу и с запаздыванием - «салют» вокруг надписи.
                SparkleBurst(key = step.number, color = palette.gold, modifier = Modifier.size(BurstSize), count = BURST_COUNT,
                    durationMillis = BURST_MILLIS)
                SparkleBurst(key = step.number to 1, color = palette.parchmentLight, modifier = Modifier.size(BurstSize * 1.4f),
                    count = BURST_COUNT, durationMillis = BURST_MILLIS * 3 / 2)
                ArabicText(
                    TITLE_ARABIC, TITLE_SIZE, palette.gold, bold = true, textAlign = TextAlign.Center,
                    modifier = Modifier
                        .breathing(scale = TITLE_BREATH)
                        .clearAndSetSemantics {
                            contentDescription = title
                            heading()
                        },
                )
            }
            Text(
                stringResource(R.string.makharij_done_step, step.number, step.title),
                color = palette.onAccent,
                style = InabahType.title3.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            step.words?.let { words ->
                Text(
                    stringResource(R.string.makharij_done_words, words.items.size, words.repeat),
                    color = palette.onAccentSecondary,
                    style = InabahType.subheadline,
                    textAlign = TextAlign.Center,
                )
            }
            FeaturedVerseBox(
                FeaturedVerse(
                    arabic = VERSE_ARABIC,
                    translation = stringResource(R.string.makharij_done_verse_translation),
                    reference = stringResource(R.string.makharij_done_verse_reference),
                ),
            )
            next?.let {
                ReadingButton(stringResource(R.string.makharij_quiz_next_step, it.title), onClick = { onOpenStep(it.number) })
            }
            ReadingOutlineButton(stringResource(R.string.makharij_quiz_to_steps), onClick = onOpenSteps)
        }
    }
}

private const val TITLE_SIZE = 52f
private const val TITLE_BREATH = 1.05f
private val BurstSize = 220.dp
private const val BURST_COUNT = 12
private const val BURST_MILLIS = 1_200
