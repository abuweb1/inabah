package app.inabah.android.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.components.GlassIconButton
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.SettingsGroup
import app.inabah.android.core.designsystem.components.SettingsIcon
import app.inabah.android.core.designsystem.components.SettingsRow
import app.inabah.android.core.designsystem.components.SettingsRowText
import app.inabah.android.core.designsystem.components.SettingsScaffold
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.settings.HadithCollectionOrder
import app.inabah.android.feature.hadith.cardGradient
import app.inabah.android.feature.hadith.icon
import app.inabah.android.feature.hadith.iconColor
import app.inabah.android.feature.hadith.title
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Поднятая строка при перетаскивании — чуть крупнее соседей. */
private const val LIFTED_SCALE = 1.02f

/** Ручка — значок 22 в sp: растёт со строкой (шаг интерфейса). */
private const val HANDLE_SIZE = 22f
private val DividerInset = 22.dp

/**
 * Место строки [index] при сдвиге пальца на [offset] от её места; [step] — шаг между строками.
 * На соседнее место — когда строка прошла середину соседней; за краями списка — крайнее место.
 */
internal fun reorderTarget(index: Int, offset: Float, step: Float, count: Int): Int {
    if (step <= 0f || count <= 0) return index
    return (index + (offset / step).roundToInt()).coerceIn(0, count - 1)
}

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
            rows = listOf { ReorderableRows(order, collections) },
        )
    }
}

/** Строки с перетаскиванием: поднятая едет за пальцем, соседи съезжают на освободившееся место. */
@Composable
private fun ReorderableRows(order: HadithCollectionOrder, collections: List<HadithCollection>) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf<HadithCollection?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var step by remember { mutableIntStateOf(0) }
    // Доезд отпущенной строки на место: новое перетаскивание его отменяет, а не делит с ним смещение.
    val settle = remember { arrayOfNulls<Job>(1) }
    fun release() {
        settle[0] = scope.launch {
            animate(dragOffset, 0f, animationSpec = Motion.collapse()) { value, _ -> dragOffset = value }
            dragging = null
        }
    }
    Column {
        collections.forEachIndexed { index, collection ->
            key(collection) {
                val isDragging = dragging == collection
                OrderRow(
                    collection = collection,
                    showsDivider = index > 0,
                    isDragging = isDragging,
                    index = index,
                    step = step,
                    dragOffset = { if (isDragging) dragOffset else 0f },
                    canMoveUp = order.canMove(collection, -1),
                    canMoveDown = order.canMove(collection, 1),
                    onMove = { by -> order.move(collection, by) },
                    modifier = Modifier.onSizeChanged { step = it.height },
                    handleModifier = Modifier.pointerInput(collection) {
                        // Жест этой ручки ведёт строку; второй палец на другой ручке, пока первый тянет, — пропускается.
                        var owns = false
                        detectVerticalDragGestures(
                            onDragStart = {
                                val settling = settle[0]?.isActive == true
                                owns = dragging == null || settling
                                if (owns) {
                                    settle[0]?.cancel()
                                    dragging = collection
                                    dragOffset = 0f
                                }
                            },
                            onVerticalDrag = { change, amount ->
                                if (!owns) return@detectVerticalDragGestures
                                change.consume()
                                val current = order.collections.value.indexOf(collection)
                                var offset = dragOffset + amount
                                val target = reorderTarget(current, offset, step.toFloat(), order.collections.value.size)
                                if (target != current) {
                                    order.move(current, target)
                                    // Строка встала на новое место — смещение считается от него.
                                    offset -= (target - current) * step
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                                dragOffset = offset
                            },
                            onDragEnd = { if (owns) release() },
                            onDragCancel = { if (owns) release() },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun OrderRow(
    collection: HadithCollection,
    showsDivider: Boolean,
    isDragging: Boolean,
    index: Int,
    step: Int,
    dragOffset: () -> Float,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Жест перетаскивания — на ручке справа. */
    handleModifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    // Соседа, которого перестановка сдвинула, — от прежнего места к новому (как список iOS).
    val shift = remember { Animatable(0f) }
    val lastIndex = remember { intArrayOf(index) }
    LaunchedEffect(index) {
        val moved = lastIndex[0] - index
        lastIndex[0] = index
        if (moved != 0 && !isDragging) {
            shift.snapTo(shift.value + moved * step)
            shift.animateTo(0f, Motion.highlight())
        }
    }
    val lift by animateFloatAsState(if (isDragging) 1f else 0f, Motion.highlight(), label = "lift")
    val moveUp = stringResource(R.string.settings_hadith_order_move_up)
    val moveDown = stringResource(R.string.settings_hadith_order_move_down)
    val handleSize = with(LocalDensity.current) { HANDLE_SIZE.sp.toDp() }
    val dividerColor = palette.hairline
    Box(
        modifier
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffset() + shift.value
                val scale = 1f + (LIFTED_SCALE - 1f) * lift
                scaleX = scale
                scaleY = scale
            }
            // Перетащенная строка — на непрозрачной подложке с тенью, поверх соседей.
            .then(if (isDragging) Modifier.surface(palette.hadithHeader, Radius.control, shadow = ShadowToken.card(palette)) else Modifier)
            .semantics(mergeDescendants = true) {
                // Перетаскивание с TalkBack неудобно — те же перестановки действиями.
                customActions = listOfNotNull(
                    if (canMoveUp) CustomAccessibilityAction(moveUp) { onMove(-1); true } else null,
                    if (canMoveDown) CustomAccessibilityAction(moveDown) { onMove(1); true } else null,
                )
            },
    ) {
        if (showsDivider && !isDragging) {
            Box(
                Modifier
                    .padding(horizontal = DividerInset)
                    .fillMaxWidth()
                    .height(Size.hairline)
                    .background(dividerColor),
            )
        }
        SettingsRow(
            trailing = {
                // Зона касания ручки — полные 48, значок — по центру.
                Box(handleModifier.size(Size.minTapTarget), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_drag_handle), contentDescription = null,
                        tint = palette.onAccentTertiary, modifier = Modifier.size(handleSize))
                }
            },
        ) {
            SettingsIcon(collection.icon, collection.cardGradient(theme), tint = collection.iconColor(theme))
            SettingsRowText(stringResource(collection.title))
        }
    }
}
