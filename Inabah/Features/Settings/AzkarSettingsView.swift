import SwiftUI

/// Настройки азкаров: время ежедневного обнуления и ручной сброс прогресса раздела.
struct AzkarSettingsView: View {
    @Environment(AzkarResetSettings.self) private var resetSettings
    @Environment(AzkarStore.self) private var store
    @Environment(\.theme) private var theme

    /// Раздел, сброс которого ждёт подтверждения.
    @State private var pendingReset: AzkarSection?

    var body: some View {
        Form {
            Section {
                ForEach(AzkarSection.allCases, id: \.self) { section in
                    DatePicker(
                        section.resetTimeLabel,
                        selection: resetTime(for: section),
                        displayedComponents: .hourAndMinute
                    )
                    .settingsRow()
                }
            } header: {
                Text("settings.reset.header")
            } footer: {
                Text("settings.reset.footer")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }

            Section {
                ForEach(AzkarSection.allCases, id: \.self) { section in
                    Button(role: .destructive) {
                        pendingReset = section
                    } label: {
                        Text(section.title)
                    }
                    .disabled(!store.hasProgress(of: section))
                    .settingsRow()
                }
            } header: {
                Text("settings.reset.now.header")
            } footer: {
                Text("settings.reset.now.footer")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
        }
        .settingsForm(background: theme.gradients.azkarBackground)
        .navigationTitle(Text("settings.azkar.title"))
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog(
            Text("settings.reset.now.confirm.title"),
            isPresented: isConfirming,
            titleVisibility: .visible,
            presenting: pendingReset
        ) { section in
            Button(role: .destructive) {
                store.resetProgress(of: section)
            } label: {
                Text("settings.reset.now.confirm.action")
            }
        } message: { section in
            Text(section.resetConfirmationMessage)
        }
        .audioPlayerInset()
    }

    private var isConfirming: Binding<Bool> {
        Binding(
            get: { pendingReset != nil },
            set: { if !$0 { pendingReset = nil } }
        )
    }

    /// Время суток как дата сегодняшнего дня — для `DatePicker`; сохраняются только часы и минуты.
    /// Календарь — тот же, что у расписания обнуления.
    private func resetTime(for section: AzkarSection) -> Binding<Date> {
        let calendar = store.calendar
        return Binding {
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

    var resetConfirmationMessage: LocalizedStringResource {
        switch self {
        case .morning: "settings.reset.now.confirm.morning"
        case .evening: "settings.reset.now.confirm.evening"
        }
    }
}

#Preview {
    NavigationStack {
        AzkarSettingsView()
    }
    .appEnvironment(.preview)
}
