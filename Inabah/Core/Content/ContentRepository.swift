import Foundation

/// Источник контента приложения. Экраны и сторы зависят только от протокола —
/// в тестах и превью подставляется `InMemoryContentRepository`, позже — загрузка с сервера.
nonisolated protocol ContentRepository: Sendable {
    func azkar(in section: AzkarSection) async throws -> [Zikr]
    func hadiths(in collection: HadithCollection) async throws -> [Hadith]
    /// Сведения для «О сборнике»; сборника нет в файле или нет перевода — `.empty`.
    func collectionInfo(_ collection: HadithCollection) async throws -> CollectionInfo
    /// Тексты напоминаний; подходящего языка нет — `nil`.
    func reminderTexts() async throws -> ReminderTexts?
    /// История версий («Что нового», «История версий»), новые сверху; негодные записи пропущены.
    func releaseNotes() async throws -> [ReleaseNote]
}

nonisolated enum ContentError: Error, Equatable {
    case resourceMissing(String)
    case decodingFailed(resource: String, description: String)
    /// Любая другая ошибка источника контента (например, сети — когда появится).
    case unknown(String)
}

/// Контент из `data/*.json` внутри Bundle.
///
/// Файлы декодируются один раз и кэшируются; работа идёт на исполнителе актора,
/// а не на главном потоке. Декодирование синхронное, поэтому между проверкой кэша
/// и записью в него нет точки приостановки — повторной загрузки одного файла не бывает.
///
/// Правило: внутри `loaded*` не должно быть `await`. Если загрузка станет асинхронной
/// (сервер), кэшировать нужно `Task` загрузки в полёте, а не готовый результат — иначе
/// из-за реентерабельности актора два одновременных вызова загрузят файл дважды.
actor BundleContentRepository: ContentRepository {
    private let bundle: Bundle
    private let languages: ContentLanguagePriority

    private var azkarFile: AzkarFileDTO?
    private var hadithFiles: [HadithCollection: [HadithDTO]] = [:]
    private var collectionsFile: [String: CollectionInfoDTO]?
    private var remindersFile: RemindersFileDTO?
    private var versionsFile: VersionsFileDTO?

    init(bundle: Bundle = .main, languages: ContentLanguagePriority = .system) {
        self.bundle = bundle
        self.languages = languages
    }

    func azkar(in section: AzkarSection) throws -> [Zikr] {
        let file = try loadedAzkarFile()
        return file.items(in: section).map { Zikr(dto: $0, section: section, languages: languages) }
    }

    func hadiths(in collection: HadithCollection) throws -> [Hadith] {
        let items = try loadedHadithFile(collection)
        return items.map { Hadith(dto: $0, collection: collection, languages: languages) }
    }

    func collectionInfo(_ collection: HadithCollection) throws -> CollectionInfo {
        if collectionsFile == nil {
            collectionsFile = try decode([String: CollectionInfoDTO].self, resource: "collections")
        }
        guard let dto = collectionsFile?[collection.rawValue] else { return .empty }
        return CollectionInfo(dto: dto, languages: languages)
    }

    func reminderTexts() throws -> ReminderTexts? {
        let file: RemindersFileDTO
        if let remindersFile {
            file = remindersFile
        } else {
            file = try decode(RemindersFileDTO.self, resource: "reminders")
            remindersFile = file
        }
        return ReminderTexts(dto: file, languages: languages)
    }

    func releaseNotes() throws -> [ReleaseNote] {
        let file: VersionsFileDTO
        if let versionsFile {
            file = versionsFile
        } else {
            file = try decode(VersionsFileDTO.self, resource: "versions")
            versionsFile = file
        }
        return ReleaseNote.notes(from: file, languages: languages)
    }

    private func loadedAzkarFile() throws -> AzkarFileDTO {
        if let azkarFile { return azkarFile }
        let file = try decode(AzkarFileDTO.self, resource: "azkar")
        azkarFile = file
        return file
    }

    private func loadedHadithFile(_ collection: HadithCollection) throws -> [HadithDTO] {
        if let cached = hadithFiles[collection] { return cached }
        let items = try decode([HadithDTO].self, resource: collection.rawValue)
        hadithFiles[collection] = items
        return items
    }

    private func decode<T: Decodable>(_ type: T.Type, resource: String) throws -> T {
        guard let url = bundle.url(forResource: resource, withExtension: "json") else {
            throw ContentError.resourceMissing("\(resource).json")
        }
        do {
            return try JSONDecoder().decode(type, from: Data(contentsOf: url))
        } catch {
            throw ContentError.decodingFailed(resource: "\(resource).json", description: String(describing: error))
        }
    }
}

/// Контент в памяти — для превью и тестов.
nonisolated struct InMemoryContentRepository: ContentRepository {
    var azkar: [AzkarSection: [Zikr]] = [:]
    var hadiths: [HadithCollection: [Hadith]] = [:]
    var infos: [HadithCollection: CollectionInfo] = [:]
    var reminders: ReminderTexts?
    var notes: [ReleaseNote] = []
    var error: ContentError?

    func azkar(in section: AzkarSection) throws -> [Zikr] {
        if let error { throw error }
        return azkar[section] ?? []
    }

    func hadiths(in collection: HadithCollection) throws -> [Hadith] {
        if let error { throw error }
        return hadiths[collection] ?? []
    }

    func collectionInfo(_ collection: HadithCollection) throws -> CollectionInfo {
        if let error { throw error }
        return infos[collection] ?? .empty
    }

    func reminderTexts() throws -> ReminderTexts? {
        if let error { throw error }
        return reminders
    }

    func releaseNotes() throws -> [ReleaseNote] {
        if let error { throw error }
        return notes
    }
}
