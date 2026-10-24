import Foundation
import Testing
@testable import Inabah

@MainActor
@Suite("Счёт азкаров")
struct AzkarStoreTests {
    /// Отдельный набор на каждый тест: прогресс сохраняется в UserDefaults.
    private let storage: IsolatedDefaults
    private var defaults: UserDefaults { storage.defaults }

    init() throws {
        storage = try IsolatedDefaults("AzkarStoreTests")
    }

    private static func zikr(_ number: Int, repetitions: Int, section: AzkarSection = .morning) -> Zikr {
        Zikr(
            id: ZikrID(section: section, number: number),
            arabic: "سُبْحَانَ اللَّهِ",
            repetitions: repetitions,
            translation: nil
        )
    }

    private func loadedStore() async -> AzkarStore {
        let repository = InMemoryContentRepository(azkar: [
            .morning: [Self.zikr(1, repetitions: 1), Self.zikr(2, repetitions: 3)],
            .evening: [Self.zikr(1, repetitions: 1, section: .evening)],
        ])
        let store = AzkarStore(repository: repository, defaults: defaults)
        await store.loadAll()
        return store
    }

    @Test("Счёт растёт до max и дальше не идёт")
    func incrementStopsAtMax() {
        let session = ZikrSession(zikr: Self.zikr(1, repetitions: 3))

        #expect(session.increment() == .counted)
        #expect(session.increment() == .counted)
        #expect(session.increment() == .completed)
        #expect(session.increment() == .alreadyCompleted)
        #expect(session.count == 3)
        #expect(session.isCompleted)
    }

    @Test("Завершение сворачивает раскрытую карточку, сброс обнуляет счёт")
    func completionAndReset() {
        let session = ZikrSession(zikr: Self.zikr(1, repetitions: 1))
        session.isExpanded = true

        session.increment()
        #expect(!session.isExpanded)

        session.isExpanded = true
        session.reset()
        #expect(session.count == 0)
        #expect(!session.isCompleted)
        #expect(!session.isExpanded)
    }

    @Test("Прогресс раздела и признак завершения")
    func sectionProgress() async throws {
        let store = await loadedStore()
        let sessions = store.sessions(in: .morning)
        try #require(sessions.count == 2)

        #expect(store.progress(of: .morning) == SectionProgress(completed: 0, total: 2))

        sessions[0].increment()
        #expect(store.progress(of: .morning).completed == 1)
        #expect(!store.progress(of: .morning).isFinished)

        (1...3).forEach { _ in sessions[1].increment() }
        #expect(store.progress(of: .morning).isFinished)
        #expect(store.progress(of: .morning).fraction == 1)

        sessions[0].reset()
        #expect(store.progress(of: .morning).completed == 1)
        store.resetProgress(of: .morning)
        #expect(store.progress(of: .morning) == SectionProgress(completed: 0, total: 2))
    }

    @Test("Разделы считаются независимо")
    func sectionsAreIndependent() async {
        let store = await loadedStore()
        store.sessions(in: .evening).forEach { $0.increment() }

        #expect(store.progress(of: .evening).isFinished)
        #expect(store.progress(of: .morning).completed == 0)
    }

    @Test("Повторная загрузка не сбрасывает прогресс")
    func reloadKeepsProgress() async {
        let store = await loadedStore()
        store.sessions(in: .morning)[0].increment()

        await store.load(.morning)

        #expect(store.progress(of: .morning).completed == 1)
    }

    @Test("Ошибка загрузки — состояние failed, повторная попытка разрешена")
    func loadFailure() async {
        let store = AzkarStore(repository: InMemoryContentRepository(error: .resourceMissing("azkar.json")), defaults: defaults)

        await store.load(.morning)

        guard case .failed(let error) = store.state(of: .morning) else {
            Issue.record("Ожидалось состояние failed, получено \(store.state(of: .morning))")
            return
        }
        #expect(error == .resourceMissing("azkar.json"))
        #expect(!store.state(of: .morning).isLoadingOrLoaded)
    }

    @Test("Пустой раздел не считается завершённым")
    func emptySectionIsNotFinished() {
        #expect(!SectionProgress(completed: 0, total: 0).isFinished)
        #expect(SectionProgress(completed: 0, total: 0).fraction == 0)
    }

    // MARK: - Состав азкаров

    /// Утро 1…3, третий — не в наборе по умолчанию.
    private func storeWithOptional() -> AzkarStore {
        var optional = Self.zikr(3, repetitions: 1)
        optional.isOptional = true
        let repository = InMemoryContentRepository(azkar: [
            .morning: [Self.zikr(1, repetitions: 1), Self.zikr(2, repetitions: 3), optional],
        ])
        return AzkarStore(repository: repository, defaults: defaults)
    }

    @Test("По умолчанию в разделе — зикры без optional; каталог — все")
    func defaultCompositionSkipsOptional() async {
        let store = storeWithOptional()
        await store.load(.morning)

        #expect(store.composition(of: .morning) == [1, 2])
        #expect(store.catalog(of: .morning).count == 3)
        #expect(store.progress(of: .morning).total == 2)
    }

    @Test("Новый набор: счёт оставшихся сохраняется, добавленный — с нуля, убранный уходит и после перезапуска")
    func applyCompositionKeepsCounts() async {
        let store = storeWithOptional()
        await store.load(.morning)
        store.sessions(in: .morning)[0].increment()
        store.sessions(in: .morning)[1].increment()

        store.applyComposition([2, 3], to: .morning)

        #expect(store.composition(of: .morning) == [2, 3])
        #expect(store.sessions(in: .morning).map(\.count) == [1, 0])
        #expect(store.progress(of: .morning) == SectionProgress(completed: 0, total: 2))

        let restarted = storeWithOptional()
        await restarted.load(.morning)
        #expect(restarted.composition(of: .morning) == [2, 3])
        #expect(restarted.sessions(in: .morning).map(\.count) == [1, 0])
    }

    @Test("Убрали невыполненный — раздел выполнен")
    func removingUnfinishedCompletesSection() async {
        let store = storeWithOptional()
        await store.load(.morning)
        store.sessions(in: .morning)[0].increment()

        store.applyComposition([1], to: .morning)

        #expect(store.progress(of: .morning) == SectionProgress(completed: 1, total: 1))
    }
}
