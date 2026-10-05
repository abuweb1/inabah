package app.inabah.android.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.ThemeStyle
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.SelectableTile
import app.inabah.android.core.designsystem.components.SettingsGroup
import app.inabah.android.core.designsystem.components.SettingsScaffold
import app.inabah.android.core.settings.AppearanceSettings

private val PreviewHeight = 96.dp
private val PreviewCardHeight = 18.dp
private val PreviewTabDot = 12.dp

/** Единые стили — сеткой 2×2 под плиткой «По умолчанию». */
private val UnifiedStyles = listOf(ThemeStyle.Violet, ThemeStyle.Emerald, ThemeStyle.Amber, ThemeStyle.Graphite)

/**
 * «Палитра» (iOS `PaletteSettingsView`, снимок 07-sections-palette): «По умолчанию» на всю ширину и
 * четыре единых стиля. Выбор применяется сразу ко всему приложению — с анимацией темы (`animateTheme`).
 */
@Composable
fun PaletteSettingsScreen(
    settings: AppearanceSettings,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val selected by settings.style.collectAsStateWithLifecycle()
    SettingsScaffold(
        background = theme.gradients.settingsBackground,
        contentPadding = contentPadding,
        modifier = modifier,
        topBar = { InabahTopBar(title = stringResource(R.string.settings_palette_title), tint = theme.palette.tabSettings, onBack = onBack) },
    ) {
        SettingsGroup(
            footer = stringResource(R.string.settings_palette_footer),
            rows = listOf {
                Column(
                    Modifier
                        .selectableGroup()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xlPlus),
                ) {
                    PaletteTile(ThemeStyle.Sections, selected, settings::select, Modifier.fillMaxWidth())
                    UnifiedStyles.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                            pair.forEach { PaletteTile(it, selected, settings::select, Modifier.weight(1f)) }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun PaletteTile(style: ThemeStyle, selected: ThemeStyle, onSelect: (ThemeStyle) -> Unit, modifier: Modifier) {
    SelectableTile(
        title = stringResource(style.title),
        subtitle = stringResource(style.subtitle),
        isSelected = style == selected,
        onSelect = { onSelect(style) },
        modifier = modifier,
    ) {
        PalettePreview(style)
    }
}

/**
 * Превью стиля — в его собственных цветах, а не в текущей теме (не мигает во время смены палитры):
 * «По умолчанию» — полосы фонов четырёх разделов; единый стиль — на его фоне утренняя и вечерняя
 * карточки и кружок цвета вкладки.
 */
@Composable
private fun PalettePreview(style: ThemeStyle) {
    val gradients = style.theme.gradients
    if (style == ThemeStyle.Sections) {
        Row(Modifier.fillMaxWidth().height(PreviewHeight)) {
            listOf(gradients.azkarBackground, gradients.hadithBackground, gradients.makharijBackground, gradients.settingsBackground)
                .forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it)) }
        }
        return
    }
    val cardShape = RoundedCornerShape(Radius.small)
    Column(
        Modifier
            .fillMaxWidth()
            .height(PreviewHeight)
            .background(gradients.azkarBackground)
            .padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(Modifier.fillMaxWidth().height(PreviewCardHeight).background(gradients.morningCard, cardShape))
        Box(Modifier.fillMaxWidth().height(PreviewCardHeight).background(gradients.eveningCard, cardShape))
        Box(Modifier.size(PreviewTabDot).background(style.theme.palette.tabAzkar, CircleShape))
    }
}

@get:StringRes
private val ThemeStyle.title: Int
    get() = when (this) {
        ThemeStyle.Sections -> R.string.palette_sections
        ThemeStyle.Violet -> R.string.palette_violet
        ThemeStyle.Emerald -> R.string.palette_emerald
        ThemeStyle.Amber -> R.string.palette_amber
        ThemeStyle.Graphite -> R.string.palette_graphite
    }

@get:StringRes
private val ThemeStyle.subtitle: Int
    get() = when (this) {
        ThemeStyle.Sections -> R.string.palette_sections_subtitle
        ThemeStyle.Violet -> R.string.palette_violet_subtitle
        ThemeStyle.Emerald -> R.string.palette_emerald_subtitle
        ThemeStyle.Amber -> R.string.palette_amber_subtitle
        ThemeStyle.Graphite -> R.string.palette_graphite_subtitle
    }
