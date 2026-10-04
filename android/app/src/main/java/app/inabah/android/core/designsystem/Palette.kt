package app.inabah.android.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Цвета темы. HEX — только здесь; экраны берут цвета из [InabahTheme].
 * Значения — тема «По умолчанию» (iOS `Assets.xcassets/Palette`), docs/android/05-design-system.md, 5.2.
 * Единые стили (violet, emerald, amber, graphite) — этап 2.
 */
@Immutable
data class Palette(
    val background: Color,
    val card: Color,
    val actionBackground: Color,
    val header: Color,
    val eveningHeader: Color,
    val hadithHeader: Color,
    val shadow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val onAccent: Color,
    val accent: Color,
    val accentLight: Color,
    val accentDim: Color,
    val accentShadow: Color,
    val morningCardShadow: Color,
    val nawawiCardShadow: Color,
    val success: Color,
    val successLight: Color,
    val successDeep: Color,
    val successDim: Color,
    val statusRead: Color,
    val statusMemorized: Color,
    val gold: Color,
    val goldLight: Color,
    val goldDeep: Color,
    val sunRays: Color,
    val parchmentLight: Color,
    val parchmentMid: Color,
    val parchmentDeep: Color,
    val parchmentInk: Color,
    val tabAzkar: Color,
    val tabHadith: Color,
    val tabMakharij: Color,
    val tabSettings: Color,
) {
    val onAccentSecondary: Color get() = onAccent.copy(alpha = 0.6f)
    val onAccentTertiary: Color get() = onAccent.copy(alpha = 0.4f)
    val onAccentStrong: Color get() = onAccent.copy(alpha = 0.85f)
    val hairline: Color get() = onAccent.copy(alpha = 0.1f)
    val divider: Color get() = onAccent.copy(alpha = 0.08f)
    val track: Color get() = onAccent.copy(alpha = 0.15f)
    val subtleFill: Color get() = onAccent.copy(alpha = 0.07f)

    companion object {
        val Sections = Palette(
            background = Color(0xFF1C1630),
            card = Color(0xFF241D3C),
            actionBackground = Color(0xFF1E1834),
            header = Color(0xFF5C33A0),
            eveningHeader = Color(0xFF180D32),
            hadithHeader = Color(0xFF1A5C4A),
            shadow = Color(0xFF000000),
            textPrimary = Color(0xFFF0EAF8),
            textSecondary = Color(0xFFB0A4C8),
            textTertiary = Color(0xFF7A6E8A),
            onAccent = Color(0xFFFFFFFF),
            accent = Color(0xFF5C33A0),
            accentLight = Color(0xFF9B6FCC),
            accentDim = Color(0xFF2D2045),
            accentShadow = Color(0xFF6B3FA8),
            morningCardShadow = Color(0xFF6B3FA8),
            nawawiCardShadow = Color(0xFF2A5C35),
            success = Color(0xFF3AAF6A),
            successLight = Color(0xFF6DBF7E),
            successDeep = Color(0xFF2A5C35),
            successDim = Color(0xFF0F2B1A),
            statusRead = Color(0xFF7EC8E3),
            statusMemorized = Color(0xFF4DE882),
            gold = Color(0xFFECC87B),
            goldLight = Color(0xFFF5D88A),
            goldDeep = Color(0xFFE0B060),
            sunRays = Color(0xFFFFE66E),
            parchmentLight = Color(0xFFF5D07A),
            parchmentMid = Color(0xFFF3D080),
            parchmentDeep = Color(0xFFE8C068),
            parchmentInk = Color(0xFF1A1208),
            tabAzkar = Color(0xFFB59BEA),
            tabHadith = Color(0xFF5ED6A6),
            tabMakharij = Color(0xFFE8A85A),
            tabSettings = Color(0xFF8FB4F0),
        )
    }
}
