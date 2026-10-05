import SwiftUI

/// Текст лицензии стороннего ресурса, встроенного в приложение (шрифт, значки).
nonisolated enum LicenseDocument: String, CaseIterable, Identifiable, Hashable, Codable, Sendable {
    /// Арабский шрифт Scheherazade New — SIL Open Font License 1.1.
    case scheherazade
    /// Значки вкладок Material Symbols — Apache License 2.0.
    case materialSymbols

    var id: Self { self }

    /// Имя файла лицензии в Bundle (без расширения `.txt`).
    var resourceName: String {
        switch self {
        case .scheherazade: "ScheherazadeNew-OFL"
        case .materialSymbols: "MaterialSymbols-LICENSE"
        }
    }

    var title: LocalizedStringResource {
        switch self {
        case .scheherazade: "about.license.scheherazade.title"
        case .materialSymbols: "about.license.material.title"
        }
    }

    var subtitle: LocalizedStringResource {
        switch self {
        case .scheherazade: "about.license.scheherazade.subtitle"
        case .materialSymbols: "about.license.material.subtitle"
        }
    }

    /// Текст лицензии из Bundle; `nil`, если файла нет.
    func text(in bundle: Bundle = .main) -> String? {
        guard let url = bundle.url(forResource: resourceName, withExtension: "txt") else { return nil }
        return try? String(contentsOf: url, encoding: .utf8)
    }

    /// Текст для экрана: строки внутри абзаца склеены — в файле они разбиты по ~70 символов,
    /// и на узком экране получалась «лесенка». Пустые строки и линии из «-» сохраняются.
    static func reflowed(_ text: String) -> String {
        text.replacing("\r\n", with: "\n")
            .components(separatedBy: "\n\n")
            .map { paragraph in
                let lines = paragraph.split(separator: "\n", omittingEmptySubsequences: false)
                let isRule = lines.contains { line in
                    let trimmed = line.trimmingCharacters(in: .whitespaces)
                    return !trimmed.isEmpty && trimmed.allSatisfy { $0 == "-" }
                }
                guard !isRule else { return paragraph }
                return lines.map { $0.trimmingCharacters(in: .whitespaces) }.joined(separator: " ")
            }
            .joined(separator: "\n\n")
    }
}

/// Полный текст лицензии — моноширинным шрифтом, с выделением для копирования.
struct LicenseView: View {
    let document: LicenseDocument

    @Environment(\.theme) private var theme
    @State private var text: String?

    var body: some View {
        ScrollView {
            Text(verbatim: text ?? "")
                .font(.caption.monospaced())
                .foregroundStyle(theme.palette.onAccentStrong)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(Spacing.xl)
        }
        .background { theme.gradients.settingsBackground.linear.ignoresSafeArea() }
        .navigationTitle(Text(document.title))
        .navigationBarTitleDisplayMode(.inline)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .task { text = document.text().map(LicenseDocument.reflowed) }
        .audioPlayerInset()
    }
}

#Preview {
    NavigationStack {
        LicenseView(document: .scheherazade)
    }
    .appEnvironment(.preview)
}
