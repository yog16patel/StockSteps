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
            .navigationTitle("Insights")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { model.connect(accounts) }
            .refreshable { model.refresh() }
    }
}
