import SwiftUI

/// Своя плавающая панель вкладок на всех iOS (на iOS 26–27 вместо системной — решение пользователя 2026-10-25):
/// капсула над нижним краем, выбранная вкладка — на «пилюле» в цвет раздела. Касание поднимает
/// стеклянную линзу, она едет за пальцем; отпустили — выбирается вкладка под линзой, линза садится
/// в пилюлю. Порт Android `FloatingTabBar.kt` — числа оттуда (сняты со снимков iOS 26).
///
/// На внутренних экранах (`compact`, Android-first 2026-10-24) панель ×0,7 от нижнего края, ниже на 8,
/// без подписей — только трансформациями отрисовки: вёрстка и отступ содержимого не меняются. Касания —
/// во всю высоту панели, по ширине — как рисунок.
struct FloatingTabBar: View {
    let selection: AppTab
    var compact = false
    let onSelect: (AppTab) -> Void

    @Environment(\.theme) private var theme

    /// Положение пилюли/линзы в долях вкладки и раскрытие линзы (0 — пилюля, 1 — линза).
    @State private var position: CGFloat = 0
    @State private var lift: CGFloat = 0
    /// Растяжение капли по скорости пальца (шире и ниже); отпустили — пружинит обратно с покачиванием.
    @State private var stretch: CGFloat = 0
    @State private var isDragging = false
    /// Вкладка под пальцем — для тактильного щелчка при переходе через вкладку.
    @State private var slotUnderFinger = 0
    @State private var rowWidth: CGFloat = 0
    @State private var barWidth: CGFloat = 0

    /// Высота капсулы — ниже системной iOS (62), как на Android (решение пользователя 2026-10-04).
    static let height: CGFloat = 54
    static let horizontalMargin: CGFloat = 21
    /// Над нижним краем экрана без полоски «домой» (iPhone SE).
    static let bottomMargin: CGFloat = 4
    /// С полоской «домой» панель iOS 26 заходит в нижнюю безопасную зону на 11 pt (снимок iOS 27:
    /// низ капсулы в 23 pt от края при зоне 34); над зоной, как на Android, она стояла заметно выше.
    static let homeIndicatorOverlap: CGFloat = 11

    /// Отступ капсулы от низа безопасной зоны: отрицательный — капсула заходит в зону.
    static func bottomOffset(safeAreaBottom: CGFloat) -> CGFloat {
        safeAreaBottom > 0 ? -homeIndicatorOverlap : bottomMargin
    }

    /// Место под панелью, которое получают экраны вкладок (над нижней безопасной зоной).
    static func reservedHeight(safeAreaBottom: CGFloat) -> CGFloat {
        height + bottomOffset(safeAreaBottom: safeAreaBottom)
    }

    /// Компактная панель: доля, на которую она уменьшается, и сдвиг вниз.
    nonisolated static let compactShrink: CGFloat = 0.3
    nonisolated static let compactLowering: CGFloat = 8
    /// На сколько опускается верх компактной панели — на столько же опускается мини-плеер (Android `CompactBarDrop`).
    static let compactDrop = height * compactShrink + compactLowering
    /// Переход полной ↔ компактной (ζ 0.85, k 400 — Android `TabBarCompactSpring`).
    static let compactAnimation = Animation.interpolatingSpring(mass: 1, stiffness: 400, damping: 34)

    private static let pillInset: CGFloat = 4
    private static let barFillOpacity = 0.07
    private static let barTintOpacity = 0.08
    private static let rimOpacity = 0.45
    private static let pillOpacity = 0.3
    /// Доля цвета раздела в невыбранных значках и подписях.
    private static let idleTint = 0.18
    /// Линза чуть шире вкладки и выше панели.
    nonisolated static let lensScaleX: CGFloat = 0.12
    nonisolated static let lensScaleY: CGFloat = 0.2
    private static let lensRimWidth: CGFloat = 1.2
    private static let lensRimBright = 0.85
    private static let lensRimDim = 0.15
    /// Капля тянется по ходу движения: доля ширины на скорость «одна вкладка в секунду», не больше
    /// `maxStretch`; по высоте сжимается на `stretchSquash` растяжения (Android `LiquidLens`).
    private static let stretchPerSlotSpeed: CGFloat = 0.05
    private static let maxStretch: CGFloat = 0.35
    nonisolated static let stretchSquash: CGFloat = 0.45
    private static let stretchSmoothing: CGFloat = 0.35
    /// Переезд без пальца: растяжение на вкладку пути.
    private static let travelStretchPerSlot: CGFloat = 0.06
    /// Панель при касании подаётся вперёд и насыщается цветом раздела.
    private static let barLiftScale: CGFloat = 0.03
    private static let barGlowOpacity = 0.22
    /// Стекло — как Android: слабое размытие (6 dp) с зерном, без подкраски.
    private static let blurFraction: CGFloat = 0.2
    private static let grainAmount: Float = 0.08
    /// Тень выходит за капсулу — маска тени с этим запасом.
    private static let shadowMaskOutset: CGFloat = 40

    var body: some View {
        let tint = selection.tint(in: theme)
        let idle = SRGBColor(theme.palette.onAccent).mixed(with: SRGBColor(tint), by: Self.idleTint).color
        let itemWidth = rowWidth / CGFloat(AppTab.ordered.count)
        let compactness: CGFloat = compact ? 1 : 0

        ZStack(alignment: .leading) {
            ZStack(alignment: .leading) {
                // Цветная пилюля выбранной вкладки — гаснет, пока раскрыта линза.
                Capsule()
                    .fill(tint.opacity(Self.pillOpacity))
                    .frame(width: itemWidth)
                    .offset(x: position * itemWidth)
                    .opacity(1 - lift)

                HStack(spacing: 0) {
                    ForEach(AppTab.ordered, id: \.self) { tab in
                        TabBarItem(
                            tab: tab,
                            color: tab == selection ? tint : idle,
                            isSelected: tab == selection,
                            compactness: compactness
                        ) {
                            onSelect(tab)
                        }
                    }
                }
                .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { rowWidth = $0 }
            }
            // Преломление под каплей — шейдер (Android `LiquidLensShader`), только пока палец на панели.
            .modifier(LiquidLensEffect(
                position: position,
                lift: lift,
                stretch: stretch,
                itemWidth: itemWidth,
                rowHeight: Self.height - 2 * Self.pillInset,
                tint: tint
            ))

            // Блик по краю линзы — может выходить за панель.
            Capsule()
                .strokeBorder(
                    LinearGradient(
                        stops: [
                            .init(color: theme.palette.onAccent.opacity(Self.lensRimBright), location: 0),
                            .init(color: theme.palette.onAccent.opacity(Self.lensRimDim), location: 0.45),
                            .init(color: theme.palette.onAccent.opacity(Self.lensRimDim), location: 0.55),
                            .init(color: theme.palette.onAccent.opacity(Self.lensRimBright), location: 1),
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    lineWidth: Self.lensRimWidth
                )
                .frame(width: itemWidth)
                .scaleEffect(
                    x: (1 + Self.lensScaleX * lift) * (1 + stretch),
                    y: (1 + Self.lensScaleY * lift) * (1 - Self.stretchSquash * stretch)
                )
                .offset(x: position * itemWidth)
                .opacity(lift)
                .allowsHitTesting(false)
        }
        .padding(Self.pillInset)
        .frame(height: Self.height)
        .background { barBackground(tint: tint) }
        .scaleEffect(1 + Self.barLiftScale * lift)
        .modifier(CompactTransform(compactness: compactness))
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { barWidth = $0 }
        // Касания — на слое без масштаба: во всю высоту панели, по ширине — как рисунок.
        .contentShape(CompactHitShape(compactness: compactness))
        .gesture(dragGesture(itemWidth: itemWidth))
        .animation(Self.compactAnimation, value: compact)
        .fixedTextSize()
        .accessibilityElement(children: .contain)
        .sensoryFeedback(.selection, trigger: slotUnderFinger)
        .animation(Motion.highlight, value: selection)
        .onAppear { position = CGFloat(index(of: selection)) }
        .onChange(of: selection) { _, tab in
            // Вкладку сменили не жестом (повторный выбор, переход из уведомления) — пилюля переезжает с линзой.
            guard !isDragging else { return }
            travel(to: index(of: tab))
        }
    }

    private func barBackground(tint: Color) -> some View {
        let shadow = ShadowToken.card(theme.palette)
        return ZStack {
            // Тень — только снаружи капсулы: стекло прозрачное, и тень под ним темнила бы панель.
            Capsule()
                .fill(.black)
                .shadow(color: shadow.color, radius: shadow.radius, y: shadow.y)
                .mask {
                    Rectangle()
                        .padding(-Self.shadowMaskOutset)
                        .overlay { Capsule().blendMode(.destinationOut) }
                        .compositingGroup()
                }
            // Лёгкое размытие содержимого под капсулой и зерно — как Android (Haze 6 dp, шум 0,08).
            BackdropBlur(fraction: Self.blurFraction)
                .clipShape(Capsule())
            Capsule()
                .fill(.white)
                .colorEffect(ShaderLibrary.glassGrain(.float(Self.grainAmount)))
            Capsule().fill(tint.opacity(Self.barTintOpacity))
            Capsule().fill(theme.palette.onAccent.opacity(Self.barFillOpacity))
            Capsule().fill(tint.opacity(Self.barGlowOpacity)).opacity(lift)
            Capsule().strokeBorder(tint.opacity(Self.rimOpacity), lineWidth: Size.hairline)
        }
    }

    // MARK: - Жест: линза за пальцем

    private func dragGesture(itemWidth: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard itemWidth > 0 else { return }
                let slot = slot(at: value.location.x - Self.pillInset, itemWidth: itemWidth)
                if !isDragging {
                    isDragging = true
                    slotUnderFinger = Int(slot.rounded())
                    withAnimation(TabBarMotion.lensRise) { lift = 1 }
                    withAnimation(TabBarMotion.pill) { position = slot }
                } else {
                    // Ведение — строго за пальцем, без пружины; капля тянется по скорости пальца
                    // (сглаживание — без дрожания от неровных отсчётов скорости).
                    let slotsPerSecond = abs(value.velocity.width) / itemWidth
                    let target = min(slotsPerSecond * Self.stretchPerSlotSpeed, Self.maxStretch)
                    var transaction = Transaction()
                    transaction.disablesAnimations = true
                    withTransaction(transaction) {
                        position = slot
                        stretch += (target - stretch) * Self.stretchSmoothing
                    }
                    slotUnderFinger = Int(slot.rounded())
                }
            }
            .onEnded { value in
                guard itemWidth > 0 else { return }
                let target = Int(slot(at: value.location.x - Self.pillInset, itemWidth: itemWidth).rounded())
                // Палец отпустил — растяжение пружинит обратно с покачиванием, как капля.
                withAnimation(TabBarMotion.stretchWobble) { stretch = 0 }
                onSelect(AppTab.ordered[target])
                travel(to: target, fromFinger: true)
            }
    }

    /// Пилюля переезжает к вкладке; на подходе линза садится в неё. Без пальца (смена вкладки кодом)
    /// капля тянется по длине пути и на подходе пружинит обратно.
    private func travel(to index: Int, fromFinger: Bool = false) {
        // Пилюля уже на месте (нажата выбранная вкладка): анимации переезда нет — её завершение
        // не пришло бы, и линза так и осталась бы поднятой. Садим сразу.
        guard position != CGFloat(index) else {
            isDragging = false
            withAnimation(TabBarMotion.lensSettle) { lift = 0 }
            return
        }
        if lift < 1 { withAnimation(TabBarMotion.lensRise) { lift = 1 } }
        if !fromFinger {
            let distance = abs(CGFloat(index) - position)
            withAnimation(TabBarMotion.lensRise) { stretch = min(distance * Self.travelStretchPerSlot, Self.maxStretch) }
        }
        withAnimation(TabBarMotion.pill, completionCriteria: .logicallyComplete) {
            position = CGFloat(index)
        } completion: {
            isDragging = false
            withAnimation(TabBarMotion.stretchWobble) { stretch = 0 }
            withAnimation(TabBarMotion.lensSettle) { lift = 0 }
        }
    }

    /// `x` — от края пилюль в несжатой панели; в компактном виде рисунок уже — палец переводится в его долю.
    private func slot(at x: CGFloat, itemWidth: CGFloat) -> CGFloat {
        let scale = compact ? 1 - Self.compactShrink : 1
        let center = barWidth / 2 - Self.pillInset
        let drawnX = (x - center) / scale + center
        return min(max(drawnX / itemWidth - 0.5, 0), CGFloat(AppTab.ordered.count - 1))
    }

    private func index(of tab: AppTab) -> Int {
        AppTab.ordered.firstIndex(of: tab) ?? 0
    }
}

/// Пружины панели — Android `spring(dampingRatio, stiffness)` при массе 1: затухание = 2·ζ·√k.
private enum TabBarMotion {
    /// Переезд пилюли (ζ 0.72, k 220) — темп подобран на телефоне пользователя.
    static let pill = Animation.interpolatingSpring(mass: 1, stiffness: 220, damping: 21.4)
    /// Подъём линзы (ζ 0.75, k 300).
    static let lensRise = Animation.interpolatingSpring(mass: 1, stiffness: 300, damping: 26)
    /// Посадка линзы в пилюлю (ζ 0.9, k 400).
    static let lensSettle = Animation.interpolatingSpring(mass: 1, stiffness: 400, damping: 36)
    /// Растяжение капли возвращается с покачиванием (ζ 0.4, k 400 — Android `StretchWobble`).
    static let stretchWobble = Animation.interpolatingSpring(mass: 1, stiffness: 400, damping: 16)
}

/// Вкладка: значок над подписью; в компактном виде подпись гаснет, значок встаёт в центр капсулы.
/// Касания ведёт панель; кнопка — для VoiceOver и Switch Control, подпись читается и в компактном виде.
private struct TabBarItem: View, Animatable {
    let tab: AppTab
    let color: Color
    let isSelected: Bool
    var compactness: CGFloat
    let action: () -> Void

    var animatableData: CGFloat {
        get { compactness }
        set { compactness = newValue }
    }

    private static let iconSize: CGFloat = 26
    private static let labelSize: CGFloat = 10

    @State private var labelHeight: CGFloat = 0

    var body: some View {
        VStack(spacing: Spacing.xxxs) {
            Image(tab.iconName)
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: Self.iconSize, height: Self.iconSize)
                // Без подписи значок опускается на половину её высоты с промежутком.
                .offset(y: (labelHeight + Spacing.xxxs) / 2 * compactness)
            Text(tab.titleKey)
                .font(.system(size: Self.labelSize, weight: .medium))
                .lineLimit(1)
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { labelHeight = $0 }
                .opacity(1 - compactness)
        }
        .foregroundStyle(color)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(tab.titleKey))
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
        .accessibilityAction { action() }
    }
}

/// Компактная панель: ×0,7 от нижнего края и ниже на 8 — только отрисовка, по ходу пружины.
private struct CompactTransform: ViewModifier, Animatable {
    var compactness: CGFloat

    var animatableData: CGFloat {
        get { compactness }
        set { compactness = newValue }
    }

    func body(content: Content) -> some View {
        content
            .scaleEffect(1 - FloatingTabBar.compactShrink * compactness, anchor: .bottom)
            .offset(y: FloatingTabBar.compactLowering * compactness)
    }
}

/// Зона касания: во всю высоту панели (опущенной на 8), по ширине — как сжатый рисунок.
private nonisolated struct CompactHitShape: Shape {
    var compactness: CGFloat

    var animatableData: CGFloat {
        get { compactness }
        set { compactness = newValue }
    }

    func path(in rect: CGRect) -> Path {
        let width = rect.width * (1 - FloatingTabBar.compactShrink * compactness)
        let frame = CGRect(
            x: rect.midX - width / 2,
            y: rect.minY + FloatingTabBar.compactLowering * compactness,
            width: width,
            height: rect.height
        )
        return Path(roundedRect: frame, cornerRadius: rect.height / 2)
    }
}

/// Преломление под каплей (`LiquidLens.metal`, перенос Android `LiquidLensShader`): шейдер получает
/// положение, раскрытие и растяжение по ходу пружин — `Animatable`. Выключен, пока капля не поднята.
private struct LiquidLensEffect: ViewModifier, Animatable {
    var position: CGFloat
    var lift: CGFloat
    var stretch: CGFloat
    let itemWidth: CGFloat
    let rowHeight: CGFloat
    let tint: Color

    /// Как Android: в центре содержимое ×0,9, у ободка сжато ×1,4; ободок — 0,3 высоты; кайма, дымка, окраска.
    private static let zoom: Float = 0.9
    private static let rimSqueeze: Float = 1.4
    private static let edgeFraction: CGFloat = 0.3
    private static let dispersion: Float = 0.06
    private static let haze: Float = 0.12
    private static let tintAmount: Float = 0.9
    /// Ободок выбирает точки дальше от центра — запас выборки за слоем.
    private static let maxSampleOffset = CGSize(width: 40, height: 20)

    var animatableData: AnimatablePair<CGFloat, AnimatablePair<CGFloat, CGFloat>> {
        get { AnimatablePair(position, AnimatablePair(lift, stretch)) }
        set {
            position = newValue.first
            lift = newValue.second.first
            stretch = newValue.second.second
        }
    }

    func body(content: Content) -> some View {
        let progress = min(max(lift, 0), 1)
        let halfWidth = itemWidth / 2 * (1 + FloatingTabBar.lensScaleX * progress) * (1 + stretch)
        let halfHeight = rowHeight / 2 * (1 + FloatingTabBar.lensScaleY * progress)
            * (1 - FloatingTabBar.stretchSquash * stretch)
        let shader = ShaderLibrary.liquidLens(
            .float2((position + 0.5) * itemWidth, rowHeight / 2),
            .float2(halfWidth, halfHeight),
            .float(progress),
            .float(Self.zoom),
            .float(Self.rimSqueeze),
            .float(halfHeight * 2 * Self.edgeFraction),
            .float(Self.dispersion),
            .float(Self.haze),
            .float(Self.tintAmount),
            .color(tint)
        )
        content.layerEffect(shader, maxSampleOffset: Self.maxSampleOffset, isEnabled: progress > 0.001 && itemWidth > 0)
    }
}

/// Лёгкое размытие содержимого под панелью (как Android Haze 6 dp): у системного размытия радиус не задаётся,
/// поэтому эффект ставится не целиком — доля `fraction` приостановленного аниматора (публичный API).
/// Вместе с размытием слабеет и подкраска системного стекла — панель не сереет.
private struct BackdropBlur: UIViewRepresentable {
    let fraction: CGFloat

    func makeUIView(context: Context) -> BlurView {
        BlurView(fraction: fraction)
    }

    func updateUIView(_ view: BlurView, context: Context) {}

    static func dismantleUIView(_ view: BlurView, coordinator: ()) {
        view.stop()
    }

    final class BlurView: UIVisualEffectView {
        private var animator: UIViewPropertyAnimator?
        private let fraction: CGFloat

        init(fraction: CGFloat) {
            self.fraction = fraction
            super.init(effect: nil)
            NotificationCenter.default.addObserver(
                self, selector: #selector(restart), name: UIApplication.willEnterForegroundNotification, object: nil
            )
            restart()
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) { fatalError("init(coder:) не используется") }

        /// Приостановленный аниматор после ухода в фон система завершает — эффект ставится заново.
        @objc private func restart() {
            stop()
            effect = nil
            let animator = UIViewPropertyAnimator(duration: 1, curve: .linear) { [weak self] in
                self?.effect = UIBlurEffect(style: .regular)
            }
            animator.pausesOnCompletion = true
            animator.fractionComplete = fraction
            self.animator = animator
        }

        func stop() {
            animator?.stopAnimation(true)
            animator = nil
        }
    }
}

extension View {
    /// Стек вкладки под своей панелью: системная полоса скрыта, снизу — прозрачное место под капсулу.
    /// Прокрутка уходит под капсулу, мини-плеер (`audioPlayerInset` у экранов) встаёт над ней.
    func floatingTabBarSpace(safeAreaBottom: CGFloat) -> some View {
        toolbarVisibility(.hidden, for: .tabBar)
            .floatingTabBarDestinationSpace(safeAreaBottom: safeAreaBottom)
    }

    /// Экран стека вкладки (поверх главной) - то же место под капсулой. Отступ всего стека
    /// (`floatingTabBarSpace`) UIKit-навигация передаёт только корню: без этого низ списков и кнопки
    /// в конце экранов стека оставались под панелью (iOS 18-25, 2026-10-19). Компактная панель места
    /// не меняет: она сжимается только отрисовкой.
    func floatingTabBarDestinationSpace(safeAreaBottom: CGFloat) -> some View {
        safeAreaInset(edge: .bottom, spacing: 0) {
            Color.clear
                .frame(height: FloatingTabBar.reservedHeight(safeAreaBottom: safeAreaBottom))
                .allowsHitTesting(false)
        }
    }
}

#Preview {
    @Previewable @State var selection = AppTab.azkar
    ZStack(alignment: .bottom) {
        Theme.inabah.gradients.azkarBackground.linear.ignoresSafeArea()
        FloatingTabBar(selection: selection) { selection = $0 }
            .padding(.horizontal, FloatingTabBar.horizontalMargin)
    }
}
