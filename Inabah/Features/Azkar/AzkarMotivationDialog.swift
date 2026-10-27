import SwiftUI

/// «Добавьте ещё азкаров» поверх «Машаа Аллах!» (решение пользователя 2026-10-25, Android `AzkarMotivationDialog`,
/// снимок `74-azkar-motivation.png`): серия `streak` дней, хадис о постоянстве, плашки `counts`.
/// «ОК» — `onOk` (состав азкаров), «В другой раз» и касание мимо — `onLater`. `backdrop` — копия экрана
/// под окном для размытия (`GlassDialog`).
struct AzkarMotivationDialog<Backdrop: View>: View {
    let streak: Int
    let counts: [CompositionCount]
    let onOk: () -> Void
    let onLater: () -> Void
    @ViewBuilder let backdrop: Backdrop

    @Environment(\.theme) private var theme

    private typealias Layout = AzkarMotivationLayout

    var body: some View {
        GeometryReader { screen in
            GlassDialog(
                style: Layout.style,
                tint: theme.palette.tabAzkar,
                rim: theme.palette.tabAzkar,
                onTapOutside: onLater
            ) {
                AzkarMotivationCard(
                    streak: streak,
                    counts: counts,
                    maxBodyHeight: screen.size.height * Layout.bodyMaxShare,
                    onOk: onOk,
                    onLater: onLater
                )
            } backdrop: {
                backdrop
            }
            .frame(width: screen.size.width, height: screen.size.height)
        }
    }
}

/// Числа окна — вне обобщённого типа (в нём нельзя хранимые статические свойства).
nonisolated private enum AzkarMotivationLayout {
    /// Как «Что нового» на Android: ширина до 360, поля 24, угол 34, затемнение 0,35, стекло в цвет раздела 0,35.
    static let style = GlassDialogStyle(
        maxWidth: 360,
        screenMargin: 24,
        cornerRadius: 34,
        scrimOpacity: 0.35,
        tintShare: 0.35,
        rimOpacity: 0.6
    )
    /// Середина окна не выше этой доли экрана — дальше прокручивается, кнопки всегда видны.
    static let bodyMaxShare: CGFloat = 0.6
}

/// Карточка окна: число серии, заголовок, текст, хадис, плашки и кнопки.
private struct AzkarMotivationCard: View {
    let streak: Int
    let counts: [CompositionCount]
    let maxBodyHeight: CGFloat
    let onOk: () -> Void
    let onLater: () -> Void

    @Environment(\.theme) private var theme

    @State private var bodyHeight: CGFloat = 0

    /// Число серии — крупнее заголовка экрана: `largeTitle` (34) × 1,6, как Android.
    private static let streakNumberSize: CGFloat = 54
    /// «Самые любимые Аллахӏом дела - постоянные, даже если их немного» (аль-Бухари 6464, Муслим 783).
    private static let hadithArabic = "أَحَبُّ الأَعْمَالِ إِلَى اللَّهِ أَدْوَمُهَا وَإِنْ قَلَّ"
    /// В одну строку на ширине окна (22 переносил «قَلَّ» на Android).
    private static let hadithArabicSize: Double = 20
    private static let hadithArabicMinScale: CGFloat = 0.75
    /// Подложка хадиса и обводка плашек — светлая дымка поверх стекла.
    private static let hadithFillOpacity = 0.08
    private static let chipBorderOpacity = 0.2

    var body: some View {
        VStack(spacing: Spacing.l) {
            // Высота по замеру содержимого: `ViewThatFits` здесь выбирал прокрутку при запасе места и мельчил
            // арабский текст. Первый кадр с высотой 0 скрыт проявлением окна.
            ScrollView {
                content
                    .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { bodyHeight = $0 }
            }
            .frame(height: min(bodyHeight, maxBodyHeight))
            .scrollBounceBehavior(.basedOnSize)

            // Одна высота, даже если текст соседней перенёсся на вторую строку.
            HStack(spacing: Spacing.m) {
                Button(action: onLater) {
                    Text("azkar.motivation.later")
                }
                .buttonStyle(DialogActionButtonStyle())
                Button(action: onOk) {
                    Text("azkar.motivation.ok")
                }
                .buttonStyle(DialogActionButtonStyle(gold: true))
            }
            .fixedSize(horizontal: false, vertical: true)
        }
        .padding(Spacing.xl)
        .fixedTextSize()
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape, onLater)
    }

    private var content: some View {
        VStack(spacing: Spacing.l) {
            streakNumber
            Text("azkar.motivation.title")
                .font(.title3)
                .foregroundStyle(theme.palette.onAccent)
                .accessibilityAddTraits(.isHeader)
            Text("azkar.motivation.message")
                .font(.callout)
                .foregroundStyle(theme.palette.onAccent)
            hadith
            if !counts.isEmpty {
                chips
            }
        }
        .multilineTextAlignment(.center)
    }

    /// Крупное число и «дней подряд» под ним; VoiceOver читает одной фразой «14 дней подряд».
    private var streakNumber: some View {
        Text(streakText)
            .accessibilityLabel(Text(AzkarStreak.daysText(streak)))
    }

    /// Каталог строк не разрешает форму множественного числа без самого числа, поэтому строка —
    /// «14⏎дней подряд»: первая строка (число) крупно золотом, больше 9999 — «9999+».
    private var streakText: AttributedString {
        var text = AttributedString(localized: "azkar.motivation.days \(streak)")
        text.font = .subheadline
        text.foregroundColor = theme.palette.onAccentSecondary
        guard let newline = text.characters.firstIndex(of: "\n") else { return text }
        let number = text.startIndex..<newline
        if streak > AzkarStreak.displayMax {
            text.replaceSubrange(number, with: AttributedString("\(AzkarStreak.displayMax)+"))
        }
        let numberEnd = text.characters.firstIndex(of: "\n") ?? text.endIndex
        text[text.startIndex..<numberEnd].font = .system(size: Self.streakNumberSize, weight: .bold)
        text[text.startIndex..<numberEnd].foregroundColor = theme.palette.gold
        return text
    }

    /// Хадис о постоянстве: арабский на пергаменте, перевод, передатчик и источник.
    private var hadith: some View {
        VStack(spacing: Spacing.s) {
            ArabicText(
                text: Self.hadithArabic,
                size: Self.hadithArabicSize,
                color: theme.palette.parchmentText,
                alignment: .center,
                lineLimit: 1
            )
            // На узком экране (iPhone 11 Pro) кегль 20 не помещается — мельчает, а не обрезается «…».
            .minimumScaleFactor(Self.hadithArabicMinScale)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Spacing.m)
            .padding(.vertical, Spacing.xs)
            .surface(
                theme.gradients.parchment.linear,
                cornerRadius: Radius.control,
                border: theme.palette.parchmentAccent,
                lineWidth: Size.parchmentBorder
            )
            .accessibilityHidden(true)
            Text("azkar.motivation.hadith")
                .font(.callout.italic())
                .foregroundStyle(theme.palette.onAccent)
            Text("azkar.motivation.hadith.source")
                .font(.caption)
                .foregroundStyle(theme.palette.onAccentSecondary)
        }
        .padding(Spacing.m)
        .frame(maxWidth: .infinity)
        .surface(theme.palette.onAccent.opacity(Self.hadithFillOpacity), cornerRadius: Radius.box)
    }

    /// Плашки в ряд; не помещаются — друг под другом.
    private var chips: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: Spacing.s) { chipViews }
            VStack(spacing: Spacing.s) { chipViews }
        }
    }

    private var chipViews: some View {
        ForEach(counts, id: \.section) { count in
            Text(count.section == .morning
                 ? "azkar.motivation.morning.count \(count.selected) \(count.total)"
                 : "azkar.motivation.evening.count \(count.selected) \(count.total)")
                .font(.footnote)
                .foregroundStyle(theme.palette.onAccentSecondary)
                .lineLimit(1)
                .padding(.horizontal, Spacing.s)
                .padding(.vertical, Spacing.xxxs)
                .overlay {
                    RoundedRectangle(cornerRadius: Radius.control)
                        .strokeBorder(theme.palette.onAccent.opacity(Self.chipBorderOpacity), lineWidth: Size.hairline)
                }
        }
    }
}

#Preview {
    AzkarMotivationDialog(
        streak: 14,
        counts: [
            CompositionCount(section: .morning, selected: 16, total: 21),
            CompositionCount(section: .evening, selected: 1, total: 22),
        ],
        onOk: {},
        onLater: {}
    ) {
        Theme.inabah.gradients.azkarBackground.linear.ignoresSafeArea()
    }
    .appEnvironment(.preview)
}
