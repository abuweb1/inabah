import Foundation
import Testing
@testable import Inabah

/// Заглушка системы: запоминает вызовы, может отказать.
private final class FakeAppIconSwitcher: AppIconSwitching {
    var alternateIconName: String?
    var supportsAlternateIcons = true
    var fails = false
    /// Удерживать ответ системы до `release()` — запрос «в полёте».
    var holds = false
    private(set) var requested: [String?] = []
    private var held: CheckedContinuation<Void, Never>?

    struct Refused: Error {}

    init(current: String? = nil) {
        alternateIconName = current
    }

    func setAlternateIconName(_ alternateIconName: String?) async throws {
        requested.append(alternateIconName)
        if holds { await withCheckedContinuation { held = $0 } }
        if fails { throw Refused() }
        self.alternateIconName = alternateIconName
    }

    func release() {
        held?.resume()
        held = nil
    }
}

@Suite("Варианты иконки приложения")
struct AppIconOptionTests {
    @Test("У классической нет имени набора, у остальных — уникальные")
    func names() {
        #expect(AppIconOption.classic.alternateIconName == nil)
        let names = AppIconOption.allCases.compactMap(\.alternateIconName)
        #expect(names.count == AppIconOption.allCases.count - 1)
        #expect(Set(names).count == names.count)
    }

    @Test("Вариант по имени от системы; неизвестное имя — классическая", arguments: AppIconOption.allCases)
    func roundTrip(option: AppIconOption) {
        #expect(AppIconOption(alternateIconName: option.alternateIconName) == option)
        #expect(AppIconOption(alternateIconName: "AppIcon-Unknown") == .classic)
    }

    @Test("Все альтернативные наборы есть в Info.plist (ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES)")
    func registeredInBundle() throws {
        let icons = try #require(Bundle.main.object(forInfoDictionaryKey: "CFBundleIcons") as? [String: Any])
        let alternates = try #require(icons["CFBundleAlternateIcons"] as? [String: Any])
        let names = Set(AppIconOption.allCases.compactMap(\.alternateIconName))
        #expect(Set(alternates.keys) == names)
    }
}

@MainActor
@Suite("Выбор иконки приложения")
struct AppIconSettingsTests {
    @Test("Текущая иконка — от системы")
    func refreshReadsSystem() {
        let settings = AppIconSettings(switcher: FakeAppIconSwitcher(current: "AppIcon-Beads"))

        settings.refresh()

        #expect(settings.current == .beads)
    }

    @Test("Выбор меняет иконку в системе")
    func selectSwitches() async {
        let switcher = FakeAppIconSwitcher()
        let settings = AppIconSettings(switcher: switcher)

        await settings.select(.dawn)
        #expect(settings.current == .dawn)

        await settings.select(.classic)
        #expect(settings.current == .classic)
        #expect(switcher.requested == ["AppIcon-Dawn", nil])
    }

    @Test("Отказ системы: выбор прежний, алерт")
    func failureKeepsSelection() async {
        let switcher = FakeAppIconSwitcher()
        switcher.fails = true
        let settings = AppIconSettings(switcher: switcher)

        await settings.select(.niche)

        #expect(settings.current == .classic)
        #expect(settings.failedToChange)
    }

    @Test("Пока иконка меняется, новые выборы игнорируются")
    func ignoresSelectionWhileChanging() async {
        let switcher = FakeAppIconSwitcher()
        switcher.holds = true
        let settings = AppIconSettings(switcher: switcher)

        let first = Task { await settings.select(.dawn) }
        while !settings.isChanging { await Task.yield() }
        await settings.select(.beads)
        switcher.release()
        await first.value

        #expect(switcher.requested == ["AppIcon-Dawn"])
        #expect(settings.current == .dawn)
        #expect(!settings.isChanging)
    }

    @Test("Повторный выбор текущей систему не вызывает")
    func selectingCurrentIsNoOp() async {
        let switcher = FakeAppIconSwitcher(current: "AppIcon-Niche")
        let settings = AppIconSettings(switcher: switcher)
        settings.refresh()

        await settings.select(.niche)

        #expect(switcher.requested.isEmpty)
    }
}
