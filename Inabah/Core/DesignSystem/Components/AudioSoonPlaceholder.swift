import SwiftUI

/// Заглушка вместо аудио, пока записей нет: неактивный ▶, название и «Аудио скоро».
/// У хадиса — на странице под отметками, у азкаров — вместо «Прослушать все»
/// (записей пока нет — с 2026-10-06; свои записи подключатся полем `audio` в данных).
struct AudioSoonPlaceholder: View {
    let title: Text

    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: Spacing.m) {
            Image(systemName: "play.fill")
                .font(.headline)
                .foregroundStyle(theme.palette.onAccentTertiary)
                .frame(width: Size.minTapTarget, height: Size.minTapTarget)
                .background(theme.palette.track, in: .circle)
                // Неактивный значок — не кнопка «Воспроизвести»: VoiceOver читает только подписи.
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.xxxs) {
                title
                    .font(.caption)
                    .foregroundStyle(theme.palette.onAccentSecondary)
                Text("audio.soon")
                    .font(.caption2)
                    .foregroundStyle(theme.palette.onAccentTertiary)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, Spacing.m)
        .padding(.horizontal, Spacing.l)
        .surface(theme.palette.subtleFill, cornerRadius: Radius.box)
        .accessibilityElement(children: .combine)
    }
}
