import Foundation
import Observation

/// Азкары и прогресс их чтения за текущий запуск приложения.
///
/// Прогресс намеренно не сохраняется — азкары читают каждый день заново (как в прототипе).
/// Живёт на уровне приложения, поэтому счёт не теряется при переходе между экранами.
@Observable
final class AzkarStore {
    @ObservationIgnored private let repository: any ContentRepository
    private(set) var sections: [AzkarSection: Loadable<[ZikrSession]>] = [:]

    init(repository: any ContentRepository) {
        self.repository = repository
    }

    func state(of section: AzkarSection) -> Loadable<[ZikrSession]> {
        sections[section] ?? .idle
    }

    func sessions(in section: AzkarSection) -> [ZikrSession] {
        state(of: section).value ?? []
    }

    /// Загружает раздел, если он ещё не загружен и не загружается. Повторный вызов после ошибки — повторная попытка.
    func load(_ section: AzkarSection) async {
        guard !state(of: section).isLoadingOrLoaded else { return }
        sections[section] = .loading
        do {
            let azkar = try await repository.azkar(in: section)
            sections[section] = .loaded(azkar.map(ZikrSession.init))
        } catch let error as ContentError {
            sections[section] = .failed(error)
        } catch {
            sections[section] = .failed(.unknown(String(describing: error)))
        }
    }

    func loadAll() async {
        for section in AzkarSection.allCases {
            await load(section)
        }
    }

    func progress(of section: AzkarSection) -> SectionProgress {
        let sessions = sessions(in: section)
        return SectionProgress(completed: sessions.count(where: \.isCompleted), total: sessions.count)
    }

    // MARK: - Оверлей завершения

    /// Разделы, для которых «Машаа Аллах!» уже показан за это прохождение. Читается только
    /// из задач, не из `body`, — наблюдение не нужно.
    @ObservationIgnored private var acknowledgedCompletions: Set<AzkarSection> = []

    /// Показать оверлей завершения — один раз за прохождение раздела, а не при каждом открытии.
    func shouldPresentCompletion(of section: AzkarSection) -> Bool {
        progress(of: section).isFinished && !acknowledgedCompletions.contains(section)
    }

    func acknowledgeCompletion(of section: AzkarSection) {
        acknowledgedCompletions.insert(section)
    }

    /// Раздел снова не выполнен (сбросили зикр) — следующее завершение покажет оверлей снова.
    func resetCompletionAcknowledgement(of section: AzkarSection) {
        acknowledgedCompletions.remove(section)
    }
}

nonisolated struct SectionProgress: Hashable, Sendable {
    let completed: Int
    let total: Int

    var fraction: Double { total == 0 ? 0 : Double(completed) / Double(total) }
    var isFinished: Bool { total > 0 && completed == total }
}
