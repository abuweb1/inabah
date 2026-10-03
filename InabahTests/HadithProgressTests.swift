import Foundation
import Testing
@testable import Inabah

@MainActor
@Suite("Отметки хадисов")
struct HadithProgressTests {
    /// Отдельный suite на каждый тест — тесты идут параллельно и не делят UserDefaults.
    private let defaults: UserDefaults
    private let first = HadithID(collection: .nawawi, number: 1)
    private let second = HadithID(collection: .nawawi, number: 2)

    init() throws {
        defaults = try #require(UserDefaults(suiteName: "HadithProgressTests.\(UUID().uuidString)"))
    }

    @Test("Без отметок — статус «нет»")
    func defaultStatus() {
        #expect(HadithProgress(defaults: defaults).status(of: first) == .none)
    }

    @Test("«Прочитан» включается и выключается")
    func toggleRead() {
        let progress = HadithProgress(defaults: defaults)

        progress.toggleRead(first)
        #expect(progress.status(of: first) == .read)

        progress.toggleRead(first)
        #expect(progress.status(of: first) == .none)
    }

    @Test("«Выучен» ставит и «прочитан»")
    func memorizedImpliesRead() {
        let progress = HadithProgress(defaults: defaults)

        progress.toggleMemorized(first)

        #expect(progress.status(of: first) == .memorized)
        #expect(progress.status(of: first).isRead)
    }

    @Test("Снятие «выучен» оставляет «прочитан»")
    func unmemorizeKeepsRead() {
        let progress = HadithProgress(defaults: defaults)
        progress.toggleMemorized(first)

        progress.toggleMemorized(first)

        #expect(progress.status(of: first) == .read)
    }

    @Test("Снятие «прочитан» снимает и «выучен»")
    func unreadClearsMemorized() {
        let progress = HadithProgress(defaults: defaults)
        progress.toggleMemorized(first)

        progress.toggleRead(first)

        #expect(progress.status(of: first) == .none)
    }

    @Test("Отметки сохраняются между запусками в ключах прототипа")
    func persistsWithPrototypeKeys() {
        let progress = HadithProgress(defaults: defaults)
        progress.toggleRead(first)
        progress.toggleMemorized(second)

        #expect(defaults.string(forKey: "h_read_nawawi_1") == "1")
        #expect(defaults.string(forKey: "h_mem_nawawi_1") == nil)
        #expect(defaults.string(forKey: "h_read_nawawi_2") == "1")
        #expect(defaults.string(forKey: "h_mem_nawawi_2") == "1")

        let restored = HadithProgress(defaults: defaults)
        #expect(restored.status(of: first) == .read)
        #expect(restored.status(of: second) == .memorized)
    }

    @Test("Снятая отметка удаляется из хранилища")
    func clearedStatusRemovesKeys() {
        let progress = HadithProgress(defaults: defaults)
        progress.toggleMemorized(first)

        progress.toggleRead(first)

        #expect(defaults.object(forKey: "h_read_nawawi_1") == nil)
        #expect(defaults.object(forKey: "h_mem_nawawi_1") == nil)
    }

    @Test("Прогресс сборника: выученные входят в прочитанные, другие сборники не считаются")
    func collectionProgress() {
        let progress = HadithProgress(defaults: defaults)
        progress.toggleRead(first)
        progress.toggleMemorized(second)
        progress.toggleRead(HadithID(collection: .qudsi, number: 1))

        let nawawi = progress.progress(in: .nawawi, total: 50)

        #expect(nawawi.read == 2)
        #expect(nawawi.memorized == 1)
        #expect(nawawi.readFraction == 0.04)
    }
}
