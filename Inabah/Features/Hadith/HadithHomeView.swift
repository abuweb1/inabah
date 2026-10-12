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
        } topAction: {
            FavoritesEntryButton()
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

/// Вход в «Избранное» — круглая кнопка справа вверху главной (решение пользователя 2026-10-10,
/// как на Android); есть всегда, и при пустом избранном. Вид — как у круглых кнопок навбара:
/// на iOS 26 их рисует системное стекло, на iOS 18–25 — кружок дизайн-системы.
private struct FavoritesEntryButton: View {
    @Environment(\.theme) private var theme

    var body: some View {
        if #available(iOS 26, *) {
            link
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .controlSize(.large)
        } else {
            link
                .buttonStyle(IconButtonStyle(
                    foreground: theme.palette.tabHadith,
                    background: theme.palette.subtleFill,
                    border: theme.palette.hairline
                ))
        }
    }

    private var link: some View {
        NavigationLink(value: HadithRoute.favorites) {
            Image(systemName: "bookmark")
                .fontWeight(.medium)
                .foregroundStyle(theme.palette.tabHadith)
        }
        .accessibilityLabel(Text("hadith.favorites.title"))
        .fixedTextSize()
    }
}

#Preview {
    NavigationStack {
        HadithHomeView()
    }
    .appEnvironment(.preview)
}
