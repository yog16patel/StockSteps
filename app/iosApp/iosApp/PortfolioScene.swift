import SwiftUI
import Shared

struct PortfolioScene: View {
    let accounts: AccountViewModel
    let watchlists: WatchlistsModel
    let onCompany: (String) -> Void
    var onSignIn: () -> Void = {}
    var onAlerts: (String) -> Void = { _ in }
    var initialSymbol: String? = nil
    var onPractice: (() -> Void)? = nil
    @State private var model = PortfolioViewModel()
    @State private var holdingSymbol: String?
    @State private var showAccount = false
    @State private var editingAccount: PortfolioAccount?
    @State private var showTransaction = false
    @State private var editingTransaction: PortfolioTransaction?
    @State private var watchlistError: String?
    @State private var watchlistInstrument: InstrumentRef?
    @State private var showInsights = false
    @State private var showTransactions = false
    /// The account a holding-scoped history was opened for, captured on tap (destination builders re-run on every parent update).
    @State private var transactionsAccount: String?
    @State private var openTransactionId: String?

    var body: some View {
        PortfolioScreen(state: model.state, holdingSymbol: initialSymbol,
            onRefresh: model.refresh, onHistory: model.history, onScenario: model.scenario, onSelect: model.select,
            onAddAccount: { editingAccount = nil; showAccount = true },
            onEditAccount: { editingAccount = $0; showAccount = true }, onDeleteAccount: model.deleteAccount,
            onAdd: { editingTransaction = nil; showTransaction = true }, onHolding: { if initialSymbol == nil { holdingSymbol = $0 } }, onCompany: onCompany, onSignIn: onSignIn, onAlerts: onAlerts,
            onWatchlist: { row in if model.state?.mockScenario == nil { watchlistInstrument = InstrumentRef(symbol: row.symbol, name: row.name, exchange: row.exchange, currency: row.currency) } },
            onEditTransaction: { editingTransaction = $0; showTransaction = true }, onDeleteTransaction: model.deleteTransaction,
            onInsights: { showInsights = true }, onPractice: initialSymbol == nil ? onPractice : nil,
            onTransactions: { transactionsAccount = model.state?.selectedAccountId; showTransactions = true },
            onOpenTransaction: { openTransactionId = $0 })
        .onAppear { model.connect(accounts); model.clearError() } // D1: no stale failure from another action on a fresh form
        .portfolioFeedback(accounts: accounts, result: model.state?.actionResult) // D3: confirmed add / edit / delete
        .sheet(isPresented: $showAccount) {
            PortfolioAccountForm(existing: editingAccount, onSave: { id, name, category, currency, archived in
                model.saveAccount(id: id, name: name, category: category, currency: currency, archived: archived)
                showAccount = false
            })
        }
        .sheet(isPresented: $showTransaction) {
            PortfolioEntryScene(accounts: accounts, model: model, existing: editingTransaction, initialInstrument: nil)
        }
        .sheet(isPresented: Binding(get: { watchlistInstrument != nil }, set: { if !$0 { watchlistInstrument = nil } })) {
            if let instrument = watchlistInstrument {
                NavigationStack {
                    List(watchlists.watchlists, id: \.id) { list in
                        Button(list.name) {
                            Task {
                                do { try await accounts.client?.addEntry(listId: list.id, instrument: instrument); watchlistInstrument = nil }
                                catch { watchlistError = error.localizedDescription }
                            }
                        }
                    }.navigationTitle("Add to watchlist")
                }
            }
        }
        .alert("Could not save to watchlist", isPresented: Binding(get: { watchlistError != nil }, set: { if !$0 { watchlistError = nil } })) { Button("OK") { watchlistError = nil } } message: { Text(watchlistError ?? "Try again.") }
        .navigationDestination(isPresented: $showInsights) {
            PortfolioInsightsScene(accounts: accounts, onCompany: onCompany, onSignIn: onSignIn)
        }
        // Phase D2: Transaction history inside Portfolio (not a tab), on this scene's model (no second load).
        .navigationDestination(isPresented: $showTransactions) {
            PortfolioTransactionsScene(accounts: accounts, model: model, symbol: initialSymbol, accountId: initialSymbol == nil ? nil : transactionsAccount,
                                       onSignIn: onSignIn)
        }
        // Phase D3: Transaction details on this scene's model.
        .navigationDestination(item: $openTransactionId) { id in
            PortfolioTransactionDetailsScene(accounts: accounts, model: model, transactionId: id, onSignIn: onSignIn)
        }
        .navigationDestination(item: $holdingSymbol) { symbol in
            PortfolioScene(accounts: accounts, watchlists: watchlists, onCompany: onCompany, onSignIn: onSignIn, onAlerts: onAlerts, initialSymbol: symbol)
        }
    }
}

struct PortfolioEntryScene: View {
    let accounts: AccountViewModel
    let model: PortfolioViewModel
    let existing: PortfolioTransaction?
    let initialInstrument: InstrumentRef?
    @Environment(\.dismiss) private var dismiss
    /// D3: the result id current when Save was tapped and the transaction sent (nil = nothing submitted).
    @State private var submittedAfter: Int64?
    @State private var submittedId: String?
    @State private var selectedInstrument: InstrumentRef?
    @State private var showSearch = false
    @State private var showCreateAccount = false
    @State private var searchModel = StockSearchViewModel(service: StockSearchService(baseURL: { BackendSettings.currentURL }))
    var body: some View {
        VStack {
        if model.state?.accounts.filter({ !$0.archived }).isEmpty == true && !PortfolioFormRules.shared.readOnly(state: model.state) {
            Button { showCreateAccount = true } label: { Text(PortfolioDisplay.shared.CREATE_ACCOUNT_FIRST).frame(maxWidth: .infinity) }
                .buttonStyle(.stockPrimary).padding(.horizontal, CGFloat(StockStepsTheme.spacing.screen))
                .disabled(model.state?.signedIn != true || model.state?.busy == true)
        }
        // Phase D1: the security search sits next to the symbol field inside the form; samples get the read-only explanation instead.
        PortfolioTransactionForm(state: model.state, existing: existing, instrument: selectedInstrument ?? initialInstrument,
                                 onSearch: existing == nil ? { showSearch = true } : nil, onUseSavedAccounts: { model.scenario(nil) }) { id, account, type, date, currency, symbol, name, exchange, quantity, price, amount, fees, notes, cashCurrency, fxRate, settlementDate, securityCurrency in
            submittedAfter = PortfolioActionFeedback.shared.baseline(result: model.state?.actionResult)
            submittedId = id
            model.saveTransaction(id: id, account: account, type: type, date: date, currency: currency, symbol: symbol, name: name,
                                  exchange: exchange, quantity: quantity, price: price, amount: amount, fees: fees, notes: notes, cashCurrency: cashCurrency, fxRate: fxRate, settlementDate: settlementDate, securityCurrency: securityCurrency, edit: existing != nil)
        }
        .id(selectedInstrument?.symbol ?? initialInstrument?.symbol ?? "new")
        }
        .sheet(isPresented: $showCreateAccount) {
            PortfolioAccountForm(existing: nil, onSave: { id, name, category, currency, archived in
                model.saveAccount(id: id, name: name, category: category, currency: currency, archived: archived)
                showCreateAccount = false
            })
        }
        .sheet(isPresented: $showSearch) {
            StockSearchScene(model: searchModel, accounts: accounts, onClose: { showSearch = false }, onOpenStock: { stock in
                selectedInstrument = InstrumentRef(symbol: stock.symbol, name: stock.name, exchange: stock.exchange, currency: stock.currency)
                showSearch = false
            })
        }
        .onAppear { model.connect(accounts) }
        // D3: dismiss only when the presenter confirms this transaction was saved (busy → idle without an error also happens when the request is
        // cancelled because the account changed). A rejection keeps the form open with the server message.
        .onChange(of: model.state?.actionResult?.id) { _, _ in
            guard let after = submittedAfter, let result = model.state?.actionResult, result.id > after, result.transactionId == submittedId,
                  result.kind == (existing == nil ? .added : .updated) else { return }
            submittedAfter = nil
            dismiss()
        }
    }
}
