import Foundation

nonisolated extension Zikr {
    init(dto: ZikrDTO, section: AzkarSection, languages: ContentLanguagePriority) {
        self.init(
            id: ZikrID(section: section, number: dto.id),
            arabic: dto.arabic,
            repetitions: Swift.max(dto.max, 1),
            audioFileName: dto.audio,
            translation: languages.select(from: dto.translations.keyedByLanguage).map { language, t in
                ZikrTranslation(
                    language: language,
                    text: t.text,
                    transliteration: t.translit.nonEmpty,
                    source: t.source.nonEmpty,
                    narrator: t.rawi.nonEmpty
                )
            },
            isQuran: dto.quran ?? false,
            isOptional: dto.optional ?? false
        )
    }
}

nonisolated extension Hadith {
    init(dto: HadithDTO, collection: HadithCollection, languages: ContentLanguagePriority) {
        self.init(
            id: HadithID(collection: collection, number: dto.id),
            arabic: dto.arabic,
            translation: languages.select(from: dto.translations.keyedByLanguage).map { language, t in
                HadithTranslation(
                    language: language,
                    narrator: t.rawi.nonEmpty,
                    text: t.text,
                    source: t.source.nonEmpty,
                    // Сноска без слова или без пояснения не показывается (как на Android).
                    notes: (t.notes ?? []).compactMap { note in
                        guard let term = note.term.nonEmpty, let text = note.text.nonEmpty else { return nil }
                        return HadithNote(term: term, text: text)
                    }
                )
            }
        )
    }
}

nonisolated extension CollectionInfo {
    /// Составитель с пустым именем — заготовка, не показывается; пустые годы и абзацы отбрасываются.
    /// Перевода на доступных языках нет — `.empty`.
    init(dto: CollectionInfoDTO, languages: ContentLanguagePriority) {
        guard let selected = languages.select(from: dto.translations.keyedByLanguage) else {
            self = .empty
            return
        }
        let t = selected.translation
        self.init(
            authors: (t.authors ?? []).compactMap { author in
                author.name.nonEmpty.map { CollectionAuthor(name: $0, years: author.years.nonEmpty) }
            },
            about: (t.about ?? []).compactMap { Optional($0).nonEmpty },
            language: selected.language
        )
    }
}

nonisolated extension ReminderTexts {
    /// Тексты на выбранном языке; языка нет — `nil` (уведомления не показываются). Текст считается
    /// заданным, если заполнен заголовок или тело.
    init?(dto: RemindersFileDTO, languages: ContentLanguagePriority) {
        guard let t = languages.select(from: dto.translations.keyedByLanguage)?.translation else { return nil }
        func bySection(_ texts: [String: ReminderTextDTO]?) -> [AzkarSection: ReminderText] {
            var result: [AzkarSection: ReminderText] = [:]
            for section in AzkarSection.allCases {
                result[section] = texts?[section.rawValue].flatMap(ReminderText.init(dto:))
            }
            return result
        }
        self.init(
            azkarStart: bySection(t.azkarStart),
            azkarUnfinished: bySection(t.azkarUnfinished),
            hadithOfDay: t.hadithOfDay.flatMap(ReminderText.init(dto:)),
            streakAtRisk: bySection(t.streakAtRisk),
            readingLesson: t.readingLesson.flatMap(ReminderText.init(dto:))
        )
    }
}

nonisolated extension ReleaseNote {
    /// Записи файла в его порядке; запись пропускается (остальные остаются), если версия не `X.Y.Z`,
    /// дата не `yyyy-MM-dd`, нет перевода на доступных языках или не осталось пунктов — как на Android.
    static func notes(from dto: VersionsFileDTO, languages: ContentLanguagePriority) -> [ReleaseNote] {
        dto.versions.compactMap { ReleaseNote(dto: $0, languages: languages) }
    }

    init?(dto: VersionDTO, languages: ContentLanguagePriority) {
        guard let version = AppVersionNumber(dto.version),
              let date = dto.date.flatMap(Self.parseDate),
              let selected = languages.select(from: (dto.translations ?? [:]).keyedByLanguage)
        else { return nil }
        let items = ((selected.translation.items ?? []) + (selected.translation.ios ?? []))
            .compactMap { Optional($0).nonEmpty }
        guard !items.isEmpty else { return nil }
        self.init(version: version, date: date, items: items, language: selected.language)
    }

    /// Строго `yyyy-MM-dd` и существующий день (`2026-02-30` — нет), полночь UTC.
    private static func parseDate(_ text: String) -> Date? {
        let parts = text.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.count == 3, parts[0].count == 4, parts[1].count == 2, parts[2].count == 2,
              let year = Int(parts[0]), let month = Int(parts[1]), let day = Int(parts[2])
        else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .gmt
        let components = DateComponents(year: year, month: month, day: day)
        guard components.isValidDate(in: calendar) else { return nil }
        return calendar.date(from: components)
    }
}

private nonisolated extension ReminderText {
    init?(dto: ReminderTextDTO) {
        guard dto.title.nonEmpty != nil || dto.body.nonEmpty != nil else { return nil }
        self.init(title: dto.title ?? "", body: dto.body ?? "")
    }
}

nonisolated extension Dictionary where Key == String {
    var keyedByLanguage: [ContentLanguage: Value] {
        Dictionary<ContentLanguage, Value>(
            map { (ContentLanguage(rawValue: $0.key), $0.value) },
            uniquingKeysWith: { first, _ in first }
        )
    }
}

nonisolated extension Optional where Wrapped == String {
    /// Пустые строки в данных означают «не заполнено» (например, `source` ждёт уточнения).
    var nonEmpty: String? {
        guard let trimmed = self?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }
}
