import SwiftUI

/// Пояснение перед окном системы (решение пользователя 2026-10-24): по центру, стекло - размыто только то,
/// что под окном, экран вокруг лишь чуть притемнён. «Не сейчас» - на экране остаётся «Разрешить».
/// Касание мимо окна ничего не делает. Стекло - общее `GlassDialog` (своё размытие копии экрана `backdrop`).
struct QiblaPermissionDialog<Backdrop: View>: View {
    let onAllow: () -> Void
    let onLater: () -> Void
    @ViewBuilder let backdrop: Backdrop

    @Environment(\.theme) private var theme
    @ScaledMetric(relativeTo: .headline) private var iconSize = QiblaDialogLayout.icon

    private typealias Layout = QiblaDialogLayout

    var body: some View {
        GlassDialog(style: Layout.style, tint: theme.palette.tabAzkar, rim: theme.palette.onAccent) {
            card
        } backdrop: {
            backdrop
        }
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
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape, onLater)
    }
}

/// Константы окна - вне обобщённого типа (в нём нельзя хранимые статические свойства).
private enum QiblaDialogLayout {
    /// Затемнение вокруг лёгкое: сквозь стекло оно тоже видно; доля цвета раздела 0,2, светлый ободок.
    static let style = GlassDialogStyle(
        maxWidth: 340,
        screenMargin: Spacing.xxl,
        cornerRadius: 28,
        scrimOpacity: 0.2,
        tintShare: 0.2,
        rimOpacity: 0.35
    )
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
