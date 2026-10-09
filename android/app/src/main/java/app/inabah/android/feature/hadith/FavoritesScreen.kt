package app.inabah.android.feature.hadith

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ContentError
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.NoticeCard
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.settings.HadithCollectionOrder
import app.inabah.android.core.settings.HadithFavorites
import app.inabah.android.core.settings.HadithProgress
import app.inabah.android.core.settings.HadithStatus
import kotlinx.coroutines.launch

/** Сборник в «Избранном»: загружен — отмеченные хадисы по номеру, иначе — его состояние загрузки. */
internal sealed interface FavoritesGroup {
    val collection: HadithCollection

    data class Hadiths(override val collection: HadithCollection, val hadiths: List<Hadith>) : FavoritesGroup
    data class Pending(override val collection: HadithCollection, val failed: Boolean) : FavoritesGroup
}

/**
 * Группы в порядке главной хадисов; закладки на номера, которых нет в сборнике (данные поменялись),
 * не показываются — сборник без видимых хадисов группы не даёт.
 */
internal fun favoritesGroups(
    favorites: Set<HadithId>,
    order: List<HadithCollection>,
    states: Map<HadithCollection, Loadable<List<Hadith>>>,
): List<FavoritesGroup> = order.mapNotNull { collection ->
    if (favorites.none { it.collection == collection }) return@mapNotNull null
    when (val state = states[collection] ?: Loadable.Idle) {
        is Loadable.Loaded -> state.value.filter { it.id in favorites }.takeIf { it.isNotEmpty() }
            ?.let { FavoritesGroup.Hadiths(collection, it) }
        is Loadable.Failed -> FavoritesGroup.Pending(collection, failed = true)
        Loadable.Idle, Loadable.Loading -> FavoritesGroup.Pending(collection, failed = false)
    }
}

/**
 * «Избранное» (решения пользователя 2026-10-10, `docs/03-screen-hadith-home.md`): хадисы из всех
 * сборников по группам в порядке главной, строки — как в списке сборника. Пусто — карточка по центру.
 * Убрать хадис — только закладкой на его экране.
 */
@Composable
fun FavoritesScreen(
    store: HadithStore,
    progress: HadithProgress,
    favorites: HadithFavorites,
    order: HadithCollectionOrder,
    onBack: () -> Unit,
    onOpenHadith: (HadithId) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    val favoriteIds by favorites.favorites.collectAsStateWithLifecycle()
    val collections by order.collections.collectAsStateWithLifecycle()
    val states by store.collections.collectAsStateWithLifecycle()
    val statuses by progress.statuses.collectAsStateWithLifecycle()
    // Повторная попытка живёт, пока экран на месте: ушли — загрузка отменяется (стор вернёт Idle).
    val scope = rememberCoroutineScope()
    val marked = remember(favoriteIds) { favoriteIds.mapTo(mutableSetOf()) { it.collection } }
    LaunchedEffect(marked) { marked.forEach { store.load(it) } }
    val groups = remember(favoriteIds, collections, states) { favoritesGroups(favoriteIds, collections, states) }

    Column(modifier.fillMaxSize().background(theme.gradients.hadithBackground)) {
        InabahTopBar(
            title = stringResource(R.string.hadith_favorites_title),
            tint = palette.tabHadith,
            onBack = onBack,
            background = palette.hadithHeader,
        )
        if (groups.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(horizontal = Spacing.xl).padding(bottom = contentPadding.calculateBottomPadding()),
                contentAlignment = Alignment.Center,
            ) {
                NoticeCard(
                    icon = R.drawable.ic_bookmark,
                    title = stringResource(R.string.hadith_favorites_empty_title),
                    message = stringResource(R.string.hadith_favorites_empty_message),
                )
            }
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                bottom = Spacing.m + contentPadding.calculateBottomPadding(),
            ),
        ) {
            groups.forEach { group ->
                item(key = group.collection.key) {
                    Text(
                        stringResource(group.collection.title),
                        color = palette.onAccent,
                        style = InabahType.headline,
                        modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.xs).semantics { heading() },
                    )
                }
                when (group) {
                    is FavoritesGroup.Hadiths -> items(group.hadiths, key = { "${it.id.collection.key}_${it.number}" }) { hadith ->
                        val interaction = remember { MutableInteractionSource() }
                        HadithRow(
                            hadith = hadith,
                            status = statuses[hadith.id] ?: HadithStatus.None,
                            modifier = Modifier
                                .clickable(interaction, indication = null, role = Role.Button) { onOpenHadith(hadith.id) }
                                .pressFeedback(interaction, opacity = PressFeedback.ROW_OPACITY),
                        )
                    }
                    is FavoritesGroup.Pending -> item(key = "${group.collection.key}_state") {
                        Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), contentAlignment = Alignment.Center) {
                            if (group.failed) {
                                ContentError(onRetry = { scope.launch { store.load(group.collection) } })
                            } else {
                                CircularProgressIndicator(color = palette.onAccent)
                            }
                        }
                    }
                }
            }
        }
    }
}
