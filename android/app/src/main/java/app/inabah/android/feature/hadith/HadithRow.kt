package app.inabah.android.feature.hadith

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.inabah.android.R
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ScheherazadeNew
import app.inabah.android.core.designsystem.components.StatusGlyph
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.formatting.toArabicIndicDigits
import app.inabah.android.core.settings.HadithStatus

/** Полоска и бейдж растут вместе с текстом строки (шаг интерфейса) — размеры в sp, как `@ScaledMetric`. */
private const val ACCENT_MIN_HEIGHT = 44f
private const val BADGE_SIZE = 34f
private const val BADGE_NUMBER_SIZE = 21f
private const val STATUS_GLYPH_SIZE = 20f
private const val CHEVRON_SIZE = 16f
private const val PREVIEW_LINES = 2

/** Подъём арабских цифр в бейдже — доля кегля (подобран по снимку). */
private const val DIGIT_LIFT = 0.16f

/**
 * Строка списка хадисов (iOS `HadithRow`): полоска статуса, номер арабскими цифрами в бейдже,
 * начало перевода, передатчик, значок статуса или «›». Статус приходит готовым значением —
 * строка не читает наблюдаемые объекты. Для TalkBack — один элемент.
 */
@Composable
fun HadithRow(hadith: Hadith, status: HadithStatus, modifier: Modifier = Modifier) {
    val palette = InabahTheme.palette
    val density = LocalDensity.current
    fun scaled(points: Float) = with(density) { points.sp.toDp() }
    val divider = palette.divider
    val hairline = with(density) { Size.hairline.toPx() }
    val title = stringResource(R.string.hadith_detail_number, hadith.number)
    val narrator = hadith.translation?.narrator
    val description = listOfNotNull(title, hadith.previewText, narrator).joinToString(", ")
    val statusLabel = status.accessibilityLabel?.let { stringResource(it) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Полоска — на всю высоту строки.
            .height(IntrinsicSize.Min)
            .drawBehind {
                drawRect(divider, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - hairline),
                    size = androidx.compose.ui.geometry.Size(size.width, hairline))
            }
            .padding(vertical = Spacing.m)
            .clearAndSetSemantics {
                contentDescription = description
                statusLabel?.let { stateDescription = it }
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(Size.accentStripe)
                .heightIn(min = scaled(ACCENT_MIN_HEIGHT))
                .fillMaxHeight()
                .background(status.stripe(palette), CircleShape),
        )
        Box(
            Modifier
                .size(scaled(BADGE_SIZE))
                .surface(status.badgeFill(palette), Radius.control, border = status.badgeBorder(palette)),
            contentAlignment = Alignment.Center,
        ) {
            // Цифры — заменой символов, не форматом по локали: та подставила бы латинские.
            Text(
                hadith.number.toArabicIndicDigits(),
                color = status.tint(palette),
                textAlign = TextAlign.Center,
                maxLines = 1,
                // Арабские цифры Scheherazade стоят на базовой линии, ниже центра кегля — поднять,
                // как «ع» на панели вкладок (сдвиг отрисовки, в sp — растёт вместе с цифрой).
                modifier = Modifier.graphicsLayer { translationY = -(BADGE_NUMBER_SIZE * DIGIT_LIFT).sp.toPx() },
                // У Scheherazade строка вдвое выше кегля (место под огласовки): строка = кегль,
                // по центру, без отступов шрифта.
                style = InabahType.body.copy(
                    fontFamily = ScheherazadeNew,
                    fontSize = BADGE_NUMBER_SIZE.sp,
                    lineHeight = BADGE_NUMBER_SIZE.sp,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(hadith.previewText, color = palette.onAccent, style = InabahType.subheadline,
                maxLines = PREVIEW_LINES, overflow = TextOverflow.Ellipsis)
            narrator?.let { Text(it, color = palette.goldMuted, style = InabahType.caption) }
        }
        val glyph = status.glyph
        if (glyph != null) {
            StatusGlyph(glyph, status.tint(palette), STATUS_GLYPH_SIZE.sp)
        } else {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null,
                tint = palette.onAccentTertiary, modifier = Modifier.size(scaled(CHEVRON_SIZE)))
        }
    }
}
