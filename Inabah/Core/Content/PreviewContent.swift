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
    ], course: previewCourse)

    /// Курс «Учимся читать» для превью: четыре шага и итог (настоящий курс - в `reading.json`).
    private static let previewCourse: ReadingCourse = {
        let fatha = ReadingMark(id: "fatha", sign: "\u{064E}", name: "фатха", where: "над буквой", sound: "а")
        let ba = ReadingLetter(
            number: 1, letter: "ب", arabicName: "بَاء", joinsNext: true, joinsPrevious: true, syllableBases: [:],
            name: "Ба", nameTransliteration: "баа", sound: "б", syllables: ["fatha": "ба"], audio: [:], note: nil
        )
        let lesson: [ReadingBlock] = [.text(.init(text: "Урок"))]
        func step(_ number: Int, _ title: String, _ subtitle: String, _ example: String, isFinal: Bool = false) -> ReadingStep {
            ReadingStep(
                number: number, markIds: [], example: example, title: title, subtitle: subtitle,
                blocks: lesson, quiz: [], isFinal: isFinal
            )
        }
        return ReadingCourse(marks: [fatha], letters: [ba], steps: [
            step(1, "Буквы", "28 букв и хамза: как называются и звучат", "ا ب ت"),
            step(2, "Огласовки", "Фатхьа, касра и домма", "بَ بِ بُ"),
            step(3, "Формы букв", "Буква в начале, в середине и в конце слова", "بـ ـبـ ـب"),
            step(4, "Сукун", "Буква без гласного", "بْ"),
            step(5, "Читаем зикр", "Утренние и вечерние азкары по словам", "سُبْحَانَ", isFinal: true),
        ])
    }()

    private static let previewAzkar: [AzkarSection: [Zikr]] = [
        .morning: [
            Zikr(
                id: ZikrID(section: .morning, number: 1),
                arabic: "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَٰهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ",
                repetitions: 1,
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
