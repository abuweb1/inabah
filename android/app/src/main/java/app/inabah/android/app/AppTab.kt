package app.inabah.android.app

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.inabah.android.R
import app.inabah.android.core.designsystem.Theme

/**
 * Вкладки корня (iOS `AppTab`). Значки — векторы (решение 4, docs/android/08, 2026-10-05): «Азкары» —
 * `auto_awesome` (сияние, как искры iOS `hands.and.sparkles`; выбор пользователя), «Махрадж» — «ع» контуром
 * Scheherazade New, как iOS `character.ar`.
 */
enum class AppTab(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    Azkar(R.string.tab_azkar, R.drawable.ic_auto_awesome),
    Hadith(R.string.tab_hadith, R.drawable.ic_book),
    Makharij(R.string.tab_makharij, R.drawable.ic_ain),
    Settings(R.string.tab_settings, R.drawable.ic_settings);

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
