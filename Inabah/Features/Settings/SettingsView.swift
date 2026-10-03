import SwiftUI

/// Настройки приложения. Пока одна секция — время ежедневного обнуления прогресса азкаров.
struct SettingsView: View {
    @Environment(AzkarResetSettings.self) private var resetSettings
    @Environment(\.theme) private var theme
    @Environment(\.calendar) private var calendar

    var body: some View {
        Form {
            Section {
                ForEach(AzkarSection.allCases, id: \.self) { section in
                    DatePicker(
                        section.resetTimeLabel,
                        selection: resetTime(for: section),
                        displayedComponents: .hourAndMinute
                    )
                }
            } header: {
                Text("settings.reset.header")
            } footer: {
                Text("settings.reset.footer")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
            .listRowBackground(theme.palette.subtleFill)
        }
        .scrollContentBackground(.hidden)
        .foregroundStyle(theme.palette.onAccent)
        .tint(theme.palette.accentLight)
        .background { theme.gradients.azkarBackground.linear.ignoresSafeArea() }
        .navigationTitle(Text("settings.title"))
        .toolbarColorScheme(.dark, for: .navigationBar)
        .audioPlayerInset()
    }

    /// Время суток как дата сегодняшнего дня — для `DatePicker`; сохраняются только часы и минуты.
    private func resetTime(for section: AzkarSection) -> Binding<Date> {
        Binding {
            let time = resetSettings.resetTime(for: section)
            return calendar.date(bySettingHour: time.hour, minute: time.minute, second: 0, of: .now) ?? .now
        } set: { date in
            let components = calendar.dateComponents([.hour, .minute], from: date)
            resetSettings.setResetTime(
                DayTime(hour: components.hour ?? 0, minute: components.minute ?? 0),
                for: section
            )
        }
    }
}

private extension AzkarSection {
    var resetTimeLabel: LocalizedStringResource {
        switch self {
        case .morning: "settings.reset.morning"
        case .evening: "settings.reset.evening"
        }
    }
}

#Preview {
    NavigationStack {
        SettingsView()
    }
    .appEnvironment(.preview)
}
