import SwiftUI

/// Общий каркас главных экранов разделов («Азкары», «Хадисы»):
/// градиентный фон, бренд-блок, аят и навигационные карточки.
struct SectionHomeLayout<Cards: View>: View {
    let background: ThemeGradient
    let brand: SectionBrand
    let verse: FeaturedVerse
    @ViewBuilder let cards: Cards

    @Environment(\.theme) private var theme

    private typealias Layout = SectionHomeLayoutMetrics

    var body: some View {
        // Контент растягивается минимум на видимую область (без статус-бара и таб-бара),
        // чтобы карточки стояли по центру свободного места под аятом (как `flex: 1` в прототипе),
        // а на маленьком экране или с крупным шрифтом всё прокручивалось.
        GeometryReader { viewport in
            ScrollView {
                VStack(spacing: 0) {
                    brandBlock
                    verseBox
                        .padding(.horizontal, Spacing.xs)
                        .padding(.top, verse.isFramed ? Spacing.l : Spacing.xs)
                    // Без рамки цитата стоит ближе к карточкам — они не прижимаются к низу.
                    Spacer(minLength: verse.isFramed ? Spacing.xxl : Spacing.m)
                    // Карточки в натуральной высоте — свободное место уходит в отступы вокруг них.
                    VStack(spacing: Spacing.m) { cards }
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: Spacing.xl)
                }
                .padding(.horizontal, Spacing.xl)
                .padding(.top, Spacing.s)
                .frame(minHeight: viewport.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .background { background.linear.ignoresSafeArea() }
    }

    private var brandBlock: some View {
        // У Scheherazade New высокая строка (запас под огласовки над и под буквами), поэтому
        // латинское название подтянуто к арабскому отрицательным интервалом — но не до хамзы под алифом.
        // Нижний вынос (хвост «ج» в «مخارج») занимает этот запас — тогда строки не сближаем.
        VStack(spacing: brand.arabicNameHasDescender ? 0 : -Spacing.l) {
            ArabicText(
                text: brand.arabicName,
                size: Layout.brandArabicSize,
                color: theme.palette.onAccent,
                bold: true,
                alignment: .center
            )
            VStack(spacing: Spacing.xxs) {
                // Латиница — системным шрифтом, как остальной интерфейс (засечки Scheherazade
                // в латинском названии пользователю не понравились).
                if let latinName = brand.latinName {
                    Text(latinName)
                        .font(.system(size: Layout.brandLatinSize, weight: .medium))
                        .foregroundStyle(theme.palette.onAccentSecondary)
                }
                Text(brand.tagline)
                    .font(.caption)
                    .tracking(Tracking.caption)
                    .foregroundStyle(theme.palette.onAccentTertiary)
                if let epigraph = brand.epigraph {
                    VStack(spacing: Spacing.xxxs) {
                        Text(epigraph.text)
                            .font(.footnote.italic())
                            .foregroundStyle(theme.palette.onAccentSecondary)
                        Text(epigraph.source)
                            .font(.caption2)
                            .foregroundStyle(theme.palette.onAccentTertiary)
                    }
                    .padding(.top, Spacing.s)
                    .padding(.horizontal, Spacing.xl)
                }
            }
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    private var verseBox: some View {
        VStack(spacing: Spacing.xs) {
            ArabicText(
                text: verse.arabic,
                size: ReadingSettings.defaultArabicFontSize,
                color: theme.palette.onAccentStrong,
                alignment: .center
            )
            Text(verse.translation)
                .font(.caption)
                .multilineTextAlignment(.center)
                .foregroundStyle(theme.palette.onAccentSecondary)
            Text(verse.reference)
                .font(.caption2)
                .foregroundStyle(theme.palette.onAccentTertiary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, verse.isFramed ? Spacing.m : 0)
        .padding(.horizontal, Spacing.xl)
        .surface(
            verse.isFramed ? theme.palette.subtleFill : .clear,
            cornerRadius: Radius.box,
            border: verse.isFramed ? theme.palette.hairline : .clear
        )
        .accessibilityElement(children: .combine)
    }
}

/// Размеры главного экрана раздела (вне дженерика: хранимые статические свойства в нём запрещены).
private enum SectionHomeLayoutMetrics {
    static let brandArabicSize: Double = 60
    static let brandLatinSize: Double = 28
}

struct SectionBrand {
    /// Арабское название — контент, не переводится.
    let arabicName: String
    /// У названия есть буквы с нижним выносом (ج, ر, ن в конце) — подзаголовок не подтягивается вверх.
    var arabicNameHasDescender = false
    /// Название латиницей под арабским; `nil` — без него.
    var latinName: LocalizedStringResource?
    let tagline: LocalizedStringResource
    /// Цитата под подзаголовком и её источник мелким шрифтом (как иснад).
    var epigraph: BrandEpigraph?
}

struct BrandEpigraph {
    let text: LocalizedStringResource
    let source: LocalizedStringResource
}

struct FeaturedVerse {
    let arabic: String
    let translation: LocalizedStringResource
    let reference: LocalizedStringResource
    /// В рамке на подложке (главная азкаров) или свободным текстом (главная хадисов —
    /// над ней уже цитата бренда, вторая рамка утяжеляет экран).
    var isFramed = true
}

/// Навигационная карточка раздела: градиент, иконка, заголовок, подпись, стрелка.
struct SectionNavCard: View {
    let title: LocalizedStringResource
    let meta: LocalizedStringResource?
    /// Название в оригинале после подписи через «·» (арабское название сборника — контент,
    /// не переводится и не хранится в каталоге строк).
    var metaOriginal: String?
    let symbolName: String
    let iconColor: Color
    let gradient: ThemeGradient
    let shadow: ShadowToken
    /// Показатели под подписью: у левого и у правого края (например, «прочитано» и «выучено»
    /// сборника).
    var leadingStat: NavCardStat?
    var trailingStat: NavCardStat?
    /// Кольцо прогресса справа (например, выполнение азкаров за сегодня).
    var ring: NavCardRing?

    @Environment(\.theme) private var theme

    private enum Layout {
        static let iconSize: CGFloat = 30
        static let verticalPadding: CGFloat = 18
        static let statGlyphSize: CGFloat = 18
    }

    var body: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: Spacing.xxxs) {
                Image(systemName: symbolName)
                    .font(.system(size: Layout.iconSize))
                    .foregroundStyle(iconColor)
                    .frame(width: Size.navCardIcon, height: Size.navCardIcon, alignment: .leading)
                    .accessibilityHidden(true)
                Spacer(minLength: Spacing.s)
                Text(title)
                    .font(.title3.bold())
                    .foregroundStyle(theme.palette.onAccent)
                if let meta {
                    metaText(meta)
                        .font(.footnote)
                        .foregroundStyle(theme.palette.onAccentSecondary)
                }
                if leadingStat != nil || trailingStat != nil {
                    HStack(spacing: Spacing.s) {
                        if let leadingStat {
                            statLabel(leadingStat)
                        }
                        Spacer(minLength: 0)
                        if let trailingStat {
                            statLabel(trailingStat)
                        }
                    }
                    .font(.subheadline.weight(.semibold))
                    .monospacedDigit()
                    .foregroundStyle(theme.palette.onAccent)
                    .lineLimit(1)
                    .padding(.top, Spacing.xxs)
                }
            }
            Spacer(minLength: Spacing.s)
            // Кольцо и стрелка — по центру карточки по вертикали.
            HStack(spacing: Spacing.m) {
                if let ring {
                    NavCardProgressRing(fraction: ring.fraction, style: ring.style)
                }
                Image(systemName: "chevron.forward")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(theme.palette.onAccentTertiary)
                    .accessibilityHidden(true)
            }
            .frame(maxHeight: .infinity)
        }
        .padding(.vertical, Layout.verticalPadding)
        .padding(.horizontal, Spacing.xlPlus)
        .frame(maxWidth: .infinity, minHeight: Size.navCardMinHeight, alignment: .leading)
        .surface(gradient.linear, cornerRadius: Radius.navCard, border: theme.palette.divider, shadow: shadow)
        .contentShape(.rect(cornerRadius: Radius.navCard))
        .accessibilityElement(children: .combine)
    }

    private func metaText(_ meta: LocalizedStringResource) -> Text {
        guard let metaOriginal else { return Text(meta) }
        return Text("section.card.meta.original \(Text(meta)) \(Text(verbatim: metaOriginal))")
    }

    private func statLabel(_ stat: NavCardStat) -> some View {
        HStack(spacing: Spacing.xs) {
            StatusGlyph(stat.glyph, size: Layout.statGlyphSize, relativeTo: .subheadline)
            Text(stat.value)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(stat.accessibilityLabel))
    }
}

/// Кольцо прогресса на карточке раздела: доля выполнения и оформление.
struct NavCardRing: Equatable {
    let fraction: Double
    let style: NavCardRingStyle
}

/// Показатель на карточке раздела: значок и короткое значение («3/50»); для VoiceOver — полная фраза.
struct NavCardStat {
    let glyph: StatusGlyph.Kind
    let value: LocalizedStringResource
    let accessibilityLabel: LocalizedStringResource
}
