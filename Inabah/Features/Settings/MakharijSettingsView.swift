import SwiftUI

/// Настройки «Махраджа» (Android `MakharijSettingsScreen`, docs/android/06-screens.md 6.8): транскрипция и
/// «Открыть все шаги» «Учимся читать»; напоминания «Пора на урок»; «Пройдено N из M», начатые шаги со сбросом и
/// сброс всего прогресса.
struct MakharijSettingsView: View {
    @Environment(ReadingStore.self) private var store
    @Environment(ReadingProgress.self) private var progress
    @Environment(ReadingSettings.self) private var settings
    @Environment(ReminderSettings.self) private var reminders
    @Environment(ReminderScheduler.self) private var scheduler
    @Environment(AzkarStore.self) private var azkarStore
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.theme) private var theme

    /// Время слотов «Пора на урок» правится черновиком и сохраняется при уходе с экрана или в фон (как «Хадис дня»).
    @State private var readingTimeDrafts: [ReadingReminderSlot: DayTime] = [:]
    /// Уведомления запрещены при попытке включить - показать «Открыть настройки».
    @State private var permissionDenied = false

    /// Шаг, сброс которого ждёт подтверждения.
    @State private var pendingStep: Int?
    /// Сброс всего прогресса ждёт подтверждения (`true`; `nil` - диалога нет).
    @State private var pendingResetAll: Bool?
    /// «Показать подсказки снова» нажата - отклик.
    @State private var hintsResetCount = 0

    var body: some View {
        let tint = theme.palette.tabMakharij
        SettingsForm {
            lessonsSection
            remindersSection
            if let course = store.course.value {
                progressSection(course)
            }
        }
        .settingsForm(background: theme.gradients.makharijBackground)
        .glassBackButton(tint: tint, header: nil)
        .navigationTitle(Text("settings.makharij.title"))
        .navigationBarTitleDisplayMode(.inline)
        .destructiveConfirmation(
            "settings.makharij.step.reset.confirm.title",
            item: $pendingStep,
            actionLabel: "settings.makharij.reset.confirm.action",
            tint: tint,
            message: { number in
                Text("settings.makharij.step.reset.confirm.message \(store.course.value?.step(number)?.title ?? "")")
            },
            perform: resetStep
        )
        .destructiveConfirmation(
            "settings.makharij.resetAll.confirm.title",
            item: $pendingResetAll,
            actionLabel: "settings.makharij.reset.confirm.action",
            tint: tint,
            message: { _ in Text("settings.makharij.resetAll.confirm.message") },
            perform: { _ in progress.reset() }
        )
        .task { await store.load() }
        .onDisappear(perform: commitReadingTimes)
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { commitReadingTimes() }
        }
        .audioPlayerInset()
    }

    /// «Напоминания» (Android `ReadingReminderGroup`, 2026-10-20): «Утром», «Днём», «Вечером», у включённого - время.
    @ViewBuilder
    private var remindersSection: some View {
        let tint = theme.palette.tabMakharij
        let anyOn = !reminders.preferences.readingSlots.isEmpty
        // Сохранённое значение - и при запрете уведомлений (как у азкаров).
        let blocked = !scheduler.isAllowed && (permissionDenied || anyOn)
        let toggle = ReminderToggleAction(scheduler: scheduler) { permissionDenied = true }
        SettingsGroup {
            ForEach(ReadingReminderSlot.allCases, id: \.self) { slot in
                let isOn = reminders.isReadingEnabled(slot)
                ReminderToggleRow(title: slot.title, subtitle: nil, isOn: isOn, tint: tint) { newValue in
                    toggle(newValue) { reminders.setReading($0, for: slot) }
                }
                if isOn {
                    HStack(spacing: Spacing.m) {
                        Text("settings.makharij.reminder.time")
                            .accessibilityHidden(true)
                        Spacer(minLength: 0)
                        TimeField(
                            label: "settings.makharij.reminder.timeDescription \(String(localized: slot.title))",
                            selection: readingTime(slot),
                            calendar: azkarStore.calendar,
                            tint: tint
                        )
                    }
                }
            }
            if blocked {
                ReminderOpenSettingsRow()
            }
        } header: {
            Text("settings.reminders.header")
        } footer: {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                if blocked {
                    Text("settings.reminders.blocked")
                        .foregroundStyle(theme.palette.onAccent)
                }
                Text(isCourseDone ? "settings.makharij.reminders.footerDone" : "settings.makharij.reminders.footer")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
        }
    }

    /// Курс пройден (`currentStep == nil`) - напоминания не приходят; до загрузки курса - нет.
    private var isCourseDone: Bool {
        guard let course = store.course.value else { return false }
        let completed = ReadingLogic.completedSteps(course: course, lessonsDone: progress.lessonsDone, wordCounts: progress.wordCounts)
        return ReadingLogic.currentStep(course: course, completed: completed) == nil
    }

    /// Время слота как дата опорного дня - для `TimeField`; в черновик идут только часы и минуты.
    private func readingTime(_ slot: ReadingReminderSlot) -> Binding<Date> {
        Binding {
            (readingTimeDrafts[slot] ?? reminders.preferences.readingTime(slot)).date(in: azkarStore.calendar)
        } set: { date in
            readingTimeDrafts[slot] = DayTime(date, in: azkarStore.calendar)
        }
    }

    private func commitReadingTimes() {
        guard !readingTimeDrafts.isEmpty else { return }
        for (slot, time) in readingTimeDrafts { reminders.setReadingTime(time, for: slot) }
        readingTimeDrafts = [:]
        scheduler.reschedule()
    }

    /// «Уроки»: подписи - под названием, как «Сворачивать арабский текст».
    private var lessonsSection: some View {
        SettingsGroup {
            toggle(
                "settings.makharij.translit",
                subtitle: "settings.makharij.translit.subtitle",
                // Тот же флаг, что глаз в уроке, только наоборот: «показывать».
                isOn: Binding(get: { !settings.transliterationHidden }, set: { settings.setTransliterationHidden(!$0) })
            )
            toggle(
                "settings.makharij.allSteps",
                subtitle: "settings.makharij.allSteps.subtitle",
                isOn: Binding(get: { settings.allStepsOpen }, set: settings.setAllStepsOpen)
            )
            // Без подтверждения: ничего не теряется, подсказки просто покажутся снова.
            Button {
                settings.resetIntros()
                hintsResetCount += 1
            } label: {
                VStack(alignment: .leading, spacing: Spacing.xxxs) {
                    Text("settings.makharij.hintsReset")
                        .font(.body)
                        .foregroundStyle(theme.palette.onAccent)
                    Text("settings.makharij.hintsReset.subtitle")
                        .font(.caption)
                        .foregroundStyle(theme.palette.onAccentSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(.rect)
            }
            .sensoryFeedback(.success, trigger: hintsResetCount)
        } header: {
            Text("settings.makharij.lessons.header")
        }
    }

    private func toggle(_ title: LocalizedStringResource, subtitle: LocalizedStringResource, isOn: Binding<Bool>) -> some View {
        Toggle(isOn: isOn) {
            VStack(alignment: .leading, spacing: Spacing.xxxs) {
                Text(title)
                    .font(.body)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
        }
        .choiceToggleStyle(tint: theme.palette.tabMakharij)
    }

    /// «Прогресс»: итог, начатые и пройденные шаги, «Сбросить весь прогресс».
    private func progressSection(_ course: ReadingCourse) -> some View {
        let completed = ReadingLogic.completedSteps(course: course, lessonsDone: progress.lessonsDone, wordCounts: progress.wordCounts)
        let started = course.steps.filter {
            ReadingLogic.hasProgress(step: $0, lessonsDone: progress.lessonsDone, wordCounts: progress.wordCounts, zikrs: progress.zikrs)
        }
        return SettingsGroup {
            Text("makharij.reading.card.progress \(ReadingLogic.completedCount(course: course, completed: completed)) \(ReadingLogic.numberedStepCount(course: course))")
                .font(.subheadline)
                .foregroundStyle(theme.palette.onAccentSecondary)
            ForEach(started, id: \.number) { step in
                StepResetRow(step: step, isDone: completed.contains(step.number)) { pendingStep = step.number }
                    .confirmationAnchor(step.number)
            }
            Button(role: .destructive) {
                pendingResetAll = true
            } label: {
                Text("settings.makharij.resetAll")
                    .fontWeight(.semibold)
            }
            .disabled(started.isEmpty)
            .confirmationAnchor(true)
        } header: {
            Text("settings.makharij.progress.header")
        } footer: {
            if started.isEmpty {
                Text("settings.makharij.progress.empty")
            }
        }
    }

    private func resetStep(_ number: Int) {
        guard let step = store.course.value?.step(number) else { return }
        progress.resetStep(number, zikrs: step.zikrs.map(\.zikr))
        // Шаг в «Пора на урок» мог смениться (сброс уроков пересобирает план и сам - `ReadingReminderWatcher`).
        scheduler.reschedule()
    }
}

private extension ReadingReminderSlot {
    var title: LocalizedStringResource {
        switch self {
        case .morning: "settings.makharij.reminder.morning"
        case .day: "settings.makharij.reminder.day"
        case .evening: "settings.makharij.reminder.evening"
        }
    }
}

/// Строка начатого шага: «Шаг N · …» / «Итог · …», «пройден» или «начат», справа «Сбросить».
private struct StepResetRow: View {
    let step: ReadingStep
    let isDone: Bool
    let onReset: () -> Void

    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: onReset) {
            HStack(spacing: Spacing.m) {
                VStack(alignment: .leading, spacing: Spacing.xxxs) {
                    Text(step.displayTitle)
                        .font(.body)
                        .foregroundStyle(theme.palette.onAccent)
                    Text(state)
                        .font(.caption)
                        .foregroundStyle(theme.palette.onAccentSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Text("settings.makharij.step.reset")
                    .font(.body)
                    .foregroundStyle(theme.palette.tabMakharij)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "\(String(localized: step.displayTitle)), \(String(localized: state))"))
        .accessibilityHint(Text("settings.makharij.step.reset"))
    }

    private var state: LocalizedStringResource {
        isDone ? "settings.makharij.step.done" : "settings.makharij.step.started"
    }
}

#Preview {
    NavigationStack {
        MakharijSettingsView()
    }
    .appEnvironment(.preview)
}
