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

    /// Переключатель показывает только сохранённое значение: нажатие просит изменить его, а включённым
    /// он становится, когда напоминание действительно включено (дали разрешение). Своего `@State` нет:
    /// прежняя копия после нажатия сбрасывалась к устаревшему `isOn` и тут же включала напоминание
    /// обратно — выключить было нельзя (1.1.1 сборка 3).
    private var binding: Binding<Bool> {
        Binding(
            get: { isOn },
            set: { value in
                if value != isOn { onChange(value) }
            }
        )
    }

    var body: some View {
        Toggle(isOn: binding) {
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
        .choiceToggleStyle(tint: tint)
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
/// отказ запоминается до ухода с экрана — показывается «заблокировано» (как на Android). Выключение —
/// всегда сразу, и при запрете уведомлений: переключатель показывает сохранённое значение (решение
/// пользователя 2026-10-10, аудит iOS 1.1.1, Р2).
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
