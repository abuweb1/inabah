import SwiftUI

/// Главная раздела «Азкары»: бренд, аят Аз-Зумар 39:54 и переход к утренним/вечерним азкарам.
struct AzkarHomeView: View {
    @Environment(AzkarStore.self) private var store
    @Environment(\.theme) private var theme

    private static let brand = SectionBrand(
        arabicName: "إنابة",
        latinName: "home.brand.latin",
        tagline: "home.brand.tagline"
    )

    private static let verse = FeaturedVerse(
        arabic: "وَأَنِيبُوا إِلَىٰ رَبِّكُمْ وَأَسْلِمُوا لَهُ",
        translation: "home.verse.translation",
        reference: "home.verse.reference"
    )

    var body: some View {
        SectionHomeLayout(
            background: theme.gradients.azkarBackground,
            brand: Self.brand,
            verse: Self.verse
        ) {
            ForEach(AzkarSection.allCases, id: \.self) { section in
                NavigationLink(value: AzkarRoute.list(section)) {
                    SectionNavCard(
                        title: section.title,
                        meta: meta(for: section),
                        symbolName: section.symbolName,
                        iconColor: section.iconColor(in: theme),
                        gradient: section.cardGradient(in: theme),
                        shadow: section.cardShadow(in: theme),
                        ring: ring(for: section)
                    )
                }
                .buttonStyle(PressScaleButtonStyle())
            }
        }
        .audioPlayerInset()
        .toolbarVisibility(.hidden, for: .navigationBar)
    }

    /// Выполнение раздела за этот запуск; до загрузки кольца нет.
    private func ring(for section: AzkarSection) -> (fraction: Double, style: NavCardRingStyle)? {
        let progress = store.progress(of: section)
        guard progress.total > 0 else { return nil }
        return (progress.fraction, section.ringStyle(in: theme))
    }

    /// Количество зикров известно после загрузки; до неё карточка без подписи.
    private func meta(for section: AzkarSection) -> LocalizedStringResource? {
        let count = store.sessions(in: section).count
        return count > 0 ? section.cardMeta(count: count) : nil
    }
}

#Preview {
    NavigationStack {
        AzkarHomeView()
    }
    .appEnvironment(.preview)
}
