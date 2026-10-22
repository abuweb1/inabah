import SwiftUI

extension View {
    /// Подтверждение разрушительного действия (сброс прогресса) для выбранного значения:
    /// показано, пока `item` не `nil`; закрытие сбрасывает `item`.
    ///
    /// `ChromeStyle.system` — системный `confirmationDialog` (на iOS 26 — стеклянная карточка у кнопки).
    /// `.custom` (iOS 18–25, там системный — лист снизу) — своя карточка над строкой, как Android
    /// `ConfirmationPopover`: строка отмечается `confirmationAnchor(_:)` тем же значением, что кладётся в `item`.
    func destructiveConfirmation<Item: Hashable>(
        _ title: LocalizedStringResource,
        item: Binding<Item?>,
        actionLabel: LocalizedStringResource,
        tint: Color,
        message: @escaping (Item) -> Text,
        perform: @escaping (Item) -> Void
    ) -> some View {
        modifier(DestructiveConfirmationModifier(
            title: title,
            item: item,
            actionLabel: actionLabel,
            tint: tint,
            message: message,
            perform: perform
        ))
    }

    /// Строка, над которой всплывает подтверждение `destructiveConfirmation` для значения `id`.
    func confirmationAnchor(_ id: some Hashable) -> some View {
        anchorPreference(key: ConfirmationAnchorKey.self, value: .bounds) { [AnyHashable(id): $0] }
    }
}

/// Рамки строк, у которых может всплыть подтверждение, — по значению подтверждения.
private struct ConfirmationAnchorKey: PreferenceKey {
    static let defaultValue: [AnyHashable: Anchor<CGRect>] = [:]

    static func reduce(value: inout [AnyHashable: Anchor<CGRect>], nextValue: () -> [AnyHashable: Anchor<CGRect>]) {
        value.merge(nextValue()) { $1 }
    }
}

private struct DestructiveConfirmationModifier<Item: Hashable>: ViewModifier {
    let title: LocalizedStringResource
    @Binding var item: Item?
    let actionLabel: LocalizedStringResource
    let tint: Color
    let message: (Item) -> Text
    let perform: (Item) -> Void

    @Environment(\.chromeStyle) private var chromeStyle

    func body(content: Content) -> some View {
        if chromeStyle == .custom {
            content
                .overlayPreferenceValue(ConfirmationAnchorKey.self) { anchors in
                    GeometryReader { proxy in
                        AnchoredPopover(
                            anchor: item.flatMap { anchors[AnyHashable($0)] }.map { proxy[$0] },
                            containerWidth: proxy.size.width,
                            width: ConfirmationCard.width,
                            tint: tint,
                            minSpaceAbove: ConfirmationCard.estimatedHeight,
                            onDismiss: dismiss
                        ) {
                            if let item {
                                ConfirmationCard(title: title, message: message(item), actionLabel: actionLabel) {
                                    perform(item)
                                    dismiss()
                                }
                            }
                        }
                    }
                }
                .animation(item == nil ? Motion.popoverClose : Motion.dialog, value: item == nil)
        } else {
            content.confirmationDialog(
                Text(title),
                isPresented: Binding(
                    get: { item != nil },
                    set: { if !$0 { item = nil } }
                ),
                titleVisibility: .visible,
                presenting: item
            ) { value in
                Button(role: .destructive) {
                    perform(value)
                } label: {
                    Text(actionLabel)
                }
            } message: { value in
                message(value)
            }
        }
    }

    private func dismiss() {
        item = nil
    }
}

/// Содержимое подтверждения — Android `ConfirmationPopover`: заголовок, пояснение и красная кнопка-капсула;
/// «Отмена» нет — закрытие касанием мимо (или жестом «назад» VoiceOver).
private struct ConfirmationCard: View {
    let title: LocalizedStringResource
    let message: Text
    let actionLabel: LocalizedStringResource
    let action: () -> Void

    @Environment(\.theme) private var theme

    static let width: CGFloat = 240
    /// Высота карточки с уголком при обычном тексте — меньше места над строкой, открыть под ней.
    static let estimatedHeight: CGFloat = 190
    private static let padding: CGFloat = 20
    private static let buttonFillOpacity = 0.1

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            Text(title)
                .font(.headline.bold())
                .foregroundStyle(theme.palette.onAccent)
                .accessibilityAddTraits(.isHeader)
            message
                .font(.footnote)
                .foregroundStyle(theme.palette.onAccentSecondary)
            Button(action: action) {
                Text(actionLabel)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(theme.palette.destructive)
                    .frame(maxWidth: .infinity, minHeight: Size.primaryButtonHeight)
                    .background(theme.palette.onAccent.opacity(Self.buttonFillOpacity), in: .capsule)
                    .contentShape(.capsule)
            }
            .buttonStyle(PressDimButtonStyle())
            .padding(.top, Spacing.m)
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(Self.padding)
    }
}
