package app.inabah.android.feature.settings

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.ZikrTranslation
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalContentTextScale
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.SettingsGroup
import app.inabah.android.core.designsystem.components.SettingsScaffold
import app.inabah.android.core.designsystem.components.StepSlider
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.settings.ContentTextSize
import app.inabah.android.core.settings.HadithStatus
import app.inabah.android.core.settings.InterfaceTextSize
import app.inabah.android.core.settings.TextSizeSettings
import app.inabah.android.feature.azkar.AzkarStore
import app.inabah.android.feature.azkar.ZikrTranslationBlock
import app.inabah.android.feature.hadith.HadithRow
import app.inabah.android.feature.hadith.HadithStore

/**
 * «Размер текста» (iOS `TextSizeSettingsView`): шаг переводов (5) и интерфейса (3) с живыми
 * образцами — перевод зикра с самым коротким переводом и строка хадиса № 1 ан-Навави.
 * Смена переводов — с анимацией кегля, интерфейса — без (перестраивается всё приложение).
 */
@Composable
fun TextSizeSettingsScreen(
    settings: TextSizeSettings,
    azkarStore: AzkarStore,
    hadithStore: HadithStore,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val content by settings.content.collectAsStateWithLifecycle()
    val interfaceSize by settings.interfaceSize.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { azkarStore.load(AzkarSection.Morning) }
    LaunchedEffect(Unit) { hadithStore.load(HadithCollection.Nawawi) }
    val azkar by azkarStore.state(AzkarSection.Morning).collectAsStateWithLifecycle()
    val hadiths by hadithStore.collections.collectAsStateWithLifecycle()
    val sampleTranslation = (azkar as? Loadable.Loaded)?.value
        ?.mapNotNull { it.zikr.translation }
        ?.minByOrNull { it.text.length }
    val sampleHadith = (hadiths[HadithCollection.Nawawi] as? Loadable.Loaded)?.value?.firstOrNull()

    SettingsScaffold(
        background = theme.gradients.settingsBackground,
        contentPadding = contentPadding,
        modifier = modifier,
        topBar = {
            InabahTopBar(title = stringResource(R.string.settings_text_size_title), tint = theme.palette.tabSettings, onBack = onBack)
        },
    ) {
        SettingsGroup(
            header = stringResource(R.string.settings_text_size_content_header),
            footer = stringResource(R.string.settings_text_size_content_footer),
            rows = listOf {
                ContentStep(content, onSelect = settings::select, sample = sampleTranslation)
            },
        )
        SettingsGroup(
            header = stringResource(R.string.settings_text_size_interface_header),
            footer = stringResource(R.string.settings_text_size_interface_footer),
            rows = listOf {
                InterfaceStep(interfaceSize, onSelect = settings::select, sample = sampleHadith)
            },
        )
    }
}

@Composable
private fun ContentStep(size: ContentTextSize, onSelect: (ContentTextSize) -> Unit, sample: ZikrTranslation?) {
    val palette = InabahTheme.palette
    // Образец плавно меняет кегль — тем же движением, что А−/А+.
    val scale by animateFloatAsState(size.scale, Motion.fontSize(), label = "contentScale")
    StepRow(
        title = stringResource(size.title),
        label = stringResource(R.string.settings_text_size_content_label),
        count = ContentTextSize.entries.size,
        index = size.ordinal,
        onSelect = { onSelect(ContentTextSize.entries[it]) },
    ) {
        sample?.let {
            CompositionLocalProvider(LocalContentTextScale provides scale) {
                ZikrTranslationBlock(
                    it,
                    Modifier
                        .padding(vertical = Spacing.xs)
                        .surface(palette.card, Radius.box)
                        .padding(Spacing.l),
                )
            }
        }
    }
}

@Composable
private fun InterfaceStep(size: InterfaceTextSize, onSelect: (InterfaceTextSize) -> Unit, sample: Hadith?) {
    StepRow(
        title = stringResource(size.title),
        label = stringResource(R.string.settings_text_size_interface_label),
        count = InterfaceTextSize.entries.size,
        index = size.ordinal,
        onSelect = { onSelect(InterfaceTextSize.entries[it]) },
    ) {
        sample?.let { HadithRow(it, HadithStatus.Read) }
    }
}

/** Название шага над ползунком и образец под ним — одна строка группы. */
@Composable
private fun StepRow(
    title: String,
    label: String,
    count: Int,
    index: Int,
    onSelect: (Int) -> Unit,
    sample: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        // Название озвучивает ползунок (значение) — здесь скрыто.
        Text(title, color = InabahTheme.palette.onAccent, modifier = Modifier.clearAndSetSemantics {},
            style = InabahType.subheadline.copy(fontWeight = FontWeight.SemiBold))
        StepSlider(count = count, index = index, onSelect = onSelect, label = label, valueTitle = title)
        sample()
    }
}

@get:StringRes
private val ContentTextSize.title: Int
    get() = when (this) {
        ContentTextSize.Smaller -> R.string.text_size_content_smaller
        ContentTextSize.Standard -> R.string.text_size_content_standard
        ContentTextSize.Larger -> R.string.text_size_content_larger
        ContentTextSize.Large -> R.string.text_size_content_large
        ContentTextSize.Largest -> R.string.text_size_content_largest
    }

@get:StringRes
private val InterfaceTextSize.title: Int
    get() = when (this) {
        InterfaceTextSize.Smaller -> R.string.text_size_interface_smaller
        InterfaceTextSize.Standard -> R.string.text_size_interface_standard
        InterfaceTextSize.Larger -> R.string.text_size_interface_larger
    }
