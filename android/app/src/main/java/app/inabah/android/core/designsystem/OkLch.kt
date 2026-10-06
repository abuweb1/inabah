package app.inabah.android.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Цвет в OKLCH: светлота [l] (0…1), насыщенность [c], оттенок [h] в градусах. В отличие от HSV,
 * светлота и насыщенность здесь не зависят от оттенка — сдвиги «темнее на 0,02» одинаково ведут себя
 * у фиолетового и зелёного (стекло навбара, `TopBar.kt`).
 */
internal data class OkLch(val l: Float, val c: Float, val h: Float) {
    /** Сдвиг светлоты, насыщенности и оттенка; насыщенность не уходит ниже нуля. */
    fun shifted(dl: Float = 0f, dc: Float = 0f, dh: Float = 0f): OkLch =
        OkLch((l + dl).coerceIn(0f, 1f), (c + dc).coerceAtLeast(0f), h + dh)

    /** Промежуточный цвет на доле [fraction] пути к [to]; оттенок — по короткой дуге. */
    fun lerp(to: OkLch, fraction: Float): OkLch {
        val dh = ((to.h - h) % FULL_TURN + FULL_TURN + HALF_TURN) % FULL_TURN - HALF_TURN
        return OkLch(l + (to.l - l) * fraction, c + (to.c - c) * fraction, (h + dh * fraction + FULL_TURN) % FULL_TURN)
    }

    /** В sRGB; цвет вне охвата sRGB обрезается по каналам. */
    fun toColor(): Color {
        val radians = Math.toRadians(h.toDouble())
        return Color(l, (c * cos(radians)).toFloat(), (c * sin(radians)).toFloat(), colorSpace = ColorSpaces.Oklab)
            .convert(ColorSpaces.Srgb)
    }

    companion object {
        fun of(color: Color): OkLch {
            val lab = color.convert(ColorSpaces.Oklab)
            val hue = Math.toDegrees(atan2(lab.blue, lab.green).toDouble()).toFloat()
            return OkLch(lab.red, hypot(lab.green, lab.blue), (hue + FULL_TURN) % FULL_TURN)
        }

        private const val FULL_TURN = 360f
        private const val HALF_TURN = 180f
    }
}
