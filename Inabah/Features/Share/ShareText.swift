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
/// Абу ад-Дарды с арабским текстом и источником; 2026-10-11: «⚠️ … ⚠️», «✅» и разделители из точек).
/// Религиозные формулировки — пользователя. Тот же текст на Android — `AzkarReminder.kt`.
extension AzkarSection {
    /// Заголовок, разделитель и призыв с разделителем — подряд; слова Абу ад-Дарды (четыре строки);
    /// разделитель и лучшее время — подряд; ссылка на приложение. Между частями — пустая строка
    /// (`docs/02-screen-azkar.md`, «Новый вид»).
    var reminderText: String {
        let header = [
            String(localized: reminderTitle),
            Self.reminderSeparator,
            String(localized: reminderCall),
            Self.reminderSeparator,
        ].joined(separator: "\n")
        let quote = [
            String(localized: "share.text.reminder.quote.intro"),
            Self.abuAdDardaArabic,
            String(localized: "share.text.reminder.quote.translation"),
            String(localized: "share.text.reminder.quote.source"),
        ].joined(separator: "\n")
        return ShareText.blocks(
            header,
            quote,
            Self.reminderSeparator + "\n" + String(localized: reminderBestTime),
            AboutLinks.appStore.absoluteString
        )
    }

    /// Слова Абу ад-Дарды по-арабски (Ахмад в «аз-Зухд», 726) — текст пользователя, не переводится.
    static let abuAdDardaArabic = "إِنَّ الَّذِينَ أَلْسِنَتُهُمْ رَطْبَةٌ بِذِكْرِ اللَّهِ يَدْخُلُ الْجَنَّةَ وَهُوَ يَضْحَكُ"

    /// Разделитель — 30 точек «•»: черта из «_» в WhatsApp — разметка курсива и ломается.
    static let reminderSeparator = String(repeating: "•", count: 30)

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
    /// Раздел, арабский, «N раз», передатчик (без подписи), транскрипция, перевод, источник,
    /// ссылка на приложение.
    var shareText: String {
        ShareText.blocks(
            String(localized: section.title),
            arabic,
            String(localized: "zikr.repetitions \(repetitions)"),
            translation?.narrator,
            translation?.transliteration,
            translation?.text,
            translation?.source,
            AboutLinks.appStore.absoluteString
        )
    }
}

extension Hadith {
    /// «Сборник · Хадис N», арабский одним абзацем, вступление («Передаётся от …», без подписи),
    /// перевод, «Приводится: …», сноски, ссылка на приложение.
    var shareText: String {
        ShareText.blocks(
            String(localized: "share.hadith.header \(String(localized: collection.title)) \(number)"),
            arabicDisplayText,
            translation?.narrator,
            translation?.text,
            translation?.source.map { String(localized: "hadith.detail.source \($0)") },
            notesBlock,
            AboutLinks.appStore.absoluteString
        )
    }

    /// «Комментарии учёных:» (на экране — «Сноски»), затем каждая сноска двумя строками — слово
    /// (без кавычек), пояснение; между сносками пустая строка, иначе текст сливается
    /// (решения пользователя 2026-10-11). Сносок нет — `nil`.
    private var notesBlock: String? {
        guard let notes = translation?.notes, !notes.isEmpty else { return nil }
        let entries = notes.map { "\($0.term)\n\($0.text)" }.joined(separator: ShareText.blockSeparator)
        return String(localized: "share.text.hadith.notes") + "\n" + entries
    }
}
