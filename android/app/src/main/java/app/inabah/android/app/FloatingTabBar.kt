package app.inabah.android.app

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ScheherazadeNew
import app.inabah.android.core.designsystem.components.fixedSp
import app.inabah.android.core.designsystem.components.surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Размеры — по снимкам iOS (системная плавающая панель вкладок, pt → dp один к одному).
/** Ниже iOS-панели (62) — по просьбе пользователя на телефоне (2026-10-04). */
private val BarHeight = 54.dp
private val BarHorizontalMargin = 21.dp
private val BarBottomMargin = 4.dp
private val PillInset = 4.dp
private val TabIconSize = 26.dp

/** Кегль подписи и буквы «ع»; панель iOS не растёт с системным шрифтом. */
private const val LABEL_SIZE = 10f
private const val GLYPH_SIZE = 21f

/**
 * Подложка капсулы — светлое стекло: лёгкая белая дымка, подкрашенная цветом раздела, — тон панели
 * близок к фону, как у прозрачной панели iOS (затемнение делало её заметно темнее фона на всех
 * главных — замечание пользователя 2026-10-04). Содержимое проходит под панелью.
 */
private const val BAR_FILL_ALPHA = 0.07f
private const val BAR_TINT_ALPHA = 0.08f

/**
 * Размытие под панелью — слабое (24 и 12 dp на телефоне размывали слишком сильно — решения пользователя 2026-10-04);
 * лёгкое зерно — без него стекло выглядит «пластиком». Без подкраски: светлое стекло поверх.
 */
private val BarBlur = HazeBlurStyle {
    blurRadius(6.dp)
    noiseFactor(0.08f)
}

/** Доля цвета раздела в невыбранных значках и подписях. */
private const val IDLE_TINT = 0.18f

/** Буква «ع» в насхе сидит низко (большая нижняя дуга) — поднять к центру значка. */
private val GlyphLift = (-5).dp

/** Обводка капсулы и «пилюля» выбранной вкладки — цвет вкладки с прозрачностью. */
private const val BAR_RIM_ALPHA = 0.45f
private const val PILL_ALPHA = 0.3f

// Линза iOS 26 (записи пользователя IMG_9750, IMG_9752): пока палец на панели, выбранная пилюля —
// прозрачная линза крупнее вкладки (выходит за панель), едет за пальцем, под ней содержимое
// увеличено и преломлено ([LiquidLensShader]), по краю — блик; сама панель насыщается цветом
// раздела. Отпустили — выбирается вкладка под линзой, линза пружиной садится в цветную пилюлю.

// Темп подобран на телефоне пользователя: 400 — слишком быстро через всю панель, 120 — слишком медленно.
private val PillSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 220f)
private val LensRise = spring<Float>(dampingRatio = 0.75f, stiffness = 300f)

/** Возврат капли в пилюлю — быстрый: начинается на подходе, не ждёт, пока переезд успокоится. */
private val LensSettle = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)

/** Доля пути (в вкладках), с которой капля начинает садиться в пилюлю. */
private const val SETTLE_DISTANCE = 0.15f

/**
 * Переезд к [target]: когда до цели меньше [SETTLE_DISTANCE] вкладки (или переезд закончен) — один раз
 * [onArrive]; хвост пружины (покачивание у цели) доигрывает уже под садящейся каплей.
 */
private suspend fun Animatable<Float, *>.travelTo(target: Float, onArrive: () -> Unit) {
    var arrived = false
    animateTo(target, PillSpring) {
        if (!arrived && abs(value - target) < SETTLE_DISTANCE) {
            arrived = true
            onArrive()
        }
    }
    if (!arrived) onArrive()
}

/** Капля чуть шире вкладки и чуть выше панели — почти не увеличивается (IMG_9754). */
private const val LENS_SCALE_X = 0.12f
private const val LENS_SCALE_Y = 0.2f

/**
 * Капля тянется по ходу движения: шире и ниже, чем быстрее палец; остановилась — пружинит обратно.
 * Растяжение — доля ширины на скорость «одна вкладка в секунду», не больше [MAX_STRETCH].
 */
private const val STRETCH_PER_SLOT_SPEED = 0.05f
private const val MAX_STRETCH = 0.35f
private const val STRETCH_SQUASH = 0.45f
private const val STRETCH_SMOOTHING = 0.35f
private val StretchWobble = spring<Float>(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow)
private val LensRimWidth = 1.2.dp
private const val LENS_RIM_BRIGHT = 0.85f
private const val LENS_RIM_DIM = 0.15f

/** Без шейдера (Android 12) — только увеличение значков под линзой. */
private const val ICON_MAGNIFY = 0.18f

/** Панель при касании чуть подаётся вперёд и насыщается цветом раздела. */
private const val BAR_LIFT_SCALE = 0.03f
private const val BAR_GLOW_ALPHA = 0.22f

/**
 * Плавающая панель вкладок, как системная панель iOS 26: капсула над нижним краем, выбранная
 * вкладка — на «пилюле» в цвет своего раздела, остальные — белые; касание и ведение пальцем —
 * линза (см. выше). Для TalkBack — группа вкладок с выбором.
 */
@Composable
fun FloatingTabBar(
    selectedTab: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    /** Экран под панелью — размывается под капсулой (Android 12+); `null` — без размытия. */
    backdrop: HazeState? = null,
) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    val tint by animateColorAsState(selectedTab.tint(theme), Motion.highlight(), label = "tabTint")
    val count = AppTab.entries.size
    // Положение пилюли/линзы в долях вкладки и раскрытие линзы (0 — пилюля, 1 — линза).
    val position = remember { Animatable(selectedTab.ordinal.toFloat()) }
    val lift = remember { Animatable(0f) }
    // Растяжение капли от скорости пальца при ведении; при переезде без пальца — от скорости самой капли.
    val dragStretch = remember { Animatable(0f) }
    fun stretch() = maxOf(dragStretch.value, abs(position.velocity) * STRETCH_PER_SLOT_SPEED).coerceIn(0f, MAX_STRETCH)
    var isDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selectedTab)

    // Вкладку сменили не жестом (повторный выбор, «Назад», открытие экрана) — пилюля переезжает с линзой.
    LaunchedEffect(selectedTab) {
        val target = selectedTab.ordinal.toFloat()
        if (isDragging || abs(position.value - target) < 0.01f) return@LaunchedEffect
        launch { lift.animateTo(1f, LensRise) }
        position.travelTo(target) { launch { lift.animateTo(0f, LensSettle) } }
    }

    val lensShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LiquidLensShader() else null
    }
    val rimColor = palette.onAccent
    // Невыбранные — не белые, а белый в тон раздела (снимки iOS android/docs/navbar).
    val idleColor = lerp(palette.onAccent, tint, IDLE_TINT)
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = BarHorizontalMargin, end = BarHorizontalMargin, bottom = BarBottomMargin)
            .graphicsLayer {
                val scale = 1f + BAR_LIFT_SCALE * lift.value
                scaleX = scale
                scaleY = scale
            }
            .height(BarHeight),
    ) {
    // Размытое содержимое под капсулой — как у стекла iOS: пергамент под панелью становится мягким
    // пятном, подписи читаются. Отдельный слой позади: обрезка по капсуле не задевает каплю,
    // которая выступает за панель. Поверх — то же светлое стекло, что и без размытия.
    if (backdrop != null) {
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(BarHeight / 2))
                .hazeBlur(HazeInput.Sources(backdrop), BarBlur),
        )
    }
    BoxWithConstraints(
        modifier = Modifier
            .matchParentSize()
            .surface(
                palette.onAccent.copy(alpha = BAR_FILL_ALPHA).compositeOver(tint.copy(alpha = BAR_TINT_ALPHA)),
                BarHeight / 2,
                border = tint.copy(alpha = BAR_RIM_ALPHA),
                shadow = ShadowToken.card(palette),
            )
            .drawBehind {
                val l = lift.value.coerceIn(0f, 1f)
                if (l > 0f) drawRoundRect(tint.copy(alpha = BAR_GLOW_ALPHA * l), cornerRadius = CornerRadius(size.height / 2))
            }
            .padding(PillInset),
    ) {
        val density = LocalDensity.current
        val itemWidthPx = with(density) { maxWidth.toPx() } / count
        val rimWidth = with(density) { LensRimWidth.toPx() }
        // Вкладка под пальцем — в долях вкладки, в пределах панели.
        fun slotAt(x: Float) = (x / itemWidthPx - 0.5f).coerceIn(0f, (count - 1).toFloat())

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(count, itemWidthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        isDragging = true
                        var follow: Job? = scope.launch { position.animateTo(slotAt(down.position.x), PillSpring) }
                        scope.launch { lift.animateTo(1f, LensRise) }
                        var lastSlot = slotAt(down.position.x).roundToInt()
                        var x = down.position.x
                        var released = false
                        val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                released = true
                                break
                            }
                            if (change.positionChange() != Offset.Zero) {
                                x = change.position.x
                                change.consume()
                                // Ведение — строго за пальцем: пружина перезапускалась бы на каждом событии и стояла.
                                follow?.cancel()
                                velocity.addPosition(change.uptimeMillis, change.position)
                                val slotsPerSecond = abs(velocity.calculateVelocity().x) / itemWidthPx
                                val target = (slotsPerSecond * STRETCH_PER_SLOT_SPEED).coerceAtMost(MAX_STRETCH)
                                // Сглаживание — без дрожания от неровных отсчётов скорости.
                                val smoothed = dragStretch.value + (target - dragStretch.value) * STRETCH_SMOOTHING
                                follow = scope.launch {
                                    position.snapTo(slotAt(x))
                                    dragStretch.snapTo(smoothed)
                                }
                                val slot = slotAt(x).roundToInt()
                                if (slot != lastSlot) {
                                    lastSlot = slot
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                            }
                        }
                        follow?.cancel()
                        val target = if (released) slotAt(x).roundToInt() else currentSelected.ordinal
                        if (released) currentOnSelect(AppTab.entries[target])
                        // Палец отпустил — растяжение пружинит обратно с покачиванием, как капля.
                        scope.launch { dragStretch.animateTo(0f, StretchWobble) }
                        scope.launch {
                            position.travelTo(target.toFloat()) {
                                isDragging = false
                                scope.launch { lift.animateTo(0f, LensSettle) }
                            }
                        }
                    }
                }
                .graphicsLayer {
                    // Преломление содержимого под линзой (Android 13+).
                    val l = lift.value
                    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && lensShader != null && l > 0.001f) {
                        val s = stretch()
                        val halfW = itemWidthPx / 2 * (1f + LENS_SCALE_X * l) * (1f + s)
                        val halfH = size.height / 2 * (1f + LENS_SCALE_Y * l) * (1f - STRETCH_SQUASH * s)
                        lensShader.effect((position.value + 0.5f) * itemWidthPx, size.height / 2, halfW, halfH, l.coerceIn(0f, 1f), tint)
                    } else {
                        null
                    }
                },
        ) {
            // Цветная пилюля выбранной вкладки — гаснет, пока раскрыта линза.
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val l = lift.value.coerceIn(0f, 1f)
                        if (l < 1f) {
                            drawRoundRect(
                                tint.copy(alpha = PILL_ALPHA * (1f - l)),
                                topLeft = Offset(position.value * itemWidthPx, 0f),
                                size = Size(itemWidthPx, size.height),
                                cornerRadius = CornerRadius(size.height / 2),
                            )
                        }
                    },
            )
            Row(Modifier.fillMaxSize().selectableGroup()) {
                AppTab.entries.forEach { tab ->
                    TabItem(
                        tab = tab,
                        selected = tab == selectedTab,
                        color = if (tab == selectedTab) tint else idleColor,
                        onClick = { onSelect(tab) },
                        magnification = {
                            if (lensShader != null) {
                                1f
                            } else {
                                val near = (1f - abs(position.value - tab.ordinal)).coerceIn(0f, 1f)
                                1f + ICON_MAGNIFY * lift.value * near
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
        // Блик по краю линзы — поверх, без преломления, может выходить за панель.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val l = lift.value.coerceIn(0f, 1f)
                    if (l <= 0f) return@drawBehind
                    val s = stretch()
                    val w = itemWidthPx * (1f + LENS_SCALE_X * l) * (1f + s)
                    val h = size.height * (1f + LENS_SCALE_Y * l) * (1f - STRETCH_SQUASH * s)
                    val left = (position.value + 0.5f) * itemWidthPx - w / 2
                    val top = (size.height - h) / 2
                    val rim = Brush.linearGradient(
                        0f to rimColor.copy(alpha = LENS_RIM_BRIGHT * l),
                        0.45f to rimColor.copy(alpha = LENS_RIM_DIM * l),
                        0.55f to rimColor.copy(alpha = LENS_RIM_DIM * l),
                        1f to rimColor.copy(alpha = LENS_RIM_BRIGHT * l),
                        start = Offset(left, top),
                        end = Offset(left + w, top + h),
                    )
                    drawRoundRect(rim, Offset(left, top), Size(w, h), CornerRadius(h / 2), style = Stroke(rimWidth))
                },
        )
    }
    }
}

@Composable
private fun TabItem(
    tab: AppTab,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    magnification: () -> Float,
    modifier: Modifier = Modifier,
) {
    // Касания ведёт панель (линза, ведение пальцем); вкладка — только для TalkBack.
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
                onClick {
                    onClick()
                    true
                }
            }
            .graphicsLayer {
                val scale = magnification()
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxxs, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(TabIconSize), contentAlignment = Alignment.Center) {
            when (val icon = tab.icon) {
                is TabIcon.Drawable -> Icon(painterResource(icon.resId), contentDescription = null, tint = color,
                    modifier = Modifier.size(TabIconSize))
                // Буква — встроенным Scheherazade, без увеличенной строки ArabicText: значок, не абзац.
                // Высокая строка шрифта не должна сжимать букву в рамке значка — без ограничения по высоте.
                is TabIcon.Glyph -> Text(
                    icon.text,
                    color = color,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .wrapContentSize(unbounded = true)
                        .graphicsLayer { translationY = GlyphLift.toPx() },
                    style = TextStyle(fontFamily = ScheherazadeNew, fontWeight = FontWeight.Bold, fontSize = fixedSp(GLYPH_SIZE)),
                )
            }
        }
        Text(
            stringResource(tab.label),
            color = color,
            maxLines = 1,
            style = InabahType.caption2.copy(fontSize = fixedSp(LABEL_SIZE), fontWeight = FontWeight.SemiBold),
        )
    }
}
