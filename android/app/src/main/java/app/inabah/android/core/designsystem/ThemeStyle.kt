package app.inabah.android.core.designsystem

/**
 * Стиль оформления (iOS `ThemeStyle`): свой цвет у каждого раздела или единая палитра.
 * `key` — значение в настройках.
 */
enum class ThemeStyle(val key: String) {
    Sections("sections"),
    Violet("violet"),
    Emerald("emerald"),
    Amber("amber"),
    Graphite("graphite");

    /** Тема стиля; строится один раз. */
    val theme: Theme by lazy {
        when (this) {
            Sections -> Theme.Sections
            Violet -> unified(ThemePalettes.Violet)
            Emerald -> unified(ThemePalettes.Emerald)
            Amber -> unified(ThemePalettes.Amber)
            Graphite -> unified(ThemePalettes.Graphite)
        }
    }

    companion object {
        fun fromKey(key: String): ThemeStyle? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Единый стиль (iOS `Theme.unified`, docs/android/05-design-system.md, 5.3): тема «По умолчанию»,
 * в которой фоны всех разделов, поверхности, шапки, акценты, вторичный текст, вкладки и карточки
 * главных — цвета стиля. Золото, пергамент, статусы, success, onAccent, textPrimary не меняются.
 */
private fun unified(colors: ThemeColors): Theme {
    val base = Theme.Sections
    val palette = base.palette.copy(
        background = colors.surface,
        card = colors.card,
        actionBackground = colors.action,
        header = colors.header,
        eveningHeader = colors.eveningHeader,
        hadithHeader = colors.header,
        textSecondary = colors.textSecondary,
        textTertiary = colors.textTertiary,
        accent = colors.accent,
        accentLight = colors.accentLight,
        accentDim = colors.accentDim,
        accentShadow = colors.accentShadow,
        morningCardShadow = colors.cardShadow,
        nawawiCardShadow = colors.cardShadow,
        tabAzkar = colors.tab,
        tabHadith = colors.tab,
        tabMakharij = colors.tab,
        tabSettings = colors.tab,
    )
    val background = ThemeGradients.unifiedBackground(colors.backgroundTop, colors.backgroundMid, colors.backgroundBottom)
    val cards = base.gradients
    // Карточки — цвета стиля с теми же углом и положениями стопов, что у «По умолчанию».
    val gradients = cards.copy(
        azkarBackground = background,
        eveningBackground = background,
        hadithBackground = background,
        settingsBackground = background,
        makharijBackground = background,
        morningCard = cards.morningCard.withColors(
            listOf(colors.morningCardStart, colors.morningCardMid, colors.morningCardEnd),
        ),
        eveningCard = cards.eveningCard.withColors(
            listOf(colors.eveningCardStart, colors.eveningCardMid, colors.eveningCardEnd),
        ),
        nawawiCard = cards.nawawiCard.withColors(
            listOf(colors.nawawiCardStart, colors.nawawiCardMid, colors.nawawiCardEnd),
        ),
        qudsiCard = cards.qudsiCard.withColors(
            listOf(colors.qudsiCardStart, colors.qudsiCardMid, colors.qudsiCardEnd),
        ),
        ajurriCard = cards.ajurriCard.withColors(
            listOf(colors.ajurriCardStart, colors.ajurriCardMid, colors.ajurriCardEnd),
        ),
    )
    return Theme(palette, gradients)
}
