package app.inabah.android.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.components.GlassIconButton
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.ReorderLabels
import app.inabah.android.core.designsystem.components.ReorderableList
import app.inabah.android.core.designsystem.components.SettingsGroup
import app.inabah.android.core.designsystem.components.SettingsIcon
import app.inabah.android.core.designsystem.components.SettingsRowText
import app.inabah.android.core.designsystem.components.SettingsScaffold
import app.inabah.android.core.settings.HadithCollectionOrder
import app.inabah.android.feature.hadith.cardGradient
import app.inabah.android.feature.hadith.icon
import app.inabah.android.feature.hadith.iconColor
import app.inabah.android.feature.hadith.title

/**
 * Порядок сборников на главной хадисов (iOS `HadithOrderSettingsView`): перетаскивание за ручку
 * справа (видна всегда), «Восстановить» в навбаре; для TalkBack — действия «Выше» / «Ниже».
 */
@Composable
fun HadithOrderSettingsScreen(
    order: HadithCollectionOrder,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val tint = theme.palette.tabHadith
    // Порядок читается здесь, в теле экрана: его смена пересчитывает и «Восстановить» в навбаре.
    val collections = order.collections.collectAsStateWithLifecycle().value
    val isDefault = order.isDefault
    SettingsScaffold(
        background = theme.gradients.hadithBackground,
        contentPadding = contentPadding,
        modifier = modifier,
        topBar = {
            InabahTopBar(title = stringResource(R.string.settings_hadith_order_title), tint = tint, onBack = onBack) {
                // Значком: текстовая кнопка обрезала бы заголовок «Порядок сборников» (как в iOS).
                GlassIconButton(
                    onClick = order::restoreDefault,
                    iconRes = R.drawable.ic_replay,
                    contentDescription = stringResource(R.string.settings_hadith_order_restore),
                    tint = tint,
                    enabled = !isDefault,
                )
            }
        },
    ) {
        SettingsGroup(
            footer = stringResource(R.string.settings_hadith_order_footer),
            // Одна «строка» группы — вся колонка: строки двигаются сами, разделители рисуются у строк.
            rows = listOf {
                ReorderableList(
                    items = collections,
                    onMove = order::move,
                    liftColor = theme.palette.hadithHeader,
                    labels = ReorderLabels(
                        stringResource(R.string.settings_hadith_order_move_up),
                        stringResource(R.string.settings_hadith_order_move_down),
                    ),
                ) { collection, _ ->
                    SettingsIcon(collection.icon, collection.cardGradient(theme), tint = collection.iconColor(theme))
                    SettingsRowText(stringResource(collection.title))
                }
            },
        )
    }
}
