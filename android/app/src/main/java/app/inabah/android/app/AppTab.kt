package app.inabah.android.app

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.inabah.android.R
import app.inabah.android.core.designsystem.Theme

/** Значок вкладки: вектор Material Symbols или буква (у «Махраджа» — «ع», как `character.ar` в iOS). */
sealed interface TabIcon {
    data class Drawable(@param:DrawableRes val resId: Int) : TabIcon
    data class Glyph(val text: String) : TabIcon
}

/** «ع» (айн) — гортанная буква, хрестоматийный пример махраджа. Контент, не переводится. */
private const val MAKHARIJ_GLYPH = "ع"

/**
 * Вкладки корня (iOS `AppTab`). Значок «Азкаров» — `folded_hands` вместо iOS `hands.and.sparkles`
 * (у Material аналога нет); свой значок — открытое решение 4 (docs/android/08).
 */
enum class AppTab(
    @param:StringRes val label: Int,
    val icon: TabIcon,
) {
    Azkar(R.string.tab_azkar, TabIcon.Drawable(R.drawable.ic_folded_hands)),
    Hadith(R.string.tab_hadith, TabIcon.Drawable(R.drawable.ic_book)),
    Makharij(R.string.tab_makharij, TabIcon.Glyph(MAKHARIJ_GLYPH)),
    Settings(R.string.tab_settings, TabIcon.Drawable(R.drawable.ic_settings));

    /** Цвет выбранной вкладки — в тон своего раздела. */
    fun tint(theme: Theme): Color = when (this) {
        Azkar -> theme.palette.tabAzkar
        Hadith -> theme.palette.tabHadith
        Makharij -> theme.palette.tabMakharij
        Settings -> theme.palette.tabSettings
    }

    /** Фон раздела под содержимым, строкой состояния и вкладками. */
    fun background(theme: Theme): Brush = when (this) {
        Azkar -> theme.gradients.azkarBackground
        Hadith -> theme.gradients.hadithBackground
        Makharij -> theme.gradients.makharijBackground
        Settings -> theme.gradients.settingsBackground
    }
}
