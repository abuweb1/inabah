package app.inabah.android.feature.hadith

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.CollectionInfo
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.ContentError
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.SettingsGroup
import app.inabah.android.core.designsystem.components.SettingsRow
import app.inabah.android.core.designsystem.components.SettingsRowText
import app.inabah.android.core.designsystem.components.SettingsScaffold
import app.inabah.android.core.settings.HadithProgress
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Значок сборника в шапке — в sp: растёт с шагом интерфейса, как текст рядом. */
private const val HEADER_ICON_SIZE = 44f
private const val ARABIC_TITLE_SIZE = 28f

/**
 * «О сборнике» (решения пользователя 2026-10-10, `docs/04-screen-hadith-list.md`): шапка с названием,
 * составители, абзацы «О книге» (из `data/collections.json`, размером перевода) и прогресс сборника.
 * Группы без содержания (у своей подборки нет составителя) не показываются.
 */
@Composable
fun CollectionAboutScreen(
    collection: HadithCollection,
    store: HadithStore,
    progress: HadithProgress,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    val hadithStates by store.collections.collectAsStateWithLifecycle()
    val infoStates by store.infos.collectAsStateWithLifecycle()
    // Повторная попытка живёт, пока экран на месте: ушли — загрузка отменяется (стор вернёт Idle).
    val scope = rememberCoroutineScope()
    // Хадисы (их число) и сведения — из разных файлов: грузятся одновременно.
    val load: suspend () -> Unit = {
        coroutineScope {
            launch { store.load(collection) }
            launch { store.loadInfo(collection) }
        }
    }
    LaunchedEffect(collection) { load() }

    SettingsScaffold(
        background = theme.gradients.hadithBackground,
        contentPadding = contentPadding,
        modifier = modifier,
        topBar = {
            InabahTopBar(
                title = stringResource(R.string.hadith_about_title),
                tint = palette.tabHadith,
                onBack = onBack,
                background = palette.hadithHeader,
            )
        },
    ) {
        val hadiths = hadithStates[collection] ?: Loadable.Idle
        val info = infoStates[collection] ?: Loadable.Idle
        when {
            hadiths is Loadable.Failed || info is Loadable.Failed -> Box(Modifier.fillMaxWidth(), Alignment.Center) {
                ContentError(onRetry = { scope.launch { load() } })
            }
            hadiths is Loadable.Loaded && info is Loadable.Loaded -> CollectionAboutContent(
                collection = collection,
                info = info.value,
                total = hadiths.value.size,
                progress = progress,
            )
            else -> Box(Modifier.fillMaxWidth(), Alignment.Center) {
                CircularProgressIndicator(color = palette.onAccent)
            }
        }
    }
}

@Composable
private fun CollectionAboutContent(collection: HadithCollection, info: CollectionInfo, total: Int, progress: HadithProgress) {
    val palette = LocalInabahTheme.current.palette
    CollectionAboutHeader(collection, total)
    if (info.authors.isNotEmpty()) {
        SettingsGroup(
            header = pluralStringResource(R.plurals.hadith_about_authors, info.authors.size),
            rows = info.authors.map { author -> { SettingsRow { SettingsRowText(author.name, subtitle = author.years) } } },
        )
    }
    if (info.about.isNotEmpty()) {
        SettingsGroup(
            header = stringResource(R.string.hadith_about_book),
            rows = listOf {
                // Абзацы — контент (категория «перевод»): растут с размером переводов, не интерфейса.
                // В строке группы — те же отступы, что у составителей выше; язык текста — для переносов.
                val style = InabahType.contentTranslation.let { base ->
                    info.language?.let { base.copy(localeList = LocaleList(it.code)) } ?: base
                }
                SettingsRow {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        info.about.forEach { Text(it, color = palette.onAccent, style = style) }
                    }
                }
            },
        )
    }
    val statuses by progress.statuses.collectAsStateWithLifecycle()
    val counts = remember(statuses, total) { progress.progress(collection, total) }
    SettingsGroup(
        header = stringResource(R.string.hadith_about_hadiths),
        rows = listOf {
            SettingsRow(compact = true) {
                Text(
                    stringResource(R.string.hadith_list_progress, counts.read, total, counts.memorized),
                    color = palette.onAccentSecondary,
                    style = InabahType.subheadline,
                )
            }
        },
    )
}

/** Значок, арабское и русское название, «N хадисов» — одним элементом для TalkBack. */
@Composable
private fun CollectionAboutHeader(collection: HadithCollection, total: Int) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    val iconSize = with(LocalDensity.current) { HEADER_ICON_SIZE.sp.toDp() }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.l)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(
            painterResource(collection.icon),
            contentDescription = null,
            tint = collection.iconColor(theme),
            modifier = Modifier.size(iconSize),
        )
        ArabicText(collection.arabicTitle, ARABIC_TITLE_SIZE, palette.onAccent, bold = true, textAlign = TextAlign.Center)
        Text(
            stringResource(collection.title),
            color = palette.onAccent,
            style = InabahType.headline,
            textAlign = TextAlign.Center,
        )
        Text(
            pluralStringResource(collection.cardMeta, total, total),
            color = palette.onAccentSecondary,
            style = InabahType.caption,
        )
    }
}
