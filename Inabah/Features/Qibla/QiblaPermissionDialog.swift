import SwiftUI

/// Пояснение перед окном системы (решение пользователя 2026-10-24): по центру, стекло - размыто только то,
/// что под окном, экран вокруг лишь чуть притемнён. «Не сейчас» - на экране остаётся «Разрешить».
/// Касание мимо окна ничего не делает.
///
/// Размытие своё, не системное (решение пользователя 2026-10-25: системное делало окно плотным и серым):
/// под окном лежит размытая копия экрана `backdrop`, вырезанная по форме окна. Копия - тот же экран в том же
/// месте слоя, поэтому совпадает с ним.
struct QiblaPermissionDialog<Backdrop: View>: View {
    let onAllow: () -> Void
    let onLater: () -> Void
    @ViewBuilder let backdrop: Backdrop

    @Environment(\.theme) private var theme
    @ScaledMetric(relativeTo: .headline) private var iconSize = QiblaDialogLayout.icon
    /// Рамка окна в слое - по ней вырезается размытая копия.
    @State private var cardFrame: CGRect = .zero

    private typealias Layout = QiblaDialogLayout

    var body: some View {
        ZStack {
            theme.palette.shadow.opacity(Layout.scrimOpacity)
                .ignoresSafeArea()
                .contentShape(.rect)
                .onTapGesture {}
                .accessibilityHidden(true)
            backdrop
                .blur(radius: Layout.blurRadius)
                .mask {
                    RoundedRectangle(cornerRadius: Layout.cornerRadius)
                        .frame(width: cardFrame.width, height: cardFrame.height)
                        .position(x: cardFrame.midX, y: cardFrame.midY)
                }
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            card
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(Layout.space)) } action: { cardFrame = $0 }
                .frame(maxWidth: Layout.maxWidth)
                .padding(Spacing.xxl)
        }
        .coordinateSpace(.named(Layout.space))
    }

    private var card: some View {
        VStack(spacing: Spacing.s) {
            Image(systemName: "location")
                .font(.system(size: iconSize * QiblaTileMetrics.symbolShare, weight: .medium))
                .foregroundStyle(theme.palette.gold)
                .frame(width: iconSize, height: iconSize)
                .accessibilityHidden(true)
            Text("qibla.dialog.title")
                .font(.headline.bold())
                .foregroundStyle(theme.palette.onAccent)
                .accessibilityAddTraits(.isHeader)
            Group {
                Text("qibla.dialog.purpose")
                Text("qibla.dialog.privacy")
            }
            .font(.footnote)
            .foregroundStyle(theme.palette.onAccentSecondary)
            Button(action: onAllow) {
                Text("qibla.permission.allow")
            }
            .buttonStyle(PrimaryButtonStyle())
            .padding(.top, Spacing.s)
            Button(action: onLater) {
                Text("qibla.dialog.later")
                    .font(.subheadline)
                    .foregroundStyle(theme.palette.onAccentSecondary)
                    .padding(.horizontal, Spacing.xl)
                    .frame(minHeight: Size.minTapTarget)
                    .contentShape(.rect)
            }
            .buttonStyle(PressDimButtonStyle())
        }
        .multilineTextAlignment(.center)
        .padding(.horizontal, Spacing.xl)
        .padding(.vertical, Spacing.xxl)
        .background { background }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape, onLater)
    }

    /// Заливка в тон раздела поверх размытой копии и светлый ободок. Без тени - под стеклом она
    /// просвечивала бы тёмным пятном.
    private var background: some View {
        let shape = RoundedRectangle(cornerRadius: Layout.cornerRadius)
        let glass = SRGBColor(theme.palette.background).mixed(with: SRGBColor(theme.palette.tabAzkar), by: Layout.tintShare).color
        return shape
            .fill(glass.opacity(Layout.fillOpacity))
            .overlay { shape.strokeBorder(theme.palette.onAccent.opacity(Layout.rimOpacity), lineWidth: Size.hairline) }
    }
}

/// Константы окна - вне обобщённого типа (в нём нельзя хранимые статические свойства).
nonisolated private enum QiblaDialogLayout {
    static let space = "qiblaDialog"
    static let maxWidth: CGFloat = 340
    static let cornerRadius: CGFloat = 28
    /// Затемнение вокруг лёгкое: сквозь стекло оно тоже видно.
    static let scrimOpacity = 0.2
    /// Своё размытие под окном (как Haze на Android).
    static let blurRadius: CGFloat = 16
    static let fillOpacity = 0.4
    static let rimOpacity = 0.35
    /// Доля цвета раздела в стекле окна.
    static let tintShare = 0.2
    static let icon: CGFloat = 34
}

/// Справка «Как повысить точность» (набросок 2026-10-24): советы, без обещания точности.
struct QiblaHelpContent: View {
    let onClose: () -> Void

    @Environment(\.theme) private var theme

    var body: some View {
        Text("qibla.help")
            .font(.title3)
            .foregroundStyle(theme.palette.onAccent)
            .padding(.bottom, Spacing.xs)
            .accessibilityAddTraits(.isHeader)
        section("qibla.help.hold.title", ["qibla.help.hold.text"])
        section("qibla.help.interference.title", ["qibla.help.interference.text"])
        section(
            "qibla.help.unstable.title",
            ["qibla.help.unstable.calibrate", "qibla.help.unstable.precise", "qibla.help.unstable.restart"],
            bullets: true
        )
        section("qibla.help.error.title", ["qibla.help.error.text"])
        Button(action: onClose) {
            Text("qibla.help.close")
        }
        .buttonStyle(PrimaryButtonStyle())
        .padding(.top, Spacing.l)
    }

    private func section(_ title: LocalizedStringResource, _ paragraphs: [LocalizedStringResource], bullets: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(title)
                .font(.callout.weight(.semibold))
                .foregroundStyle(theme.palette.goldLight)
                .accessibilityAddTraits(.isHeader)
            ForEach(paragraphs.indices, id: \.self) { index in
                HStack(alignment: .firstTextBaseline, spacing: Spacing.s) {
                    if bullets {
                        Text(verbatim: "•")
                            .foregroundStyle(theme.palette.gold)
                            .accessibilityHidden(true)
                    }
                    Text(paragraphs[index])
                        .foregroundStyle(theme.palette.onAccentSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .font(.subheadline)
            }
        }
        .padding(.top, Spacing.m)
    }
}
