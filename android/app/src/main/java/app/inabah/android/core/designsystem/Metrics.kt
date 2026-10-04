package app.inabah.android.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/** Отступы, dp (iOS `Metrics.swift` → docs/android/05-design-system.md, 5.1). */
object Spacing {
    val xxxs = 2.dp
    val xxs = 4.dp
    val xs = 6.dp
    val s = 8.dp
    val m = 12.dp
    val l = 14.dp
    val xl = 16.dp
    val xlPlus = 20.dp
    val xxl = 22.dp
    val xxxl = 32.dp
    val section = 40.dp
}

/** Радиусы скругления, dp. */
object Radius {
    val small = 5.dp
    val control = 12.dp
    val box = 14.dp
    val card = 18.dp
    val navCard = 20.dp
    val panel = 24.dp
}

/** Размеры элементов, dp. */
object Size {
    /** Зона касания на Android — 48 dp; видимый размер элементов iOS — [visibleTapTarget]. */
    val minTapTarget = 48.dp
    val visibleTapTarget = 44.dp
    val counter = 76.dp
    val navCardIcon = 38.dp
    val navCardSymbol = 30.dp
    val navCardMinHeight = 118.dp
    val progressBarHeight = 4.dp
    val primaryButtonHeight = 50.dp
    val accentStripe = 3.dp
    val miniButton = 38.dp
    val playerButton = 42.dp
    val playerMainButton = 64.dp
    val statusDot = 8.dp
    val grabberWidth = 40.dp
    val grabberHeight = 5.dp
    val ornament = 11.dp
    val hairline = 1.dp
    val parchmentBorder = 2.dp
    val ringStroke = 3.dp
}

/** Межбуквенный интервал (iOS `Tracking`, pt ≈ доля кегля). */
object Tracking {
    /** Номер хадиса над арабским текстом (0,6 pt при 12). */
    val label = 0.05.em

    /** Подзаголовок бренда, категория в плеере (0,8 pt при 11–12). */
    val caption = 0.07.em
}

/** Отклик на нажатие: масштаб и прозрачность (iOS `PressFeedback`). */
object PressFeedback {
    const val CARD_SCALE = 0.97f
    const val ICON_SCALE = 0.92f
    const val ROUND_SCALE = 0.93f
    const val COUNTER_SCALE = 0.91f
    const val WIDE_SCALE = 0.98f
    const val WIDE_OPACITY = 0.8f
    const val ROW_OPACITY = 0.65f
    const val BARE_OPACITY = 0.5f
    const val DISABLED_OPACITY = 0.4f
}

/** Анимации (iOS `Motion`, docs/android/05-design-system.md, 5.1). */
object Motion {
    private val StandardEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** Сворачивание карточек, раскрытие перевода, показ панели плеера. */
    fun <T> collapse() = tween<T>(durationMillis = 500, easing = StandardEasing)

    fun <T> counterRing() = tween<T>(durationMillis = 350, easing = EaseOut)

    fun <T> press() = tween<T>(durationMillis = 120, easing = EaseOut)

    fun <T> cardPress() = tween<T>(durationMillis = 180, easing = EaseOut)

    fun <T> overlay() = tween<T>(durationMillis = 400, easing = EaseOut)

    /** Золотая рамка звучащей карточки, выбор плитки, смена процента. */
    fun <T> highlight() = tween<T>(durationMillis = 300, easing = EaseInOut)

    fun <T> progress() = tween<T>(durationMillis = 500, easing = EaseInOut)

    /** Кегль А−/А+ — пружина без отскока. */
    fun <T> fontSize() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Пульсация каллиграфии завершения: 1 ↔ 1,05 туда-обратно. */
    const val PULSE_MILLIS = 1200
    const val PULSE_SCALE = 1.05f

    /** Пауза между заполнением счётчика и сворачиванием карточки. */
    const val COLLAPSE_DELAY_MILLIS = 550L

    /** Пауза перед оверлеем «مَا شَاءَ اللَّهُ». */
    const val COMPLETION_DELAY_MILLIS = 600L

    /** Опрос позиции плеера. */
    const val PROGRESS_TICK_MILLIS = 250L
}
