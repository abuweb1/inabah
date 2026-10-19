import Foundation
import Observation

/// Курс «Учимся читать» (`reading.json`). Прогресс - отдельно, в `ReadingProgress`: курс неизменен, а
/// отметки сохраняются между запусками.
@Observable
final class ReadingStore {
    @ObservationIgnored private let repository: any ContentRepository
    private(set) var course: Loadable<ReadingCourse> = .idle

    init(repository: any ContentRepository) {
        self.repository = repository
    }

    /// Загружает курс, если он ещё не загружен и не загружается. Повторный вызов после ошибки - повторная
    /// попытка (экран вызывает при каждом показе, как Android).
    func load() async {
        guard !course.isLoadingOrLoaded else { return }
        course = .loading
        do {
            course = .loaded(try await repository.readingCourse())
        } catch let error as ContentError {
            course = .failed(error)
        } catch {
            course = .failed(.unknown(String(describing: error)))
        }
    }
}
