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

    @Test("Хадис открывается поверх списка своего сборника")
    func openHadithDetail() {
        let router = AppRouter()
        let id = HadithID(collection: .qudsi, number: 5)

        router.open(HadithRoute.detail(id))

        #expect(router.selectedTab == .hadith)
        #expect(router.hadithPath == [.list(.qudsi), .detail(id)])
    }

    @Test("Повторный выбор вкладки «Хадисы» возвращает к главной хадисов")
    func reselectHadithPopsToRoot() {
        let router = AppRouter()
        router.open(HadithRoute.list(.nawawi))

        router.select(.hadith)

        #expect(router.hadithPath.isEmpty)
    }
}
