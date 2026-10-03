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
                        metaOriginal: collection.arabicTitle,
                        symbolName: collection.symbolName,
                        iconColor: collection.iconColor(in: theme),
                        gradient: collection.cardGradient(in: theme),
                        shadow: collection.cardShadow(in: theme),
                        leadingStat: readStat(for: collection),
                        trailingStat: memorizedStat(for: collection)
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

    private func collectionProgress(_ collection: HadithCollection) -> HadithCollectionProgress {
        progress.progress(in: collection, total: store.hadiths(in: collection).count)
    }

    /// Книга «3/50» слева — если что-то прочитано.
    private func readStat(for collection: HadithCollection) -> NavCardStat? {
        let progress = collectionProgress(collection)
        guard progress.read > 0 else { return nil }
        return NavCardStat(
            glyph: .read,
            value: "progress.fraction \(progress.read) \(progress.total)",
            accessibilityLabel: "hadith.progress.card \(progress.read) \(progress.total)"
        )
    }

    /// Сердце «2/50» справа — если что-то выучено.
    private func memorizedStat(for collection: HadithCollection) -> NavCardStat? {
        let progress = collectionProgress(collection)
        guard progress.memorized > 0 else { return nil }
        return NavCardStat(
            glyph: .memorized,
            value: "progress.fraction \(progress.memorized) \(progress.total)",
            accessibilityLabel: "hadith.progress.card.memorized \(progress.memorized) \(progress.total)"
        )
    }
}

#Preview {
    NavigationStack {
        HadithHomeView()
    }
    .appEnvironment(.preview)
}
