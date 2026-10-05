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
            ComingSoonCard()
        }
        .audioPlayerInset()
        .toolbarVisibility(.hidden, for: .navigationBar)
    }
}

/// Заглушка вместо карточек раздела: значок, «Скоро» и что здесь появится.
private struct ComingSoonCard: View {
    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: Spacing.s) {
            // Размеры — как у карточек главных (на ступень меньше прежних).
            Image(systemName: "hourglass")
                .font(.title3)
                .foregroundStyle(theme.palette.gold)
                .accessibilityHidden(true)
            Text("makharij.soon.title")
                .font(.headline)
                .foregroundStyle(theme.palette.onAccent)
            Text("makharij.soon.message")
                .font(.caption)
                .multilineTextAlignment(.center)
                .foregroundStyle(theme.palette.onAccentSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.xxl)
        .padding(.horizontal, Spacing.xlPlus)
        .surface(theme.palette.subtleFill, cornerRadius: Radius.navCard, border: theme.palette.hairline)
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    NavigationStack {
        MakharijHomeView()
    }
    .appEnvironment(.preview)
}
