import SwiftUI

/// Главная раздела «Хадисы»: бренд, хадис о понимании религии и три сборника.
struct HadithHomeView: View {
    @Environment(HadithStore.self) private var store
    @Environment(HadithProgress.self) private var progress
    @Environment(HadithCollectionOrder.self) private var order
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
            // Порядок карточек задаётся в настройках.
            ForEach(order.collections, id: \.self) { collection in
                NavigationLink(value: HadithRoute.list(collection)) {
                    SectionNavCard(
                        title: collection.title,
                        meta: meta(for: collection),
                        symbolName: collection.symbolName,
                        iconColor: collection.iconColor(in: theme),
                        gradient: collection.cardGradient(in: theme),
                        shadow: collection.cardShadow(in: theme),
                        stats: stats(for: collection)
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

    /// Книга «3/50» слева (если что-то прочитано) и сердце «2/50» справа (если что-то выучено).
    private func stats(for collection: HadithCollection) -> [NavCardStat] {
        let total = store.hadiths(in: collection).count
        let collectionProgress = progress.progress(in: collection, total: total)
        var stats: [NavCardStat] = []
        if collectionProgress.read > 0 {
            stats.append(NavCardStat(
                glyph: .read,
                value: "progress.fraction \(collectionProgress.read) \(total)",
                accessibilityLabel: "hadith.progress.card \(collectionProgress.read) \(total)"
            ))
        }
        if collectionProgress.memorized > 0 {
            stats.append(NavCardStat(
                glyph: .memorized,
                value: "progress.fraction \(collectionProgress.memorized) \(total)",
                accessibilityLabel: "hadith.progress.card.memorized \(collectionProgress.memorized) \(total)"
            ))
        }
        return stats
    }
}

#Preview {
    NavigationStack {
        HadithHomeView()
    }
    .appEnvironment(.preview)
}
