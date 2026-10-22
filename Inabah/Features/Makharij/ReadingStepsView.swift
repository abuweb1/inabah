import SwiftUI

/// Список шагов «Учимся читать»: прогресс, шаги «путём» - пройденные, текущий, открытые и закрытые
/// (Android `ReadingStepsScreen.kt`, docs/android/06-screens.md 6.7 пп. 2, 7, 15).
struct ReadingStepsView: View {
    @Environment(ReadingStore.self) private var store
    @Environment(\.theme) private var theme

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .audioPlayerInset()
            .background { theme.gradients.makharijBackground.linear.ignoresSafeArea() }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text("makharij.reading.title")
                        .font(.headline.bold())
                        .foregroundStyle(theme.palette.onAccent)
                        .lineLimit(1)
                        .fixedTextSize()
                        .accessibilityAddTraits(.isHeader)
                }
            }
            .glassBackButton(tint: theme.palette.tabMakharij, header: nil)
            .task { await store.load() }
    }

    @ViewBuilder
    private var content: some View {
        switch store.course {
        case .idle, .loading:
            ProgressView()
                .tint(theme.palette.onAccent)
        case .failed:
            ContentUnavailableView {
                Label("content.error.title", systemImage: "exclamationmark.triangle")
            } description: {
                Text("content.error.message")
            } actions: {
                Button("content.error.retry") {
                    Task { await store.load() }
                }
            }
            .foregroundStyle(theme.palette.onAccent)
        case .loaded(let course):
            ReadingStepsList(course: course)
        }
    }
}

/// Шаги курса. Единственная часть экрана, которая читает прогресс: строкам передаётся готовое состояние.
private struct ReadingStepsList: View {
    let course: ReadingCourse

    @Environment(ReadingProgress.self) private var progress
    @Environment(ReadingSettings.self) private var settings
    @Environment(ReminderSettings.self) private var reminders
    @Environment(\.theme) private var theme

    /// Кружок номера - как в строке: нить «пути» идёт через их центры.
    @ScaledMetric(relativeTo: .subheadline) private var badgeSize: CGFloat = ReadingStepRow.badgeSize

    private enum Layout {
        static let lockedOpacity = 0.55
        static let headerBottom: CGFloat = 6
    }

    var body: some View {
        let colors = ReadingColors(palette: theme.palette)
        let completed = ReadingLogic.completedSteps(course: course, lessonsDone: progress.lessonsDone, wordCounts: progress.wordCounts)
        let current = ReadingLogic.currentStep(course: course, completed: completed)
        let lastUnlocked = ReadingLogic.lastUnlocked(course: course, completed: completed)
        ScrollView {
            LazyVStack(spacing: Spacing.s) {
                header(completed: completed, colors: colors)
                    .padding(.bottom, Layout.headerBottom)
                // «Напоминать о занятиях?» - один раз, после первого пройденного шага, пока курс не пройден.
                if !reminders.readingOfferAnswered, !completed.isEmpty, current != nil {
                    ReadingReminderOfferCard(colors: colors)
                        .padding(.bottom, Layout.headerBottom)
                }
                ForEach(Array(course.steps.enumerated()), id: \.element.number) { index, step in
                    let state = ReadingLogic.stepState(
                        course: course, step: step, completed: completed, current: current, allStepsOpen: settings.allStepsOpen
                    )
                    NavigationLink(value: MakharijRoute.lesson(step.number)) {
                        ReadingStepRow(
                            step: step,
                            state: state,
                            isJustUnlocked: ReadingLogic.isJustUnlocked(
                                step: step, state: state, unlockSeen: progress.unlockSeen, lastUnlocked: lastUnlocked
                            ),
                            colors: colors
                        )
                        .opacity(state == .locked ? Layout.lockedOpacity : 1)
                    }
                    .buttonStyle(PressDimButtonStyle())
                    .disabled(state == .locked)
                    // Нить - поверх строки, не тускнеет у закрытых шагов и при нажатии.
                    .overlay(alignment: .topLeading) {
                        StepThread(
                            badgeSize: badgeSize,
                            above: index > 0 ? threadColor(after: course.steps[index - 1], completed: completed) : nil,
                            below: index < course.steps.count - 1 ? threadColor(after: step, completed: completed) : nil
                        )
                    }
                }
            }
            .padding(.horizontal, Spacing.xl)
            .padding(.top, Spacing.s)
            .padding(.bottom, Spacing.xl)
        }
        // Замок последнего открытого шага «отщёлкивается» один раз: показали - запомнить.
        .task(id: lastUnlocked) {
            guard lastUnlocked > progress.unlockSeen else { return }
            try? await Task.sleep(for: Motion.unlockSeenDelay)
            guard !Task.isCancelled else { return }
            progress.markUnlockSeen(lastUnlocked)
        }
    }

    private func header(completed: Set<Int>, colors: ReadingColors) -> some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            Text("makharij.steps.progress \(ReadingLogic.completedCount(course: course, completed: completed)) \(ReadingLogic.numberedStepCount(course: course))")
                .font(.footnote)
                .foregroundStyle(theme.palette.onAccentSecondary)
            LinearProgressBar(
                fraction: ReadingLogic.completedFraction(course: course, completed: completed),
                track: theme.palette.track,
                fill: colors.tint
            )
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Отрезок нити после шага: золотой, если шаг пройден.
    private func threadColor(after step: ReadingStep, completed: Set<Int>) -> Color {
        completed.contains(step.number) ? theme.palette.gold : theme.palette.track
    }
}

/// «Напоминать о занятиях?» (Android `ReadingReminderOfferCard`, 2026-10-20): «Включить» спрашивает разрешение и
/// включает «Пора на урок» утром, днём и вечером; «Не сейчас» - просто скрывает. Ответ запоминается в любом случае
/// и только для курса: предложение азкаров на главной им не отвечается.
private struct ReadingReminderOfferCard: View {
    let colors: ReadingColors

    @Environment(ReminderSettings.self) private var reminders
    @Environment(ReminderScheduler.self) private var scheduler

    /// Идёт запрос разрешения - кнопки неактивны: второе нажатие не запустит второй запрос.
    @State private var isRequesting = false

    var body: some View {
        NoticeCard(symbol: "clock", title: "makharij.reminderOffer.title", message: "makharij.reminderOffer.message") {
            VStack(spacing: Spacing.xs) {
                Button("reminders.offer.enable", action: enable)
                    .buttonStyle(ReadingButtonStyle(colors: colors))
                Button("reminders.offer.later", action: answer)
                    .buttonStyle(ReadingButtonStyle(colors: colors, isProminent: false))
            }
            .padding(.top, Spacing.s)
            .disabled(isRequesting)
        }
    }

    private func enable() {
        isRequesting = true
        Task {
            if await scheduler.requestAuthorization() {
                for slot in ReadingReminderSlot.allCases { reminders.setReading(true, for: slot) }
                scheduler.reschedule()
            }
            answer()
        }
    }

    /// Карточка уходит плавно - шаги не съезжают скачком.
    private func answer() {
        withAnimation(Motion.collapse) { reminders.answerReadingOffer() }
    }
}

/// Нить «пути» через кружок строки: сверху - от середины промежутка до кружка, снизу - от кружка до
/// середины следующего промежутка.
private struct StepThread: View {
    let badgeSize: CGFloat
    let above: Color?
    let below: Color?

    private enum Layout {
        static let width: CGFloat = 2
        static let halfGap = Spacing.s / 2
    }

    var body: some View {
        GeometryReader { proxy in
            let x = Spacing.m + badgeSize / 2 - Layout.width / 2
            let badgeTop = (proxy.size.height - badgeSize) / 2
            if let above {
                Rectangle()
                    .fill(above)
                    .frame(width: Layout.width, height: badgeTop + Layout.halfGap)
                    .offset(x: x, y: -Layout.halfGap)
            }
            if let below {
                Rectangle()
                    .fill(below)
                    .frame(width: Layout.width, height: badgeTop + Layout.halfGap)
                    .offset(x: x, y: badgeTop + badgeSize)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

#Preview {
    NavigationStack {
        ReadingStepsView()
    }
    .appEnvironment(.preview)
}
