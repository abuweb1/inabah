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
    val largeTitle: TextStyle @Composable @ReadOnlyComposable get() = display(34f)
    val title: TextStyle @Composable @ReadOnlyComposable get() = display(28f)
    val title2: TextStyle @Composable @ReadOnlyComposable get() = display(22f)
    val title3: TextStyle @Composable @ReadOnlyComposable get() = display(20f)
    val headline: TextStyle @Composable @ReadOnlyComposable get() = text(17f, FontWeight.SemiBold)
    val body: TextStyle @Composable @ReadOnlyComposable get() = text(17f)
    val callout: TextStyle @Composable @ReadOnlyComposable get() = text(16f)
    val subheadline: TextStyle @Composable @ReadOnlyComposable get() = text(15f)
    val footnote: TextStyle @Composable @ReadOnlyComposable get() = text(13f)
    val caption: TextStyle @Composable @ReadOnlyComposable get() = text(12f)
    val caption2: TextStyle @Composable @ReadOnlyComposable get() = text(11f)

    /**
     * Кегли выше — pt iOS. Inter шире SF Pro и с более высокими строчными: при тех же числах
     * кириллица выглядит заметно крупнее, особенно на узких экранах Android (замечание
     * пользователя 2026-10-04, снимок с телефона). Все стили интерфейса уменьшены на 8 %.
     */
    private const val INTER_SCALE = 0.92f

    @Composable
    @ReadOnlyComposable
    private fun text(points: Float, weight: FontWeight? = null) =
        TextStyle(fontFamily = LocalInabahFonts.current.text, fontSize = scaled(points), fontWeight = weight)

    @Composable
    @ReadOnlyComposable
    private fun display(points: Float) = TextStyle(fontFamily = LocalInabahFonts.current.display, fontSize = scaled(points))

    private fun scaled(points: Float): TextUnit = (points * INTER_SCALE).sp
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
