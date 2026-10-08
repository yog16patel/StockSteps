import SwiftUI

/// Acquires observable state and wires navigation; HomeScreen only renders values and callbacks.
struct HomeScene: View {
    let model: HomeViewModel
    let accounts: AccountViewModel
    let onLearn: () -> Void
    let onSearch: () -> Void
    let onWatchlist: () -> Void
    let onSettings: () -> Void
    let onPortfolio: () -> Void
    let onAlerts: (String?) -> Void
    let onExplore: (String) -> Void
    var onEarnings: (String) -> Void = { _ in }
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @State private var localHour = Calendar.current.component(.hour, from: Date())
    @AppStorage(BackendSettings.storageKey) private var backendEnvironment = BackendSettings.real

    var body: some View {
        HomeScreen(
            state: model.dashboard,
            portfolio: model.portfolio,
            onPortfolio: onPortfolio,
            localHour: localHour,
            onLearn: onLearn,
            onSearch: onSearch,
            onWatchlist: onWatchlist,
            onSettings: onSettings,
            onAlerts: onAlerts,
            onExplore: onExplore,
            onEarnings: onEarnings,
            onArticle: { if let url = URL(string: $0) { openURL(url) } },
            onRefresh: model.refresh,
            onRetryQuotes: model.retryQuotes,
            onRetryNews: model.retryNews,
            onRetryWatchlists: model.retryWatchlists,
            onRetryAlerts: model.retryAlerts,
            onClearRecent: model.clearRecent,
            showMockPersonas: BackendSettings.mockURL != nil && backendEnvironment == BackendSettings.mock,
            onPersona: model.persona
        )
        .onAppear { model.connect(accounts); model.visible(); updateHour() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.visible(); updateHour() }
        }
    }
    private func updateHour() { localHour = Calendar.current.component(.hour, from: Date()) }
}
