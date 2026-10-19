import SwiftUI

/// «О приложении»: версия, «История версий», источники текстов и записей, политика конфиденциальности
/// и строка «Лицензии». Источники согласованы с пользователем 2026-10-05.
struct AboutView: View {
    @Environment(\.theme) private var theme

    var body: some View {
        SettingsForm {
            SettingsGroup {
                AboutHeader()
            }

            // Отдельной группой сразу под шапкой (как на Android, `10b`).
            SettingsGroup {
                NavigationLink(value: SettingsRoute.versionHistory) {
                    SettingsLinkLabel {
                        Text("about.versionHistory")
                    }
                }
            }

            SettingsGroup {
                AboutTextRow(title: "about.sources.azkar.title", text: "about.sources.azkar.text")
                AboutTextRow(title: "about.sources.hadith.title", text: "about.sources.hadith.text")
                // Свои записи (2026-10-12, решение пользователя — без имени).
                AboutTextRow(title: "about.sources.audio.title", text: "about.sources.audio.text")
            } header: {
                Text("about.sources.header")
            } footer: {
                Text("about.sources.footer")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }

            // Политику требует Apple (5.1.1(i)); ссылок на исходный код и Issues нет —
            // решение пользователя 2026-10-19, как на Android.
            SettingsGroup {
                Link(destination: AboutLinks.privacy) {
                    AboutLinkLabel(title: "about.link.privacy")
                }
                NavigationLink(value: SettingsRoute.licenses) {
                    SettingsLinkLabel {
                        Text("about.licenses")
                    }
                }
            } header: {
                Text("about.app.header")
            } footer: {
                Text("about.app.footer")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
        }
        .settingsForm(background: theme.gradients.settingsBackground)
        .glassBackButton(tint: theme.palette.tabSettings, header: nil)
        .navigationTitle(Text("about.title"))
        .navigationBarTitleDisplayMode(.inline)
        .audioPlayerInset()
    }
}

/// «Лицензии»: шрифт и значки, каждая строка — полный текст лицензии.
struct LicensesView: View {
    @Environment(\.theme) private var theme

    var body: some View {
        SettingsForm {
            SettingsGroup {
                ForEach(LicenseDocument.allCases) { document in
                    NavigationLink(value: SettingsRoute.license(document)) {
                        SettingsLinkLabel {
                            AboutTextRow(title: document.title, text: document.subtitle)
                        }
                    }
                }
            } header: {
                Text("about.licenses.header")
            }
        }
        .settingsForm(background: theme.gradients.settingsBackground)
        .glassBackButton(tint: theme.palette.tabSettings, header: nil)
        .navigationTitle(Text("about.licenses"))
        .navigationBarTitleDisplayMode(.inline)
        .audioPlayerInset()
    }
}

/// Иконка, название и версия приложения.
private struct AboutHeader: View {
    @Environment(\.theme) private var theme

    @ScaledMetric(relativeTo: .body) private var iconSize: CGFloat = 72

    /// Скругление иконок iOS — доля стороны.
    private static let iconCornerRatio: CGFloat = 0.2237
    private static let arabicNameSize: Double = 28

    private var version: String { "\(AppVersion.marketing) (\(AppVersion.build))" }

    var body: some View {
        VStack(spacing: Spacing.s) {
            Image(AppIconOption.classic.preview)
                .resizable()
                .scaledToFit()
                .frame(width: iconSize, height: iconSize)
                .clipShape(.rect(cornerRadius: iconSize * Self.iconCornerRatio, style: .continuous))
                .accessibilityHidden(true)
            ArabicText(
                text: "إنابة",
                size: Self.arabicNameSize,
                color: theme.palette.onAccent,
                bold: true,
                alignment: .center
            )
            Text("about.appName")
                .font(.headline)
            Text("about.version \(version)")
                .font(.caption)
                .foregroundStyle(theme.palette.onAccentSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.m)
        .accessibilityElement(children: .combine)
    }
}

/// Заголовок и пояснение в строке списка.
private struct AboutTextRow: View {
    let title: LocalizedStringResource
    let text: LocalizedStringResource

    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xxs) {
            Text(title)
                .font(.body)
            Text(text)
                .font(.caption)
                .foregroundStyle(theme.palette.onAccentSecondary)
        }
        .padding(.vertical, Spacing.xxs)
    }
}

/// Ссылка наружу: подпись и значок «открыть в браузере».
private struct AboutLinkLabel: View {
    let title: LocalizedStringResource

    @Environment(\.theme) private var theme

    var body: some View {
        HStack {
            Text(title)
                .foregroundStyle(theme.palette.onAccent)
            Spacer(minLength: Spacing.s)
            Image(systemName: "arrow.up.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(theme.palette.onAccentTertiary)
                .accessibilityHidden(true)
        }
    }
}

/// Внешние адреса приложения: экран «О приложении» и «Поделиться».
enum AboutLinks {
    /// Страница в App Store (Apple ID приложения из App Store Connect) — последний блок
    /// текста «Поделиться».
    static let appStore = URL(string: "https://apps.apple.com/app/id6819638881")!
    static let privacy = URL(string: "https://github.com/abuweb1/inabah/blob/main/PRIVACY.md")!
}

#Preview {
    NavigationStack {
        AboutView()
    }
    .appEnvironment(.preview)
}

#Preview("Лицензии") {
    NavigationStack {
        LicensesView()
    }
    .appEnvironment(.preview)
}
