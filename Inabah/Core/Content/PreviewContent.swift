import Foundation

/// Небольшой набор контента для превью — без обращения к Bundle.
nonisolated extension InMemoryContentRepository {
    static let preview = InMemoryContentRepository(azkar: previewAzkar, hadiths: [
        .nawawi: [
            Hadith(
                id: HadithID(collection: .nawawi, number: 1),
                arabic: "عَنْ أَمِيرِ الْمُؤْمِنِينَ أَبِي حَفْصٍ عُمَرَ بْنِ الْخَطَّابِ رَضِيَ اللَّهُ عَنْهُ\nقَالَ: سَمِعْتُ رَسُولَ اللَّهِ ﷺ يَقُولُ: «إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى»",
                translation: HadithTranslation(
                    language: .base,
                    narrator: "Умар ибн аль-Хаттаб",
                    text: "«Дела оцениваются только по намерениям, и, поистине, каждому человеку достанется лишь то, что он намеревался обрести».",
                    source: "Аль-Бухари (№ 1); Муслим (№ 1907)"
                )
            ),
            Hadith(
                id: HadithID(collection: .nawawi, number: 2),
                arabic: "عَنْ أَبِي هُرَيْرَةَ رَضِيَ اللَّهُ عَنْهُ قَالَ: قَالَ رَسُولُ اللَّهِ ﷺ: «مِنْ حُسْنِ إِسْلَامِ الْمَرْءِ تَرْكُهُ مَا لَا يَعْنِيهِ»",
                translation: HadithTranslation(
                    language: .base,
                    narrator: "Абу Хурайра",
                    text: "От Абу Хурайры: «Признаком хорошего Ислама человека является то, что он оставляет не касающееся его».",
                    source: nil
                )
            ),
        ],
    ])

    private static let previewAzkar: [AzkarSection: [Zikr]] = [
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
    ]
}
