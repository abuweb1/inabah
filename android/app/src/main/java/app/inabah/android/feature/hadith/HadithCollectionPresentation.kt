package app.inabah.android.feature.hadith

import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.inabah.android.R
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Theme

// Тексты и оформление сборника — в одном месте (iOS HadithCollection+Presentation): новый сборник — один case.

@get:StringRes
val HadithCollection.title: Int
    get() = when (this) {
        HadithCollection.Nawawi -> R.string.hadith_nawawi_title
        HadithCollection.Qudsi -> R.string.hadith_qudsi_title
        HadithCollection.Ajurri -> R.string.hadith_ajurri_title
    }

/** Арабское название — контент, не переводится. */
val HadithCollection.arabicTitle: String
    get() = when (this) {
        HadithCollection.Nawawi -> "الأربعون النووية"
        HadithCollection.Qudsi -> "الأربعون القدسية"
        HadithCollection.Ajurri -> "أربعون حديثاً للآجري"
    }

/** «50 хадисов (с доп. Ибн Раджаба)». */
@get:PluralsRes
val HadithCollection.cardMeta: Int
    get() = when (this) {
        HadithCollection.Nawawi -> R.plurals.hadith_nawawi_meta
        HadithCollection.Qudsi -> R.plurals.hadith_qudsi_meta
        HadithCollection.Ajurri -> R.plurals.hadith_ajurri_meta
    }

@get:DrawableRes
val HadithCollection.icon: Int
    get() = when (this) {
        HadithCollection.Nawawi -> R.drawable.ic_book
        HadithCollection.Qudsi -> R.drawable.ic_sunny
        HadithCollection.Ajurri -> R.drawable.ic_draw
    }

fun HadithCollection.iconColor(theme: Theme): Color = when (this) {
    HadithCollection.Nawawi -> theme.palette.goldLight
    HadithCollection.Qudsi -> theme.palette.sunRays
    HadithCollection.Ajurri -> theme.palette.gold
}

fun HadithCollection.cardGradient(theme: Theme): Brush = when (this) {
    HadithCollection.Nawawi -> theme.gradients.nawawiCard
    HadithCollection.Qudsi -> theme.gradients.qudsiCard
    HadithCollection.Ajurri -> theme.gradients.ajurriCard
}

fun HadithCollection.cardShadow(theme: Theme): ShadowToken = when (this) {
    HadithCollection.Nawawi -> ShadowToken.navCard(theme.palette.nawawiCardShadow, ShadowToken.Strength.Medium)
    HadithCollection.Qudsi, HadithCollection.Ajurri -> ShadowToken.navCard(theme.palette.shadow, ShadowToken.Strength.Strong)
}
