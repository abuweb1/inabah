package app.inabah.android.feature.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.GlassDialogCard
import app.inabah.android.core.designsystem.components.GlassDialogOverlay
import app.inabah.android.core.designsystem.components.PopoverActionButton
import app.inabah.android.core.releases.ReleaseNotes
import dev.chrisbanes.haze.HazeState

/** Доля высоты экрана под пункты: длинный список прокручивается, «ОК» всегда видна. */
private const val ITEMS_MAX_HEIGHT_FRACTION = 0.5f
private const val BULLET = "•"

/** Ширина «ОК» — доля ширины карточки. */
private const val BUTTON_WIDTH_FRACTION = 0.55f

/**
 * Окно «Что нового» после обновления (docs/android/06-screens.md): слой поверх всех вкладок, один раз.
 * [backdrop] — экран вкладки, как под панелью вкладок; [tint] — цвет раздела, на котором окно появилось
 * (в единой теме — цвет темы). «ОК», касание мимо и «Назад» — одно и то же [onDismiss]
 * (версия запоминается — `WhatsNew.dismiss`).
 */
@Composable
fun WhatsNewOverlay(notes: ReleaseNotes, tint: Color, backdrop: HazeState?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    GlassDialogOverlay(onDismiss, modifier) { cardModifier ->
        WhatsNewContent(
            notes,
            onDismiss,
            itemsMaxHeight = maxHeight * ITEMS_MAX_HEIGHT_FRACTION,
            tint = tint,
            backdrop = backdrop,
            modifier = cardModifier,
        )
    }
}

/**
 * Карточка окна — для превью и UI-тестов тоже. Пункты выше [itemsMaxHeight] прокручиваются,
 * «ОК» всегда видна. [backdrop] `null` — без размытия.
 */
@Composable
fun WhatsNewContent(
    notes: ReleaseNotes,
    onDismiss: () -> Unit,
    itemsMaxHeight: Dp,
    modifier: Modifier = Modifier,
    tint: Color = InabahTheme.palette.gold,
    backdrop: HazeState? = null,
) {
    val palette = InabahTheme.palette
    GlassDialogCard(tint, backdrop, paneTitle = notes.version.text, modifier = modifier) {
        Text(
            stringResource(R.string.whats_new_title, notes.version.text),
            color = palette.onAccent,
            style = InabahType.title3,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        Column(
            Modifier
                .heightIn(max = itemsMaxHeight)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            notes.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    // Маркер TalkBack не читает — только пункт.
                    Text(BULLET, color = tint, style = InabahType.callout, modifier = Modifier.clearAndSetSemantics {})
                    Text(item, color = palette.onAccent, style = InabahType.callout)
                }
            }
        }
        // Как «Сбросить» в подтверждении (решение пользователя 2026-10-11), текст — в цвет раздела.
        // Уже карточки, по центру (решение пользователя 2026-10-11: во всю ширину — слишком растянута).
        PopoverActionButton(
            label = stringResource(R.string.whats_new_ok),
            color = tint,
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(BUTTON_WIDTH_FRACTION),
        )
    }
}
