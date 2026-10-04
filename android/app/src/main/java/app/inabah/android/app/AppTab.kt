package app.inabah.android.app

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.inabah.android.R
import app.inabah.android.core.designsystem.Theme

/**
 * Вкладки корня (iOS `AppTab`). Значки — временные из Material Icons:
 * свои векторы для «Азкаров» и «Махраджа» — открытое решение 4 (docs/android/08).
 */
enum class AppTab(
    @param:StringRes val label: Int,
    val icon: ImageVector,
) {
    Azkar(R.string.tab_azkar, Icons.Filled.Star),
    Hadith(R.string.tab_hadith, Icons.AutoMirrored.Filled.List),
    Makharij(R.string.tab_makharij, Icons.Filled.Face),
    Settings(R.string.tab_settings, Icons.Filled.Settings);

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
