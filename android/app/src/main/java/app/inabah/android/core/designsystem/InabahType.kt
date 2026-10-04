package app.inabah.android.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Текстовые стили iOS → sp при стандартной настройке шрифта (docs/android/05-design-system.md, 5.4).
 * Шрифт — встроенный Inter (`InabahFonts`: текстовый, от 20 sp — Display, как SF Pro Text / Display);
 * масштабируются системным размером шрифта. Арабский текст — только `ArabicText`.
 */
object InabahType {
    val largeTitle: TextStyle @Composable @ReadOnlyComposable get() = display(34.sp)
    val title: TextStyle @Composable @ReadOnlyComposable get() = display(28.sp)
    val title2: TextStyle @Composable @ReadOnlyComposable get() = display(22.sp)
    val title3: TextStyle @Composable @ReadOnlyComposable get() = display(20.sp)
    val headline: TextStyle @Composable @ReadOnlyComposable get() = text(17.sp, FontWeight.SemiBold)
    val body: TextStyle @Composable @ReadOnlyComposable get() = text(17.sp)
    val callout: TextStyle @Composable @ReadOnlyComposable get() = text(16.sp)
    val subheadline: TextStyle @Composable @ReadOnlyComposable get() = text(15.sp)
    val footnote: TextStyle @Composable @ReadOnlyComposable get() = text(13.sp)
    val caption: TextStyle @Composable @ReadOnlyComposable get() = text(12.sp)
    val caption2: TextStyle @Composable @ReadOnlyComposable get() = text(11.sp)

    @Composable
    @ReadOnlyComposable
    private fun text(size: TextUnit, weight: FontWeight? = null) =
        TextStyle(fontFamily = LocalInabahFonts.current.text, fontSize = size, fontWeight = weight)

    @Composable
    @ReadOnlyComposable
    private fun display(size: TextUnit) = TextStyle(fontFamily = LocalInabahFonts.current.display, fontSize = size)
}

/** Моноширинные цифры — счётчики, время, проценты. */
fun TextStyle.monospacedDigits(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * Типографика Material 3 на встроенном Inter — для системных компонентов (диалоги, переключатели)
 * и текста без явного стиля (`LocalTextStyle` = bodyLarge): размеры Material, крупные стили — Display.
 */
internal fun materialTypography(fonts: InabahFonts): Typography = Typography().run {
    fun TextStyle.with(family: FontFamily) = copy(fontFamily = family)
    copy(
        displayLarge = displayLarge.with(fonts.display),
        displayMedium = displayMedium.with(fonts.display),
        displaySmall = displaySmall.with(fonts.display),
        headlineLarge = headlineLarge.with(fonts.display),
        headlineMedium = headlineMedium.with(fonts.display),
        headlineSmall = headlineSmall.with(fonts.display),
        titleLarge = titleLarge.with(fonts.display),
        titleMedium = titleMedium.with(fonts.text),
        titleSmall = titleSmall.with(fonts.text),
        bodyLarge = bodyLarge.with(fonts.text),
        bodyMedium = bodyMedium.with(fonts.text),
        bodySmall = bodySmall.with(fonts.text),
        labelLarge = labelLarge.with(fonts.text),
        labelMedium = labelMedium.with(fonts.text),
        labelSmall = labelSmall.with(fonts.text),
    )
}
