import SwiftUI

/// Выбор палитры: «По умолчанию» (свой цвет у каждого раздела) и четыре единых стиля
/// в цвет разделов; ниже — фон под арабским текстом (решение пользователя 2026-10-06:
/// это тоже про цвет). Применяется сразу ко всему приложению.
struct PaletteSettingsView: View {
    @Environment(AppearanceSettings.self) private var settings
    @Environment(ParchmentSettings.self) private var parchmentSettings
    @Environment(\.theme) private var theme
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private static let unifiedStyles: [ThemeStyle] = [.violet, .emerald, .amber, .graphite]
    /// Под «Пергаментом» — в порядке палитр, затем «Сепия» и универсальный «Ночной».
    private static let parchmentStyles: [ParchmentStyle] = [.amethyst, .jade, .amber, .smoky, .sepia, .night]

    var body: some View {
        Form {
            Section {
                VStack(spacing: Spacing.xlPlus) {
                    tile(.sections)
                    LazyVGrid(columns: SelectionGrid.columns(for: dynamicTypeSize), spacing: Spacing.xlPlus) {
                        ForEach(Self.unifiedStyles) { tile($0) }
                    }
                }
                .padding(.vertical, Spacing.m)
                .settingsRow()
            } footer: {
                Text("settings.palette.footer")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }

            Section {
                VStack(spacing: Spacing.xlPlus) {
                    parchmentTile(.classic)
                    LazyVGrid(columns: SelectionGrid.columns(for: dynamicTypeSize), spacing: Spacing.xlPlus) {
                        ForEach(Self.parchmentStyles) { parchmentTile($0) }
                    }
                }
                .padding(.vertical, Spacing.m)
                .settingsRow()
            } header: {
                Text("settings.parchment.header")
            } footer: {
                Text("settings.parchment.footer")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }

            Section {
                // Сдвиг всегда считается от темы без сдвига — палитры и пергамента.
                TranslationShadeRow(base: settings.style.theme.withParchment(parchmentSettings.style))
                    .padding(.vertical, Spacing.m)
                    .settingsRow()
            } header: {
                Text("settings.translationShade.header")
            } footer: {
                Text("settings.translationShade.footer")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
        }
        .settingsForm(background: theme.gradients.settingsBackground)
        .navigationTitle(Text("settings.palette.title"))
        .navigationBarTitleDisplayMode(.inline)
        .audioPlayerInset()
    }

    private func tile(_ style: ThemeStyle) -> some View {
        SelectableTile(
            title: style.title,
            subtitle: style.subtitle,
            isSelected: settings.style == style,
            cornerRadius: Radius.box
        ) {
            withAnimation(Motion.highlight) { settings.select(style) }
        } preview: {
            PalettePreview(style: style)
        }
    }

    private func parchmentTile(_ style: ParchmentStyle) -> some View {
        SelectableTile(
            title: style.title,
            isSelected: parchmentSettings.style == style,
            cornerRadius: Radius.box
        ) {
            withAnimation(Motion.highlight) { parchmentSettings.select(style) }
        } preview: {
            // Настоящий пергамент в цветах варианта: тема подменена только внутри плитки.
            ParchmentPreview()
                .environment(\.theme, theme.withParchment(style))
        }
    }
}

/// «Фон перевода»: образец двух карточек и ползунок (решение пользователя 2026-10-10, как на Android).
/// Пока палец на ползунке — меняется только образец (черновик); отпустил — сдвиг сохраняется
/// и тема всего приложения меняется один раз.
private struct TranslationShadeRow: View {
    /// Тема без сдвига — от неё считаются образец и кружки по краям.
    let base: Theme

    @Environment(TranslationShadeSettings.self) private var settings
    @State private var draft: Double?

    private static let percent: Double = 100

    init(base: Theme) {
        self.base = base
        // Кружки по краям от положения ползунка не зависят — считаются при сборке строки, а не на
        // каждом кадре перетаскивания (аудит iOS 1.1.1, А5).
        startSwatch = base.withTranslationShade(-1).palette.zikrCard
        endSwatch = base.withTranslationShade(1).palette.zikrCard
    }

    private let startSwatch: Color
    private let endSwatch: Color

    var body: some View {
        let shade = draft ?? settings.value
        VStack(spacing: Spacing.l) {
            TranslationShadeSample()
                .environment(\.theme, base.withTranslationShade(shade))
            ShadeSlider(
                value: shade,
                label: "settings.translationShade.header",
                valueTitle: valueTitle(shade),
                startSwatch: startSwatch,
                endSwatch: endSwatch,
                onChange: { draft = $0 },
                onCommit: commit
            )
        }
        // Ушли с экрана посреди перетаскивания — черновик не теряется.
        .onDisappear(perform: commit)
    }

    private func commit() {
        guard let draft else { return }
        withAnimation(Motion.highlight) { settings.set(draft) }
        self.draft = nil
    }

    private func valueTitle(_ shade: Double) -> Text {
        let percent = Int((abs(shade) * Self.percent).rounded())
        if percent == 0 { return Text("settings.translationShade.default") }
        return shade < 0
            ? Text("settings.translationShade.darker \(percent)")
            : Text("settings.translationShade.lighter \(percent)")
    }
}

/// Карточки перевода зикра и хадиса в теме окружения — только картинка (VoiceOver читает ползунок).
private struct TranslationShadeSample: View {
    @Environment(\.theme) private var theme
    @Environment(\.locale) private var locale

    var body: some View {
        let text = String(localized: "settings.translationShade.sample")
        let language = ContentLanguage(rawValue: locale.language.languageCode?.identifier ?? ContentLanguage.base.rawValue)
        // Карточки — одной высоты (по более длинной).
        HStack(spacing: Spacing.m) {
            card(theme.palette.zikrCard) {
                TranslationBlock(
                    text: text,
                    language: language,
                    colors: .azkar(theme.palette),
                    narrator: String(localized: "tab.azkar")
                )
            }
            card(theme.palette.hadithCard) {
                TranslationBlock(
                    text: text,
                    language: language,
                    colors: .hadith(theme.palette),
                    narrator: String(localized: "tab.hadith"),
                    bubble: false,
                    narratorDivider: true
                )
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        .accessibilityHidden(true)
    }

    private func card(_ fill: Color, @ViewBuilder content: () -> some View) -> some View {
        content()
            .padding(Spacing.m)
            .frame(maxHeight: .infinity, alignment: .top)
            .surface(fill, cornerRadius: Radius.card, border: theme.palette.hairline, shadow: .card(theme.palette))
    }
}

/// Миниатюра фона арабского текста: пергамент с «بِسْمِ اللَّهِ» одной строкой.
private struct ParchmentPreview: View {
    @Environment(\.theme) private var theme

    private enum Layout {
        static let arabicSize: Double = 20
        static let minimumScale: CGFloat = 0.7
    }

    var body: some View {
        ParchmentPanel(bottomCornerRadius: Radius.card) {
            ArabicText(
                text: "بِسْمِ اللَّهِ",
                size: Layout.arabicSize,
                color: theme.palette.parchmentText,
                alignment: .center,
                lineLimit: 1
            )
            .minimumScaleFactor(Layout.minimumScale)
        }
        .accessibilityHidden(true)
    }
}

/// Миниатюра палитры: у единого стиля — фон, светлая и тёмная карточки, акцент;
/// у «По умолчанию» — полосы фонов всех разделов.
private struct PalettePreview: View {
    let style: ThemeStyle

    private enum Layout {
        static let height: CGFloat = 96
        static let cardHeight: CGFloat = 18
        static let accentSize: CGFloat = 12
    }

    var body: some View {
        let theme = style.theme
        Group {
            if style == .sections {
                HStack(spacing: 0) {
                    theme.gradients.azkarBackground.linear
                    theme.gradients.hadithBackground.linear
                    theme.gradients.makharijBackground.linear
                    theme.gradients.settingsBackground.linear
                }
            } else {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    RoundedRectangle(cornerRadius: Radius.small)
                        .fill(theme.gradients.morningCard.linear)
                        .frame(height: Layout.cardHeight)
                    RoundedRectangle(cornerRadius: Radius.small)
                        .fill(theme.gradients.eveningCard.linear)
                        .frame(height: Layout.cardHeight)
                    Circle()
                        .fill(theme.palette.tabAzkar)
                        .frame(width: Layout.accentSize, height: Layout.accentSize)
                }
                .padding(Spacing.m)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .background(theme.gradients.azkarBackground.linear)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: Layout.height)
        .accessibilityHidden(true)
    }
}

private extension ParchmentStyle {
    var title: LocalizedStringResource {
        switch self {
        case .classic: "parchment.classic"
        case .sepia: "parchment.sepia"
        case .amethyst: "parchment.amethyst"
        case .jade: "parchment.jade"
        case .amber: "parchment.amber"
        case .smoky: "parchment.smoky"
        case .night: "parchment.night"
        }
    }
}

private extension ThemeStyle {
    var title: LocalizedStringResource {
        switch self {
        case .sections: "palette.sections"
        case .violet: "palette.violet"
        case .emerald: "palette.emerald"
        case .amber: "palette.amber"
        case .graphite: "palette.graphite"
        }
    }

    var subtitle: LocalizedStringResource {
        switch self {
        case .sections: "palette.sections.subtitle"
        case .violet: "palette.violet.subtitle"
        case .emerald: "palette.emerald.subtitle"
        case .amber: "palette.amber.subtitle"
        case .graphite: "palette.graphite.subtitle"
        }
    }
}

#Preview {
    NavigationStack {
        PaletteSettingsView()
    }
    .appEnvironment(.preview)
}
