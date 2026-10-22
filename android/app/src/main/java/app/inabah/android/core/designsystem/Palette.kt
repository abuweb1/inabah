package app.inabah.android.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Доля `tabHadith` в цвете текста сносок ([Palette.noteText]); контраст — `NoteColorsTest`. */
private const val NOTE_TEXT_TINT = 0.3f

/** Насколько темнее шапки хадисов карточка перевода хадиса ([Palette.hadithCard]); контраст — `NoteColorsTest`. */
private const val HADITH_CARD_SHADE = 0.5f

/**
 * Цвета темы. HEX — только здесь, в [ThemeGradients] и сгенерированном [ThemePalettes]; экраны берут
 * цвета из [InabahTheme]. Значения — тема «По умолчанию» (iOS `Assets.xcassets/Palette`),
 * docs/android/05-design-system.md, 5.2; единые стили — [ThemeStyle.theme].
 */
@Immutable
data class Palette(
    val background: Color,
    val card: Color,
    /** Фон карточки зикра и образца перевода в «Размере текста» (по умолчанию — `card`); сдвигается ползунком «Фон перевода» (`withTranslationShade`). */
    val zikrCard: Color = card,
    val actionBackground: Color,
    val header: Color,
    val eveningHeader: Color,
    val hadithHeader: Color,
    /** Фон карточки перевода хадиса — тёмный оттенок шапки хадисов (в единых стилях — `card`, как у зикра). */
    val hadithCard: Color = lerp(hadithHeader, Color.Black, HADITH_CARD_SHADE),
    val shadow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** Источник под переводом зикра (по умолчанию — `textTertiary`); светлеет вместе с фоном перевода (`withTranslationShade`). */
    val zikrSource: Color = textTertiary,
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
    /** Тёмные «чернила» на золоте: значок главной кнопки плеера, цифры счётчика. Пергамент не меняет. */
    val parchmentInk: Color,
    /** Стоп градиента пергамента вместо золота — у варианта фона свой ([ParchmentStyle]); по умолчанию золото. */
    val parchmentHighlight: Color = gold,
    /** Текст на пергаменте (арабский, «ХАДИС N»): у тёмного варианта фона — светлый. */
    val parchmentText: Color = parchmentInk,
    /** Рамка, «✦» и значок «N раз» на пергаменте: у тёмного варианта фона — светлее, иначе не видны. */
    val parchmentAccent: Color = successDeep,
    /** Выделение буквы в слове на пергаменте («Учимся читать»); цвет - у варианта фона, [ParchmentStyle]. */
    val parchmentMark: Color = ParchmentStyle.Classic.colors.mark,
    /** Фон под рисунком руководства «Учимся читать» - [ParchmentColors.illustration]. */
    val parchmentIllustration: Color = ParchmentStyle.Classic.colors.illustration,
    val tabAzkar: Color,
    val tabHadith: Color,
    val tabMakharij: Color,
    val tabSettings: Color,
    /** Разрушающее действие («Сбросить»): системный красный iOS в тёмной теме. */
    val destructive: Color,
    /** Вечерняя дуга дня в календаре азкаров — 🌑, чёрный во всех палитрах (решение пользователя 2026-10-11). */
    val newMoon: Color = Color.Black,
) {
    val onAccentSecondary: Color get() = onAccent.copy(alpha = 0.6f)
    val onAccentTertiary: Color get() = onAccent.copy(alpha = 0.4f)
    val onAccentStrong: Color get() = onAccent.copy(alpha = 0.85f)
    val hairline: Color get() = onAccent.copy(alpha = 0.1f)
    val divider: Color get() = onAccent.copy(alpha = 0.08f)
    val track: Color get() = onAccent.copy(alpha = 0.15f)
    val subtleFill: Color get() = onAccent.copy(alpha = 0.07f)
    val successTint: Color get() = successDeep.copy(alpha = 0.1f)
    val successBorder: Color get() = successDeep.copy(alpha = 0.25f)
    val statusReadTint: Color get() = statusRead.copy(alpha = 0.13f)
    val statusReadStrong: Color get() = statusRead.copy(alpha = 0.32f)
    val statusReadBorder: Color get() = statusRead.copy(alpha = 0.3f)
    val statusMemorizedTint: Color get() = statusMemorized.copy(alpha = 0.12f)
    val statusMemorizedStrong: Color get() = statusMemorized.copy(alpha = 0.3f)
    val statusMemorizedBorder: Color get() = statusMemorized.copy(alpha = 0.3f)
    val goldBorder: Color get() = gold.copy(alpha = 0.25f)
    val goldTrack: Color get() = gold.copy(alpha = 0.12f)
    val goldMuted: Color get() = gold.copy(alpha = 0.45f)
    val goldTint: Color get() = gold.copy(alpha = 0.13f)
    val parchmentGlow: Color get() = parchmentLight.copy(alpha = 0.7f)
    val parchmentAccentTint: Color get() = parchmentAccent.copy(alpha = 0.1f)
    val parchmentAccentBorder: Color get() = parchmentAccent.copy(alpha = 0.25f)

    // Сноски к переводу хадиса — как пузырь перевода азкаров, но в тон раздела хадисов: `tabHadith`
    // свой в каждой палитре (в единых стилях — цвет стиля), `accent` «По умолчанию» — фиолетовый.
    val noteStripe: Color get() = tabHadith
    val noteFill: Color get() = tabHadith.copy(alpha = 0.1f)
    /** «Оттенок транскрипции» раздела хадисов: белый с примесью `tabHadith`. */
    val noteText: Color get() = lerp(onAccent, tabHadith, NOTE_TEXT_TINT)

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
            destructive = Color(0xFFFF453A),
        )
    }
}
