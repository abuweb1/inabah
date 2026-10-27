import SwiftUI

/// Кольцо Къиблы (Android `QiblaDial.kt`, макеты `IMG_0230…0233`): Кааба неподвижна, белый указатель сверху -
/// верх телефона, золотая метка - направление на Каабу, дуга - поворот к ней; при совпадении метка и участок
/// кольца зелёные. Метку читает только `QiblaDialHost`: показания перерисовывают кольцо, не перестраивая экран.
/// Здесь же - всё, что зависит от метки: градусы для VoiceOver и виброотклик на вход в совпадение.
struct QiblaDialHost: View {
    let model: QiblaModel
    /// Состояние без метки (`QiblaModel.screen`).
    let screen: QiblaUIState
    /// Описание кольца без компаса или без подтверждения - надпись под ним.
    let fallback: LocalizedStringResource

    /// Градусы для VoiceOver - с запасом у границ шага: озвучка не прыгает от дрожания.
    @State private var spokenTurn: Int?
    @State private var haptics = AlignmentHaptics()
    @State private var vibrations = 0

    var body: some View {
        let guidance = screen.guidance
        QiblaDial(
            pointer: model.pointer,
            aligned: guidance == .aligned,
            dimmed: guidance == .lowAccuracy || guidance?.isTilted == true,
            searching: screen == .locating || guidance == .waiting,
            description: description(guidance)
        )
        .onChange(of: model.pointer) { _, pointer in
            spokenTurn = pointer.map { QiblaSpokenTurn.degrees(distance: abs($0.offset), previous: spokenTurn) }
            if let pointer, haptics.shouldVibrate(pointer) { vibrations += 1 }
        }
        // Один отклик на каждый новый вход в совпадение (Android `LongPress`).
        .sensoryFeedback(.impact(weight: .heavy), trigger: vibrations)
    }

    private func description(_ guidance: QiblaGuidance?) -> Text {
        switch guidance {
        case .aligned:
            return Text("qibla.found")
        case .turn(let side, _), .tilted(let side, _):
            guard let spokenTurn else { return Text("qibla.dial.waiting") }
            return side == .right ? Text("qibla.dial.right \(spokenTurn)") : Text("qibla.dial.left \(spokenTurn)")
        case .waiting:
            return Text("qibla.dial.waiting")
        // Компас работает, но подтверждения нет («Компас неточен», «Обновите местоположение») — как надпись,
        // Android `dialDescription` (аудит 2026-10-27: было «Компас ещё не готов»).
        case .lowAccuracy, .locationUnconfirmed, nil:
            return Text(fallback)
        }
    }
}

private extension QiblaGuidance {
    var isTilted: Bool {
        if case .tilted = self { true } else { false }
    }
}

struct QiblaDial: View {
    let pointer: QiblaPointer?
    let aligned: Bool
    let dimmed: Bool
    let searching: Bool
    let description: Text

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private typealias Layout = QiblaDialLayout

    var body: some View {
        ZStack {
            if searching && !reduceMotion {
                TimelineView(.animation) { timeline in
                    canvas(scan: Self.scanAngle(at: timeline.date))
                }
            } else {
                canvas(scan: searching ? Layout.scanStillAngle : nil)
            }
            GeometryReader { proxy in
                let width = proxy.size.width * Layout.kaabaShare * Layout.ringShare
                Image(.qiblaKaaba)
                    .resizable()
                    .scaledToFit()
                    .frame(width: width, height: width)
                    .offset(y: width * Layout.kaabaDrop)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .accessibilityHidden(true)
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(description)
        .accessibilityAddTraits(.updatesFrequently)
    }

    private func canvas(scan: Double?) -> some View {
        QiblaDialCanvas(
            pointer: pointer,
            scan: scan,
            alignment: aligned ? 1 : 0,
            markerOpacity: dimmed ? Layout.dimmedOpacity : 1
        )
        .animation(Motion.collapse, value: aligned)
        .animation(Motion.collapse, value: dimmed)
    }

    /// Блик бежит по кольцу: оборот за `scanPeriod`.
    private static func scanAngle(at date: Date) -> Double {
        let period = Layout.scanPeriod
        return date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: period) / period * Layout.fullTurn
    }
}

/// Константы Android `QiblaDial.kt` (dp → pt).
private enum QiblaDialLayout {
    static let ringShare = 0.84
    static let kaabaShare = 0.44
    static let kaabaDrop = 0.05
    static let ticks = 72
    static let tickStep = 5
    static let majorEvery = 45
    static let midEvery = 15
    static let tickInset = 0.05
    static let majorLength = 0.09
    static let midLength = 0.055
    static let minorLength = 0.035
    static let midOpacity = 0.7
    static let minorOpacity = 0.4
    static let alignedHalfArc = 8.0
    static let scanArc = 40.0
    static let scanPeriod: TimeInterval = 2.4
    /// Блик при выключенных анимациях стоит здесь.
    static let scanStillAngle = 30.0
    static let glowOpacity = 0.35
    static let kaabaGlowOpacity = 0.22
    static let kaabaGlowRadius = 0.7
    static let dimmedOpacity = 0.35
    static let fullTurn = 360.0
    /// Верх кольца в углах `Path.addArc` (от оси x по часовой стрелке).
    static let top = -90.0
    static let ringWidth: CGFloat = 1.6
    static let majorWidth: CGFloat = 2.6
    static let midWidth: CGFloat = 1.4
    static let minorWidth: CGFloat = 1.2
    static let arcWidth: CGFloat = 4
    static let arcGlowWidth: CGFloat = 11
    static let scanWidth: CGFloat = 2.8
    static let markerRadius: CGFloat = 8
    static let markerGlowRadius: CGFloat = 14
    static let pointerHalfWidth: CGFloat = 8
    static let pointerHeight: CGFloat = 13
    static let pointerGap: CGFloat = 6
}

/// Рисунок кольца. Совпадение и приглушение переходят плавно (`animatableData`), угол метки - как есть:
/// сглаживает его трекер.
private struct QiblaDialCanvas: View, Animatable {
    let pointer: QiblaPointer?
    let scan: Double?
    var alignment: Double
    var markerOpacity: Double

    @Environment(\.theme) private var theme

    private typealias Layout = QiblaDialLayout

    var animatableData: AnimatablePair<Double, Double> {
        get { AnimatablePair(alignment, markerOpacity) }
        set {
            alignment = newValue.first
            markerOpacity = newValue.second
        }
    }

    var body: some View {
        let palette = theme.palette
        Canvas { context, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let radius = min(size.width, size.height) / 2 * Layout.ringShare
            drawGlow(&context, center: center, radius: radius, color: palette.tabAzkar)
            context.stroke(
                Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)),
                with: .color(palette.gold),
                lineWidth: Layout.ringWidth
            )
            drawTicks(&context, center: center, radius: radius, gold: palette.gold, tick: palette.onAccent)
            if let scan {
                let start = Layout.top - Layout.scanArc + scan
                drawArc(&context, center: center, radius: radius, start: start, sweep: Layout.scanArc,
                        color: palette.goldLight.opacity(Layout.glowOpacity), width: Layout.arcGlowWidth)
                drawArc(&context, center: center, radius: radius, start: start, sweep: Layout.scanArc,
                        color: palette.goldLight, width: Layout.scanWidth)
            }
            if let pointer {
                drawMarker(&context, pointer: pointer, center: center, radius: radius, palette: palette)
            }
            drawTopPointer(&context, center: center, radius: radius, color: palette.onAccent)
        }
    }

    /// Мягкое свечение за Каабой в тон раздела.
    private func drawGlow(_ context: inout GraphicsContext, center: CGPoint, radius: CGFloat, color: Color) {
        let glow = radius * Layout.kaabaGlowRadius
        context.fill(
            Path(ellipseIn: CGRect(x: center.x - glow, y: center.y - glow, width: glow * 2, height: glow * 2)),
            with: .radialGradient(
                Gradient(colors: [color.opacity(Layout.kaabaGlowOpacity), color.opacity(0)]),
                center: center,
                startRadius: 0,
                endRadius: glow
            )
        )
    }

    private func drawTicks(_ context: inout GraphicsContext, center: CGPoint, radius: CGFloat, gold: Color, tick: Color) {
        for index in 0..<Layout.ticks {
            let degrees = index * Layout.tickStep
            let (length, width, color): (Double, CGFloat, Color) =
                if degrees % Layout.majorEvery == 0 { (Layout.majorLength, Layout.majorWidth, gold) }
                else if degrees % Layout.midEvery == 0 { (Layout.midLength, Layout.midWidth, tick.opacity(Layout.midOpacity)) }
                else { (Layout.minorLength, Layout.minorWidth, tick.opacity(Layout.minorOpacity)) }
            let outer = radius * (1 - Layout.tickInset)
            let inner = outer - radius * length
            var line = Path()
            line.move(to: Self.point(on: Double(degrees), distance: outer, center: center))
            line.addLine(to: Self.point(on: Double(degrees), distance: inner, center: center))
            context.stroke(line, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round))
        }
    }

    private func drawMarker(
        _ context: inout GraphicsContext,
        pointer: QiblaPointer,
        center: CGPoint,
        radius: CGFloat,
        palette: Palette
    ) {
        let offset = pointer.offset
        // Дуга - в сторону поворота: около 180° сторона держится (трекер), дуга может пойти длинным путём.
        let sweep: Double =
            if pointer.side == .right && offset < 0 { offset + Layout.fullTurn }
            else if pointer.side == .left && offset > 0 { offset - Layout.fullTurn }
            else { offset }
        if alignment < 1 {
            let color = palette.goldLight.opacity(markerOpacity * (1 - alignment))
            let glow = palette.goldLight.opacity(markerOpacity * (1 - alignment) * Layout.glowOpacity)
            drawArc(&context, center: center, radius: radius, start: Layout.top, sweep: sweep, color: glow, width: Layout.arcGlowWidth)
            drawArc(&context, center: center, radius: radius, start: Layout.top, sweep: sweep, color: color, width: Layout.arcWidth)
        }
        if alignment > 0 {
            let start = Layout.top - Layout.alignedHalfArc
            let sweep = Layout.alignedHalfArc * 2
            drawArc(&context, center: center, radius: radius, start: start, sweep: sweep,
                    color: palette.success.opacity(alignment * Layout.glowOpacity), width: Layout.arcGlowWidth)
            drawArc(&context, center: center, radius: radius, start: start, sweep: sweep,
                    color: palette.success.opacity(alignment), width: Layout.arcWidth)
        }
        let marker = Self.point(on: offset, distance: radius, center: center)
        let fill = SRGBColor(palette.gold).mixed(with: SRGBColor(palette.successLight), by: alignment).color
        context.fill(Self.circle(marker, Layout.markerGlowRadius), with: .color(fill.opacity(markerOpacity * Layout.glowOpacity)))
        context.fill(Self.circle(marker, Layout.markerRadius), with: .color(fill.opacity(markerOpacity)))
    }

    /// Белый треугольник над кольцом остриём вниз - верх телефона.
    private func drawTopPointer(_ context: inout GraphicsContext, center: CGPoint, radius: CGFloat, color: Color) {
        let tipY = center.y - radius - Layout.pointerGap
        let baseY = tipY - Layout.pointerHeight
        var path = Path()
        path.move(to: CGPoint(x: center.x - Layout.pointerHalfWidth, y: baseY))
        path.addLine(to: CGPoint(x: center.x + Layout.pointerHalfWidth, y: baseY))
        path.addLine(to: CGPoint(x: center.x, y: tipY))
        path.closeSubpath()
        context.fill(path, with: .color(color))
    }

    private func drawArc(
        _ context: inout GraphicsContext,
        center: CGPoint,
        radius: CGFloat,
        start: Double,
        sweep: Double,
        color: Color,
        width: CGFloat
    ) {
        guard sweep != 0 else { return }
        var arc = Path()
        // Ось y вниз: `clockwise: false` рисует по часовой стрелке на экране.
        arc.addArc(center: center, radius: radius, startAngle: .degrees(start), endAngle: .degrees(start + sweep), clockwise: sweep < 0)
        context.stroke(arc, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round))
    }

    /// Точка на окружности: `degrees` по часовой стрелке от верха.
    private static func point(on degrees: Double, distance: CGFloat, center: CGPoint) -> CGPoint {
        let angle = degrees * .pi / 180
        return CGPoint(x: center.x + distance * sin(angle), y: center.y - distance * cos(angle))
    }

    private static func circle(_ center: CGPoint, _ radius: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
    }
}
