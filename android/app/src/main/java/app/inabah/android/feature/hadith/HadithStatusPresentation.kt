package app.inabah.android.feature.hadith

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import app.inabah.android.R
import app.inabah.android.core.designsystem.Palette
import app.inabah.android.core.designsystem.components.StatusGlyphKind
import app.inabah.android.core.settings.HadithStatus

// Цвета и значки статуса хадиса (iOS HadithStatus+Presentation) — общие для строки списка
// и кнопок экрана хадиса.

/** Основной цвет: полоска строки, цифры бейджа, значок. */
fun HadithStatus.tint(palette: Palette): Color = when (this) {
    HadithStatus.None -> palette.gold
    HadithStatus.Read -> palette.statusRead
    HadithStatus.Memorized -> palette.statusMemorized
}

/** Полоска слева в строке: без статуса — приглушённое золото. */
fun HadithStatus.stripe(palette: Palette): Color = if (this == HadithStatus.None) palette.goldBorder else tint(palette)

fun HadithStatus.badgeFill(palette: Palette): Color = when (this) {
    HadithStatus.None -> palette.goldTint
    HadithStatus.Read -> palette.statusReadTint
    HadithStatus.Memorized -> palette.statusMemorizedTint
}

fun HadithStatus.badgeBorder(palette: Palette): Color = when (this) {
    HadithStatus.None -> palette.goldBorder
    HadithStatus.Read -> palette.statusReadBorder
    HadithStatus.Memorized -> palette.statusMemorizedBorder
}

/** Значок статуса: открытая книга — прочитан, сердце со звездой — выучен; без статуса — нет. */
val HadithStatus.glyph: StatusGlyphKind?
    get() = when (this) {
        HadithStatus.None -> null
        HadithStatus.Read -> StatusGlyphKind.Read
        HadithStatus.Memorized -> StatusGlyphKind.Memorized
    }

@get:StringRes
val HadithStatus.accessibilityLabel: Int?
    get() = when (this) {
        HadithStatus.None -> null
        HadithStatus.Read -> R.string.hadith_status_read
        HadithStatus.Memorized -> R.string.hadith_status_memorized
    }
