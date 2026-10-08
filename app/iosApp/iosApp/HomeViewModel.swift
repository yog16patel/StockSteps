import Shared
import Observation

/// Native observable adapter over the account graph's shared dashboard rules and caches.
@MainActor
@Observable
final class HomeViewModel {
    private(set) var portfolio: PortfolioUiState?
    @ObservationIgnored private var portfolioSubscription: (any AccountSubscription)?
    private(set) var dashboard: PersonalDashboard?
    @ObservationIgnored private var subscription: (any AccountSubscription)?
    @ObservationIgnored private weak var accounts: AccountViewModel?

    func connect(_ accounts: AccountViewModel) {
        guard self.accounts !== accounts || subscription == nil else { return }
        subscription?.cancel()
        self.accounts = accounts
        portfolioSubscription?.cancel()
        portfolioSubscription = accounts.client?.observePortfolio { [weak self] state in self?.portfolio = state }
        subscription = accounts.client?.observeHome { [weak self] state in
            self?.dashboard = state
        }
    }
    func visible() { accounts?.client?.homeVisible() }
    func refresh() { accounts?.client?.refreshHome() }
    func retryQuotes() { accounts?.client?.retryHomeQuotes() }
    func retryNews() { accounts?.client?.retryHomeNews() }
    func retryWatchlists() { accounts?.client?.retryHomeWatchlists() }
    func retryAlerts() { accounts?.client?.retryHomeAlerts() }
    func clearRecent() { accounts?.client?.clearRecentCompanies() }
    func persona(_ id: String?) {
        if let id { accounts?.client?.showHomePersona(id: id) }
        else { accounts?.client?.useSavedHomeCompanies() }
    }
    deinit { subscription?.cancel(); portfolioSubscription?.cancel() }
}
