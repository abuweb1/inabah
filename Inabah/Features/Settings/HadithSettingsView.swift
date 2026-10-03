import SwiftUI

/// Настройки хадисов: сброс отметок «прочитан» / «выучен» каждого сборника отдельно.
struct HadithSettingsView: View {
    @Environment(HadithStore.self) private var store
    @Environment(HadithProgress.self) private var progress
    @Environment(HadithCollectionOrder.self) private var order
    @Environment(\.theme) private var theme

    /// Сборник, сброс которого ждёт подтверждения.
    @State private var pendingReset: HadithCollection?

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
        .task { await store.loadAll() }
        .audioPlayerInset()
    }
}

#Preview {
    NavigationStack {
        HadithSettingsView()
    }
    .appEnvironment(.preview)
}
