package app.inabah.android.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.components.ConfirmationPopover
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.SettingsChevron
import app.inabah.android.core.designsystem.components.SettingsGroup
import app.inabah.android.core.designsystem.components.SettingsRow
import app.inabah.android.core.designsystem.components.SettingsRowText
import app.inabah.android.core.designsystem.components.SettingsScaffold
import app.inabah.android.core.settings.HadithCollectionOrder
import app.inabah.android.core.settings.HadithProgress
import app.inabah.android.feature.hadith.HadithStore
import app.inabah.android.feature.hadith.title

/**
 * Настройки хадисов (iOS `HadithSettingsView`): порядок сборников на главной и сброс отметок
 * «прочитан» / «выучен» каждого сборника отдельно — с подтверждением над строкой.
 */
@Composable
fun HadithSettingsScreen(
    store: HadithStore,
    progress: HadithProgress,
    order: HadithCollectionOrder,
    onOpenOrder: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    val tint = palette.tabHadith
    val collections by order.collections.collectAsStateWithLifecycle()
    val states by store.collections.collectAsStateWithLifecycle()
    val statuses by progress.statuses.collectAsStateWithLifecycle()
    var pendingReset by remember { mutableStateOf<HadithCollection?>(null) }
    // Число хадисов сборника — для «из N»; загрузка уже идёт с запуска, здесь — на случай ошибки.
    LaunchedEffect(Unit) { store.loadAll() }

    SettingsScaffold(
        background = theme.gradients.hadithBackground,
        contentPadding = contentPadding,
        modifier = modifier,
        topBar = { InabahTopBar(title = stringResource(R.string.settings_hadith_title), tint = tint, onBack = onBack) },
    ) {
        SettingsGroup(
            header = stringResource(R.string.settings_hadith_home_header),
            rows = listOf {
                SettingsRow(onClick = onOpenOrder, trailing = { SettingsChevron() }) {
                    SettingsRowText(stringResource(R.string.settings_hadith_order_title))
                }
            },
        )
        collections.forEach { collection -> key(collection) {
            val total = (states[collection] as? Loadable.Loaded)?.value?.size ?: 0
            val counts = remember(statuses, total) { progress.progress(collection, total) }
            val title = stringResource(collection.title)
            SettingsGroup(
                header = title,
                rows = listOf(
                    {
                        SettingsRow(compact = true) {
                            Text(
                                stringResource(R.string.hadith_list_progress, counts.read, total, counts.memorized),
                                color = palette.onAccentSecondary,
                                style = InabahType.subheadline,
                            )
                        }
                    },
                    {
                        // Якорь — вся строка: подтверждение стоит над ней по центру, уголок — на её середину.
                        Box {
                            // Белым, как сброс в настройках азкаров (iOS 26), жирным — как заголовки групп.
                            SettingsRow(onClick = { pendingReset = collection }, enabled = counts.read > 0, compact = true) {
                                Text(
                                    stringResource(R.string.settings_hadith_reset),
                                    color = palette.onAccent,
                                    style = InabahType.body.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                            ConfirmationPopover(
                                expanded = pendingReset == collection,
                                title = stringResource(R.string.settings_hadith_reset_confirm_title),
                                message = stringResource(R.string.settings_hadith_reset_confirm_message, title),
                                actionLabel = stringResource(R.string.settings_hadith_reset_confirm_action),
                                tint = tint,
                                onConfirm = { progress.reset(collection) },
                                onDismiss = { pendingReset = null },
                            )
                        }
                    },
                ),
            )
        } }
    }
}
