package app.inabah.android.feature.azkar

import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.inabah.android.R
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Theme
import app.inabah.android.core.designsystem.components.NavCardRingStyle

// Тексты и оформление раздела — в одном месте (iOS AzkarSection+Presentation): новый раздел — один case.

@get:StringRes
val AzkarSection.title: Int
    get() = when (this) {
        AzkarSection.Morning -> R.string.azkar_morning_title
        AzkarSection.Evening -> R.string.azkar_evening_title
    }

@get:StringRes
val AzkarSection.subtitle: Int
    get() = when (this) {
        AzkarSection.Morning -> R.string.azkar_morning_subtitle
        AzkarSection.Evening -> R.string.azkar_evening_subtitle
    }

/** «16 зикров · После фаджра». */
@get:PluralsRes
val AzkarSection.cardMeta: Int
    get() = when (this) {
        AzkarSection.Morning -> R.plurals.azkar_morning_meta
        AzkarSection.Evening -> R.plurals.azkar_evening_meta
    }

@get:StringRes
val AzkarSection.completionMessage: Int
    get() = when (this) {
        AzkarSection.Morning -> R.string.azkar_completion_morning
        AzkarSection.Evening -> R.string.azkar_completion_evening
    }

@get:DrawableRes
val AzkarSection.icon: Int
    get() = when (this) {
        AzkarSection.Morning -> R.drawable.ic_sunny
        AzkarSection.Evening -> R.drawable.ic_moon_stars
    }

fun AzkarSection.iconColor(theme: Theme): Color = when (this) {
    AzkarSection.Morning -> theme.palette.sunRays
    AzkarSection.Evening -> theme.palette.gold
}

fun AzkarSection.cardGradient(theme: Theme): Brush = when (this) {
    AzkarSection.Morning -> theme.gradients.morningCard
    AzkarSection.Evening -> theme.gradients.eveningCard
}

fun AzkarSection.cardShadow(theme: Theme): ShadowToken = when (this) {
    AzkarSection.Morning -> ShadowToken.navCard(theme.palette.morningCardShadow, ShadowToken.Strength.Light)
    AzkarSection.Evening -> ShadowToken.navCard(theme.palette.shadow, ShadowToken.Strength.Medium)
}

/** Фон экрана раздела: вечерние — в цветах своей карточки. */
fun AzkarSection.background(theme: Theme): Brush = when (this) {
    AzkarSection.Morning -> theme.gradients.azkarBackground
    AzkarSection.Evening -> theme.gradients.eveningBackground
}

/** Цвет навбара и шапки прогресса. */
fun AzkarSection.headerColor(theme: Theme): Color = when (this) {
    AzkarSection.Morning -> theme.palette.header
    AzkarSection.Evening -> theme.palette.eveningHeader
}

/** Кольцо на карточке главной: утро — солнечное, вечер — золотое. */
fun AzkarSection.ringStyle(theme: Theme): NavCardRingStyle = when (this) {
    AzkarSection.Morning -> NavCardRingStyle(theme.palette.track, theme.palette.sunRays, theme.palette.onAccent)
    AzkarSection.Evening -> NavCardRingStyle(theme.palette.goldTrack, theme.palette.gold, theme.palette.gold)
}
