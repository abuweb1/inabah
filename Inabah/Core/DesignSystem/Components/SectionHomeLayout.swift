import SwiftUI

/// Общий каркас главных экранов разделов («Азкары», «Хадисы»):
/// градиентный фон, звезда-орнамент, бренд-блок, аят и навигационные карточки.
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
        .background { backgroundLayer }
    }

    private var backgroundLayer: some View {
        background.linear
            .overlay(alignment: .topTrailing) {
                DecorativeStar()
                    .foregroundStyle(theme.palette.subtleFill)
                    .frame(width: Layout.starSize, height: Layout.starSize)
                    .offset(Layout.starOffset)
            }
            .ignoresSafeArea()
    }

    private var brandBlock: some View {
        // У Scheherazade New высокая строка (запас под огласовки над и под буквами), поэтому
        // латинское название подтянуто к арабскому отрицательным интервалом — но не до хамзы под алифом.
        VStack(spacing: -Spacing.l) {
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
                    .tracking(0.8)
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
                color: theme.palette.onAccent.opacity(0.85),
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
    static let starSize: CGFloat = 200
    static let starOffset = CGSize(width: 30, height: -20)
    static let brandArabicSize: Double = 60
    static let brandLatinSize: Double = 28
}

struct SectionBrand {
    /// Арабское название — контент, не переводится.
    let arabicName: String
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
    let symbolName: String
    let iconColor: Color
    let gradient: ThemeGradient
    let shadow: ShadowToken
    /// Дополнительная строка под подписью (например, прогресс чтения сборника).
    var progress: LocalizedStringResource?
    /// Второй показатель на той же строке, у правого края (например, сколько выучено).
    var secondaryProgress: LocalizedStringResource?
    /// Кольцо прогресса справа от иконки (например, выполнение азкаров за сегодня).
    var ring: (fraction: Double, style: NavCardRingStyle)?

    @Environment(\.theme) private var theme

    private enum Layout {
        static let iconSize: CGFloat = 30
        static let decorationSize: CGFloat = 110
        static let decorationOffset = CGSize(width: 30, height: -30)
        static let verticalPadding: CGFloat = 18
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
                    Text(meta)
                        .font(.footnote)
                        .foregroundStyle(theme.palette.onAccentSecondary)
                }
                if progress != nil || secondaryProgress != nil {
                    HStack(spacing: Spacing.s) {
                        if let progress {
                            progressLabel(progress, symbolName: "checkmark.circle.fill")
                        }
                        Spacer(minLength: 0)
                        if let secondaryProgress {
                            progressLabel(secondaryProgress, symbolName: "star.fill")
                        }
                    }
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(theme.palette.onAccent)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
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
        .overlay(alignment: .topTrailing) {
            // Декоративный круг в углу, обрезается скруглением карточки.
            Circle()
                .fill(theme.palette.subtleFill)
                .frame(width: Layout.decorationSize, height: Layout.decorationSize)
                .offset(Layout.decorationOffset)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                .clipShape(.rect(cornerRadius: Radius.navCard))
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
        .contentShape(.rect(cornerRadius: Radius.navCard))
        .accessibilityElement(children: .combine)
    }

    private func progressLabel(_ text: LocalizedStringResource, symbolName: String) -> some View {
        Label {
            Text(text)
        } icon: {
            Image(systemName: symbolName)
                .accessibilityHidden(true)
        }
    }
}
