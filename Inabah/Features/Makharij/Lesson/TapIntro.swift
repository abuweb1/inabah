import SwiftUI

// «Живые» нажимаемые ячейки курса (docs/android/06-screens.md 6.7 п. 32; Android `ReadingLiveliness.kt`):
// волна по порядку чтения при каждом открытии экрана, надпись-подсказка - один раз, «зов» увеличиваемой
// плитки, кольцо удержания, вспышка нажатия. Только трансформации отрисовки - вёрстка не меняется.

/// Надпись-подсказка у ячейки: рамка ячейки в координатах окна.
struct TapHint: Equatable {
    let target: CGRect
    let text: LocalizedStringResource
}

/// Волна, подсказка и «зов» одного экрана. Ячейки сообщают о себе (`liveCell`), экран - хозяин (`tapIntroHost`).
@Observable
final class TapIntro {
    struct Cell {
        var frame: CGRect
        var hint: LocalizedStringResource
        let zoomable: Bool
    }

    /// Рамки ячеек меняются при каждой прокрутке - не наблюдаются, читаются только в моменты волны и «зова».
    @ObservationIgnored private(set) var cells: [UUID: Cell] = [:]
    @ObservationIgnored var hostFrame: CGRect = .zero

    /// Место ячейки в идущей волне; ячейки вне волны (не видны) в нём не участвуют.
    private(set) var waveOrder: [UUID: Int] = [:]
    /// Номер волны - перезапуск анимации ячеек.
    private(set) var wave = 0
    /// Номер «зова» каждой плитки - перезапуск её покачивания.
    private(set) var calls: [UUID: Int] = [:]
    var hint: TapHint?
    /// Каждое касание экрана - новый отсчёт тишины.
    private(set) var touches = 0
    /// Вступление (волна, подсказка) закончилось - можно отсчитывать тишину.
    private(set) var introFinished = false
    /// Ячейку уже нажимали в этот заход.
    @ObservationIgnored private(set) var tapped = false
    /// Отметить надпись экрана показанной (задаёт вступление захода).
    @ObservationIgnored private var markSeen: () -> Void = {}
    @ObservationIgnored private var idleCallsMade = 0

    func register(_ id: UUID, frame: CGRect, hint: LocalizedStringResource, zoomable: Bool) {
        if cells[id] == nil {
            cells[id] = Cell(frame: frame, hint: hint, zoomable: zoomable)
        } else {
            cells[id]?.frame = frame
            cells[id]?.hint = hint
        }
    }

    func unregister(_ id: UUID) {
        cells[id] = nil
    }

    /// Видимые ячейки (центр внутри экрана), у увеличиваемых экранов - по желанию только увеличиваемые.
    func visibleCells(onlyZoomable: Bool = false) -> [(id: UUID, cell: Cell)] {
        cells.filter { (!onlyZoomable || $0.value.zoomable) && ReadingLiveliness.isVisible($0.value.frame, in: hostFrame) }
            .map { (id: $0.key, cell: $0.value) }
    }

    /// Первая по порядку чтения видимая ячейка.
    func firstCell(onlyZoomable: Bool) -> (id: UUID, cell: Cell)? {
        let visible = visibleCells(onlyZoomable: onlyZoomable)
        let ranks = ReadingLiveliness.readingOrder(visible.map(\.cell.frame))
        return zip(visible, ranks).min { $0.1 < $1.1 }?.0
    }

    /// Запустить волну по видимым ячейкам; результат - её длина в секундах (нет ячеек - 0).
    @discardableResult
    func startWave() -> Double {
        let visible = visibleCells()
        guard !visible.isEmpty else { return 0 }
        let ranks = ReadingLiveliness.readingOrder(visible.map(\.cell.frame))
        waveOrder = Dictionary(uniqueKeysWithValues: zip(visible.map(\.id), ranks))
        wave += 1
        return ReadingLiveliness.waveLength(count: visible.count)
    }

    /// «Зов» плитки - покачивание.
    func call(_ id: UUID) {
        calls[id, default: 0] += 1
    }

    /// Касание экрана: подсказка уходит, тишина отсчитывается заново.
    func touched() {
        if hint != nil { hint = nil }
        touches += 1
    }

    /// Нажали ячейку (или открылся лист буквы): подсказка уходит и больше не показывается.
    func tappedCell() {
        if hint != nil { hint = nil }
        guard !tapped else { return }
        tapped = true
        markSeen()
    }

    /// Новый заход на экран: нажатий ещё не было, «зовы» в тишине - сначала.
    func beginVisit(markSeen: @escaping () -> Void) {
        self.markSeen = markSeen
        tapped = false
        idleCallsMade = 0
        introFinished = false
        hint = nil
    }

    /// Вступление захода на экран: пауза, волна, у увеличиваемых экранов - «зов» первой плитки; надпись - только
    /// если её ещё не видели (`seen`), 4 с, затем `markSeen`. Уход с экрана (отмена) - надпись не отмечается.
    func runIntro(seen: Bool, zoomable: Bool, markSeen: @escaping () -> Void) async {
        beginVisit(markSeen: markSeen)
        defer { introFinished = true }
        guard (try? await Task.sleep(for: ReadingLiveliness.waveDelay)) != nil else { return }
        let length = startWave()
        guard (try? await Task.sleep(for: .seconds(length))) != nil else { return }
        guard let first = firstCell(onlyZoomable: zoomable), !tapped else { return }
        if zoomable { call(first.id) }
        guard !seen else { return }
        hint = TapHint(target: first.cell.frame, text: first.cell.hint)
        AccessibilityNotification.Announcement(String(localized: first.cell.hint)).post()
        guard (try? await Task.sleep(for: ReadingLiveliness.hintDuration)) != nil else { return }
        hint = nil
        tappedCell()
    }

    /// Тишина без касаний: увеличиваемая плитка «зовёт», не больше `idleCalls` раз за заход.
    /// Перезапускается с каждым касанием (`touches`) - отсчёт заново.
    func waitForIdleCall() async {
        while idleCallsMade < ReadingLiveliness.idleCalls {
            guard (try? await Task.sleep(for: ReadingLiveliness.idleDelay)) != nil else { return }
            guard let cell = visibleCells(onlyZoomable: true).randomElement() else { return }
            idleCallsMade += 1
            call(cell.id)
        }
    }
}

extension EnvironmentValues {
    /// Хозяин «живых» ячеек экрана курса; `nil` - ячейки без волны и подсказки.
    @Entry var tapIntro: TapIntro? = nil
}

// MARK: - Хозяин экрана

extension View {
    /// Экран курса - хозяин «живых» ячеек: волна при каждом открытии, надпись-подсказка один раз (ключ -
    /// `ReadingScreenIntro`). Увеличиваемый экран (`zoomable`): подсказка у первой увеличиваемой плитки, «зов» в
    /// тишине, пока слово ни разу не увеличивали. `cellOpened` - открылся лист буквы: как нажатие ячейки.
    /// `intro == nil` - экран без «живых» ячеек.
    @ViewBuilder
    func tapIntroHost(_ intro: ReadingScreenIntro?, tint: Color, cellOpened: Bool = false) -> some View {
        if let intro {
            modifier(TapIntroHost(key: intro.key, zoomable: intro.zoomable, tint: tint, cellOpened: cellOpened))
        } else {
            self
        }
    }
}

private struct TapIntroHost: ViewModifier {
    let key: String
    let zoomable: Bool
    let tint: Color
    let cellOpened: Bool

    @Environment(ReadingSettings.self) private var settings
    /// Одно на жизнь экрана: ячейки регистрируются в нём при раскладке; заход на экран (`task`) - новое вступление.
    @State private var intro = TapIntro()
    @GestureState private var touching = false

    func body(content: Content) -> some View {
        content
            .environment(\.tapIntro, intro)
            .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { intro.hostFrame = $0 }
            .overlay { TapHintLayer(intro: intro, tint: tint) }
            // Наблюдатель касаний: ничего не перехватывает - прокрутка и нажатия работают как раньше.
            .simultaneousGesture(DragGesture(minimumDistance: 0).updating($touching) { _, state, _ in state = true })
            .onChange(of: touching) { _, isTouching in
                if isTouching { intro.touched() }
            }
            .onChange(of: cellOpened) { _, opened in
                if opened { intro.tappedCell() }
            }
            .task(id: key) {
                let settings = settings
                let key = key
                await intro.runIntro(seen: settings.introSeen(key), zoomable: zoomable) { settings.markIntroSeen(key) }
            }
            .task(id: IdleKey(touches: intro.touches, ready: intro.introFinished, learned: settings.zoomLearned)) {
                guard zoomable, intro.introFinished, !settings.zoomLearned else { return }
                await intro.waitForIdleCall()
            }
    }

    private struct IdleKey: Equatable {
        let touches: Int
        let ready: Bool
        let learned: Bool
    }
}

/// Надпись-подсказка: пузырь цвета раздела над ячейкой (места нет - под ней), правым краем к правому краю
/// ячейки, уголок - к её середине. Касаний не перехватывает.
private struct TapHintLayer: View {
    let intro: TapIntro
    let tint: Color

    @Environment(\.theme) private var theme
    @State private var size: CGSize = .zero

    private enum Layout {
        static let gap: CGFloat = 9
        static let arrow = CGSize(width: 12, height: 6)
    }

    var body: some View {
        GeometryReader { proxy in
            let host = proxy.frame(in: .global)
            ZStack(alignment: .topLeading) {
                if let hint = intro.hint {
                    bubble(hint, host: host)
                        .transition(.opacity)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .animation(.easeInOut(duration: ReadingLiveliness.hintFade), value: intro.hint)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func bubble(_ hint: TapHint, host: CGRect) -> some View {
        let target = hint.target.offsetBy(dx: -host.minX, dy: -host.minY)
        let x = min(max(target.maxX - size.width, 0), max(host.width - size.width, 0))
        let above = target.minY - size.height - Layout.gap
        let pointsUp = above < 0
        let y = pointsUp ? target.maxY + Layout.gap - Layout.arrow.height : above + Layout.arrow.height
        let colors = ReadingColors(palette: theme.palette)
        return Text(hint.text)
            .font(.footnote)
            .foregroundStyle(colors.onTint)
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, Spacing.m)
            .padding(.vertical, Spacing.xs)
            .padding(pointsUp ? .top : .bottom, Layout.arrow.height)
            .background {
                PopoverShape(radius: Radius.control, arrowX: target.midX - x, arrowSize: Layout.arrow, pointsUp: pointsUp)
                    .fill(tint)
            }
            .onGeometryChange(for: CGSize.self) { $0.size } action: { size = $0 }
            .opacity(size == .zero ? 0 : 1)
            .offset(x: x, y: y)
    }
}

// MARK: - Ячейка

extension View {
    /// Нажимаемая ячейка курса: участвует в волне, даёт подсказку `hint`, «зовёт» (`zoomable`), вспыхивает
    /// от касания. `observesTaps: false` - ячейка на своих жестах (плитки слов): вспышку и `tappedCell` зовёт сама.
    func liveCell(hint: LocalizedStringResource, tint: Color, zoomable: Bool = false, observesTaps: Bool = true) -> some View {
        modifier(LiveCell(hint: hint, tint: tint, zoomable: zoomable, observesTaps: observesTaps))
    }

    /// Вспышка касания: круг цвета `tint` от точки касания растёт и гаснет внутри формы ячейки.
    func tapFlash(_ flash: TapFlash?, tint: Color) -> some View {
        overlay {
            if let flash {
                TapFlashView(point: flash.point, tint: tint)
                    .id(flash.id)
                    .clipShape(.rect(cornerRadius: Radius.box))
            }
        }
    }
}

/// Касание для вспышки: точка в координатах ячейки и номер (каждое касание - новая вспышка).
struct TapFlash: Equatable {
    let point: CGPoint
    let id: Int

    func next(at point: CGPoint) -> TapFlash { TapFlash(point: point, id: id + 1) }
    static func first(at point: CGPoint) -> TapFlash { TapFlash(point: point, id: 0) }
}

private struct LiveCell: ViewModifier {
    let hint: LocalizedStringResource
    let tint: Color
    let zoomable: Bool
    let observesTaps: Bool

    @Environment(\.tapIntro) private var intro
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var id = UUID()
    @State private var flash: TapFlash?

    private static let glowWidth: CGFloat = 2

    func body(content: Content) -> some View {
        let rank = intro?.waveOrder[id]
        // Кадры анимации собираются вне главного актора - в них только готовые значения.
        let tint = tint
        let still = reduceMotion
        let radius = Radius.box
        let glowWidth = Self.glowWidth
        return content
            .tapFlash(flash, tint: tint)
            .simultaneousGesture(SpatialTapGesture().onEnded { tap in
                guard observesTaps else { return }
                flash = flash?.next(at: tap.location) ?? .first(at: tap.location)
                intro?.tappedCell()
            }, including: observesTaps ? .all : .subviews)
            .keyframeAnimator(initialValue: 0.0, trigger: WaveTrigger(wave: intro?.wave ?? 0, rank: rank)) { view, progress in
                let bump: CGFloat = still ? 0 : ReadingLiveliness.waveBump(progress)
                view
                    .overlay { RoundedRectangle(cornerRadius: radius).stroke(tint.opacity(bump), lineWidth: glowWidth) }
                    .scaleEffect(1 + (ReadingLiveliness.waveScale - 1) * bump)
            } keyframes: { _ in
                // Сначала - скачком в покой: прошлая волна кончилась на 1, плавный спуск дал бы лишний «вдох».
                // Ячейка вне волны (`rank == nil`) так и стоит в покое.
                KeyframeTrack {
                    MoveKeyframe(0)
                    LinearKeyframe(0, duration: Double(rank ?? 0) * ReadingLiveliness.waveStagger)
                    LinearKeyframe(rank == nil ? 0 : 1, duration: ReadingLiveliness.waveBreath)
                }
            }
            .keyframeAnimator(initialValue: 0.0, trigger: intro?.calls[id] ?? 0) { view, progress in
                let phase = still ? 0 : progress
                view
                    .rotationEffect(.degrees(ReadingLiveliness.wiggleRotation(phase)))
                    .scaleEffect(CGFloat(ReadingLiveliness.wiggleScale(phase)))
            } keyframes: { _ in
                KeyframeTrack {
                    MoveKeyframe(0)
                    LinearKeyframe(1, duration: ReadingLiveliness.wiggleDuration)
                }
            }
            .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { frame in
                intro?.register(id, frame: frame, hint: hint, zoomable: zoomable)
            }
            .onDisappear { intro?.unregister(id) }
    }

    private struct WaveTrigger: Equatable {
        let wave: Int
        let rank: Int?
    }
}

/// Круг вспышки: от точки касания до дальнего угла за `Motion.tapFlash`, гаснет к концу.
private struct TapFlashView: View {
    let point: CGPoint
    let tint: Color

    @State private var progress = 0.0

    private static let peakOpacity = 0.3

    var body: some View {
        TapFlashCanvas(progress: progress, point: point, tint: tint)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
            .onAppear {
                withAnimation(Motion.tapFlash) { progress = 1 }
            }
    }

    private struct TapFlashCanvas: View, Animatable {
        var progress: Double
        let point: CGPoint
        let tint: Color

        var animatableData: Double {
            get { progress }
            set { progress = newValue }
        }

        var body: some View {
            Canvas { context, size in
                guard progress < 1 else { return }
                let reach = [CGPoint.zero, CGPoint(x: size.width, y: 0), CGPoint(x: 0, y: size.height), CGPoint(x: size.width, y: size.height)]
                    .map { hypot($0.x - point.x, $0.y - point.y) }
                    .max() ?? 0
                let radius = reach * progress
                let circle = CGRect(x: point.x - radius, y: point.y - radius, width: radius * 2, height: radius * 2)
                context.fill(Path(ellipseIn: circle), with: .color(tint.opacity(TapFlashView.peakOpacity * (1 - progress))))
            }
        }
    }
}

// MARK: - Удержание

extension View {
    /// Кольцо удержания увеличиваемой плитки: пока палец держит (`pressing`), плитка подрастает, по краю от
    /// середины верхнего края по часовой идёт кольцо - и успевает замкнуться к увеличению (`PressFeedback.zoomHold`).
    func holdRing(pressing: Bool, tint: Color) -> some View {
        modifier(HoldRing(pressing: pressing, tint: tint))
    }
}

private struct HoldRing: ViewModifier {
    let pressing: Bool
    let tint: Color

    @State private var progress = 0.0

    private enum Layout {
        /// Кольцо начинает расти через столько после касания (обычное нажатие кольца не показывает).
        static let delay = 0.1
        static let release = 0.2
        static let width: CGFloat = 3
        static let scale: CGFloat = 1.07
    }

    func body(content: Content) -> some View {
        content
            .overlay {
                TopStartRoundedRectangle(cornerRadius: Radius.box)
                    .inset(by: Layout.width / 2)
                    .trim(from: 0, to: progress)
                    .stroke(tint, style: StrokeStyle(lineWidth: Layout.width, lineCap: .round))
                    .opacity(progress > 0 ? 1 : 0)
            }
            .scaleEffect(1 + (Layout.scale - 1) * progress)
            .onChange(of: pressing) { _, isPressing in
                if isPressing {
                    let duration = max(PressFeedback.zoomHold - Layout.delay, 0.01)
                    withAnimation(.linear(duration: duration).delay(Layout.delay)) { progress = 1 }
                } else {
                    withAnimation(.easeOut(duration: Layout.release)) { progress = 0 }
                }
            }
    }
}

/// Скруглённый прямоугольник, чей контур начинается посередине верхнего края и идёт по часовой - для `trim`.
private nonisolated struct TopStartRoundedRectangle: InsettableShape {
    let cornerRadius: CGFloat
    var insetAmount: CGFloat = 0

    func path(in rect: CGRect) -> Path {
        let rect = rect.insetBy(dx: insetAmount, dy: insetAmount)
        let radius = max(min(cornerRadius - insetAmount, rect.width / 2, rect.height / 2), 0)
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addArc(tangent1End: CGPoint(x: rect.maxX, y: rect.minY), tangent2End: CGPoint(x: rect.maxX, y: rect.maxY), radius: radius)
        path.addArc(tangent1End: CGPoint(x: rect.maxX, y: rect.maxY), tangent2End: CGPoint(x: rect.minX, y: rect.maxY), radius: radius)
        path.addArc(tangent1End: CGPoint(x: rect.minX, y: rect.maxY), tangent2End: CGPoint(x: rect.minX, y: rect.minY), radius: radius)
        path.addArc(tangent1End: CGPoint(x: rect.minX, y: rect.minY), tangent2End: CGPoint(x: rect.midX, y: rect.minY), radius: radius)
        path.closeSubpath()
        return path
    }

    func inset(by amount: CGFloat) -> TopStartRoundedRectangle {
        var shape = self
        shape.insetAmount += amount
        return shape
    }
}
