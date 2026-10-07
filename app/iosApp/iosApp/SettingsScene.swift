import SwiftUI

/// Wires account state and the app-level theme preference (same UserDefaults key as Kotlin's
/// `ThemePreferenceStore.KEY`). No Account, Notification, About, Privacy, Terms or Help
/// destinations exist yet, so those rows show "Coming soon".
struct SettingsScene: View {
    let model: AccountViewModel
    let onSignIn: () -> Void
    @AppStorage(AppTheme.storageKey) private var themeMode = AppTheme.system
    private var version: String? { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String }
    var body: some View {
        SettingsScreen(
            state: model.state,
            themeMode: $themeMode,
            appVersion: version,
            onSignIn: onSignIn,
            onSignOut: { Task { await model.signOut() } }
        )
    }
}

/// Stored theme values; mirror Kotlin `ThemeMode` names so both sides read the same key.
enum AppTheme {
    static let storageKey = "stocksteps.themeMode"
    static let light = "LIGHT", dark = "DARK", system = "SYSTEM"
    static func colorScheme(_ stored: String) -> ColorScheme? {
        switch stored {
        case light: .light
        case dark: .dark
        default: nil // SYSTEM keeps following iOS appearance
        }
    }
}
