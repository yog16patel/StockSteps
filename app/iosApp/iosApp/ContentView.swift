import Shared
import SwiftUI

struct ContentView: View {
    @State private var accounts = AccountViewModel()
    @State private var appLock = AppLockModel()
    @Environment(\.scenePhase) private var scenePhase
    @State private var hasEnteredApp = false
    @AppStorage(AppTheme.storageKey) private var themeMode = AppTheme.system
    private let baseURL: () -> String
    @State private var sampleData = false
    /// Where the MOCK banner goes (Phase 4C.3): tab screens show it directly above the tab bar; everything else at the bottom of the root.
    @State private var bannerPlacement = SampleBannerPlacement()
    @AppStorage(BackendSettings.storageKey) private var backendEnvironment = BackendSettings.real
    init(baseURL: @escaping () -> String = { BackendSettings.currentURL }) { self.baseURL = baseURL }
    var body: some View {
        // On tab screens each tab's content insets the banner itself, so it sits directly above the tab bar (`sampleDataInset`, Phase 4C.3, as
        // on Android). Elsewhere (pushed screens, sign-in) it is stacked below the app here: a `.safeAreaInset` on the TabView would cover the tab
        // titles, because the UIKit tab bar lays out its labels with UIKit's safe area and ignores the SwiftUI inset.
        VStack(spacing: 0) {
            Group {
                if accounts.state.initializing {
                    ProgressView("Restoring account…")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if hasEnteredApp || accounts.state.user != nil {
                    AppScene(baseURL: baseURL, accounts: accounts, appLock: appLock)
                        // While locked, the app stays underneath but is hidden from VoiceOver.
                        .accessibilityHidden(appLock.locked)
                } else {
                    AuthScene(model: accounts, onDone: { hasEnteredApp = true })
                }
            }
            // Opaque unlock surface; the app-switcher snapshot is covered whenever the app can be locked.
            .overlay {
                if appLock.locked {
                    AppUnlockView(lock: appLock, onUseAccountLogin: { Task { await accounts.signOut() } })
                } else if appLock.protects && scenePhase != .active {
                    PrivacyCover()
                }
            }
            // Shown whenever the backend reports mock data, so samples are never mistaken for prices.
            if sampleData && !bannerPlacement.tabBarVisible { SampleDataBanner() }
        }
        .environment(bannerPlacement)
        .task(id: backendEnvironment) {
            sampleData = await BackendInfoService(baseURL: baseURL).isMock()
            bannerPlacement.isMock = sampleData
        }
        // The root owns the theme; nil (System) keeps following iOS appearance changes.
        .preferredColorScheme(AppTheme.colorScheme(themeMode))
        .onChange(of: accounts.state.user?.id, initial: true) { previous, userID in
            if userID != nil { hasEnteredApp = true }
            // Signing out (or Firebase ending the session) returns to Login; nothing to go "back" to.
            if previous != nil && userID == nil { hasEnteredApp = false }
        }
        // The lock follows the account once Firebase finished restoring it.
        .onChange(of: accounts.state.initializing ? "…" : (accounts.state.user?.id ?? ""), initial: true) { _, key in
            guard key != "…" else { return }
            appLock.accountChanged(key.isEmpty ? nil : key)
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active: accounts.retrySync(); appLock.foreground()
            case .background: appLock.background()
            default: break // .inactive (app switcher, Control Center) is not leaving the app
            }
        }
    }
}

/// Asks the backend once whether it serves sample (mock) data; any failure means "not mock".
@MainActor
struct BackendInfoService {
    let baseURL: () -> String
    func isMock() async -> Bool {
        let client = IosBackendInfoClient(baseUrl: baseURL)
        defer { client.close() }
        return (try? await client.isMock())?.boolValue ?? false
    }
}

/// Phase 4C.3: whether the backend reports MOCK data (the existing single check, no new request) and whether a tab screen — and so the tab
/// bar — is on screen. Tab screens show the banner above the tab bar; the root shows it only while no tab bar is visible, so it appears once.
@MainActor
@Observable
final class SampleBannerPlacement {
    var isMock = false
    var tabBarVisible = false
}

extension View {
    /// The MOCK banner as a bottom inset of a tab's content: directly above the tab bar, and scroll content ends above it.
    func sampleDataInset() -> some View { modifier(SampleDataInset()) }
}

private struct SampleDataInset: ViewModifier {
    @Environment(SampleBannerPlacement.self) private var placement
    func body(content: Content) -> some View {
        content.safeAreaInset(edge: .bottom, spacing: 0) {
            if placement.isMock { SampleDataBanner(fillsBottomSafeArea: false) }
        }
    }
}

struct SampleDataBanner: View {
    @Environment(\.colorScheme) private var scheme
    /// True at the bottom of the root (fills the home-indicator strip); false above the tab bar.
    var fillsBottomSafeArea = true
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        Text("Sample data · mock backend")
            .font(StockStepsTheme.font(StockStepsTheme.typography.label, relativeTo: .footnote))
            .foregroundStyle(colors.textBody)
            .frame(maxWidth: .infinity)
            .padding(.vertical, CGFloat(StockStepsTheme.spacing.xxs))
            // Fills the home-indicator strip below the text (container safe area only, so it never paints behind the keyboard).
            .background(colors.warningContainer.ignoresSafeArea(.container, edges: fillsBottomSafeArea ? .bottom : []))
    }
}
