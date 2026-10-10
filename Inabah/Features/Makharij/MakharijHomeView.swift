import SwiftUI

/// Главная раздела «Махрадж» — места произношения арабских букв. Пока раздел в разработке:
/// бренд, аят аль-Муззаммиль 73:4 и карточка «Скоро».
struct MakharijHomeView: View {
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
            // Заглушка вместо карточек раздела: «Скоро» и что здесь появится.
            NoticeCard(symbol: "hourglass", title: "makharij.soon.title", message: "makharij.soon.message")
        }
        .audioPlayerInset()
        .toolbarVisibility(.hidden, for: .navigationBar)
    }
}

#Preview {
    NavigationStack {
        MakharijHomeView()
    }
    .appEnvironment(.preview)
}
