import SwiftUI

/// «Прослушать все азкары» — последняя карточка ленты: все записи раздела подряд,
/// независимо от счётчиков. Играет в фоне и на заблокированном экране.
struct AzkarPlayAllCard: View {
    let section: AzkarSection
    let azkar: [Zikr]

    @Environment(AudioPlayerController.self) private var player
    @Environment(PlaylistSettings.self) private var settings
    @Environment(\.theme) private var theme

    private var isThisPlaylistActive: Bool { player.isPlaylistActive(section.playlistID) }

    var body: some View {
        @Bindable var settings = settings

        VStack(alignment: .leading, spacing: Spacing.l) {
            HStack(spacing: Spacing.m) {
                Image(systemName: "headphones")
                    .font(.title2)
                    .foregroundStyle(theme.palette.gold)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: Spacing.xxxs) {
                    Text("audio.playAll.title")
                        .font(.headline)
                        .foregroundStyle(theme.palette.textPrimary)
                    Text("audio.playAll.subtitle")
                        .font(.footnote)
                        .foregroundStyle(theme.palette.textSecondary)
                }
            }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)

            Toggle("audio.playAll.repeats", isOn: $settings.repeatsByCount)
                .tint(theme.palette.success)
                .foregroundStyle(theme.palette.textPrimary)

            option("audio.playAll.pause") {
                Picker("audio.playAll.pause", selection: $settings.pauseBetween) {
                    ForEach(PlaylistSettings.pauseOptions, id: \.self) { seconds in
                        Text("audio.playAll.pause.seconds \(Int(seconds))").tag(seconds)
                    }
                }
            }

            option("audio.playAll.rate") {
                Picker("audio.playAll.rate", selection: $settings.rate) {
                    ForEach(PlaylistSettings.rateOptions, id: \.self) { rate in
                        Text("audio.playAll.rate.value \(Double(rate).formatted(.number))").tag(rate)
                    }
                }
            }

            PlayAllButton(playlistID: section.playlistID, action: start)
                .disabled(azkar.isEmpty)
        }
        .padding(Spacing.xl)
        .surface(theme.palette.card, cornerRadius: Radius.card, border: theme.palette.goldBorder)
        .onChange(of: settings.rate) { applyLiveSettings() }
        .onChange(of: settings.pauseBetween) { applyLiveSettings() }
    }

    private func option(_ title: LocalizedStringKey, @ViewBuilder picker: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(title)
                .font(.subheadline)
                .foregroundStyle(theme.palette.textSecondary)
            // Сегменты — кнопки: не меняются с размером интерфейса.
            picker()
                .pickerStyle(.segmented)
                .fixedTextSize()
        }
    }

    /// Плейлист этого раздела уже идёт — просто открыть плеер; иначе запустить заново.
    private func start() {
        if isThisPlaylistActive {
            player.showPanel()
            return
        }
        let items = azkar.compactMap { zikr in
            zikr.audioTrack().map {
                AudioQueueItem(track: $0, repeatCount: settings.repeatsByCount ? zikr.repetitions : 1)
            }
        }
        player.playAll(
            items,
            id: section.playlistID,
            rate: settings.rate,
            pauseBetween: .seconds(settings.pauseBetween)
        )
    }

    /// Скорость и пауза применяются сразу, если плейлист этого раздела играет.
    private func applyLiveSettings() {
        guard isThisPlaylistActive else { return }
        player.updatePlaylist(rate: settings.rate, pauseBetween: .seconds(settings.pauseBetween))
    }
}

/// Кнопка «Слушать» / «Открыть плеер». Отдельная вьюха — только она читает состояние плеера,
/// поэтому смена записи не перерисовывает переключатель и сегменты карточки.
private struct PlayAllButton: View {
    let playlistID: String
    let action: () -> Void

    @Environment(AudioPlayerController.self) private var player

    var body: some View {
        let isActive = player.isPlaylistActive(playlistID)
        Button(action: action) {
            Label(
                isActive ? "audio.playAll.open" : "audio.playAll.start",
                systemImage: isActive ? "rectangle.bottomhalf.inset.filled" : "play.fill"
            )
        }
        .buttonStyle(PrimaryButtonStyle())
    }
}
