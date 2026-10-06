import Foundation
import Testing
import UIKit
@testable import Inabah

@Suite("Палитры оформления")
struct ThemeStyleTests {
    private static let unified = ThemeStyle.allCases.filter { $0 != .sections }

    @Test("«По умолчанию» — прежняя тема без изменений")
    func sectionsIsDefaultTheme() {
        #expect(ThemeStyle.sections.theme == Theme.inabah)
    }

    @Test("У единых стилей есть все цвета, и стили различаются", arguments: unified)
    func assetsExist(style: ThemeStyle) {
        for token in ThemeColorToken.allCases {
            #expect(UIColor(named: token.assetName(for: style), in: .main, compatibleWith: nil) != nil, "\(token.assetName(for: style))")
        }
        #expect(style.theme != Theme.inabah)
    }

    @Test("Вторичный текст читается на карточке зикра (≥ 4.5:1)", arguments: unified)
    func secondaryTextContrast(style: ThemeStyle) throws {
        let text = try Contrast.luminance(ThemeColorToken.textSecondary.assetName(for: style))
        let card = try Contrast.luminance(ThemeColorToken.card.assetName(for: style))
        #expect(Contrast.ratio(text, card) >= 4.5)
    }

    @Test("Белый заголовок читается на карточках навигации (≥ 3:1, крупный жирный)", arguments: unified)
    func cardTitleContrast(style: ThemeStyle) throws {
        let mids: [ThemeColorToken] = [.morningCardMid, .eveningCardMid, .nawawiCardMid, .qudsiCardMid, .ajurriCardMid]
        for token in mids {
            let card = try Contrast.luminance(token.assetName(for: style))
            #expect(Contrast.ratio(1, card) >= 3, "\(token.assetName(for: style))")
        }
    }

    @Test("Утренние светлее вечерних, карточки сборников явно различимы", arguments: unified)
    func cardsAreDistinct(style: ThemeStyle) throws {
        let morning = try Contrast.luminance(ThemeColorToken.morningCardMid.assetName(for: style))
        let evening = try Contrast.luminance(ThemeColorToken.eveningCardMid.assetName(for: style))
        #expect(morning > evening)

        // Расстояние в OKLab: порог заметности ≈ 0.02, «явно различимы» — от 0.07.
        let collections = try [ThemeColorToken.nawawiCardMid, .qudsiCardMid, .ajurriCardMid]
            .map { try Contrast.oklab($0.assetName(for: style)) }
        for i in collections.indices {
            for j in collections.indices where j > i {
                #expect(Contrast.distance(collections[i], collections[j]) >= 0.07)
            }
        }
    }
}

@MainActor
@Suite("Выбор палитры")
struct AppearanceSettingsTests {
    private let storage: IsolatedDefaults
    private var defaults: UserDefaults { storage.defaults }

    init() throws {
        storage = try IsolatedDefaults("AppearanceSettingsTests")
    }

    @Test("По умолчанию — свой цвет у каждого раздела")
    func defaultStyle() {
        #expect(AppearanceSettings(defaults: defaults).style == .sections)
    }

    @Test("Выбор сохраняется между запусками")
    func persists() {
        AppearanceSettings(defaults: defaults).select(.amber)

        #expect(AppearanceSettings(defaults: defaults).style == .amber)
    }

    @Test("Повторный выбор той же палитры не уведомляет наблюдателей")
    func selectingCurrentIsNoOp() {
        let settings = AppearanceSettings(defaults: defaults)
        settings.select(.emerald)
        let notified = NotificationFlag()
        withObservationTracking {
            _ = settings.style
        } onChange: {
            notified.set()
        }

        settings.select(.emerald)

        #expect(!notified.value)
        settings.select(.graphite)
        #expect(notified.value)
    }

    @Test("Повреждённое значение — палитра по умолчанию")
    func unknownValue() {
        defaults.set("neon", forKey: "appearance.themeStyle")

        #expect(AppearanceSettings(defaults: defaults).style == .sections)
    }
}
