import SwiftUI

/// Тексты и оформление сборника — в одном месте, чтобы новый сборник добавлялся одним `case`.
extension HadithCollection {
    var title: LocalizedStringResource {
        switch self {
        case .nawawi: "hadith.nawawi.title"
        case .qudsi: "hadith.qudsi.title"
        case .ajurri: "hadith.ajurri.title"
        }
    }

    /// Арабское название сборника — контент, не переводится.
    var arabicTitle: String {
        switch self {
        case .nawawi: "الأربعون النووية"
        case .qudsi: "الأربعون القدسية"
        case .ajurri: "أربعون حديثاً للآجري"
        }
    }

    /// «50 хадисов (с доп. Ибн Раджаба)»; арабское название добавляет карточка (`metaOriginal`).
    func cardMeta(count: Int) -> LocalizedStringResource {
        switch self {
        case .nawawi: "hadith.nawawi.meta \(count)"
        case .qudsi: "hadith.qudsi.meta \(count)"
        case .ajurri: "hadith.ajurri.meta \(count)"
        }
    }

    var symbolName: String {
        switch self {
        case .nawawi: "book.closed.fill"
        case .qudsi: "sun.max.fill"
        case .ajurri: "pencil.and.scribble"
        }
    }

    func cardGradient(in theme: Theme) -> ThemeGradient {
        switch self {
        case .nawawi: theme.gradients.nawawiCard
        case .qudsi: theme.gradients.qudsiCard
        case .ajurri: theme.gradients.ajurriCard
        }
    }

    func iconColor(in theme: Theme) -> Color {
        switch self {
        case .nawawi: theme.palette.goldLight
        case .qudsi: theme.palette.sunRays
        case .ajurri: theme.palette.gold
        }
    }

    func cardShadow(in theme: Theme) -> ShadowToken {
        switch self {
        case .nawawi: .navCard(theme.palette.successDeep, strength: .medium)
        case .qudsi, .ajurri: .navCard(theme.palette.shadow, strength: .strong)
        }
    }
}
