package app.inabah.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inabah.android.R
import app.inabah.android.core.designsystem.FixedTextSize
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.Tracking
import app.inabah.android.core.designsystem.monospacedDigits
import app.inabah.android.core.settings.ReadingSettings

/** Кегль арабского бренда на главных. */
private const val BRAND_ARABIC_SIZE = 60f

/** Латинское «Inabah» подтягивается к арабскому: у Scheherazade высокая строка (iOS spacing −14). */
private val BrandLatinPull = (-14).dp

/** Наименьшие распорки вокруг карточек: под аятом в рамке / без рамки, под карточками. */
private val CardsTopSpacerFramed = 22.dp
private val CardsTopSpacerPlain = 12.dp
private val CardsBottomSpacer = 16.dp

/** Бренд раздела: арабское название, латинское, подзаголовок, эпиграф. */
@Immutable
data class SectionBrand(
    val arabicName: String,
    val tagline: String,
    val latinName: String? = null,
    /** У буквы есть нижний вынос (ج в «مخارج») — подпись не подтягивается, иначе хвост задевает её. */
    val arabicNameHasDescender: Boolean = false,
    val epigraph: BrandEpigraph? = null,
)

@Immutable
data class BrandEpigraph(val text: String, val source: String)

/** Аят под брендом. */
@Immutable
data class FeaturedVerse(
    val arabic: String,
    val translation: String,
    val reference: String,
    val isFramed: Boolean = true,
)

/**
 * Каркас главных экранов разделов (iOS `SectionHomeLayout`): фон до краёв, бренд, аят и карточки.
 * Прокручивается, если не помещается; если помещается — карточки стоят по центру свободного места.
 */
@Composable
fun SectionHomeLayout(
    background: Brush,
    brand: SectionBrand,
    verse: FeaturedVerse,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    cards: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(background),
    ) {
        val viewport = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewport)
                .padding(contentPadding)
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.s),
        ) {
            BrandBlock(brand)
            VerseBox(
                verse,
                Modifier
                    .padding(horizontal = Spacing.xs)
                    .padding(top = if (verse.isFramed) Spacing.l else Spacing.xs),
            )
            // Свободное место (минимальная высота = экран) делится поровну над и под карточками;
            // наименьшие распорки — отдельно: вес не может их «съесть», если содержимое не помещается.
            Spacer(Modifier.height(if (verse.isFramed) CardsTopSpacerFramed else CardsTopSpacerPlain))
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m), content = cards)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(CardsBottomSpacer))
        }
    }
}

@Composable
private fun BrandBlock(brand: SectionBrand) {
    val palette = InabahTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArabicText(brand.arabicName, BRAND_ARABIC_SIZE, palette.onAccent, bold = true, textAlign = TextAlign.Center)
        Column(
            modifier = if (brand.arabicNameHasDescender) Modifier else Modifier.pullUp(BrandLatinPull),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            // «Inabah» закреплён (10-typography.md, 10.3): не растёт с шагом интерфейса.
            brand.latinName?.let {
                FixedTextSize {
                    Text(it, color = palette.onAccentSecondary, style = InabahType.title.copy(fontWeight = FontWeight.Medium))
                }
            }
            Text(
                brand.tagline,
                color = palette.onAccentTertiary,
                style = InabahType.caption.copy(letterSpacing = Tracking.caption),
                textAlign = TextAlign.Center,
            )
            brand.epigraph?.let { epigraph ->
                Column(
                    modifier = Modifier.padding(top = Spacing.s, start = Spacing.xl, end = Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxxs),
                ) {
                    Text(epigraph.text, color = palette.onAccentSecondary, textAlign = TextAlign.Center,
                        style = InabahType.footnote.copy(fontStyle = FontStyle.Italic))
                    Text(epigraph.source, color = palette.onAccentTertiary, style = InabahType.caption2)
                }
            }
        }
    }
}

/** Отрицательный интервал iOS: блок и всё под ним поднимаются на |[amount]| (высота блока уменьшается). */
private fun Modifier.pullUp(amount: Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val shift = amount.roundToPx()
        layout(placeable.width, (placeable.height + shift).coerceAtLeast(0)) { placeable.place(0, shift) }
    }

@Composable
private fun VerseBox(verse: FeaturedVerse, modifier: Modifier = Modifier) {
    val palette = InabahTheme.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (verse.isFramed) Modifier.surface(palette.subtleFill, Radius.box, border = palette.hairline) else Modifier,
            )
            .padding(vertical = if (verse.isFramed) Spacing.m else 0.dp, horizontal = Spacing.xl)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ArabicText(verse.arabic, ReadingSettings.DEFAULT_SIZE.toFloat(), palette.onAccentStrong, textAlign = TextAlign.Center)
        Text(verse.translation, color = palette.onAccentSecondary, style = InabahType.caption, textAlign = TextAlign.Center)
        Text(verse.reference, color = palette.onAccentTertiary, style = InabahType.caption2, textAlign = TextAlign.Center)
    }
}

/** Показатель карточки сборника (прочитано / выучено): значок, значение, подпись для TalkBack. */
@Immutable
data class NavCardStat(val value: String, val accessibilityLabel: String, val glyph: StatusGlyphKind, val color: Color)

/** Кольцо прогресса на карточке. */
@Immutable
data class NavCardRing(val fraction: Double, val style: NavCardRingStyle)

/**
 * Карточка раздела на главной (iOS `SectionNavCard`): значок, заголовок, подпись, показатели,
 * кольцо и стрелка. Нажатие — масштаб 0,97; для TalkBack — одна кнопка.
 */
@Composable
fun SectionNavCard(
    title: String,
    icon: Painter,
    iconColor: Color,
    gradient: Brush,
    shadow: ShadowToken,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    leadingStat: NavCardStat? = null,
    trailingStat: NavCardStat? = null,
    ring: NavCardRing? = null,
) {
    val palette = InabahTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // clickable снаружи: зона касания не сжимается при нажатии; объединяет потомков —
            // для TalkBack карточка одна кнопка.
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .pressFeedback(interaction, scale = PressFeedback.CARD_SCALE, animation = PressAnimation.Card)
            .defaultMinSize(minHeight = Size.navCardMinHeight)
            .surface(gradient, Radius.navCard, border = palette.divider, shadow = shadow)
            .padding(vertical = NavCardVerticalPadding, horizontal = Spacing.xlPlus),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxxs)) {
            // Значок растёт вместе с текстом шага интерфейса (iOS `@ScaledMetric`): размеры — в sp.
            val iconFrame = with(LocalDensity.current) { NAV_ICON_FRAME.sp.toDp() }
            val iconSymbol = with(LocalDensity.current) { NAV_ICON_SYMBOL.sp.toDp() }
            Box(Modifier.size(iconFrame), contentAlignment = Alignment.CenterStart) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(iconSymbol))
            }
            Spacer(Modifier.size(Spacing.s))
            // Компактная карточка iOS 1.0.0: заголовок headline, подпись caption (10-typography.md, 10.3).
            Text(title, color = palette.onAccent, style = InabahType.headline)
            meta?.let { Text(it, color = palette.onAccentSecondary, style = InabahType.caption) }
            if (leadingStat != null || trailingStat != null) {
                Row(Modifier.fillMaxWidth().padding(top = Spacing.xxs), horizontalArrangement = Arrangement.SpaceBetween) {
                    leadingStat?.let { StatLabel(it) } ?: Spacer(Modifier)
                    trailingStat?.let { StatLabel(it) }
                }
            }
        }
        Spacer(Modifier.size(Spacing.s))
        Row(
            modifier = Modifier.align(Alignment.CenterVertically),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            ring?.let { NavCardProgressRing(it.fraction, it.style) }
            val chevron = with(LocalDensity.current) { CHEVRON_SIZE.sp.toDp() }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null,
                tint = palette.onAccentTertiary, modifier = Modifier.size(chevron))
        }
    }
}

private val NavCardVerticalPadding = 16.dp

/** Значок 26 в рамке 32 и стрелка — как символ headline; в sp, растут с шагом интерфейса. */
private const val NAV_ICON_SYMBOL = 26f
private const val NAV_ICON_FRAME = 32f
private const val CHEVRON_SIZE = 22f

@Composable
private fun StatLabel(stat: NavCardStat) {
    val palette = InabahTheme.palette
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = stat.accessibilityLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        StatusGlyph(stat.glyph, stat.color, InabahType.footnote.fontSize * STAT_GLYPH_SCALE)
        Text(stat.value, color = palette.onAccent, maxLines = 1,
            style = InabahType.footnote.monospacedDigits().copy(fontWeight = FontWeight.SemiBold))
    }
}

/** Значок показателя — 16 при подписи 13 (iOS `StatusGlyph(size: 16, relativeTo: .footnote)`). */
private const val STAT_GLYPH_SCALE = 16f / 13f
