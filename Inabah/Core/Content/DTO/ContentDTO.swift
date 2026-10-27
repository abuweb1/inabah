import Foundation

// Формат файлов `data/*.json` (описан в docs/06-data-structure.md).
// DTO повторяют JSON один в один; в доменные модели они превращаются в `ContentMapping`.

nonisolated struct AzkarFileDTO: Decodable, Sendable {
    let morning: [ZikrDTO]
    let evening: [ZikrDTO]

    func items(in section: AzkarSection) -> [ZikrDTO] {
        switch section {
        case .morning: morning
        case .evening: evening
        }
    }
}

nonisolated struct ZikrDTO: Decodable, Sendable {
    let id: Int
    let arabic: String
    let max: Int
    /// Имя аудиофайла в Bundle; поле необязательное — сейчас записей нет (с 2026-10-06).
    let audio: String?
    /// Сура или аят Корана (текст QPC Hafs); поле необязательное, нет — `false`.
    let quran: Bool?
    /// Не входит в набор по умолчанию («Состав азкаров»); поле необязательное, нет — `false`.
    let optional: Bool?
    let translations: [String: ZikrTranslationDTO]
}

nonisolated struct ZikrTranslationDTO: Decodable, Sendable {
    let text: String
    let translit: String?
    let source: String?
    /// Передатчик (показывается как есть, без подписи); поле необязательное — пока ни у одного зикра (2026-10-10).
    let rawi: String?
}

nonisolated struct HadithDTO: Decodable, Sendable {
    let id: Int
    let arabic: String
    let translations: [String: HadithTranslationDTO]
}

nonisolated struct HadithTranslationDTO: Decodable, Sendable {
    let rawi: String?
    let text: String
    let source: String?
    /// Сноски к словам перевода; нет поля и `[]` — сносок нет.
    let notes: [HadithNoteDTO]?
}

nonisolated struct HadithNoteDTO: Decodable, Sendable {
    let term: String?
    let text: String?
}

/// `data/collections.json` — сведения для «О сборнике»; ключ файла — `HadithCollection.rawValue`.
nonisolated struct CollectionInfoDTO: Decodable, Sendable {
    let translations: [String: CollectionTranslationDTO]
}

nonisolated struct CollectionTranslationDTO: Decodable, Sendable {
    /// Составители; заготовка `{ "name": "" }` (своя подборка къудси) не показывается.
    let authors: [CollectionAuthorDTO]?
    /// Абзацы «О книге».
    let about: [String]?
}

nonisolated struct CollectionAuthorDTO: Decodable, Sendable {
    let name: String?
    let years: String?
}

/// `data/reminders.json` — тексты напоминаний, общие с Android.
nonisolated struct RemindersFileDTO: Decodable, Sendable {
    let translations: [String: ReminderTranslationDTO]
}

nonisolated struct ReminderTranslationDTO: Decodable, Sendable {
    /// Ключи — `AzkarSection.rawValue`.
    let azkarStart: [String: ReminderTextDTO]?
    let azkarUnfinished: [String: ReminderTextDTO]?
    /// «Серия под угрозой» (2026-10-11): подстановка `{streak}` — «12 дней подряд».
    let streakAtRisk: [String: ReminderTextDTO]?
    let hadithOfDay: ReminderTextDTO?
    /// «Пора на урок» (2026-10-20): подстановка `{step}` — «Шаг 7 · Сукун».
    let readingLesson: ReminderTextDTO?
}

nonisolated struct ReminderTextDTO: Decodable, Sendable {
    let title: String?
    let body: String?
}

/// `data/versions.json` — история версий, общая с Android; новые версии сверху.
nonisolated struct VersionsFileDTO: Decodable, Sendable {
    let versions: [VersionDTO]
}

nonisolated struct VersionDTO: Decodable, Sendable {
    let version: String?
    /// `yyyy-MM-dd`.
    let date: String?
    let translations: [String: VersionTranslationDTO]?
}

nonisolated struct VersionTranslationDTO: Decodable, Sendable {
    /// Общие пункты обеих платформ.
    let items: [String]?
    /// Пункты только для iOS (`android` — для Android, здесь не читается).
    let ios: [String]?
}
