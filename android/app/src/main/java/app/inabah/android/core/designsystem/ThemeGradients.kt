package app.inabah.android.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.cos
import kotlin.math.sin

/**
 * Линейный градиент с направлением «угол CSS», как в прототипе и iOS (`ThemeGradients.swift`):
 * 0° — вверх, 90° — вправо; точки начала и конца — в долях размера фигуры.
 */
@Immutable
class AngledGradient(
    private val angleDegrees: Float,
    private val stops: List<Pair<Float, Color>>,
) : ShaderBrush() {

    override fun createShader(size: Size): Shader {
        val radians = Math.toRadians(angleDegrees.toDouble())
        val dx = (sin(radians) / 2).toFloat()
        val dy = (-cos(radians) / 2).toFloat()
        return LinearGradientShader(
            from = Offset((0.5f - dx) * size.width, (0.5f - dy) * size.height),
            to = Offset((0.5f + dx) * size.width, (0.5f + dy) * size.height),
            colors = stops.map { it.second },
            colorStops = stops.map { it.first },
        )
    }

    override fun equals(other: Any?): Boolean =
        other is AngledGradient && other.angleDegrees == angleDegrees && other.stops == stops

    override fun hashCode(): Int = 31 * angleDegrees.hashCode() + stops.hashCode()
}

/** Фоны разделов (docs/android/05-design-system.md, 5.2 «Градиенты»). Карточки и прочие — этап 2. */
@Immutable
data class ThemeGradients(
    val azkarBackground: AngledGradient,
    val eveningBackground: AngledGradient,
    val hadithBackground: AngledGradient,
    val settingsBackground: AngledGradient,
    val makharijBackground: AngledGradient,
) {
    companion object {
        private const val BACKGROUND_ANGLE = 168f

        private fun background(top: Long, mid: Long, bottom: Long, midStop: Float = 0.5f) =
            AngledGradient(
                BACKGROUND_ANGLE,
                listOf(0f to Color(top), midStop to Color(mid), 1f to Color(bottom)),
            )

        val Sections = ThemeGradients(
            azkarBackground = background(0xFF2E1562, 0xFF5C33A0, 0xFF3A1F70),
            eveningBackground = background(0xFF180D32, 0xFF2E1562, 0xFF4A2890, midStop = 0.55f),
            hadithBackground = background(0xFF103040, 0xFF1A5C4A, 0xFF0E2830),
            settingsBackground = background(0xFF1B1E26, 0xFF2C313D, 0xFF1F232C),
            makharijBackground = background(0xFF2A1606, 0xFF5C3410, 0xFF241205),
        )
    }
}
