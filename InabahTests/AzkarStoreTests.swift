import Foundation
import Testing
@testable import Inabah

@MainActor
@Suite("Счёт азкаров")
struct AzkarStoreTests {
    /// Отдельный suite на каждый тест: прогресс сохраняется в UserDefaults.
    private let defaults: UserDefaults

    init() throws {
        defaults = try #require(UserDefaults(suiteName: "AzkarStoreTests.\(UUID().uuidString)"))
    }

    private static func zikr(_ number: Int, repetitions: Int, section: AzkarSection = .morning) -> Zikr {
        Zikr(
            id: ZikrID(section: section, number: number),
            arabic: "سُبْحَانَ اللَّهِ",
            repetitions: repetitions,
            audioFileName: "\(section.rawValue)_\(number).mp3",
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
}
