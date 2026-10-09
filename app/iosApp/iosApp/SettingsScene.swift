import SwiftUI

/// Wires account state and the app-level theme preference (same UserDefaults key as Kotlin's
/// `ThemePreferenceStore.KEY`). No Account, Notification, About, Privacy, Terms or Help
/// destinations exist yet, so those rows show "Coming soon".
struct SettingsScene: View {
    let model: AccountViewModel
    let onSignIn: () -> Void
    var appLock: AppLockModel?
    var onEarningsReminders: (() -> Void)? = nil
    var onEarningsDigest: (() -> Void)? = nil
    @AppStorage(AppTheme.storageKey) private var themeMode = AppTheme.system
    @AppStorage(BackendSettings.storageKey) private var backendEnvironment = BackendSettings.real
    @State private var plan = SimulatedPlanModel()
    private var version: String? { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String }
    var body: some View {
        SettingsScreen(
            state: model.state,
            themeMode: $themeMode,
            backendEnvironment: BackendSettings.mockURL == nil ? nil : $backendEnvironment,
            simulatedPlan: BackendSettings.mockURL != nil && backendEnvironment == BackendSettings.mock && model.state.user != nil
                ? Binding(get: { plan.plan }, set: { plan.simulate($0) }) : nil,
            appVersion: version,
            onSignIn: onSignIn,
            onSignOut: { Task { await model.signOut() } },
            appLock: appLock,
            onEarningsReminders: onEarningsReminders,
            onEarningsDigest: onEarningsDigest
        )
        .onAppear { plan.connect(model) }
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

/// Development backend choice (same UserDefaults key as Kotlin's `BackendEnvironmentStore.KEY`).
/// Mock is honoured only in DEBUG builds with `STOCKSTEPS_MOCK_BACKEND_URL` configured, so
/// release builds always use the real backend.
enum BackendSettings {
    static let storageKey = "stocksteps.backendEnvironment"
    static let mock = "MOCK", real = "REAL"

    static var realURL: String { plistURL("StockStepsBackendURL") ?? "http://localhost:8080" }

    static var mockURL: String? {
        #if DEBUG
        plistURL("StockStepsMockBackendURL")
        #else
        nil
        #endif
    }

    /// "mock" or "real": the effective choice, used to keep each backend's user data apart.
    static var environment: String {
        (UserDefaults.standard.string(forKey: storageKey) == mock && mockURL != nil) ? "mock" : "real"
    }

    /// Read on every request, so switching applies to the next call of every client.
    static var currentURL: String {
        let stored = UserDefaults.standard.string(forKey: storageKey) ?? real
        if stored == mock, let mockURL { return mockURL }
        return realURL
    }

    private static func plistURL(_ key: String) -> String? {
        (Bundle.main.object(forInfoDictionaryKey: key) as? String).flatMap { $0.isEmpty || $0.contains("$(") ? nil : $0 }
    }
}
