import Shared
import Observation

@MainActor @Observable
final class PortfolioViewModel {
    private(set) var state: PortfolioUiState?
    @ObservationIgnored private var subscription: (any AccountSubscription)?
    @ObservationIgnored private weak var accounts: AccountViewModel?

    func connect(_ accounts: AccountViewModel) {
        guard self.accounts !== accounts || subscription == nil else { return }
        subscription?.cancel()
        self.accounts = accounts
        subscription = accounts.client?.observePortfolio { [weak self] state in self?.state = state }
    }
    func scenario(_ id: String?) { accounts?.client?.showPortfolioScenario(id: id) }
    func history(_ range: String) { accounts?.client?.loadPortfolioHistory(range: range) }
    func refresh() { accounts?.client?.refreshPortfolio() }
    func select(_ id: String) { accounts?.client?.selectPortfolioAccount(id: id) }
    func deleteAccount(_ id: String) { accounts?.client?.deletePortfolioAccount(id: id) }
    func deleteTransaction(_ id: String) { accounts?.client?.deletePortfolioTransaction(id: id) }
    func saveAccount(id: String, name: String, category: String, currency: String, archived: Bool) {
        accounts?.client?.savePortfolioAccount(id: id, name: name, category: category, currency: currency, archived: archived)
    }
    func saveTransaction(id: String, account: String, type: String, date: String, currency: String,
                         symbol: String, name: String, exchange: String, quantity: String, price: String, amount: String, fees: String, notes: String, cashCurrency: String, fxRate: String, settlementDate: String, securityCurrency: String, edit: Bool) {
        accounts?.client?.savePortfolioTransaction(id: id, accountId: account, type: type, date: date, currency: currency,
            symbol: symbol, name: name, exchange: exchange, quantity: quantity, price: price, amount: amount, fees: fees, notes: notes, cashCurrency: cashCurrency, fxRate: fxRate, settlementDate: settlementDate, securityCurrency: securityCurrency, edit: edit)
    }
    deinit { subscription?.cancel() }
}
