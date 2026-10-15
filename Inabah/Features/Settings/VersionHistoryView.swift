import SwiftUI

/// «История версий» (из «О приложении», как на Android, `10c`): группа на версию — «1.1.1 · 12 октября
/// 2026 г.», строки — пункты; новые сверху. Файл не загрузился — «Нет истории версий».
struct VersionHistoryView: View {
    @Environment(WhatsNew.self) private var whatsNew
    @Environment(\.theme) private var theme

    /// `nil` — ещё загружается (ничего не показывается).
    @State private var notes: [ReleaseNote]?

    var body: some View {
        Group {
            if let notes, notes.isEmpty {
                NoticeCard(symbol: "info.circle", title: "versionHistory.empty.title", message: "versionHistory.empty.message")
                    .padding(Spacing.xl)
                    .frame(maxHeight: .infinity, alignment: .top)
                    .background { theme.gradients.settingsBackground.linear.ignoresSafeArea() }
            } else {
                Form {
                    ForEach(notes ?? [], id: \.version) { note in
                        Section {
                            ForEach(Array(note.items.enumerated()), id: \.offset) { _, item in
                                Text(verbatim: item)
                                    .settingsRow()
                            }
                        } header: {
                            Text("versionHistory.header \(note.version.text) \(Self.dateText(note.date))")
                        }
                    }
                }
                .settingsForm(background: theme.gradients.settingsBackground)
            }
        }
        .navigationTitle(Text("versionHistory.title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .glassBackButton(tint: theme.palette.tabSettings, header: nil)
        .task { notes = await whatsNew.history() }
        .audioPlayerInset()
    }

    /// Дата на языке интерфейса (а не системы — приложение пока только на русском), длинная
    /// («12 октября 2026 г.»); день из файла — полночь UTC.
    private static func dateText(_ date: Date) -> String {
        let language = Bundle.main.preferredLocalizations.first ?? ContentLanguage.base.rawValue
        var style = Date.FormatStyle(date: .long, time: .omitted, locale: Locale(identifier: language))
        style.timeZone = .gmt
        return date.formatted(style)
    }
}

#Preview {
    NavigationStack {
        VersionHistoryView()
    }
    .appEnvironment(.preview)
}
