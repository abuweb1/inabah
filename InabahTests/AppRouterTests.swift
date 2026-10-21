import Foundation
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

    @Test("Напоминание о серии открывает главную азкаров из любого места")
    func reminderOpensAzkarHome() {
        let router = AppRouter()
        router.open(.list(.evening))
        router.select(.hadith)

        router.open(ReminderTarget.azkarHome)

        #expect(router.selectedTab == .azkar)
        #expect(router.azkarPath.isEmpty)
    }

    @Test("Напоминание азкаров открывает раздел, хадиса дня — хадис поверх списка сборника")
    func reminderOpensSectionAndHadith() {
        let router = AppRouter()
        let id = HadithID(collection: .qudsi, number: 7)

        router.open(ReminderTarget.azkar(.morning))
        #expect(router.selectedTab == .azkar)
        #expect(router.azkarPath == [.list(.morning)])

        router.open(ReminderTarget.hadith(id))
        #expect(router.selectedTab == .hadith)
        #expect(router.hadithPath == [.list(.qudsi), .detail(id)])
    }

    @Test("«Пора на урок» открывает урок шага поверх списка шагов")
    func reminderOpensReadingLesson() {
        let router = AppRouter()
        router.select(.settings)

        router.open(ReminderTarget.readingLesson(4))

        #expect(router.selectedTab == .makharij)
        #expect(router.makharijPath == [.steps, .lesson(4)])
    }

    @Test("«Календарь азкаров» открывается с главной азкаров и переживает кодирование маршрута")
    func openCalendar() throws {
        let router = AppRouter()
        router.select(.settings)

        router.open(.calendar)

        #expect(router.selectedTab == .azkar)
        #expect(router.azkarPath == [.calendar])
        let data = try JSONEncoder().encode(AzkarRoute.calendar)
        #expect(try JSONDecoder().decode(AzkarRoute.self, from: data) == .calendar)
    }

    @Test("Хадис открывается поверх списка своего сборника")
    func openHadithDetail() {
        let router = AppRouter()
        let id = HadithID(collection: .qudsi, number: 5)

        router.open(HadithRoute.detail(id))

        #expect(router.selectedTab == .hadith)
        #expect(router.hadithPath == [.list(.qudsi), .detail(id)])
    }

    @Test("«О сборнике» открывается поверх списка сборника")
    func openCollectionAbout() {
        let router = AppRouter()

        router.open(HadithRoute.about(.ajurri))

        #expect(router.selectedTab == .hadith)
        #expect(router.hadithPath == [.list(.ajurri), .about(.ajurri)])
    }

    @Test("«Избранное» открывается с главной хадисов")
    func openFavorites() {
        let router = AppRouter()

        router.open(HadithRoute.favorites)

        #expect(router.selectedTab == .hadith)
        #expect(router.hadithPath == [.favorites])
    }

    @Test("Повторный выбор вкладки «Настройки» возвращает к списку разделов", arguments: [
        [SettingsRoute.hadith],
        [.hadith, .hadithOrder],
        [.makharij],
        [.appIcon],
        [.palette],
        [.about, .versionHistory],
        [.about, .licenses, .license(.scheherazade)],
    ])
    func reselectSettingsPopsToRoot(path: [SettingsRoute]) {
        let router = AppRouter()
        router.select(.settings)
        router.settingsPath = path

        router.select(.settings)

        #expect(router.settingsPath.isEmpty)
    }

    @Test("Вкладка «Махрадж»: выбор и повторный выбор не трогают пути других вкладок")
    func makharijTabKeepsOtherPaths() {
        let router = AppRouter()
        router.open(.list(.evening))
        router.settingsPath = [.appIcon]

        router.select(.makharij)
        router.select(.makharij)

        #expect(router.selectedTab == .makharij)
        #expect(router.azkarPath == [.list(.evening)])
        #expect(router.settingsPath == [.appIcon])
    }

    @Test("Урок шага открывается поверх списка шагов «Махраджа»")
    func openLesson() {
        let router = AppRouter()

        router.open(MakharijRoute.lesson(3))

        #expect(router.selectedTab == .makharij)
        #expect(router.makharijPath == [.steps, .lesson(3)])
    }

    @Test("Руководство по произношению, открытое напрямую, - поверх списка шагов")
    func openGuide() {
        let router = AppRouter()

        router.open(MakharijRoute.guide(id: "articulation"))

        #expect(router.selectedTab == .makharij)
        #expect(router.makharijPath == [.steps, .guide(id: "articulation")])
    }

    @Test("Экраны урока открываются поверх урока своего шага", arguments: [
        MakharijRoute.alphabet(2),
        .extra(step: 2, topic: nil),
        .extra(step: 2, topic: 1),
        .letterForms(step: 2, letter: "ب"),
        .quiz(2),
        .words(2),
        .zikr(step: 2, zikr: 1),
    ])
    func openLessonScreen(route: MakharijRoute) {
        let router = AppRouter()

        router.open(route)

        #expect(router.selectedTab == .makharij)
        #expect(router.makharijPath == [.steps, .lesson(2), route])
    }

    @Test("Повторный выбор вкладки «Махрадж» возвращает к главной, другие вкладки не задеты")
    func reselectMakharijPopsToRoot() {
        let router = AppRouter()
        router.hadithPath = [.favorites]
        router.open(MakharijRoute.lesson(2))

        router.select(.makharij)

        #expect(router.makharijPath.isEmpty)
        #expect(router.hadithPath == [.favorites])
    }

    @Test("Повторный выбор вкладки «Хадисы» возвращает к главной хадисов")
    func reselectHadithPopsToRoot() {
        let router = AppRouter()
        router.open(HadithRoute.list(.nawawi))

        router.select(.hadith)

        #expect(router.hadithPath.isEmpty)
    }
}
