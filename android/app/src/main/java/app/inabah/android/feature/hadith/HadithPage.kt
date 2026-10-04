package app.inabah.android.feature.hadith

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import app.inabah.android.R
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithTranslation
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.Tracking
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.ParchmentPanel
import app.inabah.android.core.designsystem.components.StatusGlyph
import app.inabah.android.core.designsystem.components.StatusGlyphKind
import app.inabah.android.core.designsystem.components.ToggleTile
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.settings.HadithStatus

/** ▶ заглушки аудио — как символ headline, в sp (растёт с шагом интерфейса); круг 44 — постоянный, как в iOS. */
private const val AUDIO_ICON_SIZE = 20f

/**
 * Страница экрана хадиса (iOS `HadithPage`): арабский текст на пергаменте, отметки, заглушка аудио,
 * перевод. Статус и кегль приходят значениями — отметка другого хадиса страницу не перестраивает.
 */
@Composable
fun HadithPage(
    hadith: Hadith,
    status: HadithStatus,
    arabicFontSize: Float,
    onToggleRead: () -> Unit,
    onToggleMemorized: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.m)
            .padding(bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ArabicPanel(hadith, arabicFontSize)
        HadithStatusButtons(status, onToggleRead, onToggleMemorized)
        HadithAudioPlaceholder(hadith.number)
        hadith.translation?.let { HadithTranslationCard(it) }
    }
}

/** «ХАДИС 3» и арабский текст одним абзацем. */
@Composable
private fun ArabicPanel(hadith: Hadith, arabicFontSize: Float) {
    val palette = InabahTheme.palette
    ParchmentPanel(Modifier.fillMaxWidth(), topCornerRadius = Radius.card, bottomCornerRadius = Radius.card) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(
                stringResource(R.string.hadith_detail_number, hadith.number).uppercase(),
                color = palette.parchmentInk,
                style = InabahType.caption2.copy(fontWeight = FontWeight.Bold, letterSpacing = Tracking.label),
            )
            ArabicText(hadith.arabicDisplayText, arabicFontSize, palette.parchmentInk, Modifier.fillMaxWidth())
        }
    }
}

/** «Прочитать / Прочитан» и «Выучить / Выучен»; правило «выучен ⇒ прочитан» — в `HadithProgress`. */
@Composable
private fun HadithStatusButtons(status: HadithStatus, onToggleRead: () -> Unit, onToggleMemorized: () -> Unit) {
    val palette = InabahTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        ToggleTile(
            checked = status.isRead,
            onCheckedChange = { onToggleRead() },
            label = stringResource(if (status.isRead) R.string.hadith_status_read else R.string.hadith_status_mark_read),
            accessibilityLabel = stringResource(R.string.hadith_status_read),
            tint = palette.statusRead,
            fill = palette.statusReadTint,
            activeFill = palette.statusReadStrong,
            modifier = Modifier.weight(1f),
        ) { color, size -> StatusGlyph(StatusGlyphKind.Read, color, size, isFilled = status.isRead) }
        ToggleTile(
            checked = status.isMemorized,
            onCheckedChange = { onToggleMemorized() },
            label = stringResource(if (status.isMemorized) R.string.hadith_status_memorized else R.string.hadith_status_memorize),
            accessibilityLabel = stringResource(R.string.hadith_status_memorized),
            tint = palette.statusMemorized,
            fill = palette.statusMemorizedTint,
            activeFill = palette.statusMemorizedStrong,
            modifier = Modifier.weight(1f),
        ) { color, size -> StatusGlyph(StatusGlyphKind.Memorized, color, size, isFilled = status.isMemorized) }
    }
}

/** Аудио хадисов ещё не записано — неактивная карточка, как в прототипе. */
@Composable
private fun HadithAudioPlaceholder(number: Int) {
    val palette = InabahTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .surface(palette.subtleFill, Radius.box)
            .padding(vertical = Spacing.m, horizontal = Spacing.l)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Неактивный значок — не кнопка «Воспроизвести»: TalkBack читает только подписи.
        Box(Modifier.size(Size.visibleTapTarget).background(palette.track, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, tint = palette.onAccentTertiary,
                modifier = Modifier.size(with(LocalDensity.current) { AUDIO_ICON_SIZE.sp.toDp() }))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxxs)) {
            Text(stringResource(R.string.hadith_audio_title, number), color = palette.onAccentSecondary, style = InabahType.caption)
            Text(stringResource(R.string.hadith_audio_soon), color = palette.onAccentTertiary, style = InabahType.caption2)
        }
    }
}

/** «Передал: …», перевод и «Приводится: …» — стили переводов (настройка «Размер текста»). */
@Composable
private fun HadithTranslationCard(translation: HadithTranslation) {
    val palette = InabahTheme.palette
    // Язык перевода — только для его текста (переносы); подписи — на языке интерфейса.
    val locale = LocaleList(translation.language.code)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .surface(palette.subtleFill, Radius.box)
            .padding(vertical = Spacing.l, horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        translation.narrator?.let {
            Text(stringResource(R.string.hadith_detail_narrator, it), color = palette.onAccentSecondary,
                style = InabahType.contentNote.copy(fontWeight = FontWeight.SemiBold))
        }
        Text(translation.text, color = palette.onAccent, style = InabahType.contentTranslation.copy(localeList = locale))
        translation.source?.let {
            // Линия над источником, под ней отступ 8 (iOS: overlay сверху у padding(.top)).
            Column {
                Box(Modifier.fillMaxWidth().height(Size.hairline).background(palette.hairline))
                Text(
                    stringResource(R.string.hadith_detail_source, it),
                    color = palette.onAccentTertiary,
                    style = InabahType.contentNote.copy(fontStyle = FontStyle.Italic),
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }
        }
    }
}
