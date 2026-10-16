import SwiftUI
import Shared

/// Portfolio Phase E.2 (mirrors Compose `signInAgain`): "Sign in again" for an ended session. Signs out first — Firebase sign-out plus this
/// device's cached copy of the account; nothing on the server is deleted — then opens the existing sign-in sheet (`onSignIn`), so sign-in never
/// starts while the expired user is still signed in (no loop). A failed sign-out opens nothing and leaves the banner in place.
@MainActor func portfolioSignInAgain(_ accounts: AccountViewModel, then onSignIn: @escaping () -> Void) -> () -> Void {
    {
        Task { @MainActor in
            await accounts.signOut()
            if accounts.state.user == nil { onSignIn() }
        }
    }
}

struct PortfolioScene: View {
    let accounts: AccountViewModel
    let watchlists: WatchlistsModel
    /// 5D.5.6C3: the app's Portfolio model, shared with the Holding, Transactions and Transaction-details destinations (one subscription).
    let model: PortfolioViewModel
    let onCompany: (String) -> Void
    var onSignIn: () -> Void = {}
    var onAlerts: (String) -> Void = { _ in }
    var initialSymbol: String? = nil
    /// 5D.5.6C4: the landing's nested tabs (the dashboard only).
    var section: Binding<PortfolioSection>? = nil
    /// 5D.5.6B landing bar: the all-alerts history and Settings.
    var onNotifications: () -> Void = {}
    var onSettings: () -> Void = {}
    @Environment(TabRouter.self) private var router: TabRouter?
    @State private var showAccount = false
    @State private var editingAccount: PortfolioAccount?
    @State private var showTransaction = false
    @State private var editingTransaction: PortfolioTransaction?
    @State private var watchlistError: String?
    @State private var watchlistInstrument: InstrumentRef?

    var body: some View {
        PortfolioScreen(state: model.state, holdingSymbol: initialSymbol,
            onRefresh: model.refresh, onHistory: model.history, onScenario: model.scenario, onSelect: model.select,
            onAddAccount: { editingAccount = nil; showAccount = true },
            onEditAccount: { editingAccount = $0; showAccount = true }, onDeleteAccount: model.deleteAccount,
            onAdd: { editingTransaction = nil; showTransaction = true }, onHolding: { if initialSymbol == nil { router?.open(.holdingDetails($0)) } }, onCompany: onCompany, onSignIn: onSignIn, onAlerts: onAlerts,
            onWatchlist: { row in if model.state?.mockScenario == nil { watchlistInstrument = InstrumentRef(symbol: row.symbol, name: row.name, exchange: row.exchange, currency: row.currency) } },
            onEditTransaction: { editingTransaction = $0; showTransaction = true }, onDeleteTransaction: model.deleteTransaction,
            onInsights: { router?.open(.portfolioInsights) }, section: initialSymbol == nil ? section : nil,
            // The account a holding-scoped history is for is captured on tap.
            onTransactions: { router?.open(.portfolioTransactions(symbol: initialSymbol, accountId: initialSymbol == nil ? nil : model.state?.selectedAccountId)) },
            onOpenTransaction: { router?.open(.portfolioTransaction($0)) }, onDismissError: model.clearError,
            onSignInAgain: portfolioSignInAgain(accounts, then: onSignIn),
            onNotifications: onNotifications, onSettings: onSettings)
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
