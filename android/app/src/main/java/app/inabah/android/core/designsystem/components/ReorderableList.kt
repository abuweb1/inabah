package app.inabah.android.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.inabah.android.R
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Size
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Поднятая строка при перетаскивании — чуть крупнее соседей. */
private const val LIFTED_SCALE = 1.02f

/** Ручка — значок 22 в sp: растёт со строкой (шаг интерфейса). */
private const val HANDLE_SIZE = 22f
private val DefaultDividerInset = 22.dp
private val HandleTouchHeight = 40.dp

/**
 * Место строки [index] при сдвиге пальца на [offset] от её места; [step] — шаг между строками.
 * На соседнее место — когда строка прошла середину соседней; за краями списка — крайнее место.
 */
internal fun reorderTarget(index: Int, offset: Float, step: Float, count: Int): Int {
    if (step <= 0f || count <= 0) return index
    return (index + (offset / step).roundToInt()).coerceIn(0, count - 1)
}

/** Подписи действий TalkBack «Выше» / «Ниже» — у каждого экрана свои строки. */
data class ReorderLabels(val moveUp: String, val moveDown: String)

/**
 * Строки группы настроек с перетаскиванием за ручку справа («Порядок сборников», «Состав азкаров»):
 * поднятая едет за пальцем на подложке [liftColor], соседи съезжают на освободившееся место; для TalkBack —
 * действия [labels] и [extraActions] строки. [onMove] — строка с места `from` встаёт на место `to`;
 * владелец списка меняет [items] сразу. Одна «строка» [SettingsGroup] — весь список: разделители рисуются здесь.
 */
@Composable
fun <T : Any> ReorderableList(
    items: List<T>,
    onMove: (from: Int, to: Int) -> Unit,
    liftColor: Color,
    labels: ReorderLabels,
    modifier: Modifier = Modifier,
    dividerInset: Dp = DefaultDividerInset,
    extraActions: (T) -> List<CustomAccessibilityAction> = { emptyList() },
    /** Справа от содержимого, перед ручкой (например «×3»). */
    trailing: @Composable RowScope.(T) -> Unit = {},
    content: @Composable RowScope.(item: T, index: Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)
    var dragging by remember { mutableStateOf<T?>(null) }
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
    Column(modifier) {
        items.forEachIndexed { index, item ->
            key(item) {
                val isDragging = dragging == item
                ReorderRow(
                    showsDivider = index > 0,
                    isDragging = isDragging,
                    index = index,
                    step = step,
                    dragOffset = { if (isDragging) dragOffset else 0f },
                    liftColor = liftColor,
                    dividerInset = dividerInset,
                    actions = listOfNotNull(
                        if (index > 0) CustomAccessibilityAction(labels.moveUp) { currentOnMove(index, index - 1); true } else null,
                        if (index < items.lastIndex) CustomAccessibilityAction(labels.moveDown) { currentOnMove(index, index + 1); true } else null,
                    ) + extraActions(item),
                    modifier = Modifier.onSizeChanged { step = it.height },
                    handleModifier = Modifier.pointerInput(item) {
                        // Жест этой ручки ведёт строку; второй палец на другой ручке, пока первый тянет, — пропускается.
                        var owns = false
                        // Место строки во время жеста — своё: список владельца обновится только к следующему кадру.
                        var current = 0
                        detectVerticalDragGestures(
                            onDragStart = {
                                val settling = settle[0]?.isActive == true
                                owns = dragging == null || settling
                                if (owns) {
                                    settle[0]?.cancel()
                                    dragging = item
                                    dragOffset = 0f
                                    current = currentItems.indexOf(item)
                                }
                            },
                            onVerticalDrag = { change, amount ->
                                if (!owns || current < 0) return@detectVerticalDragGestures
                                change.consume()
                                var offset = dragOffset + amount
                                val target = reorderTarget(current, offset, step.toFloat(), currentItems.size)
                                if (target != current) {
                                    currentOnMove(current, target)
                                    // Строка встала на новое место — смещение считается от него.
                                    offset -= (target - current) * step
                                    current = target
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                                dragOffset = offset
                            },
                            onDragEnd = { if (owns) release() },
                            onDragCancel = { if (owns) release() },
                        )
                    },
                    trailing = { trailing(item) },
                    content = { content(item, index) },
                )
            }
        }
    }
}

@Composable
private fun ReorderRow(
    showsDivider: Boolean,
    isDragging: Boolean,
    index: Int,
    step: Int,
    dragOffset: () -> Float,
    liftColor: Color,
    dividerInset: Dp,
    actions: List<CustomAccessibilityAction>,
    modifier: Modifier = Modifier,
    /** Жест перетаскивания — на ручке справа. */
    handleModifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val palette = LocalInabahTheme.current.palette
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
            .then(if (isDragging) Modifier.surface(liftColor, Radius.control, shadow = ShadowToken.card(palette)) else Modifier)
            .semantics(mergeDescendants = true) {
                // Перетаскивание с TalkBack неудобно — те же перестановки действиями.
                customActions = actions
            },
    ) {
        if (showsDivider && !isDragging) {
            Box(
                Modifier
                    .padding(horizontal = dividerInset)
                    .fillMaxWidth()
                    .height(Size.hairline)
                    .background(dividerColor),
            )
        }
        // Компактная строка (решение пользователя 2026-10-05): поля над и под значком 12 вместо 20.
        SettingsRow(
            compact = true,
            trailing = {
                trailing()
                // Зона касания ручки — ширина 48, высота — строка без полей; значок — по центру.
                Box(
                    handleModifier.width(Size.minTapTarget).height(HandleTouchHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_drag_handle), contentDescription = null,
                        tint = palette.onAccentTertiary, modifier = Modifier.size(handleSize))
                }
            },
            content = content,
        )
    }
}
