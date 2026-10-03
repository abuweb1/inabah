import SwiftUI

extension View {
    /// Подтверждение разрушительного действия (сброс прогресса) для выбранного значения:
    /// диалог показан, пока `item` не `nil`; закрытие диалога сбрасывает `item`.
    func destructiveConfirmation<Item>(
        _ title: LocalizedStringResource,
        item: Binding<Item?>,
        actionLabel: LocalizedStringResource,
        message: @escaping (Item) -> Text,
        perform: @escaping (Item) -> Void
    ) -> some View {
        confirmationDialog(
            Text(title),
            isPresented: Binding(
                get: { item.wrappedValue != nil },
                set: { if !$0 { item.wrappedValue = nil } }
            ),
            titleVisibility: .visible,
            presenting: item.wrappedValue
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
