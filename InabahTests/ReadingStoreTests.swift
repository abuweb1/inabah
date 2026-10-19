import Foundation
import Testing
@testable import Inabah

@MainActor
@Suite("Курс «Учимся читать»: загрузка")
struct ReadingStoreTests {
    private static let course = ReadingCourse(marks: [], letters: [], steps: [])

    @Test("Курс загружается из репозитория")
    func loads() async {
        let store = ReadingStore(repository: InMemoryContentRepository(course: Self.course))

        await store.load()

        #expect(store.course.value == Self.course)
    }

    @Test("Ошибка репозитория - состояние ошибки, повторная загрузка - новая попытка")
    func failsAndRetries() async {
        let store = ReadingStore(repository: FailingOnceRepository(course: Self.course))

        await store.load()

        guard case .failed(let error) = store.course else {
            Issue.record("ожидалась ошибка, а не \(store.course)")
            return
        }
        #expect(error == .decodingFailed(resource: "reading.json", description: "сбой"))
        await store.load()
        #expect(store.course.value == Self.course, "вторая попытка - курс загружен")
    }

    @Test("Нет курса в репозитории - ошибка «нет файла»")
    func missingCourse() async {
        let store = ReadingStore(repository: InMemoryContentRepository())

        await store.load()

        guard case .failed(.resourceMissing) = store.course else {
            Issue.record("ожидалось «нет файла», а не \(store.course)")
            return
        }
    }
}

/// Курс с первого раза не загружается, со второго - да.
private actor FailingOnceRepository: ContentRepository {
    private let base: InMemoryContentRepository
    private var failed = false

    init(course: ReadingCourse) {
        base = InMemoryContentRepository(course: course)
    }

    func readingCourse() throws -> ReadingCourse {
        guard failed else {
            failed = true
            throw ContentError.decodingFailed(resource: "reading.json", description: "сбой")
        }
        return try base.readingCourse()
    }

    func azkar(in section: AzkarSection) throws -> [Zikr] { try base.azkar(in: section) }
    func hadiths(in collection: HadithCollection) throws -> [Hadith] { try base.hadiths(in: collection) }
    func collectionInfo(_ collection: HadithCollection) throws -> CollectionInfo { try base.collectionInfo(collection) }
    func reminderTexts() throws -> ReminderTexts? { try base.reminderTexts() }
    func releaseNotes() throws -> [ReleaseNote] { try base.releaseNotes() }
}
