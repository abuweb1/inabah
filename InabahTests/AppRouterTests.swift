import Testing
@testable import Inabah

@MainActor
@Suite("Навигация")
struct AppRouterTests {
    @Test("Повторный выбор активной вкладки возвращает к корню")
    func reselectPopsToRoot() {
        let router = AppRouter()
        router.open(.list(.morning))

        router.select(.azkar)

        #expect(router.azkarPath.isEmpty)
    }

    @Test("Выбор другой вкладки не трогает путь")
    func selectOtherTabKeepsPath() {
        let router = AppRouter()
        router.open(.list(.evening))

        router.select(.hadith)

        #expect(router.selectedTab == .hadith)
        #expect(router.azkarPath == [.list(.evening)])
    }

    @Test("open переключает на вкладку азкаров и открывает экран")
    func openRoute() {
        let router = AppRouter()
        router.select(.settings)

        router.open(.list(.morning))

        #expect(router.selectedTab == .azkar)
        #expect(router.azkarPath == [.list(.morning)])
    }
}
