import SwiftUI

/// Главная раздела «Хадисы»: бренд, хадис о понимании религии и три сборника.
struct HadithHomeView: View {
    @Environment(HadithStore.self) private var store
    @Environment(HadithProgress.self) private var progress
    @Environment(\.theme) private var theme

    private static let brand = SectionBrand(
        arabicName: "حديث",
        tagline: "hadith.home.brand.tagline",
        epigraph: BrandEpigraph(
            text: "hadith.home.epigraph.text",
            source: "hadith.home.epigraph.source"
        )
    )

    private static let verse = FeaturedVerse(
        arabic: "مَنْ يُرِدِ اللَّهُ بِهِ خَيْرًا يُفَقِّهْهُ فِي الدِّينِ",
        translation: "hadith.home.verse.translation",
        reference: "hadith.home.verse.reference",
        isFramed: false
    )

    var body: some View {
        SectionHomeLayout(
            background: theme.gradients.hadithBackground,
            brand: Self.brand,
            verse: Self.verse
        ) {
            ForEach(HadithCollection.allCases, id: \.self) { collection in
                NavigationLink(value: HadithRoute.list(collection)) {
                    SectionNavCard(
                        title: collection.title,
                        meta: meta(for: collection),
                        symbolName: collection.symbolName,
                        iconColor: collection.iconColor(in: theme),
                        gradient: collection.cardGradient(in: theme),
                        shadow: collection.cardShadow(in: theme),
                        progress: progressLine(for: collection)
                    )
                }
                .buttonStyle(PressScaleButtonStyle())
            }
        }
        .audioPlayerInset()
        .toolbarVisibility(.hidden, for: .navigationBar)
    }

    /// Количество хадисов известно после загрузки; до неё карточка без подписи.
    private func meta(for collection: HadithCollection) -> LocalizedStringResource? {
        let count = store.hadiths(in: collection).count
        return count > 0 ? collection.cardMeta(count: count) : nil
    }

    /// «Прочитано 3 из 50» — только когда что-то уже прочитано.
    private func progressLine(for collection: HadithCollection) -> LocalizedStringResource? {
        let total = store.hadiths(in: collection).count
        let collectionProgress = progress.progress(in: collection, total: total)
        guard collectionProgress.read > 0 else { return nil }
        return "hadith.progress.card \(collectionProgress.read) \(total)"
    }
}

#Preview {
    NavigationStack {
        HadithHomeView()
    }
    .appEnvironment(.preview)
}
