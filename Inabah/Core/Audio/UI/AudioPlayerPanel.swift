import SwiftUI

/// Мини-плеер над таб-баром: название, прогресс с бегунком, перемотка ±10 с,
/// плей/пауза, стоп, «сначала», скрыть и закрыть. В режиме плейлиста — переход
/// к предыдущей/следующей записи и номер повтора.
struct AudioPlayerPanel: View {
    @Environment(AudioPlayerController.self) private var player
    @Environment(\.theme) private var theme

    /// Подпись под названием ужимается, а не обрезается, когда тесно.
    private static let subtitleMinimumScale: CGFloat = 0.85

    var body: some View {
        if let item = player.currentItem {
            VStack(spacing: Spacing.m) {
                // Свайп вниз скрывает плеер — только за ручку и заголовок: на всей панели
                // жест перехватывал касания слайдера, и бегунок «застревал».
                VStack(spacing: Spacing.m) {
                    grabber
                    header(for: item)
                }
                .contentShape(.rect)
                .gesture(hideGesture)
                AudioProgressView()
                controls
            }
            .padding(.horizontal, Spacing.xl)
            .padding(.top, Spacing.s)
            .padding(.bottom, Spacing.l)
            .surface(
                theme.palette.card,
                cornerRadius: Radius.panel,
                border: theme.palette.goldBorder,
                shadow: .floating(theme.palette)
            )
            .overlay(alignment: .leading) { accentStripe }
            .padding(.horizontal, Spacing.m)
            .padding(.bottom, Spacing.s)
        }
    }

    // MARK: - Части

    /// Золотая полоса слева, как на образце; обрезается скруглением панели.
    private var accentStripe: some View {
        HStack(spacing: 0) {
            Rectangle()
                .fill(theme.palette.gold)
                .frame(width: Size.accentStripe)
            Spacer(minLength: 0)
        }
        .clipShape(.rect(cornerRadius: Radius.panel))
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private var grabber: some View {
        Capsule()
            .fill(theme.palette.goldMuted)
            .frame(width: Size.grabber.width, height: Size.grabber.height)
            .accessibilityHidden(true)
    }

    /// Название и кнопки — в одной строке; подпись («Зикр 3 из 16 · повтор 2 из 100») —
    /// под ними на всю ширину, чтобы её не теснили кнопки плейлиста.
    private func header(for item: AudioQueueItem) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xxs) {
            HStack(alignment: .top, spacing: Spacing.m) {
                VStack(alignment: .leading, spacing: Spacing.xxxs) {
                    Text(verbatim: item.track.category)
                        .font(.caption2.weight(.bold))
                        .tracking(Tracking.caption)
                        .foregroundStyle(theme.palette.textSecondary)
                    Text(verbatim: item.track.title)
                        .font(.headline)
                        .foregroundStyle(theme.palette.textPrimary)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityElement(children: .combine)

                headerButtons
            }
            subtitle(for: item)
                .font(.footnote)
                .foregroundStyle(theme.palette.textSecondary)
                .lineLimit(1)
                .minimumScaleFactor(Self.subtitleMinimumScale)
        }
    }

    private var headerButtons: some View {
        HStack(spacing: Spacing.s) {
            Button("audio.player.hide", systemImage: "eye.slash", action: player.hidePanel)
            if player.mode == .playlist {
                Button("audio.player.previous", systemImage: "chevron.up", action: player.previous)
                    .disabled(!player.canGoPrevious)
                Button("audio.player.next", systemImage: "chevron.down", action: player.next)
                    .disabled(!player.canGoNext)
            }
            Button("audio.player.close", systemImage: "xmark", action: player.close)
        }
        .labelStyle(.iconOnly)
        .buttonStyle(squareStyle)
    }

    @ViewBuilder
    private func subtitle(for item: AudioQueueItem) -> some View {
        if player.error != nil {
            // Запись не открылась или не запустилась (в плейлисте плеер уже перешёл к следующей).
            Label("audio.player.error", systemImage: "exclamationmark.triangle.fill")
                .foregroundStyle(theme.palette.gold)
        } else if player.mode == .playlist {
            HStack(spacing: Spacing.xxs) {
                Text("audio.playlist.position \(player.index + 1) \(player.queueCount)")
                if item.repeatCount > 1 {
                    Text(verbatim: "·")
                    Text("audio.playlist.repetition \(player.repetition) \(item.repeatCount)")
                }
            }
        } else {
            Text(verbatim: item.track.subtitle)
        }
    }

    private var controls: some View {
        HStack {
            Button("audio.player.restart", systemImage: "arrow.counterclockwise", action: player.restart)
                .buttonStyle(squareStyle)
            Spacer()
            Button("audio.player.back10", systemImage: "gobackward.10") {
                player.skip(by: -AudioPlayerController.skipInterval)
            }
            .buttonStyle(BareIconButtonStyle(foreground: theme.palette.textPrimary))
            Spacer()
            Button(
                player.isPlaying ? "audio.player.pause" : "audio.player.play",
                systemImage: player.isPlaying ? "pause.fill" : "play.fill",
                action: player.togglePlayPause
            )
            .buttonStyle(ProminentRoundButtonStyle())
            .contentTransition(.symbolEffect(.replace))
            Spacer()
            Button("audio.player.forward10", systemImage: "goforward.10") {
                player.skip(by: AudioPlayerController.skipInterval)
            }
            .buttonStyle(BareIconButtonStyle(foreground: theme.palette.textPrimary))
            Spacer()
            Button("audio.player.stop", systemImage: "stop.fill", action: player.stop)
                .buttonStyle(squareStyle)
        }
        .labelStyle(.iconOnly)
    }

    /// Квадратные кнопки плеера (скрыть, ⌃/⌄, закрыть, стоп, сначала) — золотая рамка, как на образце.
    private var squareStyle: IconButtonStyle {
        IconButtonStyle(
            shape: .roundedSquare,
            size: Size.playerButton,
            foreground: theme.palette.textPrimary,
            background: theme.palette.actionBackground,
            border: theme.palette.goldBorder
        )
    }

    /// Свайп вниз по панели скрывает её (звук продолжает играть).
    private var hideGesture: some Gesture {
        DragGesture(minimumDistance: 20)
            .onEnded { value in
                if value.translation.height > 40 { player.hidePanel() }
            }
    }
}

/// Полоса прогресса с бегунком и временем. Отдельная вьюха: позиция обновляется
/// несколько раз в секунду и перерисовывает только этот блок, а не всю панель.
private struct AudioProgressView: View {
    @Environment(AudioPlayerController.self) private var player
    @Environment(\.theme) private var theme

    /// Позиция пальца на полосе (0…1), пока пользователь тянет бегунок, — для времени под полосой.
    /// Перемотка выполняется при отпускании (`ScrubBar.onSeek`).
    @State private var scrubFraction: Double?

    var body: some View {
        let duration = player.duration
        let shownTime = scrubFraction.map { $0 * duration } ?? player.currentTime
        // Время — с точностью до секунды: текст и значение VoiceOver меняются раз в секунду,
        // а не на каждом отсчёте позиции.
        let elapsed = Self.format(shownTime)
        let total = Self.format(duration, rounding: .toNearestOrAwayFromZero)
        VStack(spacing: Spacing.xxs) {
            ScrubBar(
                fraction: duration > 0 ? player.currentTime / duration : 0,
                trackColor: theme.palette.track,
                fillColor: theme.palette.gold,
                thumbColor: theme.palette.goldLight,
                onScrubChange: { scrubFraction = $0 },
                // Длительность — в момент отпускания: запись могла смениться во время перетаскивания.
                onSeek: { player.seek(to: $0 * player.duration) }
            )
            .accessibilityElement()
            .accessibilityLabel(Text("audio.player.position"))
            .accessibilityValue(Text("audio.player.time \(elapsed) \(total)"))
            .accessibilityAdjustableAction { direction in
                switch direction {
                case .increment: player.skip(by: AudioPlayerController.skipInterval)
                case .decrement: player.skip(by: -AudioPlayerController.skipInterval)
                @unknown default: break
                }
            }

            HStack {
                Text(verbatim: elapsed)
                Spacer()
                // Длительность — до ближайшей секунды (2,98 с → 0:03), прошедшее время — вниз.
                Text(verbatim: total)
            }
            .font(.caption.monospacedDigit())
            .foregroundStyle(theme.palette.textSecondary)
            .accessibilityHidden(true)
        }
    }

    private static func format(_ seconds: TimeInterval, rounding: FloatingPointRoundingRule = .down) -> String {
        Duration.seconds(max(seconds, 0).rounded(rounding)).formatted(.time(pattern: .minuteSecond))
    }
}
