import Foundation
import SwiftUI
import Testing
@testable import Inabah

@MainActor
@Suite("Размер текста")
struct TextSizeSettingsTests {
    private let storage: IsolatedDefaults
    private var defaults: UserDefaults { storage.defaults }

    init() throws {
        storage = try IsolatedDefaults("TextSizeSettingsTests")
    }

    @Test("По умолчанию — обычные переводы и интерфейс")
    func defaults_areStandard() {
        let settings = TextSizeSettings(defaults: defaults)

        #expect(settings.content == .standard)
        #expect(settings.interface == .standard)
    }

    @Test("Выбор сохраняется между запусками")
    func persists() {
        let settings = TextSizeSettings(defaults: defaults)
        settings.select(content: .largest)
        settings.select(interface: .smaller)

        let reloaded = TextSizeSettings(defaults: defaults)
        #expect(reloaded.content == .largest)
        #expect(reloaded.interface == .smaller)
    }

    @Test("Повреждённое значение — обычный размер")
    func unknownValues() {
        defaults.set("huge", forKey: "appearance.contentTextSize")
        defaults.set(42, forKey: "appearance.interfaceTextSize")

        let settings = TextSizeSettings(defaults: defaults)
        #expect(settings.content == .standard)
        #expect(settings.interface == .standard)
    }

    @Test("Повторный выбор того же размера не уведомляет наблюдателей")
    func selectingCurrentIsNoOp() {
        let settings = TextSizeSettings(defaults: defaults)
        settings.select(interface: .larger)
        let notified = NotificationFlag()
        withObservationTracking {
            _ = settings.interface
            _ = settings.content
        } onChange: {
            notified.set()
        }

        settings.select(interface: .larger)
        settings.select(content: .standard)

        #expect(!notified.value)
        settings.select(content: .large)
        #expect(notified.value)
    }

    @Test("Шаги переводов растут, «Обычный» — без увеличения")
    func contentScales() {
        let scales = ContentTextSize.allCases.map(\.scale)

        let isIncreasing = zip(scales, scales.dropFirst()).allSatisfy { $0 < $1 }

        #expect(scales.count == 5)
        #expect(isIncreasing)
        #expect(ContentTextSize.standard.scale == 1)
    }

    @Test("Шаги интерфейса растут, «Обычный» — стандартный размер посередине, без размеров доступности")
    func interfaceSizes() {
        let sizes = InterfaceTextSize.allCases.map(\.dynamicTypeSize)

        let isIncreasing = zip(sizes, sizes.dropFirst()).allSatisfy { $0 < $1 }
        let hasAccessibilitySize = sizes.contains { $0.isAccessibilitySize }

        #expect(sizes.count == 3)
        #expect(isIncreasing)
        #expect(InterfaceTextSize.standard.dynamicTypeSize == .large)
        #expect(InterfaceTextSize.allCases.firstIndex(of: .standard) == 1)
        #expect(!hasAccessibilitySize)
    }

    @Test("Переводы азкаров и хадисов — общие базовые размеры", arguments: [
        (ContentTextStyle.translation, 17.0),
        (.transliteration, 15),
        (.note, 13),
    ])
    func contentBaseSizes(style: ContentTextStyle, size: Double) {
        #expect(style.baseSize == size)
    }
}
