package app.inabah.android.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.util.lerp as lerpFloat
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

    /** Цвета по положению — для единых стилей с теми же стопами. */
    val colors: List<Color> get() = stops.map { it.second }

    /** Тот же угол и положения стопов, другие цвета (по одному на стоп). */
    fun withColors(colors: List<Color>): AngledGradient {
        require(colors.size == stops.size) { "Нужно ${stops.size} цвета, передано ${colors.size}" }
        return AngledGradient(angleDegrees, stops.zip(colors) { (position, _), color -> position to color })
    }

    /**
     * Промежуточный градиент для смены палитры: цвета, положения стопов и угол — на долю [fraction]
     * пути к [to]. Разное число стопов (так у тем не бывает) — сразу [to].
     */
    fun lerp(to: AngledGradient, fraction: Float): AngledGradient {
        if (to.stops.size != stops.size) return to
        val stopsBetween = stops.zip(to.stops) { (fromAt, fromColor), (toAt, toColor) ->
            lerpFloat(fromAt, toAt, fraction) to lerpColor(fromColor, toColor, fraction)
        }
        return AngledGradient(lerpFloat(angleDegrees, to.angleDegrees, fraction), stopsBetween)
    }

    /** Начало и конец градиента в фигуре [size]: dx = sin(угол)/2, dy = −cos(угол)/2 от центра. */
    internal fun endpoints(size: Size): Pair<Offset, Offset> {
        val radians = Math.toRadians(angleDegrees.toDouble())
        val dx = (sin(radians) / 2).toFloat()
        val dy = (-cos(radians) / 2).toFloat()
        return Offset((0.5f - dx) * size.width, (0.5f - dy) * size.height) to
            Offset((0.5f + dx) * size.width, (0.5f + dy) * size.height)
    }

    override fun createShader(size: Size): Shader {
        val (from, to) = endpoints(size)
        return LinearGradientShader(
            from = from,
            to = to,
            colors = stops.map { it.second },
            colorStops = stops.map { it.first },
        )
    }

    override fun equals(other: Any?): Boolean =
        other is AngledGradient && other.angleDegrees == angleDegrees && other.stops == stops

    override fun hashCode(): Int = 31 * angleDegrees.hashCode() + stops.hashCode()
}

/** Градиенты темы (docs/android/05-design-system.md, 5.2 «Градиенты»). */
@Immutable
data class ThemeGradients(
    val azkarBackground: AngledGradient,
    val eveningBackground: AngledGradient,
    val hadithBackground: AngledGradient,
    val settingsBackground: AngledGradient,
    val makharijBackground: AngledGradient,
    val morningCard: AngledGradient,
    val eveningCard: AngledGradient,
    val nawawiCard: AngledGradient,
    val qudsiCard: AngledGradient,
    val ajurriCard: AngledGradient,
    val progressFill: AngledGradient,
    val counterButton: AngledGradient,
    val counterButtonDone: AngledGradient,
    val parchment: AngledGradient,
    val parchmentStripe: AngledGradient,
) {
    companion object {
        private const val BACKGROUND_ANGLE = 168f
        private const val CARD_ANGLE = 135f

        /** Середина градиента карточек: утренние и ан-Навави — 0,6, остальные — 0,55 (те же в единых стилях). */
        private const val MORNING_CARD_MID = 0.6f
        private const val EVENING_CARD_MID = 0.55f
        private const val NAWAWI_CARD_MID = 0.6f
        private const val QUDSI_CARD_MID = 0.55f
        private const val AJURRI_CARD_MID = 0.55f

        private const val PROGRESS_ANGLE = 90f
        private const val COUNTER_ANGLE = 160f
        private const val PARCHMENT_ANGLE = 150f
        private const val PARCHMENT_HIGHLIGHT_STOP = 0.45f
        private const val PARCHMENT_MID_STOP = 0.75f
        private const val STRIPE_ALPHA = 0.6f

        /** Фон раздела единого стиля: один градиент 168° (стопы 0 / 0,5 / 1) на все разделы. */
        internal fun unifiedBackground(top: Color, mid: Color, bottom: Color) =
            threeStop(BACKGROUND_ANGLE, top, mid, bottom)

        /** Три цвета: начало, середина в [midStop], конец. */
        private fun threeStop(angle: Float, start: Color, mid: Color, end: Color, midStop: Float = 0.5f) =
            AngledGradient(angle, listOf(0f to start, midStop to mid, 1f to end))

        private fun twoStop(angle: Float, start: Color, end: Color) =
            AngledGradient(angle, listOf(0f to start, 1f to end))

        /** Пергамент из цветов палитры — и для темы по умолчанию, и для выбранного фона ([ParchmentStyle]). */
        internal fun parchment(palette: Palette) = AngledGradient(
            PARCHMENT_ANGLE,
            listOf(
                0f to palette.parchmentLight,
                PARCHMENT_HIGHLIGHT_STOP to palette.parchmentHighlight,
                PARCHMENT_MID_STOP to palette.parchmentMid,
                1f to palette.parchmentDeep,
            ),
        )

        /**
         * Фоны и карточки «По умолчанию»; градиенты из цветов палитры (полосы, счётчик, пергамент)
         * одинаковы во всех стилях — единый стиль меняет только фоны и карточки.
         */
        val Sections: ThemeGradients = Palette.Sections.let { palette ->
            ThemeGradients(
                azkarBackground = threeStop(BACKGROUND_ANGLE, Color(0xFF2E1562), Color(0xFF5C33A0), Color(0xFF3A1F70)),
                eveningBackground = threeStop(
                    BACKGROUND_ANGLE, Color(0xFF180D32), Color(0xFF2E1562), Color(0xFF4A2890), midStop = 0.55f,
                ),
                hadithBackground = threeStop(BACKGROUND_ANGLE, Color(0xFF103040), Color(0xFF1A5C4A), Color(0xFF0E2830)),
                settingsBackground = threeStop(BACKGROUND_ANGLE, Color(0xFF1B1E26), Color(0xFF2C313D), Color(0xFF1F232C)),
                makharijBackground = threeStop(BACKGROUND_ANGLE, Color(0xFF2A1606), Color(0xFF5C3410), Color(0xFF241205)),
                morningCard = threeStop(
                    CARD_ANGLE, Color(0xFF7B4DC0), Color(0xFF9B6FCC), Color(0xFFC28FE8), MORNING_CARD_MID,
                ),
                eveningCard = threeStop(
                    CARD_ANGLE, Color(0xFF180D32), Color(0xFF2E1562), Color(0xFF4A2890), EVENING_CARD_MID,
                ),
                nawawiCard = threeStop(
                    CARD_ANGLE, Color(0xFF1A6B50), Color(0xFF2A9B70), Color(0xFF3ABC8A), NAWAWI_CARD_MID,
                ),
                qudsiCard = threeStop(
                    CARD_ANGLE, Color(0xFF0E2830), Color(0xFF1A4A5C), Color(0xFF256070), QUDSI_CARD_MID,
                ),
                ajurriCard = threeStop(
                    CARD_ANGLE, Color(0xFF12404A), Color(0xFF1E6670), Color(0xFF2A8088), AJURRI_CARD_MID,
                ),
                progressFill = twoStop(PROGRESS_ANGLE, palette.successDeep, palette.success),
                counterButton = twoStop(COUNTER_ANGLE, palette.goldLight, palette.goldDeep),
                counterButtonDone = twoStop(COUNTER_ANGLE, palette.successLight, palette.success),
                parchment = parchment(palette),
                parchmentStripe = threeStop(
                    PROGRESS_ANGLE,
                    palette.successDeep.copy(alpha = STRIPE_ALPHA),
                    palette.success.copy(alpha = STRIPE_ALPHA),
                    palette.successDeep.copy(alpha = STRIPE_ALPHA),
                ),
            )
        }
    }
}
