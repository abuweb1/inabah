import SwiftUI

/// Экран настроек — своя форма вместо системного `Form` (решение пользователя 2026-10-15: на всех iOS,
/// как Android `SettingsForm.kt`, повторяющий форму iOS 26): на iOS 18 системная форма — с заголовками
/// капсом и мелким скруглением. Группы `SettingsGroup` друг под другом с прокруткой.
struct SettingsForm<Content: View>: View {
    /// Поле над первой группой (под навбаром — 20; у «О сборнике» шапка сразу под навбаром — 0).
    var topPadding: CGFloat = SettingsMetrics.topPadding
    @ViewBuilder let content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SettingsMetrics.groupSpacing) {
                content
            }
            .padding(.horizontal, Spacing.xl)
            .padding(.top, topPadding)
            .padding(.bottom, Spacing.xl)
        }
    }
}

/// Группа настроек: заголовок (полужирный, обычным регистром), карточка строк с разделителями
/// между ними и подпись — Android `SettingsGroup`. Тот же вид вызова, что у системного `Section`.
/// `card: false` — содержимое без карточки (шапка «О сборнике»).
struct SettingsGroup<Content: View, Header: View, Footer: View>: View {
    var card = true
    @ViewBuilder let content: Content
    @ViewBuilder let header: Header
    @ViewBuilder let footer: Footer

    @Environment(\.theme) private var theme

    init(
        card: Bool = true,
        @ViewBuilder content: () -> Content,
        @ViewBuilder header: () -> Header = { EmptyView() },
        @ViewBuilder footer: () -> Footer = { EmptyView() }
    ) {
        self.card = card
        self.content = content()
        self.header = header()
        self.footer = footer()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            header
                .font(.headline.bold())
                .foregroundStyle(theme.palette.onAccent)
                .padding(.horizontal, SettingsMetrics.rowHorizontalPadding)
                .accessibilityAddTraits(.isHeader)
            if card {
                rows
            } else {
                content
            }
            footer
                .font(.footnote)
                .foregroundStyle(theme.palette.onAccentSecondary)
                .padding(.horizontal, SettingsMetrics.rowHorizontalPadding)
        }
    }

    /// Строки на общей карточке; разделитель — между строками, с отступами с обеих сторон.
    private var rows: some View {
        VStack(alignment: .leading, spacing: 0) {
            Group(subviews: content) { subviews in
                ForEach(subviews) { subview in
                    subview
                        .padding(.horizontal, SettingsMetrics.rowHorizontalPadding)
                        .padding(.vertical, Spacing.m)
                        .frame(maxWidth: .infinity, minHeight: SettingsMetrics.rowMinHeight, alignment: .leading)
                    if subview.id != subviews.last?.id {
                        Rectangle()
                            .fill(theme.palette.hairline)
                            .frame(height: Size.hairline)
                            .padding(.horizontal, SettingsMetrics.rowHorizontalPadding)
                            .accessibilityHidden(true)
                    }
                }
            }
        }
        .buttonStyle(SettingsRowButtonStyle())
        .background(theme.palette.subtleFill, in: .rect(cornerRadius: SettingsMetrics.groupRadius))
    }
}

/// Строка-кнопка или ссылка в группе: во всю ширину, нажатие — прозрачностью, без подсветки.
/// Зона нажатия — вся строка вместе с её полями (поля ставит группа снаружи кнопки).
private struct SettingsRowButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, SettingsMetrics.rowHorizontalPadding)
            .padding(.vertical, Spacing.m)
            .contentShape(.rect)
            .padding(.horizontal, -SettingsMetrics.rowHorizontalPadding)
            .padding(.vertical, -Spacing.m)
            .opacity(configuration.isPressed ? PressFeedback.rowOpacity : 1)
            .opacity(isEnabled ? 1 : PressFeedback.disabledOpacity)
    }
}

/// Строка-переход: содержимое и шеврон справа (системный `Form` рисовал его сам).
struct SettingsLinkLabel<Label: View>: View {
    @ViewBuilder let label: Label

    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: Spacing.m) {
            label
            Spacer(minLength: 0)
            Image(systemName: "chevron.forward")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(theme.palette.onAccentTertiary)
                .accessibilityHidden(true)
        }
    }
}

/// Размеры формы — Android `SettingsForm.kt` (сняты с iOS 26).
enum SettingsMetrics {
    static let groupRadius: CGFloat = 26
    static let rowMinHeight: CGFloat = 56
    static let rowHorizontalPadding: CGFloat = 22
    static let groupSpacing: CGFloat = 24
    static let topPadding: CGFloat = 20
}

/// Оформление экрана настроек: градиент раздела, светлый текст, акцент темы, тёмный навбар.
private struct SettingsFormModifier: ViewModifier {
    let background: ThemeGradient

    @Environment(\.theme) private var theme

    func body(content: Content) -> some View {
        content
            .scrollContentBackground(.hidden)
            .foregroundStyle(theme.palette.onAccent)
            .tint(theme.palette.accentLight)
            .background { background.linear.ignoresSafeArea() }
            .toolbarColorScheme(.dark, for: .navigationBar)
    }
}

extension View {
    /// Экран настроек на фоне `background` (фон раздела, к которому относятся настройки).
    func settingsForm(background: ThemeGradient) -> some View {
        modifier(SettingsFormModifier(background: background))
    }

    /// Подложка строки системного `List` (порядок сборников — там перетаскивание строк).
    func settingsRow() -> some View {
        modifier(SettingsRowModifier())
    }
}

private struct SettingsRowModifier: ViewModifier {
    @Environment(\.theme) private var theme

    func body(content: Content) -> some View {
        content.listRowBackground(theme.palette.subtleFill)
    }
}
