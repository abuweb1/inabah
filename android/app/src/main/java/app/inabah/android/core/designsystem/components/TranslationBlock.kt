package app.inabah.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Palette
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import kotlin.math.roundToInt

/** Скругление правых углов подложки — как у пузыря перевода азкаров (iOS `ZikrTranslationView`). */
private val BubbleRadius = 8.dp

/** Середина строчных букв над базовой линией — доля кегля (x-height Inter ≈ 0,55 → половина). */
private const val X_HEIGHT_CENTER = 0.27f

/**
 * Цвета блока перевода — у каждого раздела свои (в «По умолчанию» азкары фиолетовые, хадисы зелёные;
 * в единых стилях — цвета стиля). [lead] — строка над пузырём («Передал», транскрипция).
 */
@Immutable
data class TranslationColors(val stripe: Color, val fill: Color, val lead: Color, val text: Color, val source: Color) {
    companion object {
        fun azkar(palette: Palette) = TranslationColors(
            stripe = palette.accent,
            fill = palette.accentDim,
            lead = palette.textSecondary,
            text = palette.textPrimary,
            source = palette.zikrSource,
        )

        fun hadith(palette: Palette) = TranslationColors(
            stripe = palette.noteStripe,
            fill = palette.noteFill,
            lead = palette.noteText,
            text = palette.textPrimary,
            source = palette.onAccentSecondary,
        )
    }
}

/**
 * Пузырь перевода: подложка [fill] со скруглёнными правыми углами и полоса [stripe] слева (RTL — справа).
 * Общий у перевода и сносок.
 */
fun Modifier.translationBubble(fill: Color, stripe: Color): Modifier = this
    .fillMaxWidth()
    .background(fill, RoundedCornerShape(topEnd = BubbleRadius, bottomEnd = BubbleRadius))
    .drawBehind {
        val width = Size.accentStripe.toPx()
        val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - width
        drawRect(stripe, topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(width, size.height))
    }
    .padding(horizontal = Spacing.m, vertical = Spacing.s)

/**
 * Блок перевода зикра и хадиса (решение пользователя 2026-10-10, `docs/analysis/2026-10-10-shared-translation-block.md`):
 * «Передал: …» ([narrator]), транскрипция, перевод, [belowText] (сноски хадиса), источник и справа от него —
 * [sourceTrailing] (действия хадиса) на уровне последней строки. Перевод — в пузыре ([bubble], как у зикра) или
 * просто текстом (у хадиса: пузырь — только у сносок); [narratorDivider] — линия между «Передал» и текстом.
 * Сворачивает блок не он, а экран. Язык текста — язык перевода (переносы); стили — переводов (растут с «Размером переводов»).
 */
@Composable
fun TranslationBlock(
    text: String,
    locale: LocaleList,
    colors: TranslationColors,
    modifier: Modifier = Modifier,
    narrator: String? = null,
    transliteration: String? = null,
    source: String? = null,
    belowText: @Composable ColumnScope.() -> Unit = {},
    sourceTrailing: (@Composable (Modifier) -> Unit)? = null,
    bubble: Boolean = true,
    narratorDivider: Boolean = false,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        narrator?.let {
            Text(it, color = colors.lead, style = InabahType.contentNote.copy(fontWeight = FontWeight.SemiBold, localeList = locale))
            if (narratorDivider) Box(Modifier.fillMaxWidth().height(Size.hairline).background(InabahTheme.palette.hairline))
        }
        transliteration?.let {
            Text(it, color = colors.lead, style = InabahType.contentTransliteration.copy(localeList = locale))
        }
        Text(
            text,
            color = colors.text,
            style = InabahType.contentTranslation.copy(localeList = locale),
            modifier = if (bubble) Modifier.translationBubble(colors.fill, colors.stripe) else Modifier,
        )
        belowText()
        when {
            source != null -> SourceLine(source, locale, colors.source, sourceTrailing)
            sourceTrailing != null -> sourceTrailing(Modifier.align(Alignment.End))
        }
    }
}

/** Источник и справа [trailing] — середина значков на середине строчных букв последней строки источника. */
@Composable
private fun SourceLine(text: String, locale: LocaleList, color: Color, trailing: (@Composable (Modifier) -> Unit)?) {
    val style = InabahType.contentNote.copy(localeList = locale)
    if (trailing == null) {
        Text(text, color = color, style = style)
        return
    }
    val baselineToCenter = with(LocalDensity.current) { (style.fontSize.toPx() * X_HEIGHT_CENTER).roundToInt() }
    Row(Modifier.fillMaxWidth()) {
        Text(text, color = color, style = style, modifier = Modifier.weight(1f).alignBy(LastBaseline))
        trailing(Modifier.alignBy { it.measuredHeight / 2 + baselineToCenter })
    }
}
