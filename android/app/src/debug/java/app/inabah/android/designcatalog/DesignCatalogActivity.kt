package app.inabah.android.designcatalog

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.ThemeStyle
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.BareIconButton
import app.inabah.android.core.designsystem.components.IconButton
import app.inabah.android.core.designsystem.components.IconButtonShape
import app.inabah.android.core.designsystem.components.LinearProgressBar
import app.inabah.android.core.designsystem.components.NavCardRing
import app.inabah.android.core.designsystem.components.NavCardRingStyle
import app.inabah.android.core.designsystem.components.NavCardStat
import app.inabah.android.core.designsystem.components.ParchmentPanel
import app.inabah.android.core.designsystem.components.PrimaryButton
import app.inabah.android.core.designsystem.components.ProgressRing
import app.inabah.android.core.designsystem.components.ProminentRoundButton
import app.inabah.android.core.designsystem.components.SectionNavCard
import app.inabah.android.core.designsystem.components.Sparkle
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import app.inabah.android.core.designsystem.components.StatusGlyph
import app.inabah.android.core.designsystem.components.StatusGlyphKind
import app.inabah.android.core.designsystem.components.ToggleTile
import app.inabah.android.core.designsystem.components.surface

/** Арабский образец — контент, не переводится. */
private const val SAMPLE_ARABIC = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، عَدَدَ خَلْقِهِ، وَرِضَا نَفْسِهِ، وَزِنَةَ عَرْشِهِ، وَمِدَادَ كَلِمَاتِهِ"
private const val BRAND_ARABIC = "إنابة"
private const val BRAND_ARABIC_SIZE = 60f
private const val SAMPLE_ZIKR_COUNT = 16
private const val SAMPLE_HADITH_COUNT = 40

/**
 * Каталог компонентов дизайн-системы — только debug: все компоненты во всех пяти палитрах,
 * для сверки с iOS по снимкам. Запуск: `adb shell am start -n app.inabah.android/.designcatalog.DesignCatalogActivity`
 * (палитра — `--es style emerald`). Подписи стилей — ключи настроек: это инструмент разработчика.
 */
class DesignCatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(AndroidColor.TRANSPARENT), SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val initial = intent.getStringExtra(EXTRA_STYLE)?.let(ThemeStyle::fromKey) ?: ThemeStyle.Sections
        setContent { DesignCatalog(initial) }
    }

    private companion object {
        const val EXTRA_STYLE = "style"
    }
}

@Composable
private fun DesignCatalog(initial: ThemeStyle) {
    var style by rememberSaveable { mutableStateOf(initial) }
    InabahTheme(theme = style.theme) {
        val theme = InabahTheme
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.gradients.azkarBackground)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            StylePicker(style) { style = it }
            // Бренд главных — кегль 60 не должен меняться при крупном системном шрифте.
            ArabicText(BRAND_ARABIC, BRAND_ARABIC_SIZE, theme.palette.onAccent, bold = true, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())
            NavCards()
            Parchment()
            Rings()
            Buttons()
            Glyphs()
        }
    }
}

@Composable
private fun StylePicker(selected: ThemeStyle, onSelect: (ThemeStyle) -> Unit) {
    val palette = InabahTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        ThemeStyle.entries.forEach { style ->
            Text(
                style.key,
                color = if (style == selected) palette.onAccent else palette.onAccentSecondary,
                style = InabahType.caption,
                modifier = Modifier
                    .surface(if (style == selected) palette.accent else palette.subtleFill, Radius.control)
                    .clickable { onSelect(style) }
                    .padding(horizontal = Spacing.s, vertical = Spacing.xs),
            )
        }
    }
}

@Composable
private fun NavCards() {
    val palette = InabahTheme.palette
    val gradients = InabahTheme.gradients
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        SectionNavCard(
            title = stringResource(R.string.azkar_morning_title),
            meta = pluralStringResource(R.plurals.azkar_morning_meta, SAMPLE_ZIKR_COUNT, SAMPLE_ZIKR_COUNT),
            icon = painterResource(R.drawable.ic_sunny),
            iconColor = palette.sunRays,
            gradient = gradients.morningCard,
            shadow = ShadowToken.navCard(palette.morningCardShadow, ShadowToken.Strength.Light),
            ring = NavCardRing(0.13, NavCardRingStyle(palette.track, palette.sunRays, palette.onAccent)),
            onClick = {},
        )
        SectionNavCard(
            title = stringResource(R.string.azkar_evening_title),
            meta = pluralStringResource(R.plurals.azkar_evening_meta, SAMPLE_ZIKR_COUNT, SAMPLE_ZIKR_COUNT),
            icon = painterResource(R.drawable.ic_moon_stars),
            iconColor = palette.gold,
            gradient = gradients.eveningCard,
            shadow = ShadowToken.navCard(palette.shadow, ShadowToken.Strength.Medium),
            ring = NavCardRing(1.0, NavCardRingStyle(palette.goldTrack, palette.gold, palette.gold)),
            onClick = {},
        )
        listOf(
            Triple(R.string.hadith_nawawi_title, R.plurals.hadith_nawawi_meta, gradients.nawawiCard),
            Triple(R.string.hadith_qudsi_title, R.plurals.hadith_qudsi_meta, gradients.qudsiCard),
            Triple(R.string.hadith_ajurri_title, R.plurals.hadith_ajurri_meta, gradients.ajurriCard),
        ).forEachIndexed { index, (title, meta, gradient) ->
            SectionNavCard(
                title = stringResource(title),
                meta = pluralStringResource(meta, SAMPLE_HADITH_COUNT, SAMPLE_HADITH_COUNT),
                icon = painterResource(listOf(R.drawable.ic_book, R.drawable.ic_sunny, R.drawable.ic_draw)[index]),
                iconColor = listOf(palette.goldLight, palette.sunRays, palette.gold)[index],
                gradient = gradient,
                shadow = if (index == 0) {
                    ShadowToken.navCard(palette.nawawiCardShadow, ShadowToken.Strength.Medium)
                } else {
                    ShadowToken.navCard(palette.shadow, ShadowToken.Strength.Strong)
                },
                leadingStat = NavCardStat("12/40", stringResource(R.string.hadith_status_read), StatusGlyphKind.Read, palette.statusRead),
                trailingStat = NavCardStat("3", stringResource(R.string.hadith_status_memorized), StatusGlyphKind.Memorized, palette.statusMemorized),
                onClick = {},
            )
        }
    }
}

@Composable
private fun Parchment() {
    val palette = InabahTheme.palette
    Column(Modifier.surface(palette.card, Radius.card, border = palette.hairline, shadow = ShadowToken.card(palette))) {
        ParchmentPanel {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                ArabicText(SAMPLE_ARABIC, 21f, palette.parchmentText)
                Row(
                    modifier = Modifier
                        .surface(palette.parchmentAccentTint, Radius.small, border = palette.parchmentAccentBorder)
                        .padding(horizontal = Spacing.s, vertical = Spacing.xxxs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Sparkle(palette.parchmentAccent, Size.ornament * 0.8f)
                    Text(pluralStringResource(R.plurals.zikr_repetitions, 3, 3), color = palette.parchmentAccent,
                        style = InabahType.caption2.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
        Text(SAMPLE_ARABIC, color = palette.textSecondary, style = InabahType.footnote, textAlign = TextAlign.Center,
            modifier = Modifier.padding(Spacing.xl))
    }
}

@Composable
private fun Rings() {
    val palette = InabahTheme.palette
    val gradients = InabahTheme.gradients
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        ProgressRing(0.66f, palette.goldTrack, palette.gold, Modifier.size(Size.counter), lineWidth = Size.ringStroke)
        ProgressRing(1f, palette.goldTrack, palette.success, Modifier.size(Size.counter), lineWidth = Size.ringStroke)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        LinearProgressBar(0.13f, palette.track, gradients.progressFill)
        LinearProgressBar(0.6f, palette.track, gradients.progressFill)
        LinearProgressBar(1f, palette.track, palette.statusRead)
    }
}

@Composable
private fun Buttons() {
    val palette = InabahTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        IconButton({}, painterResource(R.drawable.ic_play_arrow), stringResource(R.string.audio_player_play),
            palette.textSecondary, palette.actionBackground)
        IconButton({}, painterResource(R.drawable.ic_replay), stringResource(R.string.common_back),
            palette.textSecondary, palette.actionBackground, enabled = false)
        IconButton({}, painterResource(R.drawable.ic_close), stringResource(R.string.common_cancel),
            palette.onAccent, palette.subtleFill, shape = IconButtonShape.RoundedSquare, size = Size.playerButton)
        BareIconButton({}, painterResource(R.drawable.ic_close), stringResource(R.string.common_cancel), palette.onAccent)
        ProminentRoundButton({}, painterResource(R.drawable.ic_pause), stringResource(R.string.audio_player_pause))
    }
    PrimaryButton({}, stringResource(R.string.common_ok))
    PrimaryButton({}, stringResource(R.string.common_ok), enabled = false)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        var read by rememberSaveable { mutableStateOf(true) }
        var memorized by rememberSaveable { mutableStateOf(false) }
        ToggleTile(read, { read = it }, stringResource(R.string.hadith_status_read), palette.statusRead,
            palette.statusReadTint, palette.statusReadStrong, Modifier.weight(1f)) { color, size ->
            StatusGlyph(StatusGlyphKind.Read, color, size, isFilled = read)
        }
        ToggleTile(memorized, { memorized = it }, stringResource(R.string.hadith_status_memorize), palette.statusMemorized,
            palette.statusMemorizedTint, palette.statusMemorizedStrong, Modifier.weight(1f)) { color, size ->
            StatusGlyph(StatusGlyphKind.Memorized, color, size, isFilled = memorized)
        }
    }
}

@Composable
private fun Glyphs() {
    val palette = InabahTheme.palette
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatusGlyph(StatusGlyphKind.Read, palette.statusRead, GLYPH_SIZE.sp)
        StatusGlyph(StatusGlyphKind.Read, palette.statusRead, GLYPH_SIZE.sp, isFilled = false)
        StatusGlyph(StatusGlyphKind.Memorized, palette.statusMemorized, GLYPH_SIZE.sp)
        StatusGlyph(StatusGlyphKind.Memorized, palette.statusMemorized, GLYPH_SIZE.sp, isFilled = false)
    }
}

private const val GLYPH_SIZE = 48

@Preview(heightDp = 2200)
@Composable
private fun DesignCatalogPreview() = DesignCatalog(ThemeStyle.Sections)
