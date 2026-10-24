package app.inabah.android.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.Zikr
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.ConfirmationPopover
import app.inabah.android.core.designsystem.components.GlassIconButton
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.ReorderLabels
import app.inabah.android.core.designsystem.components.ReorderableList
import app.inabah.android.core.designsystem.components.SettingsGroup
import app.inabah.android.core.designsystem.components.SettingsRow
import app.inabah.android.core.designsystem.components.SettingsScaffold
import app.inabah.android.core.designsystem.components.arabicPreview
import app.inabah.android.core.settings.AzkarComposition
import app.inabah.android.core.settings.defaultComposition
import app.inabah.android.feature.azkar.AzkarStore
import app.inabah.android.feature.azkar.headerColor
import app.inabah.android.feature.azkar.title

/** Арабское начало зикра в строке — закреплённый кегль Scheherazade, как у свёрнутых строк. */
private const val ARABIC_PREVIEW_SIZE = 19f
private const val ARABIC_PREVIEW_WORDS = 6

/** Кнопки «Убрать» / «Добавить» — значок 22 в sp: растёт со строкой (шаг интерфейса). */
private const val ACTION_ICON_SIZE = 22f
private val NumberWidth = 22.dp

/**
 * «Состав азкаров» раздела (решение пользователя 2026-10-24, вариант «Б» наброска `docs/design/azkar-composition.html`):
 * «В наборе» — номер места, начало зикра, «×N», «Убрать», перетаскивание за ручку; «Можно добавить» — «Добавить»
 * в конец набора; «Вернуть набор по умолчанию» в навбаре. Правка — черновиком: применяется при уходе с экрана
 * и уходе приложения в фон (как время азкаров), не на каждом нажатии.
 */
@Composable
fun AzkarCompositionScreen(
    section: AzkarSection,
    store: AzkarStore,
    composition: AzkarComposition,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    val tint = palette.tabAzkar
    // Раздел грузится при запуске приложения; до загрузки списка нет — экран пуст, а не «пустой набор».
    store.state(section).collectAsStateWithLifecycle().value
    val catalog = store.catalog(section)
    // Сохранённый выбор — поток: после «Вернуть набор по умолчанию» экран сразу показывает набор по умолчанию.
    val stored by composition.stored(section).collectAsStateWithLifecycle()
    val saved = remember(catalog, stored) { composition.numbers(section, catalog) }
    var draft by remember { mutableStateOf<List<Int>?>(null) }
    val numbers = draft ?: saved
    val byNumber = remember(catalog) { catalog.associateBy { it.number } }
    val inSet = numbers.mapNotNull { byNumber[it] }
    val available = catalog.filter { it.number !in numbers }
    val isDefault = numbers == defaultComposition(catalog)

    val commit by rememberUpdatedState {
        draft?.let { chosen ->
            composition.set(section, chosen, catalog)
            store.applyComposition(section)
        }
        Unit
    }
    DisposableEffect(section) { onDispose { commit() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { commit() }
    var confirmRestore by remember { mutableStateOf(false) }

    fun change(next: List<Int>) {
        draft = next
    }

    SettingsScaffold(
        background = theme.gradients.azkarBackground,
        contentPadding = contentPadding,
        modifier = modifier,
        topBar = {
            InabahTopBar(title = stringResource(section.title), tint = tint, onBack = onBack) {
                Box {
                    GlassIconButton(
                        onClick = { confirmRestore = true },
                        iconRes = R.drawable.ic_replay,
                        contentDescription = stringResource(R.string.azkar_composition_restore),
                        tint = tint,
                        enabled = !isDefault && catalog.isNotEmpty(),
                    )
                    ConfirmationPopover(
                        expanded = confirmRestore,
                        title = stringResource(R.string.azkar_composition_restore_title),
                        message = stringResource(R.string.azkar_composition_restore_message),
                        actionLabel = stringResource(R.string.azkar_composition_restore_action),
                        tint = tint,
                        onConfirm = {
                            composition.restoreDefault(section)
                            draft = null
                            store.applyComposition(section)
                        },
                        onDismiss = { confirmRestore = false },
                    )
                }
            }
        },
    ) {
        if (catalog.isEmpty()) return@SettingsScaffold
        val removeLabel = stringResource(R.string.azkar_composition_remove)
        val addLabel = stringResource(R.string.azkar_composition_add)
        val canRemove = inSet.size > 1
        SettingsGroup(
            header = stringResource(R.string.azkar_composition_in_set, inSet.size),
            footer = stringResource(R.string.azkar_composition_hint),
            // Одна «строка» группы — весь список: строки двигаются сами, разделители рисуются у строк.
            rows = listOf {
                ReorderableList(
                    items = inSet,
                    onMove = { from, to -> change(numbers.toMutableList().apply { add(to, removeAt(from)) }) },
                    liftColor = section.headerColor(theme),
                    labels = ReorderLabels(
                        stringResource(R.string.azkar_composition_move_up),
                        stringResource(R.string.azkar_composition_move_down),
                    ),
                    extraActions = { zikr ->
                        if (canRemove) listOf(CustomAccessibilityAction(removeLabel) { change(numbers - zikr.number); true }) else emptyList()
                    },
                    trailing = { zikr -> Repetitions(zikr) },
                ) { zikr, index ->
                    RowAction(
                        iconRes = R.drawable.ic_do_not_disturb_on,
                        color = palette.destructive,
                        contentDescription = removeLabel,
                        enabled = canRemove,
                        onClick = { change(numbers - zikr.number) },
                    )
                    Text(
                        "${index + 1}",
                        color = palette.onAccentTertiary,
                        style = InabahType.footnote,
                        modifier = Modifier.widthIn(min = NumberWidth),
                    )
                    ZikrSummary(zikr)
                }
            },
        )
        if (available.isNotEmpty()) {
            SettingsGroup(
                header = stringResource(R.string.azkar_composition_available, available.size),
                rows = available.map { zikr ->
                    {
                        SettingsRow(compact = true, trailing = { Repetitions(zikr) }) {
                            RowAction(
                                iconRes = R.drawable.ic_add_circle,
                                color = palette.success,
                                contentDescription = addLabel,
                                onClick = { change(numbers + zikr.number) },
                            )
                            ZikrSummary(zikr)
                        }
                    }
                },
            )
        }
    }
}

/** Начало зикра: арабский (одна строка) и начало перевода. */
@Composable
private fun RowScope.ZikrSummary(zikr: Zikr) {
    val palette = LocalInabahTheme.current.palette
    val preview = remember(zikr.arabic) { arabicPreview(zikr.arabic, ARABIC_PREVIEW_WORDS) }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxxs)) {
        ArabicText(preview, ARABIC_PREVIEW_SIZE, palette.onAccent, maxLines = 1)
        zikr.translation?.text?.let {
            Text(
                it,
                color = palette.onAccentSecondary,
                style = InabahType.footnote,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** «×N» — сколько раз читать. */
@Composable
private fun Repetitions(zikr: Zikr) {
    Text(
        stringResource(R.string.azkar_composition_repetitions, zikr.repetitions),
        color = LocalInabahTheme.current.palette.gold,
        style = InabahType.footnote,
    )
}

/** Круглая кнопка слева в строке: «Убрать» (красная) или «Добавить» (зелёная); зона касания 48 dp. */
@Composable
private fun RowAction(iconRes: Int, color: Color, contentDescription: String, onClick: () -> Unit, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val iconSize = with(LocalDensity.current) { ACTION_ICON_SIZE.sp.toDp() }
    Box(
        Modifier
            .size(Size.minTapTarget)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (enabled) color else LocalInabahTheme.current.palette.onAccentTertiary,
            modifier = Modifier.size(iconSize),
        )
    }
}
