import SwiftUI
import UIKit

/// Строка напоминания: название, подпись мелким и переключатель в цвет раздела (как на Android, `09d`).
struct ReminderToggleRow: View {
    let title: LocalizedStringResource
    let subtitle: Text?
    let isOn: Bool
    let tint: Color
    let onChange: (Bool) -> Void

    @Environment(\.theme) private var theme
    /// Положение переключателя на экране: возвращается к сохранённому сразу после нажатия — включённым
    /// он становится, только когда напоминание действительно включено (дали разрешение).
    @State private var shown = false

    var body: some View {
        Toggle(isOn: $shown) {
            VStack(alignment: .leading, spacing: Spacing.xxxs) {
                Text(title)
                    .font(.body)
                if let subtitle {
                    subtitle
                        .font(.caption)
                        .foregroundStyle(theme.palette.onAccentSecondary)
                }
            }
        }
        .tint(tint)
        .onAppear { shown = isOn }
        .onChange(of: isOn) { _, value in shown = value }
        .onChange(of: shown) { _, value in
            guard value != isOn else { return }
            onChange(value)
            shown = isOn
        }
    }
}

/// «Открыть настройки» — когда уведомления запрещены в настройках iPhone (как на Android, `09e`).
/// Ведёт в настройки уведомлений приложения, а если их нет — в общие настройки приложения (аудит Android, К2).
struct ReminderOpenSettingsRow: View {
    @Environment(\.openURL) private var openURL

    var body: some View {
        Button {
            let target = URL(string: UIApplication.openNotificationSettingsURLString)
                ?? URL(string: UIApplication.openSettingsURLString)
            if let target { openURL(target) }
        } label: {
            Text("settings.reminders.openSettings")
                .font(.body.weight(.bold))
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(.rect)
        }
        .buttonStyle(PressDimButtonStyle())
    }
}

/// Включение напоминаний с запросом разрешения: включается, только если уведомления разрешены;
/// отказ запоминается до ухода с экрана — показывается «заблокировано» (как на Android).
@MainActor
struct ReminderToggleAction {
    let scheduler: ReminderScheduler
    let onDenied: () -> Void

    func callAsFunction(_ isOn: Bool, apply: @escaping (Bool) -> Void) {
        guard isOn else {
            apply(false)
            scheduler.reschedule()
            return
        }
        Task {
            if await scheduler.requestAuthorization() {
                apply(true)
                scheduler.reschedule()
            } else {
                onDenied()
            }
        }
    }
}
