package app.inabah.android.core.designsystem.components

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.inabah.android.R
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.monospacedDigits
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

// Барабан выбора, как UIPickerView iOS: значения по кругу (…58, 59, 00, 01…), выбранная строка —
// на подсвеченной полосе посередине, дальние строки бледнее и сжаты. Значение сообщается, когда
// барабан остановился.

// Компактнее iOS-барабана: на телефоне пользователя полный размер выглядел громоздко.
private val RowHeight = 32.dp
private const val VISIBLE_ROWS = 5
private val ColumnWidth = 60.dp
private val SelectionRadius = 12.dp
private val ColumnGap = 8.dp
private const val SELECTION_ALPHA = 0.12f

/** Нет прокрутки, начатой действием TalkBack. */
private const val NO_PENDING = -1

/** «Бесконечный» список: столько повторов, чтобы до края не долистать. */
private const val LOOPS = 2_000

/** Первый видимый элемент для [value]: середина «бесконечного» списка, на две строки выше выбранного. */
internal fun wheelFirstIndex(value: Int, count: Int): Int = LOOPS / 2 * count + value - VISIBLE_ROWS / 2

/** Значение строки списка с индексом [index]. */
internal fun wheelValue(index: Int, count: Int): Int = index % count

/** Кратчайший сдвиг по кругу от [from] к [to]: 23 → 1 из 24 — вперёд на 2, а не назад на 22. */
internal fun wheelDistance(from: Int, to: Int, count: Int): Int {
    val forward = Math.floorMod(to - from, count)
    return if (forward > count / 2) forward - count else forward
}

/**
 * Строки барабана из [count] значений: по кругу (часы, минуты) или от края до края (AM / PM) —
 * тогда сверху и снизу пустые строки, чтобы крайние значения вставали на полосу выбора.
 */
internal class WheelRows(val count: Int, val looping: Boolean) {
    private val padding = if (looping) 0 else VISIBLE_ROWS / 2
    val size: Int = if (looping) count * LOOPS else count + 2 * padding

    /** Первый видимый элемент, когда на полосе [value]. */
    fun firstIndex(value: Int): Int = if (looping) wheelFirstIndex(value, count) else value

    fun valueAt(index: Int): Int = if (looping) wheelValue(index, count) else (index - padding).coerceIn(0, count - 1)

    fun isBlank(index: Int): Boolean = !looping && (index < padding || index >= padding + count)

    /** Строка на полосе после сдвига на [delta] значений: у барабана без круга — не дальше крайних. */
    fun shifted(index: Int, delta: Int): Int =
        if (looping) index + delta else (index + delta).coerceIn(padding, padding + count - 1)

    fun distance(from: Int, to: Int): Int = if (looping) wheelDistance(from, to, count) else to - from
}

/**
 * Индекс строки, чей центр ближе всего к центру окна, — она на полосе выбора. Не «первый видимый + 2»:
 * при дробной плотности сверху виден ряд в 1 px, и первый видимый сдвигается на одну строку.
 */
private fun LazyListState.centeredIndex(fallback: Int): Int {
    val info = layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index ?: fallback
}

/** Насколько гаснут и сжимаются дальние строки (доля на краю окна). */
private const val EDGE_FADE = 0.75f
private const val EDGE_SQUEEZE = 0.35f

/** Расстояние до края окна в строках: половина видимых и ещё полстроки. */
private const val EDGE_DISTANCE = VISIBLE_ROWS / 2f + 0.5f

/** Барабан чисел `0 until count` (по кругу), [label] — подпись значения, [description] — для TalkBack. */
@Composable
fun WheelPicker(
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    label: (Int) -> String,
    description: String,
    modifier: Modifier = Modifier,
    looping: Boolean = true,
) {
    val palette = InabahTheme.palette
    val rows = remember(count, looping) { WheelRows(count, looping) }
    val firstIndex = rows.firstIndex(value)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = firstIndex)
    val selected by remember(rows) {
        derivedStateOf { rows.valueAt(state.centeredIndex(fallback = firstIndex + VISIBLE_ROWS / 2)) }
    }
    val currentOnChange by rememberUpdatedState(onValueChange)
    val scope = rememberCoroutineScope()
    // Строка, к которой уже едет барабан по действию TalkBack (-1 — не едет): быстрое второе действие
    // считается от неё, а не от недоехавшей середины — два «Больше» подряд дают +2, а не +1.
    var pendingIndex by remember { mutableIntStateOf(NO_PENDING) }
    fun currentIndex() = if (pendingIndex != NO_PENDING) pendingIndex else state.centeredIndex(fallback = firstIndex + VISIBLE_ROWS / 2)
    // TalkBack: сдвиг барабана на [delta] значений — та же прокрутка, значение уйдёт по её окончании.
    fun step(delta: Int) {
        if (delta == 0) return
        val center = rows.shifted(currentIndex(), delta)
        if (center == currentIndex()) return
        pendingIndex = center
        scope.launch { state.animateScrollToItem(center - VISIBLE_ROWS / 2) }
    }
    val increase = stringResource(R.string.common_increase)
    val decrease = stringResource(R.string.common_decrease)
    // Остановок барабана: после каждой значение сверяется с принятым (ниже).
    var settles by remember { mutableIntStateOf(0) }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .filter { !it }
            .collect {
                pendingIndex = NO_PENDING
                currentOnChange(selected)
                settles++
            }
    }
    // Значение не приняли (владелец отклонил его в onChange) или сменили снаружи — барабан едет к принятому,
    // как iOS DatePicker; иначе на нём осталось бы отклонённое, а соседний барабан сохранил бы не то, что видно.
    LaunchedEffect(value, settles) {
        if (state.isScrollInProgress || selected == value) return@LaunchedEffect
        val center = state.centeredIndex(fallback = firstIndex + VISIBLE_ROWS / 2)
        state.animateScrollToItem(center + rows.distance(from = selected, to = value) - VISIBLE_ROWS / 2)
    }
    // Закрыли посреди прокрутки — применить то, что сейчас на полосе.
    DisposableEffect(state) {
        onDispose { if (state.isScrollInProgress) currentOnChange(selected) }
    }
    Box(
        modifier = modifier
            .width(ColumnWidth)
            .height(RowHeight * VISIBLE_ROWS)
            // Для TalkBack — один регулируемый элемент вместо тысяч строк списка: «Часы, 17»,
            // жесты вверх/вниз и действия «Больше»/«Меньше» листают на одно значение.
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = label(selected)
                progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..(count - 1).toFloat(), steps = count - 2)
                setProgress { target ->
                    step(target.roundToInt().coerceIn(0, count - 1) - rows.valueAt(currentIndex()))
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction(increase) { step(1); true },
                    CustomAccessibilityAction(decrease) { step(-1); true },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(rows.size) { index ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RowHeight)
                        .graphicsLayer {
                            // Дальние от середины строки — бледнее и ниже, как у барабана iOS.
                            val info = state.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                            val center = (info.viewportEndOffset + info.viewportStartOffset) / 2f
                            val distance = item?.let { abs(it.offset + it.size / 2f - center) / it.size } ?: VISIBLE_ROWS.toFloat()
                            val t = min(distance / EDGE_DISTANCE, 1f)
                            alpha = 1f - EDGE_FADE * t
                            scaleY = 1f - EDGE_SQUEEZE * t
                        },
                ) {
                    if (!rows.isBlank(index)) {
                        Text(
                            label(rows.valueAt(index)),
                            color = palette.onAccent,
                            textAlign = TextAlign.Center,
                            style = InabahType.title3.monospacedDigits(),
                        )
                    }
                }
            }
        }
    }
}

/** 12-часовой барабан: подписи «AM» / «PM» в языке интерфейса и подпись третьего барабана для TalkBack. */
data class DayPeriods(val am: String, val pm: String, val description: String)

/**
 * Барабан часов и минут (iOS DatePicker в виде колеса); полоса выбора — одна на все барабаны.
 * Без [dayPeriods] — 24 часа; с ними — часы 12, 1…11 по кругу и третий барабан AM / PM без круга,
 * как iOS при 12-часовом формате системы. [hour] и [onChange] — всегда 0…23.
 */
@Composable
fun TimeWheelPicker(
    hour: Int,
    minute: Int,
    onChange: (hour: Int, minute: Int) -> Unit,
    hourDescription: String,
    minuteDescription: String,
    modifier: Modifier = Modifier,
    dayPeriods: DayPeriods? = null,
) {
    val palette = InabahTheme.palette
    val currentHour by rememberUpdatedState(hour)
    val currentMinute by rememberUpdatedState(minute)
    val columns = if (dayPeriods == null) 2 else 3
    Box(modifier.padding(horizontal = Spacing.m, vertical = Spacing.s), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(ColumnWidth * columns + ColumnGap * (columns - 1))
                .height(RowHeight)
                .surface(SolidColor(palette.onAccent.copy(alpha = SELECTION_ALPHA)), RoundedCornerShape(SelectionRadius)),
        )
        Row {
            if (dayPeriods == null) {
                WheelPicker(HOURS, hour, { onChange(it, currentMinute) }, ::twoDigits, hourDescription)
            } else {
                // Часы 12-часового барабана: 0 — «12», остальные как есть; половина дня — с третьего барабана.
                WheelPicker(
                    HALF_DAY, hour % HALF_DAY,
                    { onChange(it + currentHour / HALF_DAY * HALF_DAY, currentMinute) },
                    { if (it == 0) HALF_DAY.toString() else it.toString() },
                    hourDescription,
                )
            }
            Box(Modifier.width(ColumnGap))
            WheelPicker(MINUTES, minute, { onChange(currentHour, it) }, ::twoDigits, minuteDescription)
            if (dayPeriods != null) {
                Box(Modifier.width(ColumnGap))
                WheelPicker(
                    DAY_PERIODS, hour / HALF_DAY,
                    { onChange(currentHour % HALF_DAY + it * HALF_DAY, currentMinute) },
                    { if (it == 0) dayPeriods.am else dayPeriods.pm },
                    dayPeriods.description,
                    looping = false,
                )
            }
        }
    }
}

private const val HOURS = 24
private const val HALF_DAY = 12
private const val DAY_PERIODS = 2
private const val MINUTES = 60

private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')
