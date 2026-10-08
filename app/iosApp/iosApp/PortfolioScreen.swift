import SwiftUI
import Shared

struct PortfolioScreen: View {
    let state: PortfolioUiState?
    let holdingSymbol: String?
    let onRefresh: () -> Void
    let onHistory: (String) -> Void
    let onScenario: (String?) -> Void
    let onSelect: (String) -> Void
    let onAddAccount: () -> Void
    let onEditAccount: (PortfolioAccount) -> Void
    let onDeleteAccount: (String) -> Void
    let onAdd: () -> Void
    let onHolding: (String) -> Void
    let onCompany: (String) -> Void
    let onSignIn: () -> Void
    let onAlerts: (String) -> Void
    let onWatchlist: (PortfolioHoldingRow) -> Void
    let onEditTransaction: (PortfolioTransaction) -> Void
    let onDeleteTransaction: (String) -> Void
    var onInsights: () -> Void = {}
    /// The separate, simulated Practice Portfolio (never mixed into these totals).
    var onPractice: (() -> Void)? = nil
    @State private var deleteAccount: PortfolioAccount?
    @State private var deleteTransaction: PortfolioTransaction?
    @Environment(\.colorScheme) private var scheme
    private let space = StockStepsTheme.spacing

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.sectionGap)) {
                Text(holdingSymbol == nil ? "Portfolio" : "Holding details")
                    .font(StockStepsTheme.font(StockStepsTheme.typography.screenTitle, relativeTo: .largeTitle))
                if let onPractice {
                    HStack(alignment: .top, spacing: CGFloat(space.sm)) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("My Portfolio").font(StockStepsTheme.font(StockStepsTheme.typography.cardTitle, relativeTo: .headline))
                            Text("Track investments you actually own.").font(.caption)
                        }
                        .stockCard()
                        .accessibilityElement(children: .combine)
                        .accessibilityAddTraits(.isSelected)
                        PracticeEntryCard(action: onPractice)
                    }
                }
                if let state {
                    if state.mockAvailable {
                        Menu("Sample scenario: \(state.mockScenario ?? "My saved accounts")") {
                            Button("My saved accounts") { onScenario(nil) }
                            ForEach(PortfolioFixtureCatalog.shared.ids, id: \.self) { id in Button(id) { onScenario(id) } }
                        }
                        if state.mockScenario != nil { Text("Read-only sample; saved accounts and watchlists stay unchanged.").font(.caption) }
                    }
                    if state.loading { ProgressView("Loading portfolio") }
                    if let error = state.error { Text(error).foregroundStyle(.red); Button("Retry", action: onRefresh) }
                    if state.offline { Text("Offline · last saved portfolio") }
                    if !state.signedIn && state.mockScenario == nil { Button("Sign in to save your portfolio", action: onSignIn) }
                    if state.accounts.filter({ !$0.archived }).isEmpty {
                        Text("Start tracking what you own").font(.title2)
                        Text("Create an account, then add existing investments or record trades. Your watchlist stays separate.")
                        Button("Create portfolio", action: onAddAccount).buttonStyle(.borderedProminent).disabled(!state.signedIn)
                    } else {
                        if holdingSymbol == nil {
                            Menu("\(state.accountName) · \(state.currency)") {
                                ForEach(state.accounts.filter { !$0.archived }, id: \.id) { account in
                                    Button(account.name) { onSelect(account.id) }
                                }
                                Button("Add account", action: onAddAccount)
                            }
                            PortfolioSummaryCard(state: state)
                            Text(state.dailyNotice).font(.caption)
                            Button("Add investment / transaction", action: onAdd).buttonStyle(.borderedProminent)
                            Button("Insights: performance, allocation and concentration", systemImage: "chart.line.uptrend.xyaxis", action: onInsights)
                                .buttonStyle(.bordered).frame(minHeight: 44)
                            Text("Portfolio history").font(.headline)
                            PortfolioHistoryChart(state: state, onHistory: onHistory)
                        }
                        Text("Holdings").font(.headline)
                        if state.holdings.isEmpty { Text("No holdings yet. Add an investment with its effective date.") }
                        ForEach(state.holdings.filter { holdingSymbol == nil || $0.symbol == holdingSymbol }, id: \.symbol) { row in
                            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                                Button { onHolding(row.symbol) } label: {
                                    VStack(alignment: .leading) {
                                        Text(row.name).font(.headline)
                                        Text("\(row.symbol) · \(row.exchange ?? "Exchange not provided") · \(row.quantity) shares").font(.subheadline)
                                        Text("\(row.currency) \(PortfolioFormat.shared.amount(value: row.value))").font(.title2).monospacedDigit()
                                    }.frame(maxWidth: .infinity, alignment: .leading)
                                }.buttonStyle(.plain)
                                Text("Price \(PortfolioFormat.shared.amount(value: row.price)) · cost \(PortfolioFormat.shared.amount(value: row.basis))")
                                Text("Unrealized \(PortfolioFormat.shared.amount(value: row.gain)) (\(PortfolioFormat.shared.amount(value: row.gainPercent))%)")
                                    .foregroundStyle(row.gain?.hasPrefix("-") == true ? .red : .green)
                                if row.stale { Text("Last available quote · delayed or stale").font(.caption) }
                                if holdingSymbol != nil {
                                    Text("Realized \(row.realized) · Dividends \(row.dividends)")
                                    Button("Company details") { onCompany(row.symbol) }
                                    Button("Price alerts") { onAlerts(row.symbol) }
                                    Button("Add to watchlist") { onWatchlist(row) }
                                }
                            }.padding(CGFloat(space.cardPadding)).frame(maxWidth: .infinity, alignment: .leading)
                                .background(StockStepsTheme.colors(scheme).surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                        }
                        Text("Cash by currency").font(.headline)
                        Text(state.cash.isEmpty ? "No cash recorded" : state.cash.joined(separator: "\n")).monospacedDigit()
                        Text("Negative cash represents unfunded or borrowed cash. Record deposits separately from existing investments.").font(.caption)
                        if holdingSymbol == nil {
                            Text("Allocation by holding and currency").font(.headline)
                            ForEach(state.allocations, id: \.symbol) { allocation in
                                Text("\(allocation.symbol) · \(allocation.currency.name) · \(PortfolioFormat.shared.amount(value: allocation.percent))% of account value")
                            }
                            ForEach(state.currencyAllocations, id: \.currency) { allocation in
                                Text("\(allocation.currency.name) · \(PortfolioFormat.shared.amount(value: allocation.percent))% · \(state.currency) \(PortfolioFormat.shared.amount(value: allocation.reportingValue))")
                            }
                            Text("Cash is included. Borrowed cash can make a holding exceed 100%. ETF look-through is not estimated.").font(.caption)
                        }
                        Text("Dividends received").font(.headline)
                        Text(state.dividends.isEmpty ? "No dividends recorded" : state.dividends.joined(separator: "\n")).monospacedDigit()
                        Text("Transactions").font(.headline)
                        ForEach(state.transactions.filter { holdingSymbol == nil || $0.instrument?.symbol == holdingSymbol }, id: \.id) { transaction in
                            VStack(alignment: .leading) {
                                Text("\(transaction.type.name.replacingOccurrences(of: "_", with: " ")) · \(transaction.instrument?.symbol ?? "")")
                                Text("\(transaction.tradeDate) · \(transaction.currency.name) \(transaction.netAmount)").font(.caption)
                                HStack {
                                    Button("Edit") { onEditTransaction(transaction) }
                                    Button("Delete", role: .destructive) { deleteTransaction = transaction }
                                }
                            }
                        }
                        if holdingSymbol == nil, let account = state.accounts.first(where: { $0.id == state.selectedAccountId }) {
                            Text("\(account.category.name) · reporting \(account.reportingCurrency.name)")
                            Button("Account settings") { onEditAccount(account) }
                            Button("Delete account", role: .destructive) { deleteAccount = account }
                        }
                    }
                    Button("Refresh portfolio", action: onRefresh).disabled(state.busy)
                } else { ProgressView("Restoring portfolio") }
            }.frame(maxWidth: CGFloat(StockStepsTheme.dimensions.contentMaxWidth))
                .padding(CGFloat(space.screen)).frame(maxWidth: .infinity)
        }
        .background(StockStepsTheme.colors(scheme).appBackground.ignoresSafeArea())
        .refreshable { onRefresh() }
        .confirmationDialog("Delete account and its transactions?", isPresented: Binding(get: { deleteAccount != nil }, set: { if !$0 { deleteAccount = nil } })) {
            Button("Delete", role: .destructive) { if let account = deleteAccount { onDeleteAccount(account.id) }; deleteAccount = nil }
        } message: { Text("Watchlists and alerts stay saved.") }
        .confirmationDialog("Delete transaction and recalculate balances?", isPresented: Binding(get: { deleteTransaction != nil }, set: { if !$0 { deleteTransaction = nil } })) {
            Button("Delete", role: .destructive) { if let transaction = deleteTransaction { onDeleteTransaction(transaction.id) }; deleteTransaction = nil }
        }
    }
}

struct PortfolioSummaryCard: View {
    let state: PortfolioUiState
    var onOpen: (() -> Void)? = nil
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.sm)) {
            Text(state.accountName).font(.headline)
            if state.accounts.filter({ !$0.archived }).isEmpty { Text("Track the investments you own, alongside your separate watchlists.") }
            else {
            Text("\(state.currency) \(PortfolioFormat.shared.amount(value: state.total))").font(.largeTitle).monospacedDigit()
            Text("Today \(PortfolioFormat.shared.amount(value: state.dailyGain)) (\(PortfolioFormat.shared.amount(value: state.dailyPercent))%)")
            if state.accounts.filter({ !$0.archived }).count > 1 { Text("All accounts · \(state.currency) \(PortfolioFormat.shared.amount(value: state.combinedTotal))") }
            Text("Invested cost \(PortfolioFormat.shared.amount(value: state.basis))")
            Text("Unrealized \(PortfolioFormat.shared.amount(value: state.unrealized)) · Realized \(PortfolioFormat.shared.amount(value: state.realized))").font(.subheadline)
            if let notice = state.notice { Text(notice).font(.caption) }
            if let asOf = state.quoteAsOf { Text("Prices as of \(asOf)").font(.caption) }
            if let asOf = state.fxAsOf { Text("Exchange rate as of \(asOf)").font(.caption) }
            }
            if let sample = state.mockScenario { Text("Read-only sample · \(sample)").font(.caption) }
            if let onOpen { Button(state.accounts.isEmpty ? "Create portfolio" : "View portfolio", action: onOpen) }
        }.padding(CGFloat(StockStepsTheme.spacing.cardPadding)).frame(maxWidth: .infinity, alignment: .leading)
            .background(StockStepsTheme.colors(scheme).surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
    }
}
