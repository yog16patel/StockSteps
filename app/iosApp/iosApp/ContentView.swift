import Shared
import SwiftUI

struct ContentView: View {
    @State private var accounts = AccountViewModel()
    @Environment(\.scenePhase) private var scenePhase
    @State private var hasEnteredApp = false
    @AppStorage(AppTheme.storageKey) private var themeMode = AppTheme.system
    private let baseURL: () -> String
    @State private var sampleData = false
    @AppStorage(BackendSettings.storageKey) private var backendEnvironment = BackendSettings.real
    init(baseURL: @escaping () -> String = { BackendSettings.currentURL }) { self.baseURL = baseURL }
    var body: some View {
        Group {
            if accounts.state.initializing {
                ProgressView("Restoring account…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if hasEnteredApp || accounts.state.user != nil {
                AppScene(baseURL: baseURL, accounts: accounts)
            } else {
                AuthScene(model: accounts, onDone: { hasEnteredApp = true })
            }
        }
        // Shown whenever the backend reports mock data, so samples are never mistaken for prices.
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if sampleData { SampleDataBanner() }
        }
        .task(id: backendEnvironment) { sampleData = await BackendInfoService(baseURL: baseURL).isMock() }
        // The root owns the theme; nil (System) keeps following iOS appearance changes.
        .preferredColorScheme(AppTheme.colorScheme(themeMode))
        .onChange(of: accounts.state.user?.id, initial: true) { _, userID in
            if userID != nil { hasEnteredApp = true }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { accounts.retrySync() }
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

struct SampleDataBanner: View {
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        Text("Sample data · mock backend")
            .font(StockStepsTheme.font(StockStepsTheme.typography.label, relativeTo: .footnote))
            .foregroundStyle(colors.textBody)
            .frame(maxWidth: .infinity)
            .padding(.vertical, CGFloat(StockStepsTheme.spacing.xxs))
            .background(colors.warningContainer)
    }
}
