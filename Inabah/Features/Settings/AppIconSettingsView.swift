import SwiftUI

/// Выбор иконки приложения: классическая и альтернативные. Иконку меняет система
/// (и сама показывает алерт «Вы изменили значок»).
struct AppIconSettingsView: View {
    @Environment(AppIconSettings.self) private var settings
    @Environment(\.theme) private var theme

    private static let columns = [GridItem(.flexible(), spacing: Spacing.l), GridItem(.flexible(), spacing: Spacing.l)]

    var body: some View {
        @Bindable var settings = settings

        Form {
            Section {
                LazyVGrid(columns: Self.columns, spacing: Spacing.xlPlus) {
                    ForEach(AppIconOption.allCases) { option in
                        AppIconTile(option: option, isSelected: option == settings.current) {
                            Task { await settings.select(option) }
                        }
                    }
                }
                .padding(.vertical, Spacing.m)
                .disabled(!settings.isSupported)
                .settingsRow()
            } footer: {
                Text("settings.appIcon.footer")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
        }
        .settingsForm(background: theme.gradients.settingsBackground)
        .navigationTitle(Text("settings.appIcon.title"))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { settings.refresh() }
        .alert(Text("settings.appIcon.error"), isPresented: $settings.failedToChange) {
            Button("settings.appIcon.error.ok", role: .cancel) {}
        }
        .audioPlayerInset()
    }
}

/// Превью иконки со скруглением как на экране «Домой», подпись и отметка выбранной.
private struct AppIconTile: View {
    let option: AppIconOption
    let isSelected: Bool
    let action: () -> Void

    @Environment(\.theme) private var theme

    private enum Layout {
        static let iconSize: CGFloat = 88
        /// Скругление иконок iOS — доля стороны.
        static let cornerRatio: CGFloat = 0.2237
        /// Зазор между иконкой и рамкой выбора.
        static let selectionInset: CGFloat = 4
    }

    var body: some View {
        Button(action: action) {
            VStack(spacing: Spacing.s) {
                Image(option.preview)
                    .resizable()
                    .scaledToFit()
                    .frame(width: Layout.iconSize, height: Layout.iconSize)
                    .clipShape(.rect(cornerRadius: Layout.iconSize * Layout.cornerRatio, style: .continuous))
                    .padding(Layout.selectionInset)
                    .overlay {
                        RoundedRectangle(
                            cornerRadius: Layout.iconSize * Layout.cornerRatio + Layout.selectionInset,
                            style: .continuous
                        )
                        .strokeBorder(isSelected ? theme.palette.accentLight : .clear, lineWidth: Size.ringStroke)
                    }
                Text(option.title)
                    .font(.footnote)
                    .foregroundStyle(theme.palette.onAccent)
                    .multilineTextAlignment(.center)
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.body)
                    .foregroundStyle(isSelected ? theme.palette.accentLight : theme.palette.onAccentTertiary)
                    .accessibilityHidden(true)
            }
            .frame(maxWidth: .infinity)
            .contentShape(.rect)
        }
        .buttonStyle(PressScaleButtonStyle())
        .animation(Motion.highlight, value: isSelected)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

#Preview {
    NavigationStack {
        AppIconSettingsView()
    }
    .appEnvironment(.preview)
}
