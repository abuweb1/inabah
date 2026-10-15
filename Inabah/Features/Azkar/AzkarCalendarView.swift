import SwiftUI

/// «Календарь азкаров» (решения пользователя 2026-10-11, Android `AzkarCalendarScreen`, снимки `13c–13e`):
/// сводка «подряд / лучшая серия / всего дней», месяц сеткой с листанием до текущего; день — кольцо
/// вокруг числа (утро — золото слева, вечер — 🌑 справа); нажатие на день — подробности над ним.
/// «Сегодня» — `AzkarStore.streakDay`, до загрузки вечерних — дата устройства.
struct AzkarCalendarView: View {
    @Environment(AzkarHistory.self) private var history
    @Environment(AzkarStore.self) private var store
    @Environment(\.theme) private var theme

    var body: some View {
        let today = store.streakDay ?? deviceToday
        AzkarCalendarContent(days: history.days, today: today)
            .audioPlayerInset()
            .background { theme.gradients.azkarBackground.linear.ignoresSafeArea() }
            .navigationTitle(Text("azkar.calendar.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(theme.palette.header, for: .navigationBar)
            .toolbarBackgroundVisibility(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .glassBackButton(tint: theme.palette.tabAzkar, header: theme.palette.header)
    }

    /// Дата устройства в григорианском календаре пояса расписания.
    private var deviceToday: AzkarCalendarDay {
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = store.calendar.timeZone
        let parts = gregorian.dateComponents([.year, .month, .day], from: .now)
        return AzkarCalendarDay(year: parts.year ?? 2026, month: parts.month ?? 1, day: parts.day ?? 1)
            ?? AzkarCalendarDay(year: 2026, month: 1, day: 1)!
    }
}

/// Содержимое календаря по истории и «сегодня» — отдельно от окружения, для превью и снимков.
struct AzkarCalendarContent: View {
    let days: [AzkarCalendarDay: AzkarDay]
    let today: AzkarCalendarDay

    @Environment(\.theme) private var theme
    @Environment(\.locale) private var locale

    /// День, подробности которого открыты.
    @State private var selected: AzkarCalendarDay?

    var body: some View {
        let streak = AzkarStreak(days: days, today: today)
        let months = AzkarCalendarMonth.range(days: days.keys, today: today)
        let formats = CalendarFormats(locale: locale)
        ScrollView {
            VStack(spacing: Spacing.xl) {
                StreakSummary(streak: streak)
                MonthPager(months: months, days: days, today: today, formats: formats, selected: $selected)
                CalendarLegend()
                if streak.fullDays == 0 {
                    Text("azkar.calendar.emptyHint")
                        .font(.footnote)
                        .foregroundStyle(theme.palette.onAccentSecondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(Spacing.xl)
            .overlayPreferenceValue(DayAnchorKey.self) { anchors in
                GeometryReader { proxy in
                    AnchoredPopover(
                        anchor: selected.flatMap { anchors[$0] }.map { proxy[$0] },
                        containerWidth: proxy.size.width,
                        width: DayDetails.width,
                        tint: theme.palette.tabAzkar,
                        onDismiss: dismiss
                    ) {
                        if let selected {
                            DayDetails(label: formats.dayTitle(selected), day: days[selected])
                        }
                    }
                }
            }
        }
        .scrollBounceBehavior(.basedOnSize)
    }

    private func dismiss() {
        withAnimation(Motion.popoverClose) { selected = nil }
    }
}

// MARK: - Сводка

/// Три числа в ряд: подряд, лучшая серия, всего полных дней.
private struct StreakSummary: View {
    let streak: AzkarStreak

    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 0) {
            column(streak.current, label: "azkar.calendar.current")
            column(streak.best, label: "azkar.calendar.best")
            column(streak.fullDays, label: "azkar.calendar.total")
        }
        .padding(.vertical, Spacing.xl)
        .surface(theme.palette.card, cornerRadius: Radius.card, border: theme.palette.hairline)
    }

    private func column(_ value: Int, label: LocalizedStringResource) -> some View {
        VStack(spacing: Spacing.xxs) {
            Self.number(value)
                .font(.title2)
                .foregroundStyle(theme.palette.onAccent)
            Text(label)
                .font(.caption)
                .foregroundStyle(theme.palette.onAccentSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }

    /// Больше 9999 — «9999+».
    private static func number(_ value: Int) -> Text {
        value > AzkarStreak.displayMax ? Text("azkar.streak.numberMax") : Text(verbatim: "\(value)")
    }
}

// MARK: - Месяцы

/// Заголовок «Октябрь 2026» с ‹ › и месяцы листанием; открывается на текущем, в будущее не листается.
private struct MonthPager: View {
    let months: [AzkarCalendarMonth]
    let days: [AzkarCalendarDay: AzkarDay]
    let today: AzkarCalendarDay
    let formats: CalendarFormats
    @Binding var selected: AzkarCalendarDay?

    @Environment(\.theme) private var theme
    @State private var visible: AzkarCalendarMonth?

    var body: some View {
        let current = visible ?? months.last
        let index = current.flatMap { months.firstIndex(of: $0) } ?? months.count - 1
        VStack(spacing: Spacing.m) {
            HStack(spacing: 0) {
                Button { show(index - 1) } label: {
                    Image(systemName: "chevron.left").font(.body.weight(.semibold))
                }
                .buttonStyle(GlassIconButtonStyle(tint: theme.palette.tabAzkar))
                .disabled(index <= 0)
                .accessibilityLabel(Text("azkar.calendar.previous"))

                Text(verbatim: current.map(formats.monthTitle) ?? "")
                    .font(.headline)
                    .foregroundStyle(theme.palette.onAccent)
                    .frame(maxWidth: .infinity)
                    .accessibilityAddTraits(.isHeader)

                Button { show(index + 1) } label: {
                    Image(systemName: "chevron.right").font(.body.weight(.semibold))
                }
                .buttonStyle(GlassIconButtonStyle(tint: theme.palette.tabAzkar))
                .disabled(index >= months.count - 1)
                .accessibilityLabel(Text("azkar.calendar.next"))
            }
            WeekdayHeader(symbols: formats.weekdaySymbols)
            ScrollView(.horizontal) {
                LazyHStack(alignment: .top, spacing: 0) {
                    ForEach(months, id: \.self) { month in
                        MonthGrid(
                            cells: month.cells(firstWeekday: formats.firstWeekday),
                            days: days,
                            today: today,
                            formats: formats,
                            selected: $selected
                        )
                        .containerRelativeFrame(.horizontal)
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: $visible)
            .scrollIndicators(.hidden)
            .onAppear { if visible == nil { visible = months.last } }
            .onChange(of: visible) { old, _ in
                // Подробности дня — только у видимого месяца; первая установка месяца — не листание.
                if old != nil, selected != nil { selected = nil }
            }
        }
    }

    private func show(_ index: Int) {
        guard months.indices.contains(index) else { return }
        withAnimation(Motion.collapse) { visible = months[index] }
    }
}

/// «пн вт ср…» по неделе языка интерфейса; для VoiceOver не читается — дату называет сама ячейка.
private struct WeekdayHeader: View {
    let symbols: [String]

    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(symbols.enumerated()), id: \.offset) { _, symbol in
                Text(verbatim: symbol)
                    .font(.subheadline)
                    .foregroundStyle(theme.palette.onAccentTertiary)
                    .frame(maxWidth: .infinity)
            }
        }
        .accessibilityHidden(true)
    }
}

/// Сетка месяца: недели строками, пустые места — до 1-го числа и после последнего.
private struct MonthGrid: View {
    let cells: [AzkarCalendarDay?]
    let days: [AzkarCalendarDay: AzkarDay]
    let today: AzkarCalendarDay
    let formats: CalendarFormats
    @Binding var selected: AzkarCalendarDay?

    var body: some View {
        VStack(spacing: Spacing.xs) {
            ForEach(Array(stride(from: 0, to: cells.count, by: 7)), id: \.self) { start in
                HStack(spacing: 0) {
                    ForEach(start..<min(start + 7, cells.count), id: \.self) { index in
                        Group {
                            if let date = cells[index] {
                                DayCell(
                                    date: date,
                                    day: days[date],
                                    isToday: date == today,
                                    isFuture: date > today,
                                    label: formats.dayTitle(date),
                                    onSelect: { select(date) }
                                )
                            } else {
                                Color.clear.frame(height: DayCell.size)
                            }
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
            }
        }
    }

    private func select(_ date: AzkarCalendarDay) {
        withAnimation(Motion.dialog) { selected = date }
    }
}

/// Ячейка дня: кольцо с числом. Будущий день — только число, без нажатия и без VoiceOver.
private struct DayCell: View {
    let date: AzkarCalendarDay
    let day: AzkarDay?
    let isToday: Bool
    let isFuture: Bool
    let label: String
    let onSelect: () -> Void

    @Environment(\.theme) private var theme

    /// Ячейка и кольцо — 44: минимум касания и место для числа на крупном шрифте.
    static let size: CGFloat = 44

    var body: some View {
        if isFuture {
            number.frame(width: Self.size, height: Self.size)
                .accessibilityHidden(true)
        } else {
            Button(action: onSelect) {
                ZStack {
                    DayMark(day: day, isToday: isToday)
                    number
                }
                .frame(width: Self.size, height: Self.size)
                .contentShape(.circle)
            }
            .buttonStyle(PressDimButtonStyle())
            .anchorPreference(key: DayAnchorKey.self, value: .bounds) { [date: $0] }
            .accessibilityLabel(Text(description))
        }
    }

    private var number: some View {
        Text(verbatim: "\(date.day)")
            .font(isToday ? .callout.bold() : .callout)
            .foregroundStyle(isFuture ? theme.palette.onAccentTertiary : theme.palette.onAccent)
            .monospacedDigit()
    }

    private var description: LocalizedStringResource {
        switch day?.status {
        case .full: "azkar.calendar.day.full \(label)"
        case .morningOnly: "azkar.calendar.day.morning \(label)"
        case .eveningOnly: "azkar.calendar.day.evening \(label)"
        case nil: "azkar.calendar.day.none \(label)"
        }
    }
}

/// Рамки ячеек дней — для карточки подробностей над выбранным днём.
private struct DayAnchorKey: PreferenceKey {
    static let defaultValue: [AzkarCalendarDay: Anchor<CGRect>] = [:]

    static func reduce(value: inout [AzkarCalendarDay: Anchor<CGRect>], nextValue: () -> [AzkarCalendarDay: Anchor<CGRect>]) {
        value.merge(nextValue()) { $1 }
    }
}

// MARK: - Кольцо дня

/// Кольцо дня (решения пользователя 2026-10-11, Android `DayMark`): под дугами — дорожка `track` по всему
/// кругу, левая дуга — утренние (`gold`), правая — вечерние (`newMoon`, 🌑); раздел полностью — сплошная
/// дуга, начат — пунктир той же толщины, не читался — только дорожка. Толщина — 9/44 размера.
/// `isToday` — лёгкая заливка внутри кольца цветом раздела.
struct DayMark: View {
    let day: AzkarDay?
    var isToday = false

    @Environment(\.theme) private var theme

    private enum Mark {
        case none, started, complete

        init(_ record: AzkarDayRecord?) {
            guard let record else { self = .none; return }
            self = record.isComplete ? .complete : .started
        }
    }

    private enum Metrics {
        static let ringFraction: CGFloat = 9.0 / 44.0
        static let dashOn: CGFloat = 0.6
        static let dashOff: CGFloat = 0.9
        static let todayFillOpacity = 0.22
    }

    var body: some View {
        let palette = theme.palette
        let morning = Mark(day?.morning)
        let evening = Mark(day?.evening)
        Canvas { context, size in
            let side = min(size.width, size.height)
            let width = side * Metrics.ringFraction
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let radius = side / 2 - width / 2
            let ring = Path { $0.addArc(center: center, radius: radius, startAngle: .zero, endAngle: .degrees(360), clockwise: false) }
            context.stroke(ring, with: .color(palette.track), lineWidth: width)
            if isToday {
                let inner = side / 2 - width
                context.fill(
                    Path(ellipseIn: CGRect(x: center.x - inner, y: center.y - inner, width: inner * 2, height: inner * 2)),
                    with: .color(palette.tabAzkar.opacity(Metrics.todayFillOpacity))
                )
            }
            // Углы — по часовой от «трёх часов» (ось y вниз): 90° — низ, 270° — верх.
            Self.half(morning, color: palette.gold, from: 90, center: center, radius: radius, width: width, in: &context)
            Self.half(evening, color: palette.newMoon, from: 270, center: center, radius: radius, width: width, in: &context)
        }
        .accessibilityHidden(true)
    }

    private static func half(
        _ mark: Mark, color: Color, from start: Double,
        center: CGPoint, radius: CGFloat, width: CGFloat, in context: inout GraphicsContext
    ) {
        let dash: [CGFloat]
        switch mark {
        case .none: return
        case .complete: dash = []
        case .started: dash = [width * Metrics.dashOn, width * Metrics.dashOff]
        }
        let arc = Path {
            $0.addArc(center: center, radius: radius, startAngle: .degrees(start), endAngle: .degrees(start + 180), clockwise: false)
        }
        context.stroke(arc, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .butt, dash: dash))
    }
}

// MARK: - Подробности дня и легенда

/// Подробности дня над ячейкой: дата и по разделу «16 из 16» или «не читались».
private struct DayDetails: View {
    let label: String
    let day: AzkarDay?

    @Environment(\.theme) private var theme

    /// Ширина 280: при 240 «Утренние азкары» переносились рядом с «не читались» (Android).
    static let width: CGFloat = 280
    private static let padding: CGFloat = 20
    private static let iconSize: CGFloat = 18

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            Text(verbatim: label)
                .font(.headline)
                .foregroundStyle(theme.palette.onAccent)
                .accessibilityAddTraits(.isHeader)
            ForEach(AzkarSection.allCases, id: \.self) { section in
                row(section, record: day?.record(of: section))
            }
        }
        .padding(Self.padding)
        .frame(width: Self.width, alignment: .leading)
    }

    private func row(_ section: AzkarSection, record: AzkarDayRecord?) -> some View {
        HStack(spacing: Spacing.s) {
            Image(systemName: section.symbolName)
                .font(.system(size: Self.iconSize))
                .foregroundStyle(section.iconColor(in: theme))
                .accessibilityHidden(true)
            Text(section.title)
                .foregroundStyle(theme.palette.onAccent)
                .frame(maxWidth: .infinity, alignment: .leading)
            Group {
                if let record {
                    Text("azkar.calendar.section.progress \(record.completed) \(record.total)")
                } else {
                    Text("azkar.calendar.section.none")
                }
            }
            .foregroundStyle(record?.isComplete == true ? theme.palette.onAccent : theme.palette.onAccentSecondary)
        }
        .font(.subheadline)
        .accessibilityElement(children: .combine)
    }
}

/// «◐ полный день  ◐ утренние  ◑ вечерние».
private struct CalendarLegend: View {
    @Environment(\.theme) private var theme
    @ScaledMetric(relativeTo: .footnote) private var markSize: CGFloat = 22

    private static let complete = AzkarDayRecord(completed: 1, total: 1)

    var body: some View {
        HStack(spacing: Spacing.xl) {
            item(AzkarDay(morning: Self.complete, evening: Self.complete), label: "azkar.calendar.legend.full")
            item(AzkarDay(morning: Self.complete), label: "azkar.calendar.legend.morning")
            item(AzkarDay(evening: Self.complete), label: "azkar.calendar.legend.evening")
        }
        .frame(maxWidth: .infinity)
    }

    private func item(_ day: AzkarDay, label: LocalizedStringResource) -> some View {
        HStack(spacing: Spacing.xs) {
            DayMark(day: day)
                .frame(width: markSize, height: markSize)
            Text(label)
                .font(.footnote)
                .foregroundStyle(theme.palette.onAccentSecondary)
        }
    }
}

// MARK: - Подписи дат

/// Подписи календаря на языке интерфейса: «Октябрь 2026», «10 октября», дни недели и начало недели.
/// Дни — григорианские, без пояса (UTC), как `AzkarCalendarDay`.
private struct CalendarFormats {
    let firstWeekday: Int
    let weekdaySymbols: [String]
    private let monthFormatter: DateFormatter
    private let dayFormatter: DateFormatter
    private let calendar: Calendar
    private let locale: Locale

    init(locale: Locale) {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = locale
        calendar.timeZone = .gmt
        self.calendar = calendar
        self.locale = locale
        firstWeekday = calendar.firstWeekday
        let symbols = calendar.shortStandaloneWeekdaySymbols
        // Строчными, как на Android («пн», `SHORT_STANDALONE`): у iOS — «Пн».
        weekdaySymbols = (0..<7).map { symbols[(calendar.firstWeekday - 1 + $0) % 7].lowercased(with: locale) }
        monthFormatter = Self.formatter("LLLL yyyy", calendar: calendar, locale: locale)
        dayFormatter = Self.formatter("d MMMM", calendar: calendar, locale: locale)
    }

    /// «Октябрь 2026» — с заглавной.
    func monthTitle(_ month: AzkarCalendarMonth) -> String {
        guard let date = calendar.date(from: DateComponents(year: month.year, month: month.month, day: 1)) else { return "" }
        let text = monthFormatter.string(from: date)
        return text.prefix(1).capitalized(with: locale) + text.dropFirst()
    }

    /// «10 октября».
    func dayTitle(_ day: AzkarCalendarDay) -> String {
        guard let date = calendar.date(from: DateComponents(year: day.year, month: day.month, day: day.day)) else { return day.key }
        return dayFormatter.string(from: date)
    }

    private static func formatter(_ format: String, calendar: Calendar, locale: Locale) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.locale = locale
        formatter.timeZone = .gmt
        formatter.dateFormat = format
        return formatter
    }
}

#Preview {
    NavigationStack {
        AzkarCalendarView()
    }
    .appEnvironment(.preview)
}
