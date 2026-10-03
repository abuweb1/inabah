import Foundation

/// Небольшой набор контента для превью — без обращения к Bundle.
nonisolated extension InMemoryContentRepository {
    static let preview = InMemoryContentRepository(azkar: [
        .morning: [
            Zikr(
                id: ZikrID(section: .morning, number: 1),
                arabic: "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَٰهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ",
                repetitions: 1,
                audioFileName: "morning_01.mp3",
                translation: ZikrTranslation(
                    language: .base,
                    text: "О Аллах, Ты — Господь мой, и нет бога, кроме Тебя. Ты создал меня, а я — Твой раб.",
                    transliteration: "Аллахумма, Анта Рабби, ля иляха илля Анта, халяктани ва ана абдука",
                    source: "(Бухари)"
                )
            ),
            Zikr(
                id: ZikrID(section: .morning, number: 2),
                arabic: "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
                repetitions: 3,
                audioFileName: "morning_02.mp3",
                translation: ZikrTranslation(
                    language: .base,
                    text: "Пречист Аллах и хвала Ему.",
                    transliteration: "Субхана-Ллахи ва би-хамдихи",
                    source: nil
                )
            ),
        ],
    ])
}
