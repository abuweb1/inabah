package app.inabah.android.feature.azkar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.components.FeaturedVerse
import app.inabah.android.core.designsystem.components.NavCardRing
import app.inabah.android.core.designsystem.components.SectionBrand
import app.inabah.android.core.designsystem.components.SectionHomeLayout
import app.inabah.android.core.designsystem.components.SectionNavCard
import app.inabah.android.core.settings.AzkarWindowSettings

// Арабский контент главной — не переводится.
private const val BRAND_ARABIC = "إنابة"
private const val VERSE_ARABIC = "وَأَنِيبُوا إِلَىٰ رَبِّكُمْ وَأَسْلِمُوا لَهُ"

/** Главная «Азкары» (iOS `AzkarHomeView`): бренд, аят Аз-Зумар 39:54, карточки разделов. */
@Composable
fun AzkarHomeScreen(
    store: AzkarStore,
    windowSettings: AzkarWindowSettings,
    onOpenSection: (AzkarSection) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    SectionHomeLayout(
        background = theme.gradients.azkarBackground,
        brand = SectionBrand(
            arabicName = BRAND_ARABIC,
            tagline = stringResource(R.string.home_brand_tagline),
            latinName = stringResource(R.string.home_brand_latin),
        ),
        verse = FeaturedVerse(
            arabic = VERSE_ARABIC,
            translation = stringResource(R.string.home_verse_translation),
            reference = stringResource(R.string.home_verse_reference),
        ),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        AzkarSection.entries.forEach { section ->
            AzkarSectionCard(store, windowSettings, section, onClick = { onOpenSection(section) })
        }
    }
}

@Composable
private fun AzkarSectionCard(store: AzkarStore, windowSettings: AzkarWindowSettings, section: AzkarSection, onClick: () -> Unit) {
    val theme = LocalInabahTheme.current
    val state by store.state(section).collectAsStateWithLifecycle()
    val progress by store.progress(section).collectAsStateWithLifecycle()
    val isInWindow by store.isInWindow(section).collectAsStateWithLifecycle()
    val windows by windowSettings.windows.collectAsStateWithLifecycle()
    // Число зикров известно после загрузки; до неё — без подписи и кольца.
    val count = (state as? Loadable.Loaded)?.value?.size ?: 0
    val isLoaded = progress.total > 0
    SectionNavCard(
        title = stringResource(section.title),
        icon = painterResource(section.icon),
        iconColor = section.iconColor(theme),
        gradient = section.cardGradient(theme),
        shadow = section.cardShadow(theme),
        onClick = onClick,
        meta = if (count > 0) pluralStringResource(section.cardMeta, count, count) else null,
        // Кольцо — только во время азкаров; вне его — время окна.
        ring = if (isLoaded && isInWindow) NavCardRing(progress.fraction, section.ringStyle(theme)) else null,
        trailingNote = if (isLoaded && !isInWindow) windows.getValue(section).rangeText() else null,
    )
}
