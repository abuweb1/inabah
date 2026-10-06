package app.inabah.android.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Тень подложки (iOS `ShadowToken`): цвет с прозрачностью, радиус размытия, сдвиг вниз.
 * Рисуется только у фоновой фигуры ([app.inabah.android.core.designsystem.components.surface]),
 * не у меняющегося содержимого — иначе пересчитывается на каждом кадре.
 */
@Immutable
data class ShadowToken(val color: Color, val radius: Dp, val offsetY: Dp) {
    /** Сила тени карточки главной. */
    enum class Strength(val alpha: Float) {
        Light(0.35f),
        Medium(0.4f),
        Strong(0.45f),
    }

    companion object {
        fun card(palette: Palette) = ShadowToken(palette.shadow.copy(alpha = 0.35f), radius = 6.dp, offsetY = 2.dp)

        /** Плеер. */
        fun floating(palette: Palette) = ShadowToken(palette.shadow.copy(alpha = 0.45f), radius = 18.dp, offsetY = 6.dp)

        fun goldButton(palette: Palette) = ShadowToken(palette.goldDeep.copy(alpha = 0.4f), radius = 10.dp, offsetY = 4.dp)

        fun navCard(color: Color, strength: Strength) =
            ShadowToken(color.copy(alpha = strength.alpha), radius = 12.dp, offsetY = 8.dp)
    }
}
