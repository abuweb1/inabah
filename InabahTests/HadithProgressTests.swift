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

    @Test("Сброс сборника удаляет все его отметки и не трогает другие сборники")
    func resetCollection() {
        let progress = HadithProgress(defaults: defaults)
        progress.toggleRead(first)
        progress.toggleMemorized(second)
        let qudsi = HadithID(collection: .qudsi, number: 1)
        progress.toggleRead(qudsi)

        progress.reset(.nawawi)

        #expect(progress.status(of: first) == .none)
        #expect(progress.status(of: second) == .none)
        #expect(progress.status(of: qudsi) == .read)
        #expect(defaults.object(forKey: "h_mem_nawawi_2") == nil)
        let restored = HadithProgress(defaults: defaults)
        #expect(restored.progress(in: .nawawi, total: 50).read == 0)
        #expect(restored.status(of: qudsi) == .read)
    }

    @Test("Номер 0 (адресация прототипа) и номера за пределами сборника в прогресс не попадают")
    func outOfRangeNumbersIgnored() {
        defaults.set("1", forKey: "h_read_nawawi_0")
        defaults.set("1", forKey: "h_read_nawawi_51")
        defaults.set("1", forKey: "h_read_nawawi_50")

        let progress = HadithProgress(defaults: defaults).progress(in: .nawawi, total: 50)

        #expect(progress.read == 1)
    }

    @Test("«Выучен» без «прочитан» в хранилище читается как «выучен»")
    func memorizedWithoutReadLoads() {
        defaults.set("1", forKey: "h_mem_qudsi_3")

        #expect(HadithProgress(defaults: defaults).status(of: HadithID(collection: .qudsi, number: 3)) == .memorized)
    }

    @Test("Отметки одного сборника не попадают в другой, посторонние значения игнорируются")
    func collectionsAreSeparate() {
        defaults.set("1", forKey: "h_read_ajurri_1")
        defaults.set("0", forKey: "h_read_nawawi_2")

        let progress = HadithProgress(defaults: defaults)

        #expect(progress.status(of: first) == .none)
        #expect(progress.status(of: second) == .none)
        #expect(progress.status(of: HadithID(collection: .ajurri, number: 1)) == .read)
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
