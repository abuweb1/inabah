import SwiftUI

/// Настройки хадисов: сброс отметок «прочитан» / «выучен» каждого сборника отдельно,
/// «Хадис дня» и очистка «Избранного».
struct HadithSettingsView: View {
    @Environment(HadithStore.self) private var store
    @Environment(HadithProgress.self) private var progress
    @Environment(HadithFavorites.self) private var favorites
    @Environment(HadithCollectionOrder.self) private var order
    @Environment(ReminderSettings.self) private var reminders
    @Environment(ReminderScheduler.self) private var scheduler
    @Environment(AzkarStore.self) private var azkarStore
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.theme) private var theme

    /// Сборник, сброс которого ждёт подтверждения.
    @State private var pendingReset: HadithCollection?
    /// Очистка «Избранного» ждёт подтверждения (`true`; `nil` — диалога нет).
    @State private var pendingFavoritesClear: Bool?
    /// Время хадиса дня, пока экран открыт: сохраняется при уходе с экрана или в фон, а не на
    /// каждом шаге колеса (как время азкаров).
    @State private var hadithTimeDraft: DayTime?
    /// В разрешении только что отказали — показать «заблокировано».
    @State private var permissionDenied = false

    var body: some View {
        Form {
            Section {
                NavigationLink(value: SettingsRoute.hadithOrder) {
                    Text("settings.hadith.order.title")
                }
                .settingsRow()
            } header: {
                Text("settings.hadith.home.header")
            }

            ForEach(order.collections, id: \.self) { collection in
                let total = store.hadiths(in: collection).count
                let collectionProgress = progress.progress(in: collection, total: total)
                Section {
                    Text("hadith.list.progress \(collectionProgress.read) \(total) \(collectionProgress.memorized)")
                        .font(.subheadline)
                        .foregroundStyle(theme.palette.onAccentSecondary)
                        .settingsRow()
                    Button(role: .destructive) {
                        pendingReset = collection
                    } label: {
                        Text("settings.hadith.reset")
                    }
                    .disabled(collectionProgress.read == 0)
                    .settingsRow()
                } header: {
                    Text(collection.title)
                }
            }

            hadithOfDaySection

            Section {
                Button(role: .destructive) {
                    pendingFavoritesClear = true
                } label: {
                    Text("settings.hadith.favorites.clear")
                }
                // Неактивна, если закладок нет (правило — как на Android: по сохранённым закладкам).
                .disabled(favorites.isEmpty)
                .settingsRow()
            } header: {
                Text("hadith.favorites.title")
            }
        }
        .settingsForm(background: theme.gradients.hadithBackground)
        .navigationTitle(Text("settings.hadith.title"))
        .navigationBarTitleDisplayMode(.inline)
        .destructiveConfirmation(
            "settings.hadith.reset.confirm.title",
            item: $pendingReset,
            actionLabel: "settings.hadith.reset.confirm.action",
            message: { Text("settings.hadith.reset.confirm.message \(String(localized: $0.title))") },
            perform: { progress.reset($0) }
        )
        .destructiveConfirmation(
            "settings.hadith.favorites.clear.confirm.title",
            item: $pendingFavoritesClear,
            actionLabel: "settings.hadith.favorites.clear.confirm.action",
            message: { _ in Text("settings.hadith.favorites.clear.confirm.message") },
            perform: { _ in favorites.clear() }
        )
        .task { await store.loadAll() }
        .audioPlayerInset()
        .onDisappear(perform: commitHadithTime)
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { commitHadithTime() }
        }
    }

    /// «Хадис дня» (решения пользователя 2026-10-10, вид — как на Android): переключатель и время.
    @ViewBuilder
    private var hadithOfDaySection: some View {
        let allowed = scheduler.isAllowed
        // Сохранённое значение — и при запрете уведомлений (Р2, как в настройках азкаров).
        let isOn = reminders.preferences.hadithOfDay
        let blocked = !allowed && (permissionDenied || reminders.preferences.hadithOfDay)
        let toggle = ReminderToggleAction(scheduler: scheduler) { permissionDenied = true }
        Section {
            ReminderToggleRow(title: "settings.hadithOfDay.toggle", subtitle: nil, isOn: isOn, tint: theme.palette.tabHadith) { newValue in
                toggle(newValue) { reminders.setHadithOfDay($0) }
            }
            .settingsRow()
            if isOn {
                DatePicker(selection: hadithTime, displayedComponents: .hourAndMinute) {
                    Text("settings.hadithOfDay.time")
                }
                .accessibilityLabel(Text("settings.hadithOfDay.time.description"))
                .settingsRow()
            }
            if blocked {
                ReminderOpenSettingsRow()
                    .settingsRow()
            }
        } header: {
            Text("settings.hadithOfDay.header")
        } footer: {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                if blocked {
                    Text("settings.reminders.blocked")
                        .foregroundStyle(theme.palette.onAccent)
                }
                Text("settings.hadithOfDay.footer")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
        }
    }

    /// Время суток как дата опорного дня — для `DatePicker`; в черновик идут только часы и минуты.
    private var hadithTime: Binding<Date> {
        Binding {
            (hadithTimeDraft ?? reminders.preferences.hadithTime).date(in: azkarStore.calendar)
        } set: { date in
            hadithTimeDraft = DayTime(date, in: azkarStore.calendar)
        }
    }

    private func commitHadithTime() {
        guard let hadithTimeDraft else { return }
        reminders.setHadithTime(hadithTimeDraft)
        self.hadithTimeDraft = nil
        scheduler.reschedule()
    }
}

#Preview {
    NavigationStack {
        HadithSettingsView()
    }
    .appEnvironment(.preview)
}
