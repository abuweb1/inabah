import Observation
import UIKit

/// Иконка приложения на экране «Домой»: основная и альтернативные наборы из `Assets.xcassets`.
///
/// Имена наборов должны совпадать с настройкой сборки `ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES`
/// (по ней Xcode добавляет `CFBundleAlternateIcons` в Info.plist).
nonisolated enum AppIconOption: String, CaseIterable, Identifiable, Sendable {
    /// Основная иконка (`AppIcon`).
    case classic
    case niche
    case beads
    case dawn

    var id: Self { self }

    /// Имя альтернативного набора; у основной — `nil`.
    var alternateIconName: String? {
        switch self {
        case .classic: nil
        case .niche: "AppIcon-Niche"
        case .beads: "AppIcon-Beads"
        case .dawn: "AppIcon-Dawn"
        }
    }

    /// Вариант по имени, которое сообщает система; неизвестное имя — основная иконка.
    init(alternateIconName name: String?) {
        self = Self.allCases.first { $0.alternateIconName == name } ?? .classic
    }
}

/// Смена иконки приложения — `UIApplication`; в тестах и превью подставляется заглушка.
protocol AppIconSwitching: AnyObject {
    var supportsAlternateIcons: Bool { get }
    var alternateIconName: String? { get }
    func setAlternateIconName(_ alternateIconName: String?) async throws
}

/// Системная смена иконки. `UIApplication.shared` берётся в момент вызова: окружение приложения
/// создаётся раньше, чем `UIApplication`.
final class SystemAppIconSwitcher: AppIconSwitching {
    var supportsAlternateIcons: Bool { UIApplication.shared.supportsAlternateIcons }
    var alternateIconName: String? { UIApplication.shared.alternateIconName }

    func setAlternateIconName(_ alternateIconName: String?) async throws {
        try await UIApplication.shared.setAlternateIconName(alternateIconName)
    }
}

/// Выбор иконки приложения в настройках. Выбор хранит сама система — своё хранение не нужно.
@Observable
final class AppIconSettings {
    @ObservationIgnored private let switcher: any AppIconSwitching

    private(set) var current: AppIconOption = .classic
    private(set) var isSupported = true
    /// Система не смогла сменить иконку — для алерта.
    var failedToChange = false

    init(switcher: any AppIconSwitching) {
        self.switcher = switcher
    }

    /// Текущая иконка — у системы (при открытии экрана: при создании окружения её ещё не спросить).
    func refresh() {
        let option = AppIconOption(alternateIconName: switcher.alternateIconName)
        if current != option { current = option }
        let supported = switcher.supportsAlternateIcons
        if isSupported != supported { isSupported = supported }
    }

    func select(_ option: AppIconOption) async {
        guard option != current else { return }
        do {
            try await switcher.setAlternateIconName(option.alternateIconName)
            current = option
        } catch {
            failedToChange = true
        }
    }
}

/// Без смены иконки процесса — для превью: выбор только запоминается.
final class PreviewAppIconSwitcher: AppIconSwitching {
    private(set) var alternateIconName: String?
    var supportsAlternateIcons: Bool { true }

    func setAlternateIconName(_ alternateIconName: String?) async throws {
        self.alternateIconName = alternateIconName
    }
}
