import Shared
import Observation

/// Mirrors the shared Kotlin `PortfolioAnalyticsPresenter` (same state and actions as Android).
@MainActor @Observable
final class PortfolioInsightsViewModel {
    private(set) var state: InsightsUiState?
    @ObservationIgnored private var subscription: (any AccountSubscription)?
    @ObservationIgnored private weak var accounts: AccountViewModel?

    func connect(_ accounts: AccountViewModel) {
        guard self.accounts !== accounts || subscription == nil else { return }
        subscription?.cancel()
        self.accounts = accounts
        subscription = accounts.client?.observeInsights { [weak self] state in self?.state = state }
    }
    func select(_ id: String) { accounts?.client?.selectInsightsAccount(id: id) }
    func period(_ label: String) { accounts?.client?.selectInsightsPeriod(label: label) }
    func benchmark(_ name: String) { accounts?.client?.selectInsightsBenchmark(name: name) }
    func scenario(_ id: String?) { accounts?.client?.showInsightsScenario(id: id) }
    func refresh() { accounts?.client?.refreshInsights() }
    deinit { subscription?.cancel() }
}

/// The simulated StockSteps+ plan shown in Settings → Development (mock backend only).
@MainActor @Observable
final class SimulatedPlanModel {
    private(set) var plan = "FREE"
    @ObservationIgnored private var subscription: (any AccountSubscription)?
    @ObservationIgnored private weak var accounts: AccountViewModel?

    func connect(_ accounts: AccountViewModel) {
        guard self.accounts !== accounts || subscription == nil else { return }
        subscription?.cancel()
        self.accounts = accounts
        subscription = accounts.client?.observeEntitlements { [weak self] value in
            self?.plan = value?.plus == true ? "PLUS" : (value?.status == .expired ? "EXPIRED" : "FREE")
        }
    }
    func simulate(_ plan: String) { accounts?.client?.simulatePlan(plan: plan) }
    deinit { subscription?.cancel() }
}
