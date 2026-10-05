import SwiftUI

/// Выбор палитры: «По умолчанию» (свой цвет у каждого раздела) и четыре единых стиля
/// в цвет разделов. Применяется сразу ко всему приложению.
struct PaletteSettingsView: View {
    @Environment(AppearanceSettings.self) private var settings
    @Environment(\.theme) private var theme
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private static let unifiedStyles: [ThemeStyle] = [.violet, .emerald, .amber, .graphite]

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
