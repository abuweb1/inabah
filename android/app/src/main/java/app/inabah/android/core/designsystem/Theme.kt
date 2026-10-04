package app.inabah.android.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.runtime.staticCompositionLocalOf

/** Тема приложения: палитра и градиенты текущего стиля (iOS `Theme`, `@Environment(\.theme)`). */
@Immutable
data class Theme(
    val palette: Palette,
    val gradients: ThemeGradients,
) {
    companion object {
        val Sections = Theme(Palette.Sections, ThemeGradients.Sections)
    }
}

val LocalInabahTheme = staticCompositionLocalOf { Theme.Sections }

/**
 * Корень оформления. Только тёмная тема, без динамических цветов Material You:
 * вид задаёт своя дизайн-система, Material 3 — основа для системных компонентов (диалоги, переключатели).
 */
@Composable
fun InabahTheme(
    theme: Theme = Theme.Sections,
    content: @Composable () -> Unit,
) {
    val palette = theme.palette
    val colorScheme = darkColorScheme(
        primary = palette.accentLight,
        onPrimary = palette.onAccent,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.card,
        onSurface = palette.textPrimary,
        onSurfaceVariant = palette.textSecondary,
    )
    // Шрифты собираются один раз на процесс ресурсов (только встроенные — см. InterFont.kt).
    val resources = LocalResources.current
    val fonts = remember(resources) { InabahFonts.from(resources) }
    val typography = remember(fonts) { materialTypography(fonts) }
    CompositionLocalProvider(LocalInabahTheme provides theme, LocalInabahFonts provides fonts) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}

object InabahTheme {
    val palette: Palette
        @Composable @ReadOnlyComposable get() = LocalInabahTheme.current.palette

    val gradients: ThemeGradients
        @Composable @ReadOnlyComposable get() = LocalInabahTheme.current.gradients
}
