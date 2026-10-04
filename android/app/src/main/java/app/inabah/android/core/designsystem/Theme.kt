package app.inabah.android.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
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
    CompositionLocalProvider(LocalInabahTheme provides theme) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

object InabahTheme {
    val palette: Palette
        @Composable @ReadOnlyComposable get() = LocalInabahTheme.current.palette

    val gradients: ThemeGradients
        @Composable @ReadOnlyComposable get() = LocalInabahTheme.current.gradients
}
