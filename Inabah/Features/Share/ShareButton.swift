import SwiftUI
import UIKit

/// Кнопка «Поделиться»: системное окно с готовым текстом («Скопировать» — его пункт).
/// Значок — свой «Книга со стрелкой» (`shareIcon`, общий с Android), не системный.
/// Оформление кнопки задаёт вызывающий (`buttonStyle`). Текст собирается при нажатии,
/// а не при каждой перерисовке карточки.
struct ShareButton: View {
    /// Подпись: видна при `showsTitle`, иначе — только для VoiceOver.
    var title: LocalizedStringResource = "share.action"
    var showsTitle = false
    var iconSize: CGFloat
    let text: () -> String

    var body: some View {
        Button {
            ShareSheet.present(text: text())
        } label: {
            if showsTitle {
                label
            } else {
                label.labelStyle(.iconOnly)
            }
        }
    }

    private var label: some View {
        Label {
            Text(title)
        } icon: {
            Image(.shareIcon)
                .resizable()
                .scaledToFit()
                .frame(width: iconSize, height: iconSize)
        }
    }
}

/// Системное окно «Поделиться» через `UIActivityViewController` со строкой — не `ShareLink`.
///
/// `ShareLink` отдаёт текст через `Transferable` отложенно (`NSItemProvider`): окно заранее не
/// знает, что это текст: на iPhone пользователя (2026-10-06) не было «Скопировать», а WhatsApp
/// не получал ничего (Telegram получал). Строка, переданная сразу, — обычный текст для всех.
@MainActor
enum ShareSheet {
    static func present(text: String) {
        guard let presenter = topViewController() else { return }
        let controller = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        controller.popoverPresentationController?.sourceView = presenter.view
        presenter.present(controller, animated: true)
    }

    /// Верхний показанный контроллер активной сцены — окно встаёт поверх листов и меню.
    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.keyWindow?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}
