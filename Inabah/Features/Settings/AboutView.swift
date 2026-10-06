import SwiftUI

/// «О приложении»: версия, источники текстов и записей, лицензии шрифта и значков,
/// политика конфиденциальности и репозиторий. Источники согласованы с пользователем 2026-10-05.
struct AboutView: View {
    @Environment(\.theme) private var theme

    var body: some View {
        Form {
            Section {
                AboutHeader()
                    .settingsRow()
            }

            Section {
                AboutTextRow(title: "about.sources.azkar.title", text: "about.sources.azkar.text")
                    .settingsRow()
                AboutTextRow(title: "about.sources.hadith.title", text: "about.sources.hadith.text")
                    .settingsRow()
                Link(destination: AboutLinks.azkar) {
                    AboutLinkLabel(title: "about.link.azkar")
                }
                .settingsRow()
                Link(destination: AboutLinks.sunnah) {
                    AboutLinkLabel(title: "about.link.sunnah")
                }
                .settingsRow()
            } header: {
                Text("about.sources.header")
            } footer: {
                Text("about.sources.footer")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }

            Section {
                ForEach(LicenseDocument.allCases) { document in
                    NavigationLink(value: SettingsRoute.license(document)) {
                        AboutTextRow(title: document.title, text: document.subtitle)
                    }
                    .settingsRow()
                }
            } header: {
                Text("about.licenses.header")
            }

            Section {
                Link(destination: AboutLinks.privacy) {
                    AboutLinkLabel(title: "about.link.privacy")
                }
                .settingsRow()
                Link(destination: AboutLinks.repository) {
                    AboutLinkLabel(title: "about.link.repository")
                }
                .settingsRow()
                Link(destination: AboutLinks.issues) {
                    AboutLinkLabel(title: "about.link.issues")
                }
                .settingsRow()
            } header: {
                Text("about.app.header")
            } footer: {
                Text("about.app.footer")
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
        }
        .settingsForm(background: theme.gradients.settingsBackground)
        .navigationTitle(Text("about.title"))
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

/// Версия приложения из Info.plist: «1.0.0» и номер сборки.
enum AppVersion {
    static var marketing: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
    }

    static var build: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? ""
    }
}

/// Внешние адреса приложения: экран «О приложении» и «Поделиться».
enum AboutLinks {
    /// Страница в App Store (Apple ID приложения из App Store Connect) — последний блок
    /// текста «Поделиться».
    static let appStore = URL(string: "https://apps.apple.com/app/id6819638881")!
    static let azkar = URL(string: "https://azkar.ru")!
    static let sunnah = URL(string: "https://sunnah.com")!
    static let repository = URL(string: "https://github.com/abuweb1/inabah")!
    static let privacy = URL(string: "https://github.com/abuweb1/inabah/blob/main/PRIVACY.md")!
    static let issues = URL(string: "https://github.com/abuweb1/inabah/issues")!
}

#Preview {
    NavigationStack {
        AboutView()
    }
    .appEnvironment(.preview)
}
