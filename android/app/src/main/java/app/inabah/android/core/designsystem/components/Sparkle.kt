package app.inabah.android.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp

/**
 * Четырёхконечная звёздочка «✦» вектором: символа U+2726 нет во встроенных шрифтах, а системный
 * шрифт подставлять нельзя (вид зависел бы от шрифта, выбранного в системе). Стороны вогнутые,
 * как у глифа SF; для TalkBack скрыта.
 */
@Composable
fun Sparkle(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2
        val cy = h / 2
        // Контрольные точки ближе к центру — вогнутые стороны (доля полуразмера).
        val kx = w / 2 * SPARKLE_WAIST
        val ky = h / 2 * SPARKLE_WAIST
        val path = Path().apply {
            moveTo(cx, 0f)
            quadraticTo(cx + kx, cy - ky, w, cy)
            quadraticTo(cx + kx, cy + ky, cx, h)
            quadraticTo(cx - kx, cy + ky, 0f, cy)
            quadraticTo(cx - kx, cy - ky, cx, 0f)
            close()
        }
        drawPath(path, color)
    }
}

/** «Талия» звёздочки: чем меньше, тем тоньше лучи. */
private const val SPARKLE_WAIST = 0.2f
