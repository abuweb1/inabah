import SwiftUI

/// Отступы, радиусы и размеры из прототипа — единая шкала для всех экранов.
/// В коде экранов — только эти токены, без числовых литералов.
enum Spacing {
    static let xxxs: CGFloat = 2
    static let xxs: CGFloat = 4
    static let xs: CGFloat = 6
    static let s: CGFloat = 8
    static let m: CGFloat = 12
    static let l: CGFloat = 14
    static let xl: CGFloat = 16
    static let xlPlus: CGFloat = 20
    static let xxl: CGFloat = 22
    static let xxxl: CGFloat = 32
    static let section: CGFloat = 40
}

enum Radius {
    static let small: CGFloat = 5
    /// Квадратные кнопки, плашки.
    static let control: CGFloat = 12
    static let box: CGFloat = 14
    static let card: CGFloat = 18
    static let navCard: CGFloat = 20
    /// Панель мини-плеера.
    static let panel: CGFloat = 24
}

enum Size {
    /// Минимальная зона нажатия по HIG.
    static let minTapTarget: CGFloat = 44
    static let counter: CGFloat = 76
    static let navCardIcon: CGFloat = 38
    static let navCardMinHeight: CGFloat = 118
    static let progressBarHeight: CGFloat = 4
    /// Высота основной кнопки действия («На главную», «Слушать»).
    static let primaryButtonHeight: CGFloat = 50
    /// Акцентная полоса (край плеера, перевод, пергамент).
    static let accentStripe: CGFloat = 3
    /// Кнопки мини-строки выполненной карточки.
    static let miniButton: CGFloat = 38
    /// Квадратные кнопки плеера.
    static let playerButton: CGFloat = 42
    /// Большая кнопка плей/пауза плеера.
    static let playerMainButton: CGFloat = 64
    /// Точка «выполнено» в мини-строке.
    static let statusDot: CGFloat = 8
    /// Ручка панели плеера.
    static let grabber = CGSize(width: 40, height: 5)
    /// Размер «✦»-орнаментов пергамента.
    static let ornament: CGFloat = 11
}

/// Длительности анимаций — единые для всего приложения.
enum Motion {
    /// Сворачивание/разворачивание карточек и блоков: кривая прототипа `cubic-bezier(.4,0,.2,1)`,
    /// чуть дольше его 0.42 с — по отзыву пользователя разворачивание было слишком резким.
    static let collapse = Animation.timingCurve(0.4, 0, 0.2, 1, duration: 0.5)
    static let counterRing = 0.35
    /// Нажатие кнопки (уменьшение).
    static let press = Animation.easeOut(duration: 0.12)
    /// Нажатие крупной карточки.
    static let cardPress = Animation.easeOut(duration: 0.18)
    /// Появление/исчезновение оверлея.
    static let overlay = Animation.easeOut(duration: 0.4)
    /// Подсветка звучащей карточки.
    static let highlight = Animation.easeInOut(duration: 0.3)
    /// Смена кегля арабского кнопками А−/А+.
    static let fontSize = Animation.smooth(duration: 0.3)
    /// Полоса прогресса раздела.
    static let progress = Animation.easeInOut(duration: 0.5)
    /// Пауза между заполнением счётчика и сворачиванием карточки (вспышка «готово»).
    static let collapseDelay = Duration.milliseconds(550)
    /// Пауза перед показом оверлея «Машаа Аллах!» после последнего зикра.
    static let completionDelay = Duration.milliseconds(600)
    /// Период обновления позиции воспроизведения. Бегунок интерполирует движение
    /// между отсчётами анимацией той же длительности — без скачков.
    static let progressTick = Duration.milliseconds(250)
    static let progressTickSeconds: Double = 0.25
}
