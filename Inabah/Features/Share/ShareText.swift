import Foundation

/// Текст «Поделиться» зикром и хадисом: всё, что на экране, блоками через пустую строку,
/// в порядке экрана; последним блоком — ссылка на приложение в App Store.
///
/// Только текст (решение пользователя 2026-10-06): «Скопировать» — пункт системного окна.
/// Тот же состав на Android — `ShareText.kt` (docs/02-screen-azkar.md, «Поделиться»).
nonisolated enum ShareText {
    static let blockSeparator = "\n\n"

    /// Блоки через пустую строку; отсутствующие и пустые пропускаются, края обрезаются.
    static func blocks(_ blocks: String?...) -> String {
        blocks
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: blockSeparator)
    }
}

/// «Сделать напоминание» — заготовленный текст рассылки, отдельно для утренних и вечерних
/// (решения пользователя 2026-10-06: без звёздочек WhatsApp, «до восхода солнца», слова
/// Абу ад-Дарды с арабским текстом и источником). Религиозные формулировки — пользователя.
/// Тот же текст на Android — `AzkarReminder.kt`.
extension AzkarSection {
    /// Заголовок, призыв, слова Абу ад-Дарды (четыре строки подряд), черта и лучшее время,
    /// ссылка на приложение.
    var reminderText: String {
        let quote = [
            String(localized: "share.text.reminder.quote.intro"),
            Self.abuAdDardaArabic,
            String(localized: "share.text.reminder.quote.translation"),
            String(localized: "share.text.reminder.quote.source"),
        ].joined(separator: "\n")
        return ShareText.blocks(
            String(localized: reminderTitle),
            String(localized: reminderCall),
            quote,
            Self.reminderSeparator + "\n" + String(localized: reminderBestTime),
            AboutLinks.appStore.absoluteString
        )
    }

    /// Слова Абу ад-Дарды по-арабски (Ахмад в «аз-Зухд», 726) — текст пользователя, не переводится.
    static let abuAdDardaArabic = "إِنَّ الَّذِينَ أَلْسِنَتُهُمْ رَطْبَةٌ بِذِكْرِ اللَّهِ يَدْخُلُ الْجَنَّةَ وَهُوَ يَضْحَكُ"

    /// Черта перед лучшим временем чтения — как в рассылке.
    static let reminderSeparator = "______________________"

    private var reminderTitle: LocalizedStringResource {
        switch self {
        case .morning: "share.text.reminder.morning.title"
        case .evening: "share.text.reminder.evening.title"
        }
    }

    private var reminderCall: LocalizedStringResource {
        switch self {
        case .morning: "share.text.reminder.morning.call"
        case .evening: "share.text.reminder.evening.call"
        }
    }

    private var reminderBestTime: LocalizedStringResource {
        switch self {
        case .morning: "share.text.reminder.morning.time"
        case .evening: "share.text.reminder.evening.time"
        }
    }
}

// Названия раздела и сборника (`title`) — в расширениях оформления на главном акторе,
// поэтому и тексты ниже — на нём; строит их вьюха.
extension Zikr {
    /// Раздел, арабский, «N раз», транскрипция, перевод, источник, ссылка на приложение.
    var shareText: String {
        ShareText.blocks(
            String(localized: section.title),
            arabic,
            String(localized: "zikr.repetitions \(repetitions)"),
            translation?.transliteration,
            translation?.text,
            translation?.source,
            AboutLinks.appStore.absoluteString
        )
    }
}

extension Hadith {
    /// «Сборник · Хадис N», арабский одним абзацем, «Передал: …», перевод, «Приводится: …»,
    /// ссылка на приложение.
    var shareText: String {
        ShareText.blocks(
            String(localized: "share.hadith.header \(String(localized: collection.title)) \(number)"),
            arabicDisplayText,
            translation?.narrator.map { String(localized: "hadith.detail.narrator \($0)") },
            translation?.text,
            translation?.source.map { String(localized: "hadith.detail.source \($0)") },
            AboutLinks.appStore.absoluteString
        )
    }
}
