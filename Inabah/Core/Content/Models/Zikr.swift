import Foundation

/// Раздел азкаров. `rawValue` совпадает с ключом в `azkar.json`.
nonisolated enum AzkarSection: String, CaseIterable, Hashable, Codable, Sendable {
    case morning
    case evening
}

/// Глобально уникальный идентификатор зикра: номер (`id` в JSON) уникален только внутри раздела.
nonisolated struct ZikrID: Hashable, Codable, Sendable {
    let section: AzkarSection
    let number: Int
}

/// Зикр с уже выбранным переводом — вьюхи не работают со словарём языков.
nonisolated struct Zikr: Identifiable, Hashable, Sendable {
    let id: ZikrID
    let arabic: String
    /// Сколько раз читать (`max` в JSON), не меньше 1.
    let repetitions: Int
    /// Имя аудиофайла в Bundle, например `morning_01.mp3`; `nil` — записи нет (сейчас ни у одного
    /// зикра, с 2026-10-06; на карточке — «Аудио скоро»).
    var audioFileName: String? = nil
    let translation: ZikrTranslation?
    /// Сура или аят Корана (текст QPC Hafs) — полный текст на карточке шрифтом мусхафа (2026-10-25).
    var isQuran = false
    /// Не входит в набор по умолчанию — добавляется в «Составе азкаров» (2026-10-24).
    var isOptional = false

    var section: AzkarSection { id.section }
    var number: Int { id.number }
}

nonisolated struct ZikrTranslation: Hashable, Sendable {
    let language: ContentLanguage
    let text: String
    let transliteration: String?
    /// Источник («(Бухари)»); `nil`, если в данных не указан.
    let source: String?
    /// Передатчик — показывается как есть, без подписи; `nil`, если в данных не указан.
    var narrator: String? = nil
}
