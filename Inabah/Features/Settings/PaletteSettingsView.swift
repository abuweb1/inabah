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
