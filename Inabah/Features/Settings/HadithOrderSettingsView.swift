import SwiftUI

/// Порядок карточек сборников на главной хадисов: перетаскивание за ручку справа.
struct HadithOrderSettingsView: View {
    @Environment(HadithCollectionOrder.self) private var order
    @Environment(\.theme) private var theme

    var body: some View {
        List {
            Section {
                ForEach(order.collections, id: \.self) { collection in
                    HadithOrderRow(collection: collection)
                        .settingsRow()
                        // Перетаскивание с VoiceOver неудобно — те же перестановки действиями.
                        .accessibilityActions {
                            if order.canMove(collection, by: -1) {
                                Button("settings.hadith.order.moveUp") { order.move(collection, by: -1) }
                            }
                            if order.canMove(collection, by: 1) {
                                Button("settings.hadith.order.moveDown") { order.move(collection, by: 1) }
                            }
                        }
                }
                .onMove { order.move(fromOffsets: $0, toOffset: $1) }
            } footer: {
                Text("settings.hadith.order.footer")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
        }
        // Ручки перетаскивания видны всегда — экран только для этого.
        .environment(\.editMode, .constant(.active))
        .settingsForm(background: theme.gradients.hadithBackground)
        .navigationTitle(Text("settings.hadith.order.title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    withAnimation(Motion.highlight) { order.restoreDefault() }
                } label: {
                    // Значком: текстовая кнопка обрезала заголовок «Порядок сборников».
                    Image(systemName: "arrow.counterclockwise")
                }
                .disabled(order.isDefault)
                .accessibilityLabel(Text("settings.hadith.order.restore"))
            }
        }
        .audioPlayerInset()
    }
}

/// Значок сборника в цвет его карточки и название.
private struct HadithOrderRow: View {
    let collection: HadithCollection

    @Environment(\.theme) private var theme

    private enum Layout {
        static let iconSize: CGFloat = 32
    }

    var body: some View {
        HStack(spacing: Spacing.m) {
            Image(systemName: collection.symbolName)
                .font(.body)
                .foregroundStyle(collection.iconColor(in: theme))
                .frame(width: Layout.iconSize, height: Layout.iconSize)
                .background(collection.cardGradient(in: theme).linear, in: .rect(cornerRadius: Radius.small))
                .accessibilityHidden(true)
            Text(collection.title)
        }
        .padding(.vertical, Spacing.xxs)
    }
}

#Preview {
    NavigationStack {
        HadithOrderSettingsView()
    }
    .appEnvironment(.preview)
}
