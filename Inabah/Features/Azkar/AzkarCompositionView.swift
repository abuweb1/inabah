import SwiftUI

/// «Состав азкаров» раздела (Android-first 2026-10-24, вариант «Б» наброска `docs/design/azkar-composition.html`):
/// «В наборе» — убрать «−» и порядок перетаскиванием за ручку справа, «Можно добавить» — «+» в конец набора,
/// «↺» в навбаре — набор по умолчанию (с подтверждением, сразу).
///
/// Правки меняют черновик экрана; набор сохраняется и применяется при уходе с экрана и в фон — как время
/// азкаров: счёт и прогресс пересчитываются один раз, по итогу.
struct AzkarCompositionView: View {
    let section: AzkarSection

    @Environment(AzkarStore.self) private var store
    @Environment(ReminderScheduler.self) private var scheduler
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.theme) private var theme

    /// Номера набора, пока экран открыт; `nil` — раздел ещё не загружен.
    @State private var draft: [Int]?
    @State private var pendingRestore: RestoreRequest?

    var body: some View {
        content
            .settingsForm(background: theme.gradients.azkarBackground)
            .navigationTitle(Text(section.title))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    RestoreCompositionButton(isDefault: isDefault) { pendingRestore = .restore }
                }
            }
            .glassBackButton(tint: theme.palette.tabAzkar, header: nil)
            .destructiveConfirmation(
                "azkar.composition.restore.title",
                item: $pendingRestore,
                actionLabel: "azkar.composition.restore.action",
                tint: theme.palette.tabAzkar,
                message: { _ in Text("azkar.composition.restore.message") },
                perform: { _ in restoreDefault() }
            )
            .audioPlayerInset()
            .task {
                await store.load(section)
                if draft == nil, !store.catalog(of: section).isEmpty {
                    draft = store.composition(of: section)
                }
            }
            .onDisappear(perform: commit)
            .onChange(of: scenePhase) { _, phase in
                if phase == .background { commit() }
            }
    }

    @ViewBuilder
    private var content: some View {
        if let draft {
            CompositionList(
                catalog: store.catalog(of: section),
                numbers: draft,
                onChange: { self.draft = $0 }
            )
            // Карточка подтверждения на iOS 18–25 ищет строку-якорь, а кнопка — в навбаре: якорь под ней,
            // у верхнего края списка, — карточка откроется под шапкой уголком к «↺» (как Android).
            .overlay(alignment: .topTrailing) {
                Color.clear
                    .frame(width: Size.minTapTarget, height: Size.hairline)
                    .padding(.trailing, Spacing.xl)
                    .confirmationAnchor(RestoreRequest.restore)
                    .accessibilityHidden(true)
            }
        } else {
            ProgressView()
                .tint(theme.palette.onAccent)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private var isDefault: Bool {
        draft == nil || draft == store.defaultComposition(of: section)
    }

    private func restoreDefault() {
        let numbers = store.defaultComposition(of: section)
        withAnimation(Motion.highlight) { draft = numbers }
        apply(numbers)
    }

    private func commit() {
        guard let draft else { return }
        apply(draft)
    }

    /// Новый набор — новый счёт раздела: «ещё не прочитаны» в напоминаниях считаются по нему.
    private func apply(_ numbers: [Int]) {
        guard numbers != store.composition(of: section) else { return }
        store.applyComposition(numbers, to: section)
        scheduler.reschedule()
    }
}

/// Подтверждение «Вернуть набор по умолчанию» ждёт ответа — значение для `destructiveConfirmation(item:)`.
private enum RestoreRequest: Hashable {
    case restore
}

/// «↺» — как у «Порядка сборников».
private struct RestoreCompositionButton: View {
    let isDefault: Bool
    let action: () -> Void

    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            Image(systemName: "arrow.counterclockwise")
                .fontWeight(.medium)
        }
        .headerIconButtonStyle(tint: theme.palette.tabAzkar, header: nil)
        .disabled(isDefault)
        .accessibilityLabel(Text("azkar.composition.restore"))
        .fixedTextSize()
    }
}

/// Две группы: набор (перетаскивание) и то, что можно добавить. Ручки видны всегда — экран для этого.
private struct CompositionList: View {
    let catalog: [Zikr]
    let numbers: [Int]
    let onChange: ([Int]) -> Void

    @Environment(\.theme) private var theme

    var body: some View {
        let byNumber = Dictionary(catalog.map { ($0.number, $0) }, uniquingKeysWith: { first, _ in first })
        let inSet = numbers.compactMap { byNumber[$0] }
        let available = catalog.filter { !numbers.contains($0.number) }
        List {
            Section {
                ForEach(Array(inSet.enumerated()), id: \.element.id) { index, zikr in
                    CompositionRow(zikr: zikr, place: index + 1) {
                        CompositionToggle(kind: .remove, isEnabled: inSet.count > 1) { remove(zikr) }
                    } actions: {
                        // Перетаскивание с VoiceOver неудобно — те же перестановки и «Убрать» действиями.
                        if index > 0 {
                            Button("azkar.composition.moveUp") { move(zikr, by: -1) }
                        }
                        if index < inSet.count - 1 {
                            Button("azkar.composition.moveDown") { move(zikr, by: 1) }
                        }
                        if inSet.count > 1 {
                            Button("azkar.composition.remove") { remove(zikr) }
                        }
                    }
                    .settingsRow()
                }
                .onMove { source, destination in
                    var reordered = numbers
                    reordered.move(fromOffsets: source, toOffset: destination)
                    onChange(reordered)
                }
            } header: {
                Text("azkar.composition.inSet \(inSet.count)")
                    .groupHeader()
            } footer: {
                Text("azkar.composition.hint")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }

            if !available.isEmpty {
                Section {
                    ForEach(available) { zikr in
                        CompositionRow(zikr: zikr, place: nil) {
                            CompositionToggle(kind: .add, isEnabled: true) { onChange(numbers + [zikr.number]) }
                        } actions: {
                            Button("azkar.composition.add") { onChange(numbers + [zikr.number]) }
                        }
                        .settingsRow()
                        .moveDisabled(true)
                    }
                } header: {
                    Text("azkar.composition.available \(available.count)")
                        .groupHeader()
                }
            }
        }
        .environment(\.editMode, .constant(.active))
    }

    private func remove(_ zikr: Zikr) {
        guard numbers.count > 1 else { return }
        onChange(numbers.filter { $0 != zikr.number })
    }

    private func move(_ zikr: Zikr, by offset: Int) {
        guard let index = numbers.firstIndex(of: zikr.number), numbers.indices.contains(index + offset) else { return }
        var reordered = numbers
        reordered.swapAt(index, index + offset)
        onChange(reordered)
    }
}

/// Заголовок группы — как у `SettingsGroup`: крупнее и светлее системного.
private struct GroupHeaderModifier: ViewModifier {
    @Environment(\.theme) private var theme

    func body(content: Content) -> some View {
        content
            .font(.headline.bold())
            .foregroundStyle(theme.palette.onAccent)
            .textCase(nil)
    }
}

private extension View {
    func groupHeader() -> some View {
        modifier(GroupHeaderModifier())
    }
}

/// «−» (красный) или «+» (зелёный) слева в строке.
private struct CompositionToggle: View {
    enum Kind {
        case add, remove
    }

    let kind: Kind
    let isEnabled: Bool
    let action: () -> Void

    @Environment(\.theme) private var theme

    @ScaledMetric(relativeTo: .body) private var iconSize: CGFloat = 22

    var body: some View {
        Button(action: action) {
            Image(systemName: kind == .add ? "plus.circle.fill" : "minus.circle.fill")
                .font(.system(size: iconSize))
                .symbolRenderingMode(.palette)
                .foregroundStyle(.white, kind == .add ? theme.palette.success : theme.palette.destructive)
                .frame(width: Size.minTapTarget, height: Size.minTapTarget)
                .contentShape(.rect)
        }
        // В режиме правки строки `List` кнопка с обычным стилем не нажимается.
        .buttonStyle(.borderless)
        .disabled(!isEnabled)
        .opacity(isEnabled ? 1 : PressFeedback.disabledOpacity)
        .accessibilityLabel(Text(kind == .add ? "azkar.composition.add" : "azkar.composition.remove"))
    }
}

/// Номер места (в наборе), начало арабского и перевода, «×N». Для VoiceOver — кнопка отдельно,
/// остальное одним описанием с действиями `actions`.
private struct CompositionRow<Toggle: View, Actions: View>: View {
    let zikr: Zikr
    let place: Int?
    @ViewBuilder let toggle: Toggle
    @ViewBuilder let actions: Actions

    @Environment(\.theme) private var theme

    private static var arabicSize: Double { 19 }
    private static var arabicWords: Int { 6 }

    var body: some View {
        HStack(spacing: Spacing.s) {
            toggle
            summary
                .accessibilityElement(children: .combine)
                .accessibilityActions { actions }
        }
    }

    private var summary: some View {
        HStack(spacing: Spacing.s) {
            if let place {
                Text(verbatim: "\(place)")
                    .font(.footnote.monospacedDigit())
                    .foregroundStyle(theme.palette.onAccentTertiary)
            }
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                ArabicText(
                    text: ArabicText.preview(of: zikr.arabic, words: Self.arabicWords),
                    size: Self.arabicSize,
                    color: theme.palette.onAccent,
                    lineLimit: 1
                )
                if let translation = zikr.translation {
                    Text(verbatim: translation.text)
                        .font(.footnote)
                        .foregroundStyle(theme.palette.onAccentSecondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 0)
            Text("azkar.composition.repetitions \(zikr.repetitions)")
                .font(.subheadline)
                .foregroundStyle(theme.palette.gold)
        }
    }
}
