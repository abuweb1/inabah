import SwiftUI

/// Главная раздела «Махрадж» - места произношения арабских букв: бренд, аят аль-Муззаммиль 73:4 и
/// карточка курса «Учимся читать» (Android `MakharijHomeScreen.kt`).
struct MakharijHomeView: View {
    @Environment(ReadingStore.self) private var store
    @Environment(\.theme) private var theme

    private static let brand = SectionBrand(
        arabicName: "مخارج",
        arabicNameHasDescender: true,
        tagline: "makharij.home.brand.tagline"
    )

    private static let verse = FeaturedVerse(
        arabic: "وَرَتِّلِ الْقُرْآنَ تَرْتِيلًا",
        translation: "makharij.home.verse.translation",
        reference: "makharij.home.verse.reference"
    )

    var body: some View {
        SectionHomeLayout(
            background: theme.gradients.makharijBackground,
            brand: Self.brand,
            verse: Self.verse
        ) {
            NavigationLink(value: MakharijRoute.steps) {
                ReadingCourseCard()
            }
            .buttonStyle(PressScaleButtonStyle())
        }
        .audioPlayerInset()
        .toolbarVisibility(.hidden, for: .navigationBar)
        .task { await store.load() }
    }
}

/// Карточка «Учимся читать»: текущий шаг и кольцо пройденного (итог не считается). До загрузки курса и
/// при ошибке - без подписи и кольца: экран ошибки - в списке шагов.
private struct ReadingCourseCard: View {
    @Environment(ReadingStore.self) private var store
    @Environment(ReadingProgress.self) private var progress
    @Environment(\.theme) private var theme

    var body: some View {
        let colors = ReadingColors(palette: theme.palette)
        let course = store.course.value
        let completed = course.map {
            ReadingLogic.completedSteps(course: $0, lessonsDone: progress.lessonsDone, wordCounts: progress.wordCounts)
        } ?? []
        SectionNavCard(
            title: "makharij.reading.title",
            meta: course.map { summary(ReadingLogic.homeSummary(course: $0, completed: completed)) },
            iconAsset: "tabIconMakharij",
            iconColor: theme.palette.gold,
            gradient: colors.card,
            shadow: .navCard(theme.palette.shadow, strength: .strong),
            ring: course.map {
                NavCardRing(
                    fraction: ReadingLogic.completedFraction(course: $0, completed: completed),
                    style: NavCardRingStyle(track: theme.palette.track, fill: theme.palette.gold, text: theme.palette.onAccent)
                )
            }
        )
    }

    private func summary(_ summary: ReadingHomeSummary) -> LocalizedStringResource {
        switch summary {
        case .final(let title): "makharij.final.title \(title)"
        case .step(let number, let total, let title): "makharij.reading.card.step \(number) \(total) \(title)"
        case .allDone: "makharij.reading.allDone"
        case .progress(let done, let total): "makharij.reading.card.progress \(done) \(total)"
        }
    }
}

#Preview {
    NavigationStack {
        MakharijHomeView()
    }
    .appEnvironment(.preview)
}
