import Foundation
import Testing
@testable import Inabah

@MainActor
@Suite("Избранное")
struct HadithFavoritesTests {
    let storage: IsolatedDefaults
    var defaults: UserDefaults { storage.defaults }

    let nawawi = HadithID(collection: .nawawi, number: 7)
    let qudsi = HadithID(collection: .qudsi, number: 1)

    init() throws {
        storage = try IsolatedDefaults("HadithFavoritesTests")
    }

    @Test("Закладка ставится и снимается, сохраняется между запусками")
    func toggleAndRestore() {
        let favorites = HadithFavorites(defaults: defaults)

        favorites.toggle(nawawi)
        favorites.toggle(qudsi)
        #expect(favorites.isFavorite(nawawi))
        #expect(defaults.string(forKey: "h_fav_nawawi_7") == "1")

        favorites.toggle(qudsi)
        #expect(!favorites.isFavorite(qudsi))
        #expect(defaults.object(forKey: "h_fav_qudsi_1") == nil)

        let restored = HadithFavorites(defaults: defaults)
        #expect(restored.numbers == [.nawawi: [7]])
        #expect(!restored.isEmpty)
    }

    @Test("Разбор пропускает чужое значение, номер 0 и неизвестный сборник")
    func strayKeysIgnored() {
        defaults.set("0", forKey: "h_fav_nawawi_2")
        defaults.set("1", forKey: "h_fav_nawawi_0")
        defaults.set("1", forKey: "h_fav_bukhari_3")

        let favorites = HadithFavorites(defaults: defaults)

        #expect(favorites.isEmpty)
    }

    @Test("Очистка удаляет все закладки, включая неизвестные, и не трогает отметки")
    func clearRemovesAllFavorites() {
        defaults.set("1", forKey: "h_fav_bukhari_3")
        defaults.set("1", forKey: "h_read_nawawi_7")
        let favorites = HadithFavorites(defaults: defaults)
        favorites.toggle(nawawi)

        favorites.clear()

        #expect(favorites.isEmpty)
        #expect(defaults.object(forKey: "h_fav_nawawi_7") == nil)
        #expect(defaults.object(forKey: "h_fav_bukhari_3") == nil)
        #expect(defaults.string(forKey: "h_read_nawawi_7") == "1")
    }

    @Test("Сброс отметок сборника не трогает избранное")
    func progressResetKeepsFavorites() {
        let favorites = HadithFavorites(defaults: defaults)
        favorites.toggle(nawawi)
        let progress = HadithProgress(defaults: defaults)
        progress.toggleRead(nawawi)

        progress.reset(.nawawi)

        #expect(HadithFavorites(defaults: defaults).isFavorite(nawawi))
    }
}

@Suite("Группы «Избранного»")
struct FavoritesGroupTests {
    private func hadiths(_ collection: HadithCollection, _ numbers: ClosedRange<Int>) -> [Hadith] {
        numbers.map { Hadith(id: HadithID(collection: collection, number: $0), arabic: "ا", translation: nil) }
    }

    @Test("Порядок главной, хадисы по номеру, несуществующие номера и пустые сборники скрыты")
    func grouping() {
        let groups = FavoritesGroup.make(
            order: [.ajurri, .nawawi, .qudsi],
            favorites: [.nawawi: [9, 2, 99], .qudsi: [77], .ajurri: [1]],
            states: [
                .nawawi: .loaded(hadiths(.nawawi, 1...50)),
                .qudsi: .loaded(hadiths(.qudsi, 1...42)),
                .ajurri: .loaded(hadiths(.ajurri, 1...40)),
            ]
        )

        #expect(groups.map(\.collection) == [.ajurri, .nawawi])
        #expect(groups[1].content == .hadiths(hadiths(.nawawi, 2...2) + hadiths(.nawawi, 9...9)))
    }

    @Test("Сборник без закладок не грузится и не показывается; незагруженный — индикатор, ошибка — ошибка")
    func loadingStates() {
        let groups = FavoritesGroup.make(
            order: [.nawawi, .qudsi, .ajurri],
            favorites: [.nawawi: [1], .qudsi: [1]],
            states: [.qudsi: .failed(.unknown("сбой"))]
        )

        #expect(groups == [
            FavoritesGroup(collection: .nawawi, content: .loading),
            FavoritesGroup(collection: .qudsi, content: .failed),
        ])
    }
}
