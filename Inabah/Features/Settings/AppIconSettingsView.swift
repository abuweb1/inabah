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

/// Превью иконки со скруглением как на экране «Домой».
private struct AppIconTile: View {
    let option: AppIconOption
    let isSelected: Bool
    let action: () -> Void

    private enum Layout {
        static let iconSize: CGFloat = 88
        /// Скругление иконок iOS — доля стороны.
        static let cornerRatio: CGFloat = 0.2237
    }

    var body: some View {
        SelectableTile(
            title: option.title,
            isSelected: isSelected,
            cornerRadius: Layout.iconSize * Layout.cornerRatio,
            action: action
        ) {
            Image(option.preview)
                .resizable()
                .scaledToFit()
                .frame(width: Layout.iconSize, height: Layout.iconSize)
        }
    }
}

#Preview {
    NavigationStack {
        AppIconSettingsView()
    }
    .appEnvironment(.preview)
}
