package app.inabah.android.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.ParchmentStyle
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.Theme
import app.inabah.android.core.designsystem.ThemeStyle
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.ParchmentPanel
import app.inabah.android.core.designsystem.components.SelectableTile
import app.inabah.android.core.designsystem.components.SettingsGroup
import app.inabah.android.core.designsystem.components.SettingsScaffold
import app.inabah.android.core.designsystem.components.ShadeSlider
import app.inabah.android.core.designsystem.components.TranslationBlock
import app.inabah.android.core.designsystem.components.TranslationColors
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.designsystem.withParchment
import app.inabah.android.core.designsystem.withTranslationShade
import app.inabah.android.core.settings.AppearanceSettings
import app.inabah.android.core.settings.ParchmentSettings
import app.inabah.android.core.settings.TranslationShadeSettings
import kotlin.math.abs
import kotlin.math.roundToInt

private val PreviewHeight = 96.dp
private val PreviewCardHeight = 18.dp
private val PreviewTabDot = 12.dp

/** Сдвиг фона перевода (−1…1) → проценты в описании ползунка. */
private const val PERCENT = 100

/** Образец на превью фона — «Во имя Аллаха»; контент, не переводится. */
private const val PREVIEW_ARABIC = "بِسْمِ اللَّهِ"
private const val PREVIEW_ARABIC_SIZE = 20f

/** Единые стили — сеткой 2×2 под плиткой «По умолчанию». */
private val UnifiedStyles = listOf(ThemeStyle.Violet, ThemeStyle.Emerald, ThemeStyle.Amber, ThemeStyle.Graphite)

/**
 * Фоны под «Пергаментом» (он — на всю ширину, как «По умолчанию» у палитр): сначала по палитрам в их порядке
 * (Фиолетовая, Изумрудная, Янтарная, Графит), затем «Сепия» и универсальный «Ночной».
 */
private val ParchmentGrid = listOf(
    ParchmentStyle.Amethyst, ParchmentStyle.Jade, ParchmentStyle.Amber,
    ParchmentStyle.Smoky, ParchmentStyle.Sepia, ParchmentStyle.Night,
)

/**
 * «Палитра» (iOS `PaletteSettingsView`, снимок 07-sections-palette): «По умолчанию» на всю ширину и
 * четыре единых стиля. Выбор применяется сразу ко всему приложению — с анимацией темы (`animateTheme`).
 */
@Composable
fun PaletteSettingsScreen(
    settings: AppearanceSettings,
    parchmentSettings: ParchmentSettings,
    translationShadeSettings: TranslationShadeSettings,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val selected by settings.style.collectAsStateWithLifecycle()
    val parchment by parchmentSettings.style.collectAsStateWithLifecycle()
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
        // Фон под арабским текстом — тоже про цвет, поэтому здесь (решение пользователя 2026-10-06).
        SettingsGroup(
            header = stringResource(R.string.settings_parchment_header),
            footer = stringResource(R.string.settings_parchment_footer),
            rows = listOf {
                Column(
                    Modifier
                        .selectableGroup()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xlPlus),
                ) {
                    ParchmentTile(ParchmentStyle.Classic, parchment, parchmentSettings::select, Modifier.fillMaxWidth())
                    ParchmentGrid.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                            pair.forEach { ParchmentTile(it, parchment, parchmentSettings::select, Modifier.weight(1f)) }
                        }
                    }
                }
            },
        )
        // Фон перевода — под фоном арабского текста (решение пользователя 2026-10-10).
        SettingsGroup(
            header = stringResource(R.string.settings_translation_shade_header),
            footer = stringResource(R.string.settings_translation_shade_footer),
            rows = listOf {
                TranslationShadeRow(
                    base = selected.theme.withParchment(parchment),
                    settings = translationShadeSettings,
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.m),
                )
            },
        )
    }
}

/**
 * Образец двух карточек перевода и ползунок. Пока палец на ползунке, меняется только образец (черновик);
 * отпустил — сохраняется, и вся тема плавно перекрашивается (`animateTheme` в `MainActivity`).
 * [base] — тема без сдвига: сдвиг всегда считается от неё.
 */
@Composable
private fun TranslationShadeRow(base: Theme, settings: TranslationShadeSettings, modifier: Modifier = Modifier) {
    val saved by settings.shade.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf<Float?>(null) }
    val shade = draft ?: saved
    val percent = (abs(shade) * PERCENT).roundToInt()
    val valueTitle = when {
        percent == 0 -> stringResource(R.string.settings_translation_shade_default)
        shade < 0f -> stringResource(R.string.settings_translation_shade_darker, percent)
        else -> stringResource(R.string.settings_translation_shade_lighter, percent)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        TranslationShadeSample(base.withTranslationShade(shade))
        ShadeSlider(
            value = shade,
            onValueChange = { draft = it },
            onValueChangeFinished = {
                draft?.let(settings::set)
                draft = null
            },
            label = stringResource(R.string.settings_translation_shade_header),
            valueTitle = valueTitle,
            startSwatch = base.withTranslationShade(-1f).palette.zikrCard,
            endSwatch = base.withTranslationShade(1f).palette.zikrCard,
        )
    }
}

/** Карточки перевода зикра и хадиса в теме [theme] — только картинка (TalkBack читает ползунок). */
@Composable
private fun TranslationShadeSample(theme: Theme) {
    val palette = theme.palette
    val text = stringResource(R.string.settings_translation_shade_sample)
    val locale = LocaleList.current
    CompositionLocalProvider(LocalInabahTheme provides theme) {
        // Карточки — одной высоты (по более длинной).
        Row(Modifier.height(IntrinsicSize.Max).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            SampleCard(palette.zikrCard, Modifier.weight(1f)) {
                TranslationBlock(text, locale, TranslationColors.azkar(palette), narrator = stringResource(R.string.tab_azkar))
            }
            SampleCard(palette.hadithCard, Modifier.weight(1f)) {
                TranslationBlock(
                    text, locale, TranslationColors.hadith(palette),
                    narrator = stringResource(R.string.tab_hadith), bubble = false, narratorDivider = true,
                )
            }
        }
    }
}

@Composable
private fun SampleCard(fill: Color, modifier: Modifier, content: @Composable () -> Unit) {
    val palette = LocalInabahTheme.current.palette
    Box(
        modifier
            .fillMaxHeight()
            .surface(fill, Radius.card, border = palette.hairline, shadow = ShadowToken.card(palette))
            .padding(horizontal = Spacing.m, vertical = Spacing.m),
    ) { content() }
}

@Composable
private fun ParchmentTile(style: ParchmentStyle, selected: ParchmentStyle, onSelect: (ParchmentStyle) -> Unit, modifier: Modifier) {
    SelectableTile(
        title = stringResource(style.title),
        isSelected = style == selected,
        onSelect = { onSelect(style) },
        modifier = modifier,
    ) {
        ParchmentPreview(style)
    }
}

/**
 * Превью фона — настоящий пергамент в цветах варианта (тема подменена только внутри плитки), как его
 * увидят азкары и хадисы; не мигает при смене выбора.
 */
@Composable
private fun ParchmentPreview(style: ParchmentStyle) {
    val theme = LocalInabahTheme.current.withParchment(style)
    CompositionLocalProvider(LocalInabahTheme provides theme) {
        // Образец — только картинка: TalkBack читает название плитки, а не «بِسْمِ اللَّهِ» на каждой из шести.
        ParchmentPanel(Modifier.clearAndSetSemantics {}, topCornerRadius = 0.dp, bottomCornerRadius = 0.dp) {
            ArabicText(
                PREVIEW_ARABIC, PREVIEW_ARABIC_SIZE, theme.palette.parchmentText,
                Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1,
            )
        }
    }
}

@get:StringRes
private val ParchmentStyle.title: Int
    get() = when (this) {
        ParchmentStyle.Classic -> R.string.parchment_classic
        ParchmentStyle.Sepia -> R.string.parchment_sepia
        ParchmentStyle.Amethyst -> R.string.parchment_amethyst
        ParchmentStyle.Jade -> R.string.parchment_jade
        ParchmentStyle.Amber -> R.string.parchment_amber
        ParchmentStyle.Smoky -> R.string.parchment_smoky
        ParchmentStyle.Night -> R.string.parchment_night
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
