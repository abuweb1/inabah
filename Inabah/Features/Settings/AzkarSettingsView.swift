import SwiftUI

/// Настройки азкаров: время азкаров («с — до») и ручной сброс прогресса раздела.
struct AzkarSettingsView: View {
    @Environment(AzkarStore.self) private var store
    @Environment(\.theme) private var theme

    /// Раздел, сброс которого ждёт подтверждения.
    @State private var pendingReset: AzkarSection?

    var body: some View {
        Form {
            Section {
                ForEach(AzkarSection.allCases, id: \.self) { section in
                    AzkarWindowRow(section: section)
                        .settingsRow()
                }
            } header: {
                Text("settings.window.header")
            } footer: {
                Text("settings.window.footer")
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
        .destructiveConfirmation(
            "settings.reset.now.confirm.title",
            item: $pendingReset,
            actionLabel: "settings.reset.now.confirm.action",
            message: { Text($0.resetConfirmationMessage) },
            perform: { store.resetProgress(of: $0) }
        )
        .audioPlayerInset()
        // Новое время применяется к прогрессу при уходе с экрана, а не на каждом шаге колеса:
        // прокрутка через «сейчас» не стирает прочитанное.
        .onDisappear { store.reconcile() }
    }
}

/// «Утренние  с [5:00] до [12:00]». При крупном тексте выбор времени уходит под название.
private struct AzkarWindowRow: View {
    let section: AzkarSection

    @Environment(AzkarWindowSettings.self) private var windowSettings
    @Environment(AzkarStore.self) private var store

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: Spacing.s) {
                Text(section.title)
                Spacer(minLength: Spacing.s)
                pickers
            }
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(section.title)
                pickers
            }
        }
    }

    private var pickers: some View {
        HStack(spacing: Spacing.xs) {
            Text("settings.window.from")
            DatePicker(section.windowStartLabel, selection: start, displayedComponents: .hourAndMinute)
                .labelsHidden()
            Text("settings.window.to")
            DatePicker(section.windowEndLabel, selection: end, displayedComponents: .hourAndMinute)
                .labelsHidden()
        }
        .fixedSize()
    }

    /// Время суток как дата сегодняшнего дня — для `DatePicker`; сохраняются только часы и минуты.
    /// Календарь — тот же, что у расписания азкаров.
    private var start: Binding<Date> {
        Binding {
            windowSettings.window(for: section).start.date(in: store.calendar)
        } set: { date in
            windowSettings.setStart(DayTime(date, in: store.calendar), for: section)
        }
    }

    private var end: Binding<Date> {
        Binding {
            windowSettings.window(for: section).end.date(in: store.calendar)
        } set: { date in
            windowSettings.setEnd(DayTime(date, in: store.calendar), for: section)
        }
    }
}

private extension AzkarSection {
    var windowStartLabel: LocalizedStringResource {
        switch self {
        case .morning: "settings.window.morning.start"
        case .evening: "settings.window.evening.start"
        }
    }

    var windowEndLabel: LocalizedStringResource {
        switch self {
        case .morning: "settings.window.morning.end"
        case .evening: "settings.window.evening.end"
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
