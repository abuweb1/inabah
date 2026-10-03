import SwiftUI

/// Настройки хадисов: сброс отметок «прочитан» / «выучен» каждого сборника отдельно.
struct HadithSettingsView: View {
    @Environment(HadithStore.self) private var store
    @Environment(HadithProgress.self) private var progress
    @Environment(\.theme) private var theme

    /// Сборник, сброс которого ждёт подтверждения.
    @State private var pendingReset: HadithCollection?

    var body: some View {
        Form {
            ForEach(HadithCollection.allCases, id: \.self) { collection in
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
        .confirmationDialog(
            Text("settings.hadith.reset.confirm.title"),
            isPresented: isConfirming,
            titleVisibility: .visible,
            presenting: pendingReset
        ) { collection in
            Button(role: .destructive) {
                progress.reset(collection)
            } label: {
                Text("settings.hadith.reset.confirm.action")
            }
        } message: { collection in
            Text("settings.hadith.reset.confirm.message \(String(localized: collection.title))")
        }
        .task { await store.loadAll() }
        .audioPlayerInset()
    }

    private var isConfirming: Binding<Bool> {
        Binding(
            get: { pendingReset != nil },
            set: { if !$0 { pendingReset = nil } }
        )
    }
}

#Preview {
    NavigationStack {
        HadithSettingsView()
    }
    .appEnvironment(.preview)
}
