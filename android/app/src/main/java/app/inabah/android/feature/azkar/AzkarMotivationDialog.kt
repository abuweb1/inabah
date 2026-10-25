package app.inabah.android.feature.azkar

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import app.inabah.android.R
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.GlassDialogCard
import app.inabah.android.core.designsystem.components.GlassDialogOverlay
import app.inabah.android.core.designsystem.components.GoldActionButton
import app.inabah.android.core.designsystem.components.PopoverActionButton
import app.inabah.android.core.designsystem.components.surface
import dev.chrisbanes.haze.HazeState

/** «Самые любимые Аллахӏом дела - постоянные, даже если их немного» (Муслим 783) — арабский не переводится. */
private const val HADITH_ARABIC = "أَحَبُّ الأَعْمَالِ إِلَى اللَّهِ أَدْوَمُهَا وَإِنْ قَلَّ"
private const val HADITH_ARABIC_SIZE = 20f

/** Число серии — крупнее заголовка экрана (набросок `docs/design/azkar-motivation.html`). */
private const val STREAK_NUMBER_SCALE = 1.6f

/** Доля высоты экрана под середину окна: не помещается — прокручивается, кнопки всегда видны. */
private const val BODY_MAX_HEIGHT_FRACTION = 0.6f

/** Подложка хадиса и плашек — светлая дымка поверх стекла. */
private const val HADITH_FILL_ALPHA = 0.08f
private const val CHIP_BORDER_ALPHA = 0.2f

/** Сколько азкаров раздела в наборе — плашка «Утренние 16 из 21» (только разделы, где выбраны не все). */
data class CompositionCount(val section: AzkarSection, val selected: Int, val total: Int)

/**
 * «Добавьте ещё азкаров» поверх «Машаа Аллах!» (решение пользователя 2026-10-25, набросок
 * `docs/design/azkar-motivation.html`): серия [streak] дней, хадис о постоянстве, плашки [counts].
 * «ОК» — [onOk] (состав азкаров), «В другой раз», касание мимо и «Назад» — [onLater].
 * [backdrop] — экран под окном (размытие), `null` — без размытия.
 */
@Composable
fun AzkarMotivationDialog(
    streak: Int,
    counts: List<CompositionCount>,
    backdrop: HazeState?,
    onOk: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassDialogOverlay(onDismiss = onLater, modifier = modifier) { cardModifier ->
        AzkarMotivationContent(
            streak, counts, onOk, onLater,
            bodyMaxHeight = maxHeight * BODY_MAX_HEIGHT_FRACTION,
            backdrop = backdrop,
            modifier = cardModifier,
        )
    }
}

/** Карточка окна — для превью и UI-тестов тоже. */
@Composable
fun AzkarMotivationContent(
    streak: Int,
    counts: List<CompositionCount>,
    onOk: () -> Unit,
    onLater: () -> Unit,
    bodyMaxHeight: Dp,
    modifier: Modifier = Modifier,
    backdrop: HazeState? = null,
) {
    val palette = InabahTheme.palette
    val title = stringResource(R.string.azkar_motivation_title)
    GlassDialogCard(palette.tabAzkar, backdrop, paneTitle = title, modifier = modifier) {
        Column(
            Modifier
                .heightIn(max = bodyMaxHeight)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            StreakNumber(streak)
            Text(
                title,
                color = palette.onAccent,
                style = InabahType.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.azkar_motivation_message),
                color = palette.onAccent,
                style = InabahType.callout,
                textAlign = TextAlign.Center,
            )
            Hadith()
            if (counts.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    counts.forEach { CountChip(it) }
                }
            }
        }
        // Одна высота, даже если текст соседней перенёсся на вторую строку.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            PopoverActionButton(
                label = stringResource(R.string.azkar_motivation_later),
                color = palette.onAccent,
                onClick = onLater,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            GoldActionButton(
                label = stringResource(R.string.azkar_motivation_ok),
                onClick = onOk,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

/** Крупное число и «дней подряд» под ним; TalkBack читает одной фразой «14 дней подряд». */
@Composable
private fun StreakNumber(streak: Int) {
    val palette = InabahTheme.palette
    val spoken = streakDaysText(streak)
    val numberStyle = InabahType.largeTitle
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        Text(
            streakNumberText(streak),
            color = palette.gold,
            style = numberStyle.copy(fontSize = numberStyle.fontSize * STREAK_NUMBER_SCALE, fontWeight = FontWeight.Bold),
        )
        Text(pluralStringResource(R.plurals.azkar_motivation_days, streak), color = palette.onAccentSecondary, style = InabahType.subheadline)
    }
}

/** Хадис о постоянстве: арабский на пергаменте, перевод, передатчик и источник. */
@Composable
private fun Hadith() {
    val palette = InabahTheme.palette
    val gradients = InabahTheme.gradients
    Column(
        Modifier
            .fillMaxWidth()
            .surface(palette.onAccent.copy(alpha = HADITH_FILL_ALPHA), Radius.box)
            .padding(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        ArabicText(
            HADITH_ARABIC, HADITH_ARABIC_SIZE, palette.parchmentText, textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .surface(gradients.parchment, Radius.control, border = palette.parchmentAccent, lineWidth = Size.parchmentBorder)
                .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        )
        Text(
            stringResource(R.string.azkar_motivation_hadith),
            color = palette.onAccent,
            style = InabahType.callout.copy(fontStyle = FontStyle.Italic),
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.azkar_motivation_hadith_source),
            color = palette.onAccentSecondary,
            style = InabahType.caption,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CountChip(count: CompositionCount) {
    val palette = InabahTheme.palette
    val text = when (count.section) {
        AzkarSection.Morning -> R.string.azkar_motivation_morning_count
        AzkarSection.Evening -> R.string.azkar_motivation_evening_count
    }
    Text(
        stringResource(text, count.selected, count.total),
        color = palette.onAccentSecondary,
        style = InabahType.footnote,
        modifier = Modifier
            .border(Size.hairline, palette.onAccent.copy(alpha = CHIP_BORDER_ALPHA), RoundedCornerShape(Radius.control))
            .padding(horizontal = Spacing.s, vertical = Spacing.xxxs),
    )
}
