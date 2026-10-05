import Foundation

nonisolated extension Hadith {
    /// Длина превью в списке — как `substring(0, 80)` в прототипе.
    static let previewLength = 80

    /// Начало перевода для строки списка: без вступления «От Абу Хурайры: …» (передатчик
    /// показан отдельной строкой), не длиннее `previewLength` символов.
    var previewText: String {
        guard let text = translation?.text else { return "" }
        let body = text.replacing(Self.narratorPrefix, with: "", maxReplacements: 1)
        guard body.count > Self.previewLength else { return body }
        return String(body.prefix(Self.previewLength)).trimmingCharacters(in: .whitespaces) + "…"
    }

    /// Арабский текст одним абзацем: переносы строк в данных — это переносы строк печатного
    /// издания, а не смысловые (прототип их тоже не показывает).
    var arabicDisplayText: String {
        arabic.split(whereSeparator: \.isNewline)
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .joined(separator: " ")
    }

    /// Вступление «От …:» в начале перевода (регулярное выражение прототипа).
    private static var narratorPrefix: Regex<Substring> { #/^От [^:：]+[:：]\s*/# }
}
