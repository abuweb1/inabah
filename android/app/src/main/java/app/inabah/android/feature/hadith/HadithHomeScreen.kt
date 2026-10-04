package app.inabah.android.feature.hadith

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.components.BrandEpigraph
import app.inabah.android.core.designsystem.components.FeaturedVerse
import app.inabah.android.core.designsystem.components.NavCardStat
import app.inabah.android.core.designsystem.components.SectionBrand
import app.inabah.android.core.designsystem.components.SectionHomeLayout
import app.inabah.android.core.designsystem.components.SectionNavCard
import app.inabah.android.core.designsystem.components.StatusGlyphKind
import app.inabah.android.core.settings.HadithCollectionOrder
import app.inabah.android.core.settings.HadithProgress

// Арабский контент главной — не переводится.
private const val BRAND_ARABIC = "حديث"
private const val VERSE_ARABIC = "مَنْ يُرِدِ اللَّهُ بِهِ خَيْرًا يُفَقِّهْهُ فِي الدِّينِ"
private const val NO_BREAK_SPACE = ' '

/** Главная «Хадисы» (iOS `HadithHomeView`): бренд с эпиграфом, хадис о понимании религии, сборники. */
@Composable
fun HadithHomeScreen(
    store: HadithStore,
    progress: HadithProgress,
    order: HadithCollectionOrder,
    onOpenCollection: (HadithCollection) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val collections by order.collections.collectAsStateWithLifecycle()
    SectionHomeLayout(
        background = theme.gradients.hadithBackground,
        brand = SectionBrand(
            arabicName = BRAND_ARABIC,
            tagline = stringResource(R.string.hadith_home_brand_tagline),
            epigraph = BrandEpigraph(
                text = stringResource(R.string.hadith_home_epigraph_text),
                source = stringResource(R.string.hadith_home_epigraph_source),
            ),
        ),
        verse = FeaturedVerse(
            arabic = VERSE_ARABIC,
            translation = stringResource(R.string.hadith_home_verse_translation),
            reference = stringResource(R.string.hadith_home_verse_reference),
            isFramed = false,
        ),
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        // Порядок карточек задаётся в настройках.
        collections.forEach { collection ->
            key(collection) {
                HadithCollectionCard(store, progress, collection, onClick = { onOpenCollection(collection) })
            }
        }
    }
}

/** Карточка сборника: подпись — после загрузки, показатели — если что-то прочитано или выучено. */
@Composable
private fun HadithCollectionCard(
    store: HadithStore,
    progress: HadithProgress,
    collection: HadithCollection,
    onClick: () -> Unit,
) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    val states by store.collections.collectAsStateWithLifecycle()
    val statuses by progress.statuses.collectAsStateWithLifecycle()
    val total = (states[collection] as? Loadable.Loaded)?.value?.size ?: 0
    val counts = remember(statuses, total) { progress.progress(collection, total) }
    val meta = if (total > 0) {
        // Арабское название переносится на новую строку целиком, не по словам (10-typography.md, 10.3).
        stringResource(
            R.string.section_card_meta_original,
            pluralStringResource(collection.cardMeta, total, total),
            collection.arabicTitle.replace(' ', NO_BREAK_SPACE),
        )
    } else {
        null
    }
    SectionNavCard(
        title = stringResource(collection.title),
        icon = painterResource(collection.icon),
        iconColor = collection.iconColor(theme),
        gradient = collection.cardGradient(theme),
        shadow = collection.cardShadow(theme),
        onClick = onClick,
        meta = meta,
        leadingStat = if (counts.read > 0) {
            NavCardStat(
                value = stringResource(R.string.progress_fraction, counts.read, total),
                accessibilityLabel = stringResource(R.string.hadith_progress_card, counts.read, total),
                glyph = StatusGlyphKind.Read,
                // Значки показателей — белые, как в iOS (не цветом статуса).
                color = palette.onAccent,
            )
        } else {
            null
        },
        trailingStat = if (counts.memorized > 0) {
            NavCardStat(
                value = stringResource(R.string.progress_fraction, counts.memorized, total),
                accessibilityLabel = stringResource(R.string.hadith_progress_card_memorized, counts.memorized, total),
                glyph = StatusGlyphKind.Memorized,
                color = palette.onAccent,
            )
        } else {
            null
        },
    )
}
