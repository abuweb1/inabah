package app.inabah.android.core.qibla

/** Чем кончился запрос доступа без разрешения. */
enum class DenialOutcome {
    /** Окно Android закрыли «Назад» или касанием мимо - не отказ, можно спросить снова. */
    Dismissed,

    /** «Не разрешать» - можно спросить ещё раз (Android предлагает пояснение). */
    Denied,

    /** «Больше не спрашивать» (или окно Android уже не показывается) - только настройки телефона. */
    DeniedForever,
}

/** Ответ быстрее - окна Android не было: он отвечает отказом сразу, когда спрашивать уже нельзя. */
const val NO_DIALOG_ANSWER_MILLIS = 400L

/**
 * Отказ в доступе к местоположению (аудит 2026-10-24): Android не говорит прямо, что «больше не спрашивать».
 * [rationaleBefore] / [rationaleNow] - `shouldShowRequestPermissionRationale` до и после запроса,
 * [answeredInMillis] - сколько шёл запрос.
 */
fun classifyDenial(rationaleBefore: Boolean, rationaleNow: Boolean, answeredInMillis: Long): DenialOutcome = when {
    // После первого «Не разрешать» Android предлагает пояснение.
    rationaleNow -> DenialOutcome.Denied
    // Пояснение было и пропало - выбрали «больше не спрашивать».
    rationaleBefore -> DenialOutcome.DeniedForever
    // Пояснения не было ни до, ни после: окна не было вовсе (быстро) или его закрыли, ничего не выбрав.
    answeredInMillis < NO_DIALOG_ANSWER_MILLIS -> DenialOutcome.DeniedForever
    else -> DenialOutcome.Dismissed
}
