import SwiftUI
import Shared

/// Portfolio dashboard (Global UI Refinement Phase 3, the reference screen): mode selector → account selector → value hero → add
/// transaction → performance → holdings → cash / allocation → dividends → transactions → insights. Low-frequency account actions live in
/// the overflow menu, explanations in the (i) sheet. Every number comes from `PortfolioUiState`; missing values stay missing. With
/// `holdingSymbol` the same screen shows one holding's details (unchanged behaviour). Mirrors the Compose `PortfolioScreen`.
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
    @State private var showAbout = false
    @State private var allTransactions = false
    @State private var showDailyReason = false
    @State private var rowWidth: CGFloat = 0
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    private let space = StockStepsTheme.spacing
    private let type = StockStepsTheme.typography
    private let present = PortfolioPresentation.shared
    private static let savedAccounts = "My saved accounts"
    private static let recentTransactions = 5

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            // Plain VStack (not Lazy): the old LazyVStack reserved a large blank block under Allocation on iOS.
            VStack(alignment: .leading, spacing: CGFloat(space.md)) {
                header(colors)
                if let onPractice, holdingSymbol == nil {
                    StockSegmentedControl(segments: [("My Portfolio", "briefcase"), ("Practice", "graduationcap")], selected: 0) { index in
                        if index == 1 { onPractice() }
                    }
                    .accessibilityHint("Practice opens the separate simulated portfolio")
                }
                if let state {
                    status(state)
                    let active = state.accounts.filter { !$0.archived }
                    if active.isEmpty {
                        StockStateMessage(title: "Start tracking what you own",
                                          message: "Create an account, then add existing investments or record trades. Your watchlist stays separate.",
                                          systemImage: "chart.pie",
                                          actionTitle: state.signedIn && !state.busy ? "Create portfolio" : nil, action: onAddAccount)
                    } else {
                        content(state, active: active, colors: colors)
                    }
                } else { ProgressView("Restoring portfolio").frame(maxWidth: .infinity) }
            }.frame(maxWidth: CGFloat(StockStepsTheme.dimensions.contentMaxWidth))
                .padding(CGFloat(space.screen)).frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .refreshable { onRefresh() }
        .sheet(isPresented: $showAbout) { if let state { aboutSheet(state) } }
        .confirmationDialog("Delete account and its transactions?", isPresented: Binding(get: { deleteAccount != nil }, set: { if !$0 { deleteAccount = nil } })) {
            Button("Delete", role: .destructive) { if let account = deleteAccount { onDeleteAccount(account.id) }; deleteAccount = nil }
        } message: { Text("Watchlists and alerts stay saved.") }
        .confirmationDialog("Delete transaction and recalculate balances?", isPresented: Binding(get: { deleteTransaction != nil }, set: { if !$0 { deleteTransaction = nil } })) {
            Button("Delete", role: .destructive) { if let transaction = deleteTransaction { onDeleteTransaction(transaction.id) }; deleteTransaction = nil }
        }
    }

    @ViewBuilder private func header(_ colors: StockColors) -> some View {
        HStack(spacing: CGFloat(space.xs)) {
            Text(holdingSymbol == nil ? "Portfolio" : "Holding details")
                .font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).foregroundStyle(colors.textTitle)
                .accessibilityAddTraits(.isHeader)
                .frame(maxWidth: .infinity, alignment: .leading)
            if holdingSymbol == nil, let state {
                Button { showAbout = true } label: {
                    Image(systemName: "info.circle").foregroundStyle(colors.iconSecondary).frame(width: 44, height: 44)
                }.accessibilityLabel("About these numbers")
                let account = state.accounts.first { $0.id == state.selectedAccountId && !$0.archived }
                Menu {
                    Button("Refresh portfolio", systemImage: "arrow.clockwise", action: onRefresh).disabled(state.busy)
                    if let account {
                        Button("Account settings", systemImage: "gearshape") { onEditAccount(account) }
                        Button("Delete account…", systemImage: "trash", role: .destructive) { deleteAccount = account }
                    }
                } label: {
                    Image(systemName: "ellipsis.circle").foregroundStyle(colors.iconSecondary).frame(width: 44, height: 44)
                }.accessibilityLabel("Portfolio actions")
            }
        }
    }

    /// Sample scenario, loading, sync errors, offline and sign-in — compact, above the numbers they qualify.
    @ViewBuilder private func status(_ state: PortfolioUiState) -> some View {
        if state.mockAvailable {
            StockSelectField(label: "Sample scenario", options: [Self.savedAccounts] + PortfolioFixtureCatalog.shared.ids,
                             selection: Binding(get: { state.mockScenario ?? Self.savedAccounts },
                                                set: { value in onScenario(value == Self.savedAccounts ? nil : value) }),
                             optionLabel: { $0 })
            if state.mockScenario != nil { StockBanner(kind: .sample, message: "Read-only sample; saved accounts and watchlists stay unchanged.") }
        }
        if state.loading { ProgressView("Loading portfolio").frame(maxWidth: .infinity) }
        // One banner for sync problems: offline (cached data) and/or the error, with Retry — never stale data presented as current.
        if state.error != nil || state.offline {
            StockBanner(kind: state.error != nil ? .error : .warning, message: state.error ?? "Values may be out of date.",
                        title: state.offline ? "Offline · last saved portfolio" : "Couldn't refresh your portfolio",
                        actionTitle: state.error != nil ? "Retry" : nil, action: state.error != nil ? onRefresh : nil)
        }
        if !state.signedIn && state.mockScenario == nil {
            StockBanner(kind: .info, message: "Sign in to save your portfolio securely.", actionTitle: "Sign in", action: onSignIn)
        }
    }

    @ViewBuilder private func content(_ state: PortfolioUiState, active: [PortfolioAccount], colors: StockColors) -> some View {
        if holdingSymbol == nil {
            accountSelector(state, active: active, colors: colors)
            hero(state, accountCount: active.count, colors: colors)
            Button(action: onAdd) { Text("+ Add transaction").frame(maxWidth: .infinity) }.buttonStyle(.stockPrimary).disabled(state.busy)
            PortfolioHistoryChart(state: state, onHistory: onHistory)
        }
        let rows = state.holdings.filter { holdingSymbol == nil || $0.symbol == holdingSymbol }
        sectionTitle(holdingSymbol == nil && !rows.isEmpty ? "Holdings (\(rows.count))" : "Holdings", colors)
        if rows.isEmpty {
            Text("No holdings yet. Add an investment with its effective date.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting).stockCard()
        } else {
            VStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.element.symbol) { index, row in
                    if index > 0 { StockDivider() }
                    holdingRow(row, colors: colors)
                }
            }.stockCard(padding: 0)
                .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { rowWidth = $0 }
        }
        if holdingSymbol != nil, let row = rows.first {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                StockMetricGrid(items: [
                    StockMetricItem(label: "Price", value: "\(row.currency) \(PortfolioFormat.shared.amount(value: row.price))"),
                    StockMetricItem(label: "Cost", value: "\(row.currency) \(PortfolioFormat.shared.amount(value: row.basis))"),
                    StockMetricItem(label: "Realized", value: row.realized),
                    StockMetricItem(label: "Dividends", value: row.dividends)
                ], maxColumns: 2)
                Button("Company details") { onCompany(row.symbol) }.frame(minHeight: 44)
                Button("Price alerts") { onAlerts(row.symbol) }.frame(minHeight: 44)
                Button("Add to watchlist") { onWatchlist(row) }.frame(minHeight: 44)
            }.stockCard()
        }
        if holdingSymbol == nil { allocation(state, colors: colors) }
        // Cash and dividends share one card: each is a labelled line, so empty values cost one row, not a whole section.
        sectionTitle("Cash and dividends", colors)
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            valueLines("Cash", state.cash, colors: colors)
            StockDivider()
            valueLines("Dividends received", state.dividends, colors: colors)
        }.stockCard()
        let transactions = state.transactions.filter { holdingSymbol == nil || $0.instrument?.symbol == holdingSymbol }
        sectionTitle(transactions.isEmpty ? "Transactions" : "Transactions (\(transactions.count))", colors)
        VStack(alignment: .leading, spacing: 0) {
            if transactions.isEmpty {
                Text("No transactions yet").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                    .padding(CGFloat(space.cardPadding)).frame(maxWidth: .infinity, alignment: .leading)
            }
            let shown = allTransactions ? transactions : Array(transactions.prefix(Self.recentTransactions))
            ForEach(Array(shown.enumerated()), id: \.element.id) { index, transaction in
                if index > 0 { StockDivider() }
                transactionRow(transaction, colors: colors)
            }
            if transactions.count > Self.recentTransactions {
                Button(allTransactions ? "Show recent only" : "Show all \(transactions.count)") { allTransactions.toggle() }
                    .font(StockStepsTheme.font(type.bodyMedium)).padding(.horizontal, CGFloat(space.cardPadding)).frame(minHeight: 44)
            }
        }.stockCard(padding: 0)
        if holdingSymbol == nil {
            Button(action: onInsights) {
                HStack(spacing: CGFloat(space.md)) {
                    StockIconTile(systemName: "chart.line.uptrend.xyaxis", container: colors.primaryContainer, content: colors.primaryText)
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        Text("Portfolio insights").font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle)
                        Text("Performance, allocation and concentration").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: "chevron.right").foregroundStyle(colors.iconSecondary).accessibilityHidden(true)
                }.stockCard().contentShape(Rectangle())
            }.buttonStyle(.plain).accessibilityElement(children: .combine)
        }
    }

    private func sectionTitle(_ title: String, _ colors: StockColors) -> some View {
        Text(title).font(StockStepsTheme.font(type.sectionTitle)).foregroundStyle(colors.textTitle)
            .accessibilityAddTraits(.isHeader).padding(.top, CGFloat(space.md))
    }

    /// "Cash      CAD 1,200.00" — label left, one value per line right; "None recorded" when empty (never an invented 0.00).
    private func valueLines(_ label: String, _ values: [String], colors: StockColors) -> some View {
        HStack(alignment: .top, spacing: CGFloat(space.md)) {
            Text(label).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting).frame(maxWidth: .infinity, alignment: .leading)
            VStack(alignment: .trailing, spacing: CGFloat(space.xxs)) {
                if values.isEmpty { Text("None recorded").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textMeta) }
                ForEach(values, id: \.self) { Text($0).font(StockStepsTheme.font(type.numberLabelStrong)).foregroundStyle(colors.textValue) }
            }
        }.accessibilityElement(children: .combine)
    }

    /// "Personal · CAD ▾" with a menu of accounts (name, type, currency) and "+ Add".
    private func accountSelector(_ state: PortfolioUiState, active: [PortfolioAccount], colors: StockColors) -> some View {
        let selected = active.first { $0.id == state.selectedAccountId }
        return HStack(spacing: CGFloat(space.sm)) {
            Menu {
                ForEach(active, id: \.id) { account in
                    Button { onSelect(account.id) } label: {
                        if account.id == selected?.id { Label(present.accountLabel(account: account), systemImage: "checkmark") }
                        else { Text(present.accountLabel(account: account)) }
                        Text(present.accountDetail(account: account))
                    }
                }
            } label: {
                HStack(spacing: CGFloat(space.sm)) {
                    Text(selected.map { present.accountLabel(account: $0) } ?? "Choose an account")
                        .font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle).lineLimit(2).multilineTextAlignment(.leading)
                    Image(systemName: "chevron.down").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.iconSecondary)
                }
                .padding(.horizontal, CGFloat(space.md)).padding(.vertical, CGFloat(space.sm)).frame(minHeight: 44)
                .background(colors.surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.button)))
            }
            .accessibilityLabel("Account: \(selected.map { present.accountLabel(account: $0) } ?? "none")")
            .accessibilityHint("Change account")
            Spacer(minLength: 0)
            Button("+ Add", action: onAddAccount).buttonStyle(.stockSecondary).disabled(!state.signedIn || state.busy)
                .accessibilityLabel("Add account")
        }
    }

    /// Value hero: total (dominant), today's change (or that it is unavailable), invested / unrealized / realized, freshness.
    private func hero(_ state: PortfolioUiState, accountCount: Int, colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text("Total portfolio value").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSupporting)
            Text("\(state.currency) \(PortfolioFormat.shared.amount(value: state.total))")
                .font(StockStepsTheme.font(type.largeNumber)).foregroundStyle(colors.textValue)
                .minimumScaleFactor(0.7).lineLimit(2)
            if let today = present.todayChange(state: state) {
                StockPriceChange(percentage: "\(today) today", direction: present.direction(amount: state.dailyGain), style: type.numberMedium, lineLimit: nil)
            } else {
                // The reason stays one tap away instead of adding two lines to every view of the hero.
                HStack(spacing: CGFloat(space.xs)) {
                    Text(present.TODAY_UNAVAILABLE).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                    if !state.dailyNotice.isEmpty {
                        Button(showDailyReason ? "Hide" : "Why?") { showDailyReason.toggle() }
                            .font(StockStepsTheme.font(type.label)).foregroundStyle(colors.primaryText).frame(minHeight: 44)
                            .accessibilityLabel(showDailyReason ? "Hide reason" : "Why is today's change unavailable?")
                    }
                }
                if showDailyReason { Text(state.dailyNotice).font(StockStepsTheme.font(type.caption)).foregroundStyle(colors.textMeta) }
            }
            StockDivider().padding(.vertical, CGFloat(space.xs))
            StockMetricGrid(items: [
                StockMetricItem(label: "Invested cost", value: PortfolioFormat.shared.amount(value: state.basis)),
                StockMetricItem(label: "Unrealized P/L", value: present.signedAmount(amount: state.unrealized) ?? "—", direction: present.direction(amount: state.unrealized)),
                StockMetricItem(label: "Realized P/L", value: present.signedAmount(amount: state.realized) ?? "—", direction: present.direction(amount: state.realized))
            ], rowsWhenNarrow: true)
            if accountCount > 1 {
                Text("All accounts · \(state.currency) \(PortfolioFormat.shared.amount(value: state.combinedTotal))").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
            }
            if let notice = state.notice { Text(notice).font(StockStepsTheme.font(type.caption)).foregroundStyle(colors.cautionText) }
            ForEach(present.freshnessLines(state: state), id: \.self) { Text($0).font(StockStepsTheme.font(type.caption)).foregroundStyle(colors.textMeta) }
            if let sample = state.mockScenario { StockStatusBadge(text: "Sample · \(sample)", kind: .sample, size: .compact) }
        }.stockCard()
    }

    /// Compact holding row: logo · name / ticker · shares — value / unrealized change; one spoken description. At large Dynamic Type
    /// sizes the values move under the name so long company names never break mid-word.
    private func holdingRow(_ row: PortfolioHoldingRow, colors: StockColors) -> some View {
        // Narrow rows (small phones, split view) and large text put the values under the name, so neither is squeezed.
        let stacked = typeSize >= .xxxLarge || (rowWidth > 0 && rowWidth < CGFloat(PortfolioLayout.shared.stackedRowWidth))
        return Button { onHolding(row.symbol) } label: {
            HStack(spacing: CGFloat(space.md)) {
                StockTickerAvatar(symbol: row.symbol, logoUrl: row.logoUrl)
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(row.name).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle).lineLimit(2).multilineTextAlignment(.leading)
                    Text(present.holdingSubtitle(row: row)).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                    if row.stale { Text("Last available quote · delayed or stale").font(StockStepsTheme.font(type.caption)).foregroundStyle(colors.textMeta) }
                    if stacked { holdingValues(row, alignment: .leading, colors: colors) }
                }.frame(maxWidth: .infinity, alignment: .leading)
                if !stacked { holdingValues(row, alignment: .trailing, colors: colors).fixedSize(horizontal: true, vertical: false) }
            }
            .padding(.horizontal, CGFloat(space.cardPadding)).padding(.vertical, CGFloat(space.sm))
            .frame(minHeight: CGFloat(StockStepsTheme.dimensions.rowMinHeight)).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(holdingSymbol != nil)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(present.holdingDescription(row: row))
        .accessibilityAddTraits(holdingSymbol == nil ? .isButton : [])
    }

    private func holdingValues(_ row: PortfolioHoldingRow, alignment: HorizontalAlignment, colors: StockColors) -> some View {
        VStack(alignment: alignment, spacing: CGFloat(space.xxs)) {
            Text(present.holdingValue(row: row)).font(StockStepsTheme.font(type.numberLabelStrong)).foregroundStyle(colors.textValue).lineLimit(1)
            if let lines = present.holdingChangeLines(row: row) {
                let direction = present.direction(amount: row.gain)
                StockPriceChange(percentage: lines.first! as String, direction: direction)
                if let second = lines.second as String? {
                    Text(second).font(StockStepsTheme.font(type.numberLabel))
                        .foregroundStyle(direction == .down ? colors.negativeText : direction == .up ? colors.positiveText : colors.textSupporting)
                }
            } else {
                Text("Gain unavailable").font(StockStepsTheme.font(type.caption)).foregroundStyle(colors.textMeta)
            }
        }
    }

    /// Human-readable transaction with Edit / Delete in a menu.
    private func transactionRow(_ transaction: PortfolioTransaction, colors: StockColors) -> some View {
        let title = [present.transactionLabel(type: transaction.type), present.transactionDetail(transaction: transaction)].compactMap { $0 }.joined(separator: " · ")
        return HStack(spacing: CGFloat(space.sm)) {
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                Text(title).font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(colors.textTitle)
                Text("\(present.dateLabel(date: transaction.tradeDate)) · \(present.transactionAmount(transaction: transaction))").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .combine)
            Menu {
                Button("Edit", systemImage: "pencil") { onEditTransaction(transaction) }
                Button("Delete…", systemImage: "trash", role: .destructive) { deleteTransaction = transaction }
            } label: {
                Image(systemName: "ellipsis").foregroundStyle(colors.iconSecondary).frame(width: 44, height: 44)
            }.accessibilityLabel("Actions for \(title)")
        }
        .padding(.leading, CGFloat(space.cardPadding)).padding(.vertical, CGFloat(space.xs))
    }

    /// Allocation by holding and by currency as labelled bars; one line when there is nothing to allocate (no empty chart area).
    @ViewBuilder private func allocation(_ state: PortfolioUiState, colors: StockColors) -> some View {
        sectionTitle("Allocation", colors)
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            if state.allocations.isEmpty && state.currencyAllocations.isEmpty {
                Text("Allocation appears once you have holdings.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
            }
            if !state.allocations.isEmpty { Text("By holding").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSupporting) }
            ForEach(state.allocations, id: \.symbol) { allocation in
                allocationBar("\(allocation.symbol) · \(allocation.currency.name)", percent: allocation.percent,
                              detail: allocation.reportingValue.map { "\(state.currency) \(PortfolioFormat.shared.amount(value: $0))" }, colors: colors)
            }
            if !state.currencyAllocations.isEmpty {
                Text("By currency").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSupporting).padding(.top, CGFloat(space.xs))
            }
            ForEach(state.currencyAllocations, id: \.currency) {
                allocationBar($0.currency.name, percent: $0.percent, detail: "\(state.currency) \(PortfolioFormat.shared.amount(value: $0.reportingValue))", colors: colors)
            }
        }.stockCard()
    }

    private func allocationBar(_ label: String, percent: String?, detail: String?, colors: StockColors) -> some View {
        let text = percent.map { "\(PortfolioFormat.shared.amount(value: $0))%" } ?? "—"
        let fraction = CGFloat(present.allocationFraction(percent: percent))
        return VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            HStack(spacing: CGFloat(space.sm)) {
                Text(label).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody).frame(maxWidth: .infinity, alignment: .leading)
                Text(text).font(StockStepsTheme.font(type.numberLabelStrong)).foregroundStyle(colors.textValue)
                if let detail { Text(detail).font(StockStepsTheme.font(type.numberLabel)).foregroundStyle(colors.textSupporting) }
            }
            Capsule().fill(colors.surfaceSecondary).frame(height: CGFloat(StockStepsTheme.dimensions.rangeBar))
                .overlay(alignment: .leading) {
                    GeometryReader { proxy in Capsule().fill(colors.primary).frame(width: proxy.size.width * fraction) }
                }
                .accessibilityHidden(true)
        }.accessibilityElement(children: .combine)
    }

    /// Explanations moved out of the dashboard: daily return, missing values, history, cash and allocation rules.
    private func aboutSheet(_ state: PortfolioUiState) -> some View {
        NavigationStack {
            List {
                ForEach([state.dailyNotice.isEmpty ? nil : state.dailyNotice, state.notice, state.historyNotice,
                         "Cash is included in account value. A negative cash balance represents unfunded or borrowed cash; record deposits separately from existing investments.",
                         "Borrowed cash can make a holding exceed 100%. Sector and ETF look-through allocations are not estimated."].compactMap { $0 }, id: \.self) {
                    Text($0).font(StockStepsTheme.font(type.small))
                }
            }
            .navigationTitle("About these numbers").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { showAbout = false } } }
        }
        .presentationDetents([.medium, .large])
    }
}

