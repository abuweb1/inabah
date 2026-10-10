import SwiftUI

/// Настройки азкаров: время азкаров («с — до») и ручной сброс прогресса раздела.
///
/// Время правится в черновике и сохраняется, только когда пользователь уходит с экрана
/// (или приложение уходит в фон). Иначе сверка прогресса, которая срабатывает и при
/// возврате в приложение, и по таймеру границы, могла увидеть полуготовое окно и обнулить
/// прочитанное (аудит 2026-10-06, §5.2).
struct AzkarSettingsView: View {
    @Environment(AzkarStore.self) private var store
    @Environment(AzkarWindowSettings.self) private var windowSettings
    @Environment(ReminderSettings.self) private var reminders
    @Environment(ReminderScheduler.self) private var scheduler
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.theme) private var theme

    /// В разрешении только что отказали — показать «заблокировано», даже если ничего не включено.
    @State private var permissionDenied = false

    /// Раздел, сброс которого ждёт подтверждения.
    @State private var pendingReset: AzkarSection?
    /// Время азкаров, пока экран открыт. Пусто до появления экрана — тогда строки
    /// показывают сохранённое время.
    @State private var drafts: [AzkarSection: AzkarWindow] = [:]

    var body: some View {
        Form {
            Section {
                ForEach(AzkarSection.allCases, id: \.self) { section in
                    AzkarWindowRow(section: section, window: draft(for: section))
                        .settingsRow()
                }
            } header: {
                Text("settings.window.header")
            } footer: {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    if hasEmptyWindow {
                        Text("settings.window.sameTime")
                            .foregroundStyle(theme.palette.onAccent)
                    }
                    Text("settings.window.footer")
                        .foregroundStyle(theme.palette.onAccentSecondary)
                }
            }

            remindersSection

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
        .onAppear(perform: loadDrafts)
        .onDisappear(perform: commitDrafts)
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { commitDrafts() }
        }
    }

    /// «Напоминания» (решения пользователя 2026-10-10, вид — как на Android): в начале времени каждого
    /// раздела (время — из черновика) и «если ещё не прочитаны». Переключатели сохраняются сразу;
    /// включён — только если сохранён и уведомления разрешены.
    @ViewBuilder
    private var remindersSection: some View {
        let allowed = scheduler.isAllowed
        let preferences = reminders.preferences
        let blocked = !allowed && (permissionDenied || !preferences.azkarStart.isEmpty || preferences.azkarUnfinished)
        let toggle = ReminderToggleAction(scheduler: scheduler) { permissionDenied = true }
        Section {
            ForEach(AzkarSection.allCases, id: \.self) { section in
                ReminderToggleRow(
                    title: section.title,
                    subtitle: Text("settings.reminders.start.subtitle \(draft(for: section).wrappedValue.start.text(in: store.calendar))"),
                    isOn: allowed && reminders.isStartEnabled(section),
                    tint: theme.palette.tabAzkar
                ) { isOn in
                    toggle(isOn) { reminders.setStart($0, for: section) }
                }
                .settingsRow()
            }
            ReminderToggleRow(
                title: "settings.reminders.unfinished",
                subtitle: Text("settings.reminders.unfinished.subtitle"),
                isOn: allowed && preferences.azkarUnfinished,
                tint: theme.palette.tabAzkar
            ) { isOn in
                toggle(isOn) { reminders.setUnfinished($0) }
            }
            .disabled(preferences.azkarStart.isEmpty)
            .settingsRow()
            if blocked {
                ReminderOpenSettingsRow()
                    .settingsRow()
            }
        } header: {
            Text("settings.reminders.header")
        } footer: {
            if blocked {
                Text("settings.reminders.blocked")
                    .foregroundStyle(theme.palette.onAccent)
            }
        }
    }

    /// В черновике есть раздел, у которого начало совпало с концом, — такое время не сохранится.
    private var hasEmptyWindow: Bool {
        drafts.values.contains { !$0.isValid }
    }

    private func draft(for section: AzkarSection) -> Binding<AzkarWindow> {
        Binding {
            drafts[section] ?? windowSettings.window(for: section)
        } set: { window in
            drafts[section] = window
        }
    }

    private func loadDrafts() {
        drafts = Dictionary(uniqueKeysWithValues: AzkarSection.allCases.map { ($0, windowSettings.window(for: $0)) })
    }

    /// Сохранить черновик и применить время к прогрессу. Окно без длины не сохраняется —
    /// у раздела остаётся прежнее время. Повторный вызов без изменений ничего не делает.
    private func commitDrafts() {
        for (section, window) in drafts {
            windowSettings.set(window, for: section)
        }
        store.reconcile()
        // Новое время азкаров — новое время напоминаний.
        scheduler.reschedule()
    }
}

/// «Утренние  с [5:00] до [12:00]». При крупном тексте выбор времени уходит под название.
private struct AzkarWindowRow: View {
    let section: AzkarSection
    @Binding var window: AzkarWindow

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

    /// Время суток как дата опорного дня — для `DatePicker`; в черновик идут только часы и минуты.
    /// Календарь — тот же, что у расписания азкаров.
    private var start: Binding<Date> {
        Binding {
            window.start.date(in: store.calendar)
        } set: { date in
            window = AzkarWindow(start: DayTime(date, in: store.calendar), end: window.end)
        }
    }

    private var end: Binding<Date> {
        Binding {
            window.end.date(in: store.calendar)
        } set: { date in
            window = AzkarWindow(start: window.start, end: DayTime(date, in: store.calendar))
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
