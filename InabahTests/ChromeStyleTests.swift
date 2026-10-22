import Testing
@testable import Inabah

@Suite("Стекло таб-бара и шапок")
struct ChromeStyleTests {
    @Test("Системное стекло — только на iOS 26–27", arguments: [
        (18, ChromeStyle.custom),
        (25, .custom),
        (26, .system),
        (27, .system),
        (28, .custom),
    ])
    func resolvesByMajorVersion(osMajor: Int, expected: ChromeStyle) {
        #expect(ChromeStyle.resolve(osMajor: osMajor) == expected)
    }

    @MainActor
    @Test("Вкладки своей панели — все и по порядку системной")
    func orderedTabs() {
        #expect(AppTab.ordered == [.azkar, .hadith, .makharij, .settings])
    }
}
