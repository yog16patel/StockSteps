import SwiftUI
import Shared

/// Acquires the shared Insights model and wires navigation; rendering is `PortfolioInsightsScreen`.
struct PortfolioInsightsScene: View {
    let accounts: AccountViewModel
    let onCompany: (String) -> Void
    var onSignIn: () -> Void = {}
    @State private var model = PortfolioInsightsViewModel()

    var body: some View {
        PortfolioInsightsScreen(state: model.state, onSelectAccount: model.select, onSelectPeriod: model.period,
                                onSelectBenchmark: model.benchmark, onScenario: model.scenario, onRefresh: model.refresh,
                                onCompany: onCompany, onSignIn: onSignIn)
            // C.1: one visible heading — the in-screen title (marked as a header; it wraps at AX5, where a native large title truncated to
            // "Portfolio ins…"). The bar keeps only the back button, as on Holding details.
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { model.connect(accounts) }
            .refreshable { model.refresh() }
    }
}
