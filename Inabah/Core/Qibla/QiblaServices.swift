import Foundation
import SwiftUI

/// Источники «Къиблы» для экрана: доступ, координаты, компас и разовые подсказки. Модель (`QiblaModel`) экран
/// создаёт сам — своя на каждое открытие.
struct QiblaServices {
    let access: QiblaLocationAccess
    let location: any QiblaLocationSource
    let heading: any QiblaHeadingSource
    let hints: QiblaHints

    /// Core Location и Core Motion.
    static func system(defaults: UserDefaults) -> QiblaServices {
        QiblaServices(
            access: QiblaLocationAccess(),
            location: CoreLocationSource(),
            heading: CoreHeadingSource(),
            hints: QiblaHints(defaults: defaults)
        )
    }
}

extension EnvironmentValues {
    /// `nil` — вне приложения (превью без окружения): экран «Къибла» пуст, кнопка входа без подсказки.
    @Entry var qibla: QiblaServices?
}
