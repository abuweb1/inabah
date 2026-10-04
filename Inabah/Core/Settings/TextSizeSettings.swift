import Observation
import SwiftUI

/// Размер переводов (перевод, транскрипция, источник, передатчик) — множитель к базовым
/// размерам `ContentTextStyle`. Системный размер текста на переводы не влияет.
nonisolated enum ContentTextSize: String, CaseIterable, Identifiable, Sendable {
    case smaller
    case standard
    case larger
    case large
    case largest

    var id: Self { self }

    var scale: Double {
        switch self {
        case .smaller: 0.9
        case .standard: 1
        case .larger: 1.15
        case .large: 1.3
        case .largest: 1.5
        }
    }
}

/// Размер интерфейса (карточки главных, списки, плеер, настройки) — подменяет системный
/// размер текста во всём приложении. Заголовки экранов, кнопки и таб-бар закреплены
/// (`fixedTextSize()`), всплывающие окна остаются системными.
nonisolated enum InterfaceTextSize: String, CaseIterable, Identifiable, Sendable {
    case standard
    case larger
    case largest

    var id: Self { self }

    /// Для body: 17 / 19 / 23 pt. Размеры для доступности не используются — вёрстка
    /// рассчитана на обычные.
    var dynamicTypeSize: DynamicTypeSize {
        switch self {
        case .standard: .large
        case .larger: .xLarge
        case .largest: .xxxLarge
        }
    }
}

/// Размер текста, выбранный в настройках. Сохраняется между запусками.
@Observable
final class TextSizeSettings {
    private enum Key {
        static let content = "appearance.contentTextSize"
        static let interface = "appearance.interfaceTextSize"
    }

    @ObservationIgnored private let defaults: UserDefaults

    private(set) var content: ContentTextSize
    private(set) var interface: InterfaceTextSize

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        // Неизвестное или повреждённое значение — обычный размер.
        content = defaults.string(forKey: Key.content).flatMap(ContentTextSize.init(rawValue:)) ?? .standard
        interface = defaults.string(forKey: Key.interface).flatMap(InterfaceTextSize.init(rawValue:)) ?? .standard
    }

    /// Запись только при изменении: `@Observable` уведомляет и о записи того же значения,
    /// а от размера интерфейса зависит всё дерево вьюх.
    func select(content newSize: ContentTextSize) {
        guard newSize != content else { return }
        content = newSize
        defaults.set(newSize.rawValue, forKey: Key.content)
    }

    func select(interface newSize: InterfaceTextSize) {
        guard newSize != interface else { return }
        interface = newSize
        defaults.set(newSize.rawValue, forKey: Key.interface)
    }
}
