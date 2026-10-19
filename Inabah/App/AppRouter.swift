import Foundation
import Observation

enum AppTab: Hashable {
    case azkar
    case hadith
    case makharij
    case settings
}

/// Экраны внутри вкладки «Азкары». `Codable` — для будущих deep links (виджет, уведомления).
nonisolated enum AzkarRoute: Hashable, Codable, Sendable {
    case list(AzkarSection)
    /// «Календарь азкаров» — из капсулы серии на главной.
    case calendar
}

/// Экраны внутри вкладки «Настройки».
nonisolated enum SettingsRoute: Hashable, Codable, Sendable {
    case azkar
    case hadith
    /// Порядок сборников на главной хадисов (из настроек хадисов).
    case hadithOrder
    /// Выбор иконки приложения.
    case appIcon
    /// Выбор палитры оформления.
    case palette
    /// Размер текста переводов и интерфейса.
    case textSize
    /// «О приложении»: источники, политика конфиденциальности, строка «Лицензии».
    case about
    /// Список лицензий шрифта и значков (из «О приложении»).
    case licenses
    /// Полный текст лицензии (из «Лицензий»).
    case license(LicenseDocument)
    /// «История версий» (из «О приложении»).
    case versionHistory
}

/// Экраны внутри вкладки «Хадисы».
nonisolated enum HadithRoute: Hashable, Codable, Sendable {
    case list(HadithCollection)
    case detail(HadithID)
    /// «О сборнике» — из шапки списка сборника.
    case about(HadithCollection)
    /// «Избранное» — с главной хадисов; хадис из него открывается поверх.
    case favorites
}

/// Экраны внутри вкладки «Махрадж» (курс «Учимся читать»). Номер шага - `ReadingStep.number`.
nonisolated enum MakharijRoute: Hashable, Codable, Sendable {
    /// Список шагов курса - из карточки на главной.
    case steps
    /// Урок шага - поверх списка шагов.
    case lesson(Int)
    /// «Весь алфавит» шага: все слоги или все закрытые слоги - поверх урока.
    case alphabet(Int)
    /// Справка к уроку (`topic == nil`) или тема урока «Особые случаи» по номеру - поверх урока.
    case extra(step: Int, topic: Int?)
    /// Карточка буквы из таблицы форм: формы и слова-примеры букв группы - поверх урока.
    case letterForms(step: Int, letter: String)
    /// «Проверить себя» - поверх урока.
    case quiz(Int)
    /// «Читаем слова» - поверх урока.
    case words(Int)
}

/// Навигация приложения: выбранная вкладка и типизированный стек каждой вкладки.
///
/// Типизированный путь вместо `NavigationPath` позволяет открыть любой экран из кода
/// (`open(_:)`) и проверить навигацию в тестах.
@Observable
final class AppRouter {
    var selectedTab: AppTab = .azkar
    var azkarPath: [AzkarRoute] = []
    var hadithPath: [HadithRoute] = []
    var makharijPath: [MakharijRoute] = []
    var settingsPath: [SettingsRoute] = []

    /// Выбор вкладки из таб-бара. Повторный выбор активной вкладки возвращает к её корню.
    /// `TabView` вызывает установку выбора и во время отрисовки (при запуске) — поэтому
    /// состояние меняется, только если действительно есть что менять: иначе SwiftUI
    /// предупреждает «Modifying state during view update».
    func select(_ tab: AppTab) {
        if tab == selectedTab {
            popToRoot(tab)
        } else {
            selectedTab = tab
        }
    }

    func popToRoot(_ tab: AppTab) {
        switch tab {
        case .azkar:
            if !azkarPath.isEmpty { azkarPath.removeAll() }
        case .hadith:
            if !hadithPath.isEmpty { hadithPath.removeAll() }
        case .makharij:
            if !makharijPath.isEmpty { makharijPath.removeAll() }
        case .settings:
            if !settingsPath.isEmpty { settingsPath.removeAll() }
        }
    }

    func open(_ route: AzkarRoute) {
        selectedTab = .azkar
        azkarPath = [route]
    }

    /// Хадис и «О сборнике» открываются поверх списка своего сборника — «назад» ведёт в список,
    /// как в приложении.
    func open(_ route: HadithRoute) {
        selectedTab = .hadith
        switch route {
        case .list, .favorites:
            hadithPath = [route]
        case .detail(let id):
            hadithPath = [.list(id.collection), route]
        case .about(let collection):
            hadithPath = [.list(collection), route]
        }
    }

    /// Урок открывается поверх списка шагов, экраны урока - поверх урока: «назад» ведёт туда, откуда
    /// пришли бы в приложении (как Android `AppRouter.open`).
    func open(_ route: MakharijRoute) {
        selectedTab = .makharij
        switch route {
        case .steps:
            makharijPath = [route]
        case .lesson:
            makharijPath = [.steps, route]
        case .alphabet(let step), .quiz(let step), .words(let step),
             .extra(let step, _), .letterForms(let step, _):
            makharijPath = [.steps, .lesson(step), route]
        }
    }

    /// Нажатие на напоминание: главная азкаров, раздел азкаров или хадис.
    func open(_ target: ReminderTarget) {
        switch target {
        case .azkarHome:
            if selectedTab != .azkar { selectedTab = .azkar }
            popToRoot(.azkar)
        case .azkar(let section):
            open(AzkarRoute.list(section))
        case .hadith(let id):
            open(HadithRoute.detail(id))
        }
    }
}
