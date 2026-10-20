package app.inabah.android.feature.makharij

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.Indication
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.settings.ReadingSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// Живые нажимаемые ячейки «Учимся читать» (решение пользователя 2026-10-20, набросок
// docs/design/makharij-tap-affordance-2.html): при первом открытии экрана - волна по нажимаемым ячейкам и подсказка
// у первой; у увеличиваемых плиток - «зов» покачиванием с наклоном; удержание - плитка растёт, по краю кольцо.
// Только трансформации отрисовки (graphicsLayer, drawWithContent) - вёрстка не меняется.

/** Подсказка у ячейки: где она (в координатах корня) и что сказать. */
@Stable
internal class TapHint(val target: Rect, val text: String)

/**
 * Волна, подсказка и «зов» одного экрана. Ячейки сообщают о себе через [tapCell] (где стоят, какую подсказку дать);
 * экран оборачивает содержимое в [TapIntroHost]. [seen] - подсказку этого экрана уже показывали: волны нет.
 */
@Stable
class TapIntro internal constructor(
    internal val seen: Boolean,
    internal val zoomable: Boolean,
    /** Ячейку нажали - экран показан (сохранить «видел»). */
    private val onSeen: () -> Unit = {},
) {
    private val cells = mutableMapOf<Any, Cell>()
    internal var hostBounds: Rect = Rect.Zero

    /** Время волны в мс от начала; [WAVE_OFF] - волны нет. */
    internal val wave = Animatable(WAVE_OFF)
    internal var hint by mutableStateOf<TapHint?>(null)
    internal var wiggleId by mutableStateOf<Any?>(null)
    internal val wiggle = Animatable(1f)

    /**
     * Последнее касание экрана - для «зова» при тишине; читается только в эффекте (`snapshotFlow`), не в композиции:
     * иначе каждое касание перерисовывало бы экран.
     */
    internal var lastTouch by mutableLongStateOf(0L)

    /** Ячейку уже нажимали (не прокрутка) - подсказки первого открытия больше не будет. */
    internal var tapped = false
        private set

    internal class Cell(var bounds: Rect, var hint: String, val zoomable: Boolean)

    internal fun register(id: Any, bounds: Rect, hint: String, zoomable: Boolean) {
        val cell = cells[id]
        if (cell == null) cells[id] = Cell(bounds, hint, zoomable) else {
            cell.bounds = bounds
            cell.hint = hint
        }
    }

    /** Ячейка ушла из композиции (страница листания, прокрутка) - не участвует в волне и «зове». */
    internal fun unregister(id: Any) {
        cells.remove(id)
    }

    /** Где ячейка [id] (последнее, что она сообщила); не сообщала - пусто. */
    internal fun boundsOf(id: Any): Rect = cells[id]?.bounds ?: Rect.Zero

    /** Место ячеек в волне (по порядку чтения) - считается в начале волны. */
    private var order: Map<Any, Int> = emptyMap()

    internal fun visibleCells(onlyZoomable: Boolean = false): List<Pair<Any, Cell>> =
        cells.entries.filter { (_, c) -> (!onlyZoomable || c.zoomable) && isVisible(c.bounds, hostBounds) }.map { it.key to it.value }

    /** Ячейка для подсказки - первая по порядку чтения (правая в верхнем видимом ряду). */
    internal fun firstCell(onlyZoomable: Boolean = false): Pair<Any, Cell>? {
        val cells = visibleCells(onlyZoomable)
        val ranks = readingOrder(cells.map { it.second.bounds })
        return cells.indices.minByOrNull { ranks[it] }?.let(cells::get)
    }

    /** Любое касание экрана: «не тишина» для «зова», подсказка уходит (иначе при прокрутке осталась бы на месте). */
    internal fun touched(now: Long) {
        lastTouch = now
        hint = null
    }

    /** Нажатие ячейки (или открыт лист буквы): подсказка уходит, экран отмечается показанным. */
    internal fun tappedCell() {
        hint = null
        if (!tapped) {
            tapped = true
            onSeen()
        }
    }

    internal suspend fun runWave() {
        val cellsNow = visibleCells()
        if (cellsNow.isEmpty()) return
        val ranks = readingOrder(cellsNow.map { it.second.bounds })
        order = cellsNow.indices.associate { cellsNow[it].first to ranks[it] }
        val length = (cellsNow.size - 1) * WAVE_STAGGER_MILLIS + WAVE_CELL_MILLIS
        wave.snapTo(0f)
        wave.animateTo(length, tween(length.roundToInt(), easing = LinearEasing))
        wave.snapTo(WAVE_OFF)
    }

    /** «Зов» плитки [id]; прервали (касание, увеличение) - плитка сразу в покое, не застывает наклонённой. */
    internal suspend fun call(id: Any) {
        try {
            wiggleId = id
            wiggle.snapTo(0f)
            wiggle.animateTo(1f, tween(WIGGLE_MILLIS, easing = LinearEasing))
        } finally {
            wiggleId = null
        }
    }

    /** Волна (0…1 подъёма) у ячейки [id] сейчас: ячейки - по очереди, через [WAVE_STAGGER_MILLIS]; вне волны - 0. */
    internal fun waveBump(id: Any): Float {
        val time = wave.value
        if (time < 0f) return 0f
        val rank = order[id] ?: return 0f
        val phase = (time - rank * WAVE_STAGGER_MILLIS) / WAVE_CELL_MILLIS
        return if (phase <= 0f || phase >= 1f) 0f else sin(PI * phase).toFloat()
    }
}

/** Подсказки экрана, который сейчас на виду; нет - ячейки без волны и «зова». */
internal val LocalTapIntro = staticCompositionLocalOf<TapIntro?> { null }

/**
 * Состояние живости экрана [key] (`lesson.3`, `words`): первое открытие - через [FIRST_DELAY_MILLIS] волна, потом
 * подсказка у первой ячейки ([HINT_MILLIS]; у увеличиваемых - и «зов»); первое касание ячейки убирает её, и
 * экран отмечается показанным. [zoomable] - плитки увеличиваются: пока слово ни разу не увеличивали, после
 * [IDLE_MILLIS] без касаний зовёт случайная видимая плитка, не больше [IDLE_CALLS] раз за заход.
 */
@Composable
internal fun rememberTapIntro(key: String, settings: ReadingSettings, zoomable: Boolean = false): TapIntro {
    val intro = remember(key) {
        TapIntro(seen = settings.introSeen(key), zoomable = zoomable, onSeen = { settings.markIntroSeen(key) })
    }
    // Всё - в одном эффекте: состояние читается в корутине, композиция экрана от касаний не перерисовывается.
    LaunchedEffect(intro) {
        if (!intro.seen) {
            delay(FIRST_DELAY_MILLIS)
            intro.runWave()
            val first = intro.firstCell(onlyZoomable = zoomable)
            if (first != null && !intro.tapped) {
                val (id, cell) = first
                intro.hint = TapHint(cell.bounds, cell.hint)
                if (zoomable) launch { intro.call(id) }
                delay(HINT_MILLIS)
                intro.hint = null
                settings.markIntroSeen(key)
            }
        }
        if (!zoomable) return@LaunchedEffect
        // Тишина: каждое касание экрана начинает отсчёт заново (и прерывает идущий «зов»).
        var calls = 0
        combine(snapshotFlow { intro.lastTouch }, settings.zoomLearned) { _, learned -> learned }.collectLatest { learned ->
            while (!learned && calls < IDLE_CALLS) {
                delay(IDLE_MILLIS)
                val cells = intro.visibleCells(onlyZoomable = true)
                if (cells.isEmpty()) return@collectLatest
                calls++
                intro.call(cells.random().first)
            }
        }
    }
    return intro
}

/**
 * Хозяин живости экрана: содержимое и поверх - подсказка у ячейки (не перехватывает касания, не берёт фокус;
 * TalkBack прочитает её как вежливое объявление). Любое касание экрана - «не тишина» для «зова».
 */
@Composable
internal fun TapIntroHost(intro: TapIntro, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .onGloballyPositioned { intro.hostBounds = it.boundsInRoot() }
            .pointerInput(intro) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    intro.touched(System.currentTimeMillis())
                }
            },
    ) {
        CompositionLocalProvider(LocalTapIntro provides intro) { content() }
        HintLayer(intro)
    }
}

/** Подсказка поверх экрана - своя область перерисовки: смена подсказки не трогает содержимое экрана. */
@Composable
private fun HintLayer(intro: TapIntro) {
    val hint = intro.hint
    Box {
        AnimatedVisibility(hint != null, enter = fadeIn(tween(HINT_FADE_MILLIS)), exit = fadeOut(tween(HINT_FADE_MILLIS))) {
            // Пока уходит - прежний текст на прежнем месте.
            val shown = remember { arrayOf<TapHint?>(null) }
            if (hint != null) shown[0] = hint
            shown[0]?.let { HintBubble(it, intro.hostBounds) }
        }
    }
}

/**
 * Пузырь подсказки над ячейкой (нет места сверху - под ней), правым краем к правому краю ячейки; стрелка - к её
 * середине (вниз, если пузырь сверху).
 */
@Composable
private fun HintBubble(hint: TapHint, host: Rect) {
    val colors = readingColors
    val gap = with(LocalDensity.current) { HINT_GAP.toPx() }
    // Где стрелка (x в пузыре) и вниз ли она - считает раскладка, читает отрисовка.
    val arrow = remember { floatArrayOf(0f, 1f) }
    Text(
        hint.text,
        color = colors.onTint,
        style = InabahType.footnote,
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(placeable.width, placeable.height) {
                    val x = (hint.target.right - host.left - placeable.width).coerceIn(0f, (host.width - placeable.width).coerceAtLeast(0f))
                    val above = hint.target.top - host.top - placeable.height - gap
                    val y = if (above >= 0f) above else hint.target.bottom - host.top + gap
                    arrow[0] = (hint.target.center.x - host.left - x).coerceIn(0f, placeable.width.toFloat())
                    arrow[1] = if (above >= 0f) 1f else 0f
                    placeable.place(IntOffset(x.roundToInt(), y.roundToInt()))
                }
            }
            .drawBehind {
                val half = HINT_ARROW.toPx()
                val (cx, down) = arrow[0].coerceIn(half + Radius.control.toPx(), size.width - half - Radius.control.toPx()) to (arrow[1] > 0f)
                val base = if (down) size.height else 0f
                val tip = if (down) size.height + half else -half
                drawPath(Path().apply { moveTo(cx - half, base); lineTo(cx + half, base); lineTo(cx, tip); close() }, colors.tint)
            }
            .semantics { liveRegion = LiveRegionMode.Polite }
            .surface(colors.tint, Radius.control)
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
    )
}

/**
 * Ячейка, которую можно нажать: сообщает [intro], где стоит и какую подсказку [hint] дать; волна - «вдох»
 * ×[WAVE_SCALE] и свечение рамки [glow] (скругление [cornerRadius]); [zoomable] - увеличивается долгим нажатием,
 * «зов» - покачивание с наклоном. Касание ячейки убирает подсказку. [id] - постоянный ключ ячейки
 * (`remember { Any() }` в ячейке). Ставить первым - до клипа и нажатия, чтобы рост и свечение не обрезались.
 */
internal fun Modifier.tapCell(
    intro: TapIntro?,
    id: Any,
    hint: String,
    glow: Color,
    zoomable: Boolean = false,
    cornerRadius: Dp = Radius.box,
): Modifier {
    if (intro == null) return this
    return this
        .onGloballyPositioned { intro.register(id, it.boundsInRoot(), hint, zoomable) }
        .pointerInput(intro, id) {
            // Нажатие - касание, которое отпустили (или держат дольше долгого нажатия), не сдвинув дальше порога:
            // прокрутка, начатая на ячейке, подсказку не «съедает».
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    if (!change.pressed || change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) {
                        intro.tappedCell()
                        break
                    }
                }
            }
        }
        .graphicsLayer {
            val bump = intro.waveBump(id)
            val calling = intro.wiggleId == id
            val p = if (calling) intro.wiggle.value else 1f
            val scale = (1f + (WAVE_SCALE - 1f) * bump) * (if (calling) wiggleScale(p) else 1f)
            scaleX = scale
            scaleY = scale
            rotationZ = if (calling) wiggleRotation(p) else 0f
        }
        .drawWithContent {
            drawContent()
            val bump = intro.waveBump(id)
            if (bump > 0f) {
                val r = cornerRadius.toPx()
                drawRoundRect(glow.copy(alpha = glow.alpha * bump), cornerRadius = CornerRadius(r), style = Stroke(GLOW_WIDTH.toPx()))
            }
        }
}

/**
 * Постоянный ключ ячейки для [tapCell]: ячейка ушла из композиции (страница листания) - удаляется из подсказок
 * экрана, иначе «призраки» получали бы волну и «зов».
 */
@Composable
internal fun rememberTapCellId(): Any {
    val intro = LocalTapIntro.current
    val id = remember { Any() }
    DisposableEffect(intro, id) { onDispose { intro?.unregister(id) } }
    return id
}

/**
 * Нажимаемая ячейка курса целиком: [tapCell] и клип её формой (скругление [cornerRadius]) - вспышка [cellRipple]
 * не выходит за углы. Ставить до `clickable`.
 */
internal fun Modifier.liveCell(
    intro: TapIntro?,
    id: Any,
    hint: String,
    glow: Color,
    zoomable: Boolean = false,
    cornerRadius: Dp = Radius.box,
): Modifier = tapCell(intro, id, hint, glow, zoomable, cornerRadius).clip(RoundedCornerShape(cornerRadius))

/** Вспышка при нажатии - рябь цвета раздела (вместо `indication = null` у ячеек курса). */
@Composable
internal fun cellRipple(color: Color = readingColors.tint): Indication = ripple(color = color)

/**
 * Доля удержания 0…1 для увеличиваемой плитки: растёт, пока палец держит (после [HOLD_START_MILLIS], чтобы короткое
 * нажатие не «дёргало»), до долгого нажатия системы; отпустили или прокрутили - назад за [HOLD_RELEASE_MILLIS].
 */
@Composable
internal fun rememberHoldProgress(interactionSource: InteractionSource): State<Float> {
    val progress = remember(interactionSource) { Animatable(0f) }
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis.toInt()
    LaunchedEffect(interactionSource, timeout) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> launch {
                    progress.animateTo(1f, tween((timeout - HOLD_START_MILLIS).coerceAtLeast(1), delayMillis = HOLD_START_MILLIS, easing = LinearEasing))
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> launch { progress.animateTo(0f, tween(HOLD_RELEASE_MILLIS)) }
            }
        }
    }
    return progress.asState()
}

/** Удержание: плитка растёт до ×[HOLD_SCALE], по краю заполняется кольцо цвета [color] (скругление [cornerRadius]). */
internal fun Modifier.holdRing(progress: () -> Float, color: Color, cornerRadius: Dp = Radius.box): Modifier = this
    .graphicsLayer {
        val scale = lerp(1f, HOLD_SCALE, progress())
        scaleX = scale
        scaleY = scale
    }
    .drawWithContent {
        drawContent()
        val p = progress()
        if (p <= 0f) return@drawWithContent
        val inset = RING_WIDTH.toPx() / 2
        val r = cornerRadius.toPx()
        val outline = Path().apply {
            addRoundRect(RoundRect(Rect(inset, inset, size.width - inset, size.height - inset), CornerRadius(r)), Path.Direction.Clockwise)
        }
        val measure = PathMeasure().apply { setPath(outline, false) }
        val length = measure.length
        // Кольцо - от середины верхнего края по часовой стрелке (контур начинается в другом месте).
        val start = ringStart(measure, length, size.width / 2, inset)
        val end = start + length * p
        val segment = Path()
        measure.getSegment(start, minOf(end, length), segment, true)
        if (end > length) measure.getSegment(0f, end - length, segment, true)
        drawPath(segment, color, style = Stroke(RING_WIDTH.toPx(), cap = StrokeCap.Round))
    }

/** Где на контуре [measure] (длиной [length]) середина верхнего края ([x], [y]) - начало кольца удержания. */
private fun ringStart(measure: PathMeasure, length: Float, x: Float, y: Float): Float {
    var best = 0f
    var bestDistance = Float.MAX_VALUE
    for (i in 0 until RING_SAMPLES) {
        val at = length * i / RING_SAMPLES
        val point = measure.getPosition(at)
        val distance = (point.x - x) * (point.x - x) + (point.y - y) * (point.y - y)
        if (distance < bestDistance) {
            bestDistance = distance
            best = at
        }
    }
    return best
}

/**
 * Место каждой ячейки [cells] в волне - по порядку чтения: ряд за рядом сверху вниз, в ряду справа налево (как
 * читают по-арабски; просьба пользователя 2026-10-20 - волна бежит по рядам, а не «пятном»). Ряд - ячейки, чей
 * верх ниже верха ряда меньше чем на половину высоты.
 */
internal fun readingOrder(cells: List<Rect>): List<Int> {
    val byTop = cells.indices.sortedBy { cells[it].top }
    val rows = mutableListOf<MutableList<Int>>()
    for (i in byTop) {
        val row = rows.lastOrNull()
        val rowTop = row?.let { cells[it.first()] }
        if (rowTop != null && cells[i].top - rowTop.top < rowTop.height / 2) row.add(i) else rows.add(mutableListOf(i))
    }
    val ranks = IntArray(cells.size)
    var rank = 0
    for (row in rows) for (i in row.sortedByDescending { cells[it].right }) ranks[i] = rank++
    return ranks.toList()
}

/** Ячейка видна в [host] целиком по высоте хотя бы наполовину и не за краями по ширине. */
internal fun isVisible(cell: Rect, host: Rect): Boolean =
    cell.width > 0f && cell.center.y in host.top..host.bottom && cell.center.x in host.left..host.right

/** «Зов» - поворот в градусах по доле [p] (как в наброске: −4°, 4°, −3°, 2°, 0). */
internal fun wiggleRotation(p: Float): Float = keyframe(p, WIGGLE_TIMES, WIGGLE_ANGLES)

/** «Зов» - масштаб по доле [p]: плитка чуть растёт и возвращается. */
internal fun wiggleScale(p: Float): Float = keyframe(p, WIGGLE_TIMES, WIGGLE_SCALES)

private fun keyframe(p: Float, times: FloatArray, values: FloatArray): Float {
    if (p <= times.first()) return values.first()
    for (i in 1 until times.size) {
        if (p <= times[i]) return lerp(values[i - 1], values[i], (p - times[i - 1]) / (times[i] - times[i - 1]))
    }
    return values.last()
}

private const val WAVE_OFF = -1f
/** Сколько мс ячейка «вдыхает» в волне; следующая по порядку - на [WAVE_STAGGER_MILLIS] позже (как в наброске). */
private const val WAVE_CELL_MILLIS = 450f
private const val WAVE_STAGGER_MILLIS = 55f
private const val WAVE_SCALE = 1.06f
private val GLOW_WIDTH = 2.dp
internal const val FIRST_DELAY_MILLIS = 500L
internal const val HINT_MILLIS = 4_000L
internal const val IDLE_MILLIS = 6_000L
internal const val IDLE_CALLS = 3
private const val HINT_FADE_MILLIS = 300
private val HINT_GAP = 9.dp
private val HINT_ARROW = 6.dp
private const val WIGGLE_MILLIS = 900
private val WIGGLE_TIMES = floatArrayOf(0f, 0.15f, 0.3f, 0.45f, 0.6f, 0.75f, 1f)
private val WIGGLE_ANGLES = floatArrayOf(0f, -4f, 4f, -3f, 2f, 0f, 0f)
private val WIGGLE_SCALES = floatArrayOf(1f, 1.06f, 1.06f, 1.05f, 1.03f, 1.02f, 1f)
private const val HOLD_START_MILLIS = 100
private const val HOLD_RELEASE_MILLIS = 200
/** Рост при удержании - поверх сжатия нажатия (×0,92): вместе плитка растёт примерно до ×1,07. */
private const val HOLD_SCALE = 1.16f
private const val RING_SAMPLES = 96
private val RING_WIDTH = 3.dp
