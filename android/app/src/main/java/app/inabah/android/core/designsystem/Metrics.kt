package app.inabah.android.core.designsystem

import androidx.compose.ui.unit.dp

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

/** Размеры элементов, dp. Остальные токены — этап 2. */
object Size {
    /** Зона касания на Android — 48 dp (видимый размер iOS — 44). */
    val minTapTarget = 48.dp
    val hairline = 1.dp
}
