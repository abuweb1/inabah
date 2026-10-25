/// Источники «Къиблы» для экрана: доступ, координаты, компас. Модель (`QiblaModel`) экран создаёт сам —
/// своя на каждое открытие.
struct QiblaServices {
    let access: QiblaLocationAccess
    let location: any QiblaLocationSource
    let heading: any QiblaHeadingSource

    /// Core Location и Core Motion.
    static func system() -> QiblaServices {
        QiblaServices(access: QiblaLocationAccess(), location: CoreLocationSource(), heading: CoreHeadingSource())
    }
}
