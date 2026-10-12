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
    /// The period chosen on this screen (nil = none yet): history stays on demand, so the card can tell "not requested" from "no values".
    @State private var requestedPeriod: String?
    @State private var sampleDetails = false
    @State private var choosingScenario = false
    @State private var rowWidth: CGFloat = 0
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    private let space = StockStepsTheme.spacing
    private let type = StockStepsTheme.typography
    private let present = PortfolioPresentation.shared
    private let display = PortfolioDisplay.shared
    private static let savedAccounts = PortfolioDisplay.shared.SAVED_ACCOUNTS
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
                        StockStateMessage(title: display.EMPTY_TITLE,
                                          message: display.EMPTY_MESSAGE,
                                          systemImage: "chart.pie",
                                          actionTitle: state.signedIn && !state.busy ? display.EMPTY_ACTION : nil, action: onAddAccount)
                    } else {
                        content(state, active: active, colors: colors)
                    }
                } else { ProgressView(display.RESTORING).frame(maxWidth: .infinity) }
            }.frame(maxWidth: CGFloat(StockStepsTheme.dimensions.contentMaxWidth))
                .padding(CGFloat(space.screen)).frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .refreshable { onRefresh() }
        .sheet(isPresented: $showAbout) { if let state { aboutSheet(state) } }
        .sheet(isPresented: $choosingScenario) {
            PortfolioScenarioSheet(selected: state?.mockScenario ?? Self.savedAccounts) { id in
                choosingScenario = false
                onScenario(id == Self.savedAccounts ? nil : id)
            }
        }
        .confirmationDialog(deleteAccount.map { display.deleteAccountTitle(name: $0.name) } ?? display.DELETE, isPresented: Binding(get: { deleteAccount != nil }, set: { if !$0 { deleteAccount = nil } }), titleVisibility: .visible) {
            Button(display.DELETE, role: .destructive) { if let account = deleteAccount { onDeleteAccount(account.id) }; deleteAccount = nil }
        } message: { Text(display.DELETE_ACCOUNT_MESSAGE) }
        .confirmationDialog(display.DELETE_TRANSACTION_TITLE, isPresented: Binding(get: { deleteTransaction != nil }, set: { if !$0 { deleteTransaction = nil } }), titleVisibility: .visible) {
            Button(display.DELETE, role: .destructive) { if let transaction = deleteTransaction { onDeleteTransaction(transaction.id) }; deleteTransaction = nil }
        } message: { Text(display.DELETE_TRANSACTION_MESSAGE) }
    }

    @ViewBuilder private func header(_ colors: StockColors) -> some View {
        HStack(spacing: CGFloat(space.xs)) {
            Text(holdingSymbol == nil ? display.TITLE : display.HOLDING_DETAILS_TITLE)
                .font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).foregroundStyle(colors.textTitle)
                .accessibilityAddTraits(.isHeader)
                .frame(maxWidth: .infinity, alignment: .leading)
            if holdingSymbol == nil, let state {
                Button { showAbout = true } label: {
                    Image(systemName: "info.circle").foregroundStyle(colors.iconSecondary)
                        .frame(width: CGFloat(StockStepsTheme.dimensions.touchTarget), height: CGFloat(StockStepsTheme.dimensions.touchTarget))
                }.accessibilityLabel(display.ABOUT_TITLE)
                let account = state.accounts.first { $0.id == state.selectedAccountId && !$0.archived }
                Menu {
                    Button("Refresh portfolio", systemImage: "arrow.clockwise", action: onRefresh).disabled(state.busy)
                    if let account {
                        Button("Account settings", systemImage: "gearshape") { onEditAccount(account) }
                        Button("Delete account…", systemImage: "trash", role: .destructive) { deleteAccount = account }
                    }
                    // MOCK only: one entry opening the grouped scenario sheet (B.1 — the inline list ran past the screen).
                    if state.mockAvailable {
                        Button(display.SCENARIO_MENU, systemImage: "square.stack.3d.up") { choosingScenario = true }
                    }
                } label: {
                    Image(systemName: "ellipsis.circle").foregroundStyle(colors.iconSecondary)
                        .frame(width: CGFloat(StockStepsTheme.dimensions.touchTarget), height: CGFloat(StockStepsTheme.dimensions.touchTarget))
                }.accessibilityLabel("Portfolio actions")
            }
        }
    }

    /// Sample scenario, loading, sync errors, offline and sign-in — compact, above the numbers they qualify.
    @ViewBuilder private func status(_ state: PortfolioUiState) -> some View {
        // The scenario is chosen in the ⋯ menu; while one is shown this read-only banner stays visible (never collapsed).
        // B.1: one short line; the full explanation is one tap away (never collapsed away entirely).
        if state.mockScenario != nil {
            StockBanner(kind: .sample, message: sampleDetails ? "\(display.SAMPLE_SHORT) \(display.SAMPLE_BANNER)" : display.SAMPLE_SHORT,
                        actionTitle: sampleDetails ? display.SAMPLE_HIDE : display.SAMPLE_DETAILS, action: { sampleDetails.toggle() })
        }
        if state.loading { ProgressView(display.LOADING).frame(maxWidth: .infinity) }
        // One banner for sync problems: offline (cached data) and/or the error, with Retry — never stale data presented as current.
        if state.error != nil || state.offline {
            StockBanner(kind: state.error != nil ? .error : .warning, message: state.error ?? display.OUT_OF_DATE,
                        title: state.offline ? display.OFFLINE_TITLE : display.REFRESH_FAILED_TITLE,
                        actionTitle: state.error != nil ? "Retry" : nil, action: state.error != nil ? onRefresh : nil)
        }
        if !state.signedIn && state.mockScenario == nil {
            StockBanner(kind: .info, message: display.SIGN_IN_PROMPT, actionTitle: "Sign in", action: onSignIn)
        }
    }

    @ViewBuilder private func content(_ state: PortfolioUiState, active: [PortfolioAccount], colors: StockColors) -> some View {
        if holdingSymbol == nil {
            overviewCard(state, active: active, colors: colors)
            // Outside the overview so the total stays next to its history.
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                Button(action: onAdd) { Text("+ Add transaction").frame(maxWidth: .infinity) }.buttonStyle(.stockPrimary).disabled(state.busy)
                // Samples are read-only (the presenter rejects changes); say why the action is off instead of a silent grey button.
                if state.mockScenario != nil {
                    Text(display.ADD_DISABLED_SAMPLE).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                }
            }
        }
        let rows = state.holdings.filter { holdingSymbol == nil || $0.symbol == holdingSymbol }
        sectionTitle(holdingSymbol == nil && !rows.isEmpty ? "Holdings (\(rows.count))" : "Holdings", colors)
        if rows.isEmpty {
            Text(display.HOLDINGS_EMPTY).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting).stockCard()
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
                    StockMetricItem(label: "Realized", value: present.holdingRealized(row: row), direction: present.direction(amount: row.realized)),
                    StockMetricItem(label: "Dividends", value: present.holdingDividends(row: row))
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
                Text(display.TRANSACTIONS_EMPTY).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
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
                        Text(display.INSIGHTS_TITLE).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle)
                        Text(display.INSIGHTS_SUBTITLE).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: "chevron.right").foregroundStyle(colors.iconSecondary).accessibilityHidden(true)
                }.stockCard().contentShape(Rectangle())
            }.buttonStyle(.plain).accessibilityElement(children: .combine).accessibilityHint(display.INSIGHTS_OPEN)
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
        let menu = Menu {
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
                    .font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle).multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                Image(systemName: "chevron.down").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.iconSecondary)
            }
            .padding(.horizontal, CGFloat(space.md)).padding(.vertical, CGFloat(space.sm)).frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
            .background(colors.surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.button)))
        }
        .accessibilityLabel("Account: \(selected.map { present.accountLabel(account: $0) } ?? "none")")
        .accessibilityHint("Change account")
        let add = Button("+ Add", action: onAddAccount).buttonStyle(.stockSecondary).disabled(!state.signedIn || state.busy)
            .accessibilityLabel("Add account")
        // B.1: at accessibility sizes "+ Add" moves under the selector — side by side it squeezed the name to "Pers / on…" at AX5.
        return Group {
            if typeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) { menu; add }
            } else {
                HStack(spacing: CGFloat(space.sm)) { menu; Spacer(minLength: 0); add }
            }
        }
    }

    /// Overview card (Portfolio Phase B), mirroring Compose `PortfolioOverviewCard`: account context → total value (largest; wraps, never
    /// shrinks) → today's change or "unavailable · Why?" → all-accounts line → account value history → Invested / Unrealized / Realized with
    /// currencies → caution notice and one freshness line. Text from `PortfolioOverviewDisplay`; value and caption share a Dynamic Type curve.
    private func overviewCard(_ state: PortfolioUiState, active: [PortfolioAccount], colors: StockColors) -> some View {
        let overview = PortfolioOverviewDisplay.shared.of(state: state, activeAccounts: Int32(active.count))
        return VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            accountSelector(state, active: active, colors: colors)
            // One spoken summary (total, today's change in words, all accounts) when there is no "Why?" button to reach; otherwise the total alone.
            Group {
                if overview.today != nil {
                    VStack(alignment: .leading, spacing: CGFloat(space.sm)) { summaryContent(state, overview, colors) }
                        .accessibilityElement(children: .ignore).accessibilityLabel(overview.spoken)
                } else {
                    VStack(alignment: .leading, spacing: CGFloat(space.sm)) { summaryContent(state, overview, colors) }
                }
            }
            StockDivider()
            PortfolioHistorySection(state: state, requested: requestedPeriod,
                                    onPeriod: { period in requestedPeriod = period; onHistory(period) }, onRetry: onRefresh)
            StockDivider()
            StockMetricGrid(items: overview.metrics.map { StockMetricItem(label: $0.label, value: $0.value, direction: $0.direction) }, rowsWhenNarrow: true)
            if let notice = overview.notice {
                Text(notice).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.cautionText)
            }
            if let freshness = overview.freshness {
                Text(freshness).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textMeta)
            }
        }.stockCard()
    }

    /// Total value, today's change (or "unavailable · Why?") and the all-accounts line at the top of the overview card.
    @ViewBuilder private func summaryContent(_ state: PortfolioUiState, _ overview: PortfolioOverview, _ colors: StockColors) -> some View {
        let labels = PortfolioOverviewDisplay.shared
        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            StockTitleWithBadge {
                Text(overview.totalLabel).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
            } badge: {
                if let badge = overview.sampleBadge { StockStatusBadge(text: badge, kind: .sample, size: .compact) }
            }
            if let scenario = overview.sampleScenario {
                Text(scenario).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
            }
            Text(overview.total).font(StockStepsTheme.font(type.largeNumber, relativeTo: .title1)).foregroundStyle(colors.textValue)
                .fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            // The label only above a value: "Today's change unavailable" already names itself.
            if let today = overview.today {
                Text(labels.TODAY_LABEL).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                StockPriceChange(percentage: today, direction: overview.todayDirection, style: type.numberMedium, lineLimit: nil)
            } else {
                // The reason stays one tap away instead of adding two lines to every view of the card.
                HStack(spacing: CGFloat(space.xs)) {
                    Text(present.TODAY_UNAVAILABLE).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                    if !state.dailyNotice.isEmpty {
                        Button(showDailyReason ? "Hide" : "Why?") { showDailyReason.toggle() }
                            .font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.primaryText)
                            .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
                            .accessibilityLabel(showDailyReason ? "Hide reason" : "Why is today's change unavailable?")
                    }
                }
                if showDailyReason { Text(state.dailyNotice).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textMeta) }
            }
        }
        if let all = overview.allAccounts {
            Text(all).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
        }
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
                         display.ABOUT_CASH, display.ABOUT_ALLOCATION].compactMap { $0 }, id: \.self) {
                    Text($0).font(StockStepsTheme.font(type.small))
                }
            }
            .navigationTitle(display.ABOUT_TITLE).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { showAbout = false } } }
        }
        .presentationDetents([.medium, .large])
    }
}

/// MOCK sample scenarios grouped by what they show (Portfolio B.1, `PortfolioScenarioGroups`), opened from the ⋯ menu; mirrors the Compose
/// `PortfolioScenarioSheet`. The active one is checked and announced as selected.
struct PortfolioScenarioSheet: View {
    let selected: String
    let onSelect: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme
    private let display = PortfolioDisplay.shared
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        NavigationStack {
            List {
                Section { Text(display.SCENARIO_NOTE).font(StockStepsTheme.font(StockStepsTheme.typography.small, relativeTo: .footnote)).foregroundStyle(colors.textSupporting) }
                ForEach(PortfolioScenarioGroups.shared.groups(ids: PortfolioFixtureCatalog.shared.ids), id: \.title) { group in
                    Section(group.title) {
                        ForEach(group.ids, id: \.self) { id in
                            Button { onSelect(id) } label: {
                                HStack {
                                    Text(display.scenarioLabel(id: id)).foregroundStyle(id == selected ? colors.primaryText : colors.textBody)
                                    Spacer()
                                    if id == selected { Image(systemName: "checkmark").foregroundStyle(colors.primaryText).accessibilityHidden(true) }
                                }
                                .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
                                .contentShape(Rectangle())
                            }
                            .accessibilityAddTraits(id == selected ? [.isSelected] : [])
                        }
                    }
                }
            }
            .navigationTitle(display.SAMPLE_SCENARIO).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(display.SCENARIO_DONE) { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}
