import SwiftUI

/// Действия экрана «Къибла» - одним значением, чтобы содержимое не зависело от модели.
struct QiblaActions {
    var requestPermission: () -> Void = {}
    var openSettings: () -> Void = {}
    var refreshLocation: () -> Void = {}
    var openHelp: () -> Void = {}
}

/// Что под кольцом в каждом состоянии (`docs/android/06-screens.md`, «Къибла», таблица состояний).
struct QiblaHeadline {
    enum Mark: Equatable {
        case arrow(TurnSide)
        case check
        case spinner
        case none
    }

    /// Цвет надписи: золото - поворот и проблемы компаса, зелёный - найдено, белый - ожидание и доступ.
    enum Tone {
        case gold, ok, plain
    }

    enum Action {
        case allow, openSettings, retry, refresh

        var title: LocalizedStringResource {
            switch self {
            case .allow: "qibla.permission.allow"
            case .openSettings: "qibla.open.settings"
            case .retry: "qibla.retry"
            case .refresh: "qibla.location.refresh"
            }
        }
    }

    let mark: Mark
    let title: LocalizedStringResource
    let hint: LocalizedStringResource
    let tone: Tone
    var action: Action?

    init(_ state: QiblaUIState) {
        switch state {
        case .loading:
            self.init(.spinner, "qibla.loading", "qibla.loading.hint", .plain)
        case .needsPermission(let denied):
            self = denied
                ? .init(.none, "qibla.denied.title", "qibla.denied.hint", .plain, .openSettings)
                : .init(.none, "qibla.permission.title", "qibla.permission.hint", .plain, .allow)
        // На iOS раздел «Геолокация» системы открыть нельзя - только экран приложения в настройках.
        case .locationOff:
            self.init(.none, "qibla.location.off.title", "qibla.location.off.hint", .plain, .openSettings)
        case .locating:
            self.init(.spinner, "qibla.locating.title", "qibla.locating.hint", .plain)
        case .locationFailed:
            self.init(.none, "qibla.failed.title", "qibla.failed.hint", .plain, .retry)
        case .noCompass(let bearing, _):
            let degrees = Int(bearing.rounded()) % 360
            self.init(.none, "qibla.no.compass.title", "qibla.no.compass.hint \(degrees)", .plain)
        case .compass(_, let location, _, let guidance):
            switch guidance {
            case .waiting:
                self.init(.spinner, "qibla.waiting", "qibla.waiting.hint", .plain)
            case .lowAccuracy:
                self.init(.none, "qibla.low.accuracy.title", "qibla.low.accuracy.hint", .gold)
            case .tilted(let side, let amount):
                self.init(.arrow(side), Self.turnTitle(side, amount), "qibla.tilted.hint", .gold)
            case .turn(let side, let amount):
                self.init(.arrow(side), Self.turnTitle(side, amount), "qibla.turn.hint", .gold)
            case .aligned:
                self.init(.check, "qibla.found", "qibla.found.hint", .ok)
            // Метка у указателя, но координатам нельзя доверить подтверждение (решение пользователя 2026-10-24).
            case .locationUnconfirmed:
                self = location.nearKaaba
                    ? .init(.none, "qibla.near.kaaba.title", "qibla.near.kaaba.hint", .gold, .openSettings)
                    : .init(.none, "qibla.stale.title", "qibla.stale.hint", .gold, .refresh)
            }
        }
    }

    private init(
        _ mark: Mark,
        _ title: LocalizedStringResource,
        _ hint: LocalizedStringResource,
        _ tone: Tone,
        _ action: Action? = nil
    ) {
        self.mark = mark
        self.title = title
        self.hint = hint
        self.tone = tone
        self.action = action
    }

    /// «Поверните направо» (> 45°), «Правее» (15-45°), «Немного правее» (< 15°) - степень с запасом считает трекер.
    private static func turnTitle(_ side: TurnSide, _ amount: TurnAmount) -> LocalizedStringResource {
        switch (amount, side) {
        case (.far, .right): "qibla.turn.right"
        case (.far, .left): "qibla.turn.left"
        case (.mid, .right): "qibla.turn.right.mid"
        case (.mid, .left): "qibla.turn.left.mid"
        case (.near, .right): "qibla.turn.right.near"
        case (.near, .left): "qibla.turn.left.near"
        }
    }
}

/// Знак, крупная надпись, подпись и кнопка действия. Знак подтянут к кольцу: под кольцом пустое поле.
struct QiblaHeadlineBlock: View {
    let headline: QiblaHeadline
    let actions: QiblaActions

    @Environment(\.theme) private var theme

    private enum Layout {
        static let markHeight: CGFloat = 26
        /// Подтянут к кольцу (было 22 - пользователь попросил отступ от кольца ~10 px).
        static let markLift: CGFloat = 18
        static let markIcon: CGFloat = 30
        static let spinnerShare: CGFloat = 0.75
    }

    @ScaledMetric(relativeTo: .title2) private var markIcon = Layout.markIcon

    var body: some View {
        VStack(spacing: 0) {
            mark
                .frame(maxWidth: .infinity)
                .frame(height: Layout.markHeight)
            Text(headline.title)
                .font(.title2.bold())
                .foregroundStyle(titleColor)
                .multilineTextAlignment(.center)
                .padding(.top, Spacing.xxs)
                .accessibilityAddTraits(.isHeader)
            Text(headline.hint)
                .font(.subheadline)
                .foregroundStyle(theme.palette.onAccentSecondary)
                .multilineTextAlignment(.center)
                .padding(.top, Spacing.xs)
            if let action = headline.action {
                Button(action: perform(action)) {
                    Text(action.title)
                }
                .buttonStyle(PrimaryButtonStyle())
                .padding(.top, Spacing.l)
            }
        }
        .padding(.top, -Layout.markLift)
    }

    @ViewBuilder
    private var mark: some View {
        switch headline.mark {
        case .arrow(let side):
            QiblaTurnArrow()
                .stroke(theme.palette.gold, style: StrokeStyle(lineWidth: QiblaTurnArrow.lineWidth, lineCap: .round, lineJoin: .round))
                .frame(width: QiblaTurnArrow.size.width, height: QiblaTurnArrow.size.height)
                .scaleEffect(x: side == .left ? -1 : 1)
                .accessibilityHidden(true)
        case .check:
            Image(systemName: "checkmark.circle.fill")
                .resizable()
                .frame(width: markIcon, height: markIcon)
                .foregroundStyle(theme.palette.background, theme.palette.successLight)
                .accessibilityHidden(true)
        case .spinner:
            ProgressView()
                .tint(theme.palette.gold)
                .accessibilityHidden(true)
        case .none:
            Color.clear
        }
    }

    private var titleColor: Color {
        switch headline.tone {
        case .gold: theme.palette.goldLight
        case .ok: theme.palette.successLight
        case .plain: theme.palette.onAccent
        }
    }

    private func perform(_ action: QiblaHeadline.Action) -> () -> Void {
        switch action {
        case .allow: actions.requestPermission
        case .openSettings: actions.openSettings
        case .retry, .refresh: actions.refreshLocation
        }
    }
}

/// Стрелка поворота (картинка пользователя, 2026-10-24): линия, промежуток и отдельный уголок - одной линией
/// со скруглённым изгибом; влево - зеркально.
nonisolated private struct QiblaTurnArrow: Shape {
    static let size = CGSize(width: 104, height: 20)
    /// Линия 2,4, уголок 3 на Android - здесь одна толщина, близкая к обеим.
    static let lineWidth: CGFloat = 2.7
    private static let lineEnd: CGFloat = 0.74
    private static let chevronWidth: CGFloat = 0.42
    private static let chevronHalf: CGFloat = 0.4

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let midY = rect.midY
        path.move(to: CGPoint(x: rect.minX + Self.lineWidth, y: midY))
        path.addLine(to: CGPoint(x: rect.minX + rect.width * Self.lineEnd, y: midY))
        let tipX = rect.maxX - Self.lineWidth
        let backX = tipX - rect.height * Self.chevronWidth
        let half = rect.height * Self.chevronHalf
        path.move(to: CGPoint(x: backX, y: midY - half))
        path.addLine(to: CGPoint(x: tipX, y: midY))
        path.addLine(to: CGPoint(x: backX, y: midY + half))
        return path
    }
}

/// Плитка: значок (или ожидание) слева, текст, справа - действие. Проблема - текст золотом.
struct QiblaTile<Icon: View, Trailing: View>: View {
    let text: LocalizedStringResource
    let warn: Bool
    @ViewBuilder let icon: Icon
    @ViewBuilder let trailing: Trailing

    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: Spacing.l) {
            icon
                .foregroundStyle(theme.palette.gold)
                .accessibilityHidden(true)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(warn ? theme.palette.goldLight : theme.palette.onAccent)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, Spacing.s)
            trailing
        }
        .padding(.leading, Spacing.xl)
        .padding(.trailing, Spacing.s)
        .padding(.vertical, Spacing.xs)
        .frame(minHeight: QiblaTileMetrics.minHeight)
        .surface(
            theme.palette.card.opacity(QiblaTileMetrics.fillOpacity),
            cornerRadius: Radius.card,
            border: theme.palette.hairline
        )
    }
}

enum QiblaTileMetrics {
    static let minHeight: CGFloat = 60
    static let fillOpacity = 0.55
    static let icon: CGFloat = 24
    /// SF-значок в поле `icon` - чуть меньше поля, как значки Material в 24 dp.
    static let symbolShare: CGFloat = 0.85
}

/// Местоположение: определено / приблизительное / устарело + «Обновить» / определяем / нет доступа.
struct QiblaLocationTile: View {
    let state: QiblaUIState
    let onRefresh: () -> Void

    @Environment(\.theme) private var theme
    @ScaledMetric(relativeTo: .subheadline) private var iconSize = QiblaTileMetrics.icon

    var body: some View {
        let note = state.location
        switch state {
        case .needsPermission:
            tile("qibla.location.none", warn: true, symbol: "location.slash")
        case .locationOff:
            tile("qibla.location.off.row", warn: true, symbol: "location.slash")
        case .locationFailed:
            tile("qibla.failed.title", warn: true, symbol: "location.slash")
        case .loading, .locating:
            QiblaTile(text: "qibla.location.searching", warn: false) {
                ProgressView()
                    .tint(theme.palette.gold)
                    .frame(width: iconSize, height: iconSize)
            } trailing: {
                EmptyView()
            }
        default:
            if note?.stale == true {
                QiblaTile(text: "qibla.location.stale", warn: true) {
                    symbol("location")
                } trailing: {
                    Button(action: onRefresh) {
                        Text("qibla.location.refresh")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(theme.palette.gold)
                            .padding(.horizontal, Spacing.s)
                            .frame(minHeight: Size.minTapTarget)
                            .contentShape(.rect)
                    }
                    .buttonStyle(PressDimButtonStyle())
                }
            } else if note?.approximate == true {
                // У Мекки «приблизительное» - проблема (решение пользователя 2026-10-24), в остальном мире - пометка.
                tile("qibla.location.approximate", warn: note?.nearKaaba == true, symbol: "location")
            } else {
                tile("qibla.location.found", warn: false, symbol: "location")
            }
        }
    }

    private func tile(_ text: LocalizedStringResource, warn: Bool, symbol name: String) -> some View {
        QiblaTile(text: text, warn: warn) {
            symbol(name)
        } trailing: {
            EmptyView()
        }
    }

    private func symbol(_ name: String) -> some View {
        Image(systemName: name)
            .font(.system(size: iconSize * QiblaTileMetrics.symbolShare, weight: .medium))
            .frame(width: iconSize, height: iconSize)
    }
}

/// Совет «Вдали от металлических предметов»: золотой компас слева, ⓘ «Как повысить точность» справа.
struct QiblaAdviceTile: View {
    let helpHintVisible: Bool
    let onOpenHelp: () -> Void

    @Environment(\.theme) private var theme
    @ScaledMetric(relativeTo: .subheadline) private var iconSize = QiblaTileMetrics.icon

    var body: some View {
        QiblaTile(text: "qibla.advice.metal", warn: false) {
            QiblaCompassGlyph(color: theme.palette.gold)
                .frame(width: iconSize, height: iconSize)
        } trailing: {
            Button(action: onOpenHelp) {
                Image(systemName: "info")
                    .font(.body.weight(.semibold))
            }
            .buttonStyle(GlassIconButtonStyle(tint: theme.palette.tabAzkar))
            .accessibilityLabel(Text("qibla.help"))
            .hintBubble(
                "qibla.help",
                isVisible: helpHintVisible,
                tint: theme.palette.tabAzkar,
                textColor: theme.palette.background,
                below: false
            )
        }
    }
}

/// Свой компас плитки-совета (Android `ic_compass.xml`, решение пользователя 2026-10-24): кольцо крупное и
/// тонкое, стрелка узкая у центра и острая на концах; северная половина сплошная, южная - полупрозрачная.
struct QiblaCompassGlyph: View {
    let color: Color

    private enum Layout {
        /// Вектор Android - поле 24 × 24.
        static let viewport: CGFloat = 24
        static let ringRadius: CGFloat = 10.4
        static let ringWidth: CGFloat = 1.5
        static let southOpacity = 0.5
    }

    var body: some View {
        Canvas { context, size in
            let scale = min(size.width, size.height) / Layout.viewport
            context.scaleBy(x: scale, y: scale)
            let center = Layout.viewport / 2
            let radius = Layout.ringRadius
            context.stroke(
                Path(ellipseIn: CGRect(x: center - radius, y: center - radius, width: radius * 2, height: radius * 2)),
                with: .color(color),
                lineWidth: Layout.ringWidth
            )
            context.fill(Self.needle([(17.3, 6.7), (13.15, 13.15), (10.85, 10.85)]), with: .color(color))
            context.fill(Self.needle([(6.7, 17.3), (10.85, 10.85), (13.15, 13.15)]), with: .color(color.opacity(Layout.southOpacity)))
        }
        .accessibilityHidden(true)
    }

    private static func needle(_ points: [(CGFloat, CGFloat)]) -> Path {
        Path { path in
            path.addLines(points.map { CGPoint(x: $0.0, y: $0.1) })
            path.closeSubpath()
        }
    }
}

/// Строка внизу: «Держите телефон ровно…»; наклонён - золотом «Телефон наклонён…».
struct QiblaHoldFlatNote: View {
    let tilted: Bool

    @Environment(\.theme) private var theme

    var body: some View {
        Text(tilted ? "qibla.tilted" : "qibla.hold.flat")
            .font(.footnote)
            .foregroundStyle(tilted ? theme.palette.goldLight : theme.palette.onAccentSecondary)
            .multilineTextAlignment(.center)
    }
}
