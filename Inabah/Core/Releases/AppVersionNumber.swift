import Foundation

/// Номер версии приложения строго вида `X.Y.Z` (как на Android, `AppVersion.kt`): части сравниваются
/// числами (`1.10.0 > 1.9.0`), `1.01.0 == 1.1.0`. Иное (`1.1`, `v1.1.1`, `1.1.1-beta`) — не версия.
nonisolated struct AppVersionNumber: Comparable, Hashable, Sendable, CustomStringConvertible {
    /// Исходная строка — для показа («Что нового в 1.1.1»).
    let text: String
    private let parts: [Int]

    init?(_ text: String?) {
        guard let text else { return nil }
        let pieces = text.split(separator: ".", omittingEmptySubsequences: false)
        guard pieces.count == 3 else { return nil }
        var parts: [Int] = []
        for piece in pieces {
            guard !piece.isEmpty, piece.allSatisfy(\.isASCIINumber), let value = Int(piece) else { return nil }
            parts.append(value)
        }
        self.text = text
        self.parts = parts
    }

    var description: String { text }

    static func == (lhs: Self, rhs: Self) -> Bool { lhs.parts == rhs.parts }
    func hash(into hasher: inout Hasher) { hasher.combine(parts) }
    static func < (lhs: Self, rhs: Self) -> Bool { lhs.parts.lexicographicallyPrecedes(rhs.parts) }
}

private nonisolated extension Character {
    var isASCIINumber: Bool { isASCII && isNumber }
}

/// Версия и сборка из Info.plist («Версия 1.1.1 (3)» в «О приложении», «Что нового»).
nonisolated enum AppVersion {
    static var marketing: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    static var build: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? ""
    }
}
