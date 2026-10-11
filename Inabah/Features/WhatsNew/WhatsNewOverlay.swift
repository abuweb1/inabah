import SwiftUI

/// Слой окна «Что нового» над всеми вкладками (как на Android, `WhatsNewOverlay.kt`, `10a`, `10e`, `10f`):
/// всегда в иерархии, чтобы затемнение и карточка появлялись и уходили анимацией.
struct WhatsNewLayer: View {
    @Environment(WhatsNew.self) private var whatsNew
    @Environment(AppRouter.self) private var router
    @Environment(\.theme) private var theme

    var body: some View {
        GeometryReader { window in
            ZStack {
                if let note = whatsNew.pending {
                    // Затемнение: нажатие мимо карточки — как «ОК». Не элемент VoiceOver.
                    theme.palette.background.opacity(WhatsNewMetrics.scrimOpacity)
                        .ignoresSafeArea()
                        .contentShape(.rect)
                        .onTapGesture { whatsNew.dismiss() }
                        .accessibilityHidden(true)
                        .transition(.opacity)
                    WhatsNewCard(
                        note: note,
                        // Цвет — вкладки, на которой окно появилось.
                        tint: router.selectedTab.tint(in: theme),
                        width: min(WhatsNewMetrics.maxWidth, window.size.width - 2 * WhatsNewMetrics.screenMargin),
                        maxListHeight: window.size.height * WhatsNewMetrics.maxListShare
                    ) { whatsNew.dismiss() }
                    .transition(.scale(scale: WhatsNewMetrics.appearScale).combined(with: .opacity))
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .animation(Motion.dialog, value: whatsNew.pending)
    }
}

private enum WhatsNewMetrics {
    static let scrimOpacity = 0.35
    static let maxWidth: CGFloat = 360
    static let screenMargin: CGFloat = 24
    static let cornerRadius: CGFloat = 34
    /// Доля цвета вкладки в заливке и прозрачность заливки поверх размытия.
    static let tintShare = 0.35
    static let fillOpacity = 0.55
    static let borderOpacity = 0.6
    /// Список пунктов — не выше половины экрана, дальше прокручивается: «ОК» всегда видна.
    static let maxListShare: CGFloat = 0.5
    static let okFillOpacity = 0.1
    static let okWidthShare: CGFloat = 0.55
    static let appearScale: CGFloat = 0.9
}

/// Стеклянная карточка: «Что нового в 1.1.1», пункты с «•» в цвет вкладки и «ОК».
private struct WhatsNewCard: View {
    let note: ReleaseNote
    let tint: Color
    let width: CGFloat
    let maxListHeight: CGFloat
    let onDismiss: () -> Void

    @Environment(\.theme) private var theme

    @State private var listHeight: CGFloat = 0

    private typealias Metrics = WhatsNewMetrics

    var body: some View {
        VStack(spacing: Spacing.l) {
            Text("whatsNew.title \(note.version.text)")
                .font(.title3)
                .foregroundStyle(theme.palette.onAccent)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)

            ScrollView {
                items
                    .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { listHeight = $0 }
            }
            .frame(height: min(listHeight, maxListHeight))
            .scrollBounceBehavior(.basedOnSize)

            Button(action: onDismiss) {
                Text("whatsNew.ok")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(tint)
                    .frame(width: width * Metrics.okWidthShare, height: Size.primaryButtonHeight)
                    .background(theme.palette.onAccent.opacity(Metrics.okFillOpacity), in: .capsule)
                    .contentShape(.capsule)
            }
            .buttonStyle(PressDimButtonStyle())
        }
        .padding(Spacing.xl)
        .frame(width: width)
        .background { background }
        .fixedTextSize()
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
    }

    private var items: some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            ForEach(Array(note.items.enumerated()), id: \.offset) { _, item in
                HStack(alignment: .firstTextBaseline, spacing: Spacing.s) {
                    Text(verbatim: "•")
                        .foregroundStyle(tint)
                        .accessibilityHidden(true)
                    Text(verbatim: item)
                        .foregroundStyle(theme.palette.onAccent)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .font(.callout)
            }
        }
    }

    /// Размытие того, что под окном, и заливка в тон вкладки поверх; рамка цвета вкладки. Тень — у
    /// фоновой фигуры (правило теней дизайн-системы).
    private var background: some View {
        let shape = RoundedRectangle(cornerRadius: Metrics.cornerRadius)
        let fill = SRGBColor(theme.palette.background).mixed(with: SRGBColor(tint), by: Metrics.tintShare).color
        return shape
            .fill(.ultraThinMaterial)
            .overlay { shape.fill(fill.opacity(Metrics.fillOpacity)) }
            .overlay { shape.strokeBorder(tint.opacity(Metrics.borderOpacity), lineWidth: Size.hairline) }
            .compositingGroup()
            .shadow(
                color: ShadowToken.floating(theme.palette).color,
                radius: ShadowToken.floating(theme.palette).radius,
                y: ShadowToken.floating(theme.palette).y
            )
    }
}

#Preview {
    ZStack {
        Theme.inabah.gradients.azkarBackground.linear.ignoresSafeArea()
        WhatsNewCard(
            note: ReleaseNote(
                version: AppVersionNumber("1.1.1")!,
                date: .now,
                items: ["Новое название — Inabah", "Избранные хадисы — закладка на экране хадиса"],
                language: .base
            ),
            tint: Theme.inabah.palette.tabAzkar,
            width: 345,
            maxListHeight: 400
        ) {}
    }
    .appEnvironment(.preview)
}
