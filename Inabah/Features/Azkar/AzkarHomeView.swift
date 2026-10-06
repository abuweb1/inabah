import SwiftUI

/// Главная раздела «Азкары»: бренд, аят Аз-Зумар 39:54 и переход к утренним/вечерним азкарам.
struct AzkarHomeView: View {
    @Environment(AzkarStore.self) private var store
    @Environment(AzkarWindowSettings.self) private var windowSettings
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
                        ring: ring(for: section),
                        trailingNote: windowNote(for: section)
                    )
                }
                .buttonStyle(PressScaleButtonStyle())
            }
        }
        .audioPlayerInset()
        .toolbarVisibility(.hidden, for: .navigationBar)
    }

    /// Выполнение раздела — только во время азкаров; до загрузки кольца нет.
    private func ring(for section: AzkarSection) -> NavCardRing? {
        let progress = store.progress(of: section)
        guard progress.total > 0, store.isInWindow(section) else { return nil }
        return NavCardRing(fraction: progress.fraction, style: section.ringStyle(in: theme))
    }

    /// Вне времени азкаров вместо кольца — само время («5:00–12:00»).
    private func windowNote(for section: AzkarSection) -> Text? {
        guard store.state(of: section).value != nil, !store.isInWindow(section) else { return nil }
        return windowSettings.window(for: section).rangeText(in: store.calendar)
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
