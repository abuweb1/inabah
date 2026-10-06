package app.inabah.android.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Цвета одного единого стиля (iOS `ThemeColorToken`): из них [ThemeStyle.theme] собирает тему,
 * заменяя часть цветов «По умолчанию». Имена и порядок — как токены
 * `scripts/generate-theme-palettes.py`, значения — в сгенерированном [ThemePalettes].
 */
@Immutable
data class ThemeColors(
    val backgroundTop: Color,
    val backgroundMid: Color,
    val backgroundBottom: Color,
    val header: Color,
    val eveningHeader: Color,
    val surface: Color,
    val card: Color,
    val action: Color,
    val accent: Color,
    val accentLight: Color,
    val accentDim: Color,
    val accentShadow: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val tab: Color,
    val cardShadow: Color,
    val morningCardStart: Color,
    val morningCardMid: Color,
    val morningCardEnd: Color,
    val eveningCardStart: Color,
    val eveningCardMid: Color,
    val eveningCardEnd: Color,
    val nawawiCardStart: Color,
    val nawawiCardMid: Color,
    val nawawiCardEnd: Color,
    val qudsiCardStart: Color,
    val qudsiCardMid: Color,
    val qudsiCardEnd: Color,
    val ajurriCardStart: Color,
    val ajurriCardMid: Color,
    val ajurriCardEnd: Color,
)
