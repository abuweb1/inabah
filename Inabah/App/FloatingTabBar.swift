import SwiftUI

/// Своя плавающая панель вкладок — копия системной панели iOS 26 для `ChromeStyle.custom` (iOS 18–25):
/// капсула над нижним краем, выбранная вкладка — на «пилюле» в цвет раздела. Касание поднимает
/// стеклянную линзу, она едет за пальцем; отпустили — выбирается вкладка под линзой, линза садится
/// в пилюлю. Порт Android `FloatingTabBar.kt` — числа оттуда (сняты со снимков iOS 26).
struct FloatingTabBar: View {
    let selection: AppTab
    let onSelect: (AppTab) -> Void

    @Environment(\.theme) private var theme

    /// Положение пилюли/линзы в долях вкладки и раскрытие линзы (0 — пилюля, 1 — линза).
    @State private var position: CGFloat = 0
    @State private var lift: CGFloat = 0
    @State private var isDragging = false
    /// Вкладка под пальцем — для тактильного щелчка при переходе через вкладку.
    @State private var slotUnderFinger = 0
    @State private var rowWidth: CGFloat = 0

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

    private static let pillInset: CGFloat = 4
    private static let barFillOpacity = 0.07
    private static let barTintOpacity = 0.08
    private static let rimOpacity = 0.45
    private static let pillOpacity = 0.3
    /// Доля цвета раздела в невыбранных значках и подписях.
    private static let idleTint = 0.18
    /// Линза чуть шире вкладки и выше панели.
    private static let lensScaleX: CGFloat = 0.12
    private static let lensScaleY: CGFloat = 0.2
    private static let lensRimWidth: CGFloat = 1.2
    private static let lensRimBright = 0.85
    private static let lensRimDim = 0.15
    /// Содержимое под линзой увеличено (преломления, как в iOS 26, на iOS 18 нет).
    private static let iconMagnify: CGFloat = 0.18
    /// Панель при касании подаётся вперёд и насыщается цветом раздела.
    private static let barLiftScale: CGFloat = 0.03
    private static let barGlowOpacity = 0.22

    var body: some View {
        let tint = selection.tint(in: theme)
        let idle = SRGBColor(theme.palette.onAccent).mixed(with: SRGBColor(tint), by: Self.idleTint).color
        let itemWidth = rowWidth / CGFloat(AppTab.ordered.count)

        ZStack(alignment: .leading) {
            // Цветная пилюля выбранной вкладки — гаснет, пока раскрыта линза.
            Capsule()
                .fill(tint.opacity(Self.pillOpacity))
                .frame(width: itemWidth)
                .offset(x: position * itemWidth)
                .opacity(1 - lift)

            HStack(spacing: 0) {
                ForEach(Array(AppTab.ordered.enumerated()), id: \.element) { index, tab in
                    TabBarItem(tab: tab, color: tab == selection ? tint : idle, isSelected: tab == selection) {
                        onSelect(tab)
                    }
                    .modifier(LensMagnification(index: CGFloat(index), position: position, lift: lift, amount: Self.iconMagnify))
                }
            }
            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { rowWidth = $0 }

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
                .scaleEffect(x: 1 + Self.lensScaleX * lift, y: 1 + Self.lensScaleY * lift)
                .offset(x: position * itemWidth)
                .opacity(lift)
                .allowsHitTesting(false)
        }
        .padding(Self.pillInset)
        .frame(height: Self.height)
        .background { barBackground(tint: tint) }
        .contentShape(Capsule())
        .gesture(dragGesture(itemWidth: itemWidth))
        .scaleEffect(1 + Self.barLiftScale * lift)
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
            // Размытое содержимое под капсулой; тень — только у этой фигуры.
            Capsule()
                .fill(.ultraThinMaterial)
                .shadow(color: shadow.color, radius: shadow.radius, y: shadow.y)
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
                    // Ведение — строго за пальцем, без пружины.
                    var transaction = Transaction()
                    transaction.disablesAnimations = true
                    withTransaction(transaction) { position = slot }
                    slotUnderFinger = Int(slot.rounded())
                }
            }
            .onEnded { value in
                guard itemWidth > 0 else { return }
                let target = Int(slot(at: value.location.x - Self.pillInset, itemWidth: itemWidth).rounded())
                onSelect(AppTab.ordered[target])
                travel(to: target)
            }
    }

    /// Пилюля переезжает к вкладке; на подходе линза садится в неё.
    private func travel(to index: Int) {
        // Пилюля уже на месте (нажата выбранная вкладка): анимации переезда нет — её завершение
        // не пришло бы, и линза так и осталась бы поднятой. Садим сразу.
        guard position != CGFloat(index) else {
            isDragging = false
            withAnimation(TabBarMotion.lensSettle) { lift = 0 }
            return
        }
        if lift < 1 { withAnimation(TabBarMotion.lensRise) { lift = 1 } }
        withAnimation(TabBarMotion.pill, completionCriteria: .logicallyComplete) {
            position = CGFloat(index)
        } completion: {
            isDragging = false
            withAnimation(TabBarMotion.lensSettle) { lift = 0 }
        }
    }

    private func slot(at x: CGFloat, itemWidth: CGFloat) -> CGFloat {
        min(max(x / itemWidth - 0.5, 0), CGFloat(AppTab.ordered.count - 1))
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
}

/// Вкладка: значок над подписью. Касания ведёт панель; кнопка — для VoiceOver и Switch Control.
private struct TabBarItem: View {
    let tab: AppTab
    let color: Color
    let isSelected: Bool
    let action: () -> Void

    private static let iconSize: CGFloat = 26
    private static let labelSize: CGFloat = 10

    var body: some View {
        VStack(spacing: Spacing.xxxs) {
            Image(tab.iconName)
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: Self.iconSize, height: Self.iconSize)
            Text(tab.titleKey)
                .font(.system(size: Self.labelSize, weight: .medium))
                .lineLimit(1)
        }
        .foregroundStyle(color)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
        .accessibilityAction { action() }
    }
}

/// Увеличение вкладки под линзой — по ходу анимации, а не только в конце: `Animatable`.
private struct LensMagnification: ViewModifier, Animatable {
    let index: CGFloat
    var position: CGFloat
    var lift: CGFloat
    let amount: CGFloat

    var animatableData: AnimatablePair<CGFloat, CGFloat> {
        get { AnimatablePair(position, lift) }
        set {
            position = newValue.first
            lift = newValue.second
        }
    }

    func body(content: Content) -> some View {
        let near = min(max(1 - abs(position - index), 0), 1)
        content.scaleEffect(1 + amount * lift * near)
    }
}

extension View {
    /// Стек вкладки под своей панелью: системная полоса скрыта, снизу — прозрачное место под капсулу.
    /// Прокрутка уходит под капсулу, мини-плеер (`audioPlayerInset` у экранов) встаёт над ней.
    @ViewBuilder
    func floatingTabBarSpace(_ style: ChromeStyle, safeAreaBottom: CGFloat) -> some View {
        switch style {
        case .system:
            self
        case .custom:
            self
                .toolbarVisibility(.hidden, for: .tabBar)
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    Color.clear
                        .frame(height: FloatingTabBar.reservedHeight(safeAreaBottom: safeAreaBottom))
                        .allowsHitTesting(false)
                }
        }
    }

    /// Экран стека вкладки (поверх главной) - то же место под капсулой. Отступ всего стека
    /// (`floatingTabBarSpace`) UIKit-навигация передаёт только корню: без этого низ списков и кнопки
    /// в конце экранов стека оставались под панелью (iOS 18-25, 2026-10-19).
    @ViewBuilder
    func floatingTabBarDestinationSpace(_ style: ChromeStyle, safeAreaBottom: CGFloat) -> some View {
        switch style {
        case .system:
            self
        case .custom:
            self.safeAreaInset(edge: .bottom, spacing: 0) {
                Color.clear
                    .frame(height: FloatingTabBar.reservedHeight(safeAreaBottom: safeAreaBottom))
                    .allowsHitTesting(false)
            }
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
