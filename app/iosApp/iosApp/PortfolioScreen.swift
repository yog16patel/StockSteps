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
    /// Phase D2: opens Transaction history (scoped to `holdingSymbol` on Holding details); nil keeps the preview without a link.
    var onTransactions: (() -> Void)? = nil
    /// Phase D3: opens Transaction details for a row; nil keeps rows without the tap.
    var onOpenTransaction: ((String) -> Void)? = nil
    /// D3.1: dismisses a rejected save / delete / account change (Retry would only refresh).
    var onDismissError: () -> Void = {}
    /// E.2: "Sign in again" for an ended session (`portfolioSignInAgain`).
    var onSignInAgain: () -> Void = {}
    @State private var deleteAccount: PortfolioAccount?
    @State private var deleteTransaction: PortfolioTransaction?
    @State private var showAbout = false
    @State private var showDailyReason = false
    /// The period chosen on this screen (nil = none yet): history stays on demand, so the card can tell "not requested" from "no values".
    @State private var requestedPeriod: String?
    @State private var sampleDetails = false
    @State private var choosingScenario = false
    /// Phase C: display-only holdings order (nil = the presenter's order); not persisted.
    @State private var holdingSort: PortfolioHoldingSort?
    @State private var byCurrency = false
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
                    StockSegmentedControl(segments: [("My Portfolio", "briefcase"), ("Practice", "graduationcap")], selected: 0, onSelect: { index in
                        if index == 1 { onPractice() }
                    }, compactIcons: true)
                    .accessibilityHint("Practice opens the separate simulated portfolio")
                }
                if let state {
                    status(state)
                    let active = state.accounts.filter { !$0.archived }
                    if active.isEmpty {
                        StockStateMessage(title: display.EMPTY_TITLE,
                                          message: display.EMPTY_MESSAGE,
                                          systemImage: "chart.pie",
                                          actionTitle: state.signedIn && !state.busy && !state.sessionExpired ? display.EMPTY_ACTION : nil, action: onAddAccount)
                    } else {
                        content(state, active: active, colors: colors)
                    }
                } else { ProgressView(display.RESTORING).frame(maxWidth: .infinity) }
            }.frame(maxWidth: CGFloat(StockStepsTheme.dimensions.contentMaxWidth))
                .padding(CGFloat(space.screen)).frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        // C.1: no navigation bar on this tab, so scrolled content showed through the status bar (time over numbers). A zero-height strip at the
        // top of the safe area paints the page background behind the status bar; decorative and never hit-testable.
        .overlay(alignment: .top) {
            Color.clear.frame(height: 0)
                .background(colors.appBackground.ignoresSafeArea(edges: .top))
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
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
        } message: { Text(deleteConfirmation(deleteTransaction)) }
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
            portfolioErrorBanner(state, onRefresh: onRefresh, onDismiss: onDismissError, onSignInAgain: onSignInAgain)
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
                Button(action: onAdd) { Text("+ Add transaction").frame(maxWidth: .infinity) }.buttonStyle(.stockPrimary).disabled(state.busy || state.sessionExpired)
                // Samples are read-only (the presenter rejects changes); say why the action is off instead of a silent grey button.
                if state.mockScenario != nil {
                    Text(display.ADD_DISABLED_SAMPLE).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                } else if let reason = display.addDisabledReason(state: state) {
                    // E.2: the same for an ended session (an ordinary outage keeps Add; the save explains its own failure).
                    Text(reason).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                }
            }
        }
        let items = PortfolioHoldingsDisplay.shared.items(state: state, sort: holdingSymbol == nil ? holdingSort : nil)
            .filter { holdingSymbol == nil || $0.symbol == holdingSymbol }
        // Phase C: the sort menu sits beside the title (display order only) instead of a toolbar.
        // C.1: no "Holdings" title on Holding details — the holding row itself heads that screen.
        if holdingSymbol == nil {
            // C.1: at accessibility sizes the sort menu goes under the title instead of squeezing it.
            if typeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 0) {
                    sectionTitle(!items.isEmpty ? "Holdings (\(items.count))" : "Holdings", colors)
                    if items.count > 1 { holdingSortMenu(colors) }
                }
            } else {
                HStack(alignment: .lastTextBaseline) {
                    sectionTitle(!items.isEmpty ? "Holdings (\(items.count))" : "Holdings", colors)
                    Spacer(minLength: CGFloat(space.sm))
                    if items.count > 1 { holdingSortMenu(colors) }
                }
            }
        }
        if items.isEmpty {
            Text(display.HOLDINGS_EMPTY).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting).stockCard(bordered: false)
        } else {
            VStack(spacing: 0) {
                ForEach(Array(items.enumerated()), id: \.element.symbol) { index, item in
                    if index > 0 { StockDivider().padding(.horizontal, CGFloat(space.cardPadding)) }
                    holdingRow(item, colors: colors)
                }
            }.stockCard(padding: 0, bordered: false)
                .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { rowWidth = $0 }
        }
        // Holding details (Phase C): every holding metric with its currency in a responsive grid, then the actions as navigation rows.
        if holdingSymbol != nil, let row = state.holdings.first(where: { $0.symbol == holdingSymbol }) {
            StockMetricGrid(items: PortfolioHoldingsDisplay.shared.detailMetrics(row: row).map { StockMetricItem(label: present.keepTogether(label: $0.label), value: $0.value, direction: $0.direction) },
                            maxColumns: 2, rowsWhenNarrow: true)
                .stockCard(bordered: false)
            sectionTitle(PortfolioHoldingsDisplay.shared.DETAILS_ACTIONS, colors)
            VStack(spacing: 0) {
                StockNavigationRow(title: PortfolioHoldingsDisplay.shared.COMPANY_DETAILS, icon: "doc.text") { onCompany(row.symbol) }.padding(.horizontal, CGFloat(space.cardPadding))
                StockDivider().padding(.horizontal, CGFloat(space.cardPadding))
                StockNavigationRow(title: PortfolioHoldingsDisplay.shared.PRICE_ALERTS, icon: "bell") { onAlerts(row.symbol) }.padding(.horizontal, CGFloat(space.cardPadding))
                StockDivider().padding(.horizontal, CGFloat(space.cardPadding))
                StockNavigationRow(title: PortfolioHoldingsDisplay.shared.ADD_TO_WATCHLIST, icon: "star") { onWatchlist(row) }.padding(.horizontal, CGFloat(space.cardPadding))
            }.stockCard(padding: 0, bordered: false)
        }
        if holdingSymbol == nil { allocation(state, colors: colors) }
        // Cash and dividends share one card: each is a labelled line, so empty values cost one row, not a whole section. Account-wide, so
        // not on Holding details (Phase C: it isn't this holding's cash).
        if holdingSymbol == nil {
            sectionTitle("Cash and dividends", colors)
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                valueLines("Cash", state.cash, colors: colors)
                StockDivider()
                valueLines("Dividends received", state.dividends, colors: colors)
            }.stockCard(bordered: false)
        }
        let transactions = state.transactions.filter { holdingSymbol == nil || $0.instrument?.symbol == holdingSymbol }
        sectionTitle(transactions.isEmpty ? "Transactions" : "Transactions (\(transactions.count))", colors)
        VStack(alignment: .leading, spacing: 0) {
            if transactions.isEmpty {
                Text(display.TRANSACTIONS_EMPTY).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                    .padding(CGFloat(space.cardPadding)).frame(maxWidth: .infinity, alignment: .leading)
            }
            // D2: a short preview; the full list is Transaction history (the inline "Show all" toggle grew this page without limit).
            let readOnly = PortfolioFormRules.shared.readOnly(state: state)
            ForEach(Array(transactions.prefix(Self.recentTransactions).enumerated()), id: \.element.id) { index, transaction in
                if index > 0 { StockDivider().padding(.horizontal, CGFloat(space.cardPadding)) } // C.1 closure: inset like the holdings rows
                PortfolioTransactionRow(transaction: transaction, logoUrl: state.logo(for: transaction),
                                        onOpen: onOpenTransaction.map { open in { open(transaction.id) } }, onEdit: readOnly ? nil : { onEditTransaction(transaction) },
                                        onDelete: readOnly ? nil : { deleteTransaction = transaction })
            }
            // D2 owner decision: the link is shown whenever there is at least one transaction (one consistent entry point).
            if !transactions.isEmpty, let onTransactions {
                Button(PortfolioTransactionsDisplay.shared.seeAll(count: Int32(transactions.count)), action: onTransactions)
                    .font(StockStepsTheme.font(type.bodyMedium)).padding(.horizontal, CGFloat(space.cardPadding)).frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
            }
        }.stockCard(padding: 0, bordered: false)
        if holdingSymbol == nil {
            Button(action: onInsights) {
                HStack(spacing: CGFloat(space.md)) {
                    // Decorative tile hidden at accessibility sizes: with it the subtitle broke as "concentratio / n" at AX5 (Phase C).
                    if !typeSize.isAccessibilitySize {
                        StockIconTile(systemName: "chart.line.uptrend.xyaxis", container: colors.primaryContainer, content: colors.primaryText)
                    }
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        Text(display.INSIGHTS_TITLE).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle)
                        Text(display.INSIGHTS_SUBTITLE).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: "chevron.right").foregroundStyle(colors.iconSecondary).accessibilityHidden(true)
                }.stockCard(bordered: false).contentShape(Rectangle())
            }.buttonStyle(.plain).accessibilityElement(children: .combine).accessibilityHint(display.INSIGHTS_OPEN)
        }
    }

    private func sectionTitle(_ title: String, _ colors: StockColors) -> some View {
        Text(title).font(StockStepsTheme.font(type.sectionTitle)).foregroundStyle(colors.textTitle)
            .accessibilityAddTraits(.isHeader).padding(.top, CGFloat(space.md))
    }

    /// "Cash      CAD 1,200.00" — label left, one value per line right; "None recorded" when empty (never an invented 0.00).
    private func valueLines(_ label: String, _ values: [String], colors: StockColors) -> some View {
        let title = Text(label).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
        let lines = VStack(alignment: typeSize.isAccessibilitySize ? .leading : .trailing, spacing: CGFloat(space.xxs)) {
            if values.isEmpty { Text("None recorded").font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textMeta) }
            // D3.1: "USD 168.50" / "-USD 51.50" from the presenter's exact values (display only); cash below zero is labelled.
            ForEach(values, id: \.self) { raw in
                let line = present.moneyLine(line: raw)
                Text(verbatim: line.text).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .footnote)).foregroundStyle(colors.textValue)
                    .accessibilityLabel(line.spoken)
                if line.negative && label == "Cash" {
                    Text(display.CASH_UNFUNDED).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                }
            }
        }
        // Phase C: at accessibility sizes the value goes under its label — side by side "Dividends received" broke as "Dividend / s" at AX5.
        return Group {
            if typeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) { title; lines }.frame(maxWidth: .infinity, alignment: .leading)
            } else {
                HStack(alignment: .top, spacing: CGFloat(space.md)) { title.frame(maxWidth: .infinity, alignment: .leading); lines }
            }
        }.accessibilityElement(children: .combine)
    }

    /// "Personal · CAD ▾" with a menu of accounts (name, type, currency) and "+ Add".
    private func accountSelector(_ state: PortfolioUiState, active: [PortfolioAccount], colors: StockColors) -> some View {
        let menu = PortfolioAccountMenu(accounts: active, selectedId: state.selectedAccountId, onSelect: onSelect) // D2: shared with Transaction history
        let add = Button("+ Add", action: onAddAccount).buttonStyle(.stockSecondary).disabled(!state.signedIn || state.busy || state.sessionExpired)
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
            StockMetricGrid(items: overview.metrics.map { StockMetricItem(label: present.keepTogether(label: $0.label), value: $0.value, direction: $0.direction) }, rowsWhenNarrow: true)
            if let notice = overview.notice {
                Text(notice).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.cautionText)
            }
            if let freshness = overview.freshness {
                Text(freshness).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textMeta)
            }
        }.stockCard(bordered: false)   // Phase C: borderless — surface colour and whitespace separate it (no shadow added).
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
            Text(overview.total).font(StockStepsTheme.font(type.largeNumber, relativeTo: .title1)).foregroundStyle(colors.textValue)
                .fixedSize(horizontal: false, vertical: true)
            // C.1: under the total, so the total sits right under its label.
            if let scenario = overview.sampleScenario {
                Text(scenario).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
            }
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

    /// Holding row (Phase C, mirrors Compose): avatar · name / "AAPL · NASDAQ · 10 shares" / optional weight — market value (strong) over the
    /// signed change. One spoken description. Narrow rows and large text put the values under the name (shared `PortfolioLayout`), so names
    /// wrap on whole words and values are never cut.
    private func holdingRow(_ item: PortfolioHoldingItem, colors: StockColors) -> some View {
        let stacked = typeSize >= .xxxLarge || (rowWidth > 0 && rowWidth < CGFloat(PortfolioLayout.shared.stackedRowWidth))
        let name = Text(item.name).font(StockStepsTheme.font(type.bodySemiBold, relativeTo: .body)).foregroundStyle(colors.textTitle)
            .multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
        let details = Group {
            Text(item.subtitle).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
            if let weight = item.weight { Text(weight).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textMeta) }
            if item.stale { Text(PortfolioHoldingsDisplay.shared.STALE).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textMeta) }
        }
        return Button { onHolding(item.symbol) } label: {
            Group {
                if stacked {
                    // C.1: structured vertical layout — avatar beside the name only; ticker, weight and values use the full row width below.
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        HStack(spacing: CGFloat(space.md)) { StockTickerAvatar(symbol: item.symbol, logoUrl: item.logoUrl); name }
                        details
                        holdingValues(item, alignment: .leading, colors: colors)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    HStack(spacing: CGFloat(space.md)) {
                        StockTickerAvatar(symbol: item.symbol, logoUrl: item.logoUrl)
                        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) { name; details }.frame(maxWidth: .infinity, alignment: .leading)
                        holdingValues(item, alignment: .trailing, colors: colors).fixedSize(horizontal: true, vertical: false)
                    }
                }
            }
            .padding(.horizontal, CGFloat(space.cardPadding)).padding(.vertical, CGFloat(space.sm))
            .frame(minHeight: CGFloat(StockStepsTheme.dimensions.rowMinHeight)).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        // Not tappable on Holding details, but not dimmed either (`.disabled` greyed the row and lowered its contrast — Phase C).
        .allowsHitTesting(holdingSymbol == nil)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(item.spoken)
        .accessibilityAddTraits(holdingSymbol == nil ? .isButton : [])
    }

    private func holdingValues(_ item: PortfolioHoldingItem, alignment: HorizontalAlignment, colors: StockColors) -> some View {
        VStack(alignment: alignment, spacing: CGFloat(space.xxs)) {
            Text(item.value).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .footnote)).foregroundStyle(colors.textValue)
            if let change = item.change {
                StockPriceChange(percentage: change, direction: item.direction, lineLimit: nil)
            } else {
                Text(PortfolioHoldingsDisplay.shared.GAIN_UNAVAILABLE).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textMeta)
            }
        }
    }

    /// "Sort: Value ▾" — Default / Value / Name / Change %; display order only, nothing saved.
    private func holdingSortMenu(_ colors: StockColors) -> some View {
        Menu {
            Picker(PortfolioHoldingsDisplay.shared.SORT_TITLE, selection: $holdingSort) {
                Text(PortfolioHoldingsDisplay.shared.DEFAULT_ORDER).tag(PortfolioHoldingSort?.none)
                ForEach(PortfolioHoldingSort.entries, id: \.self) { Text($0.label).tag(PortfolioHoldingSort?.some($0)) }
            }
        } label: {
            HStack(spacing: CGFloat(space.xxs)) {
                Text(PortfolioHoldingsDisplay.shared.sortLabel(sort: holdingSort)).font(StockStepsTheme.font(type.label, relativeTo: .footnote))
                Image(systemName: "chevron.down").font(StockStepsTheme.font(type.caption, relativeTo: .footnote))
            }
            .foregroundStyle(colors.primaryText)
            .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
        }
        .accessibilityLabel(PortfolioHoldingsDisplay.shared.SORT_TITLE)
        .accessibilityValue(holdingSort?.label ?? PortfolioHoldingsDisplay.shared.DEFAULT_ORDER)
    }

    /// Allocation (Phase C, mirrors Compose): by holding as a donut + legend when every slice is valid (`PortfolioAllocationDisplay`), else
    /// labelled bars; by currency as a second legend group, collapsed by default. Server percentages and amounts only.
    @ViewBuilder private func allocation(_ state: PortfolioUiState, colors: StockColors) -> some View {
        let view = PortfolioAllocationDisplay.shared.of(state: state)
        let labels = PortfolioAllocationDisplay.shared
        sectionTitle(labels.TITLE, colors)
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            if view.holdings.isEmpty && view.currencies.isEmpty {
                Text(labels.EMPTY).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
            }
            if !view.holdings.isEmpty {
                Text(labels.BY_HOLDING).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                if view.donut {
                    let width = max(rowWidth - CGFloat(space.cardPadding) * 2, 0)
                    let diameter = CGFloat(StockAllocationLayout.shared.diameter(availableWidth: Float(width > 0 ? width : 320)))
                    let besideLayout = !(typeSize.isAccessibilitySize || typeSize >= .xxLarge)
                    let legendWidth = besideLayout ? max(width - diameter - CGFloat(space.md), 0) : width
                    let stackedGroup = StockAllocationLegendRow.groupStacked(view.holdings.map { ($0.label, $0.percent, $0.amount) }, width: legendWidth, typeSize: typeSize)
                    let legend = VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                        ForEach(Array(view.holdings.enumerated()), id: \.offset) { _, e in
                            StockAllocationLegendRow(colorIndex: Int(e.colorIndex), label: e.label, percent: e.percent, amount: e.amount, spoken: e.spoken, groupStacked: stackedGroup)
                        }
                    }
                    let donut = StockAllocationDonut(fractions: view.holdings.map { $0.fraction }, colorIndexes: view.holdings.map { Int($0.colorIndex) }, diameter: diameter)
                    if !besideLayout {
                        VStack(alignment: .center, spacing: CGFloat(space.md)) { donut; legend }.frame(maxWidth: .infinity)
                    } else {
                        HStack(spacing: CGFloat(space.md)) { donut; legend }
                    }
                } else {
                    ForEach(Array(view.holdings.enumerated()), id: \.offset) { _, e in allocationBar(e, colors: colors) }
                }
                // D3.1: percentages are of account value (holdings + cash), so say why they don't add up to 100%.
                if let note = view.cashNote {
                    Text(note).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                }
            }
            if !view.currencies.isEmpty {
                if !view.holdings.isEmpty { StockDivider() }
                Button { byCurrency.toggle() } label: {
                    HStack {
                        Text("\(labels.BY_CURRENCY) (\(view.currencies.count))").font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Image(systemName: byCurrency ? "chevron.up" : "chevron.down").foregroundStyle(colors.iconSecondary).accessibilityHidden(true)
                    }
                    .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget)).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(.isHeader)
                .accessibilityValue(byCurrency ? "Expanded" : "Collapsed")
                .accessibilityHint(byCurrency ? labels.HIDE_CURRENCY : labels.SHOW_CURRENCY)
                if byCurrency {
                    let currencyWidth = max(rowWidth - CGFloat(space.cardPadding) * 2, 0)
                    let currencyStacked = StockAllocationLegendRow.groupStacked(view.currencies.map { ($0.label, $0.percent, $0.amount) }, width: currencyWidth, typeSize: typeSize)
                    ForEach(Array(view.currencies.enumerated()), id: \.offset) { _, e in
                        StockAllocationLegendRow(colorIndex: Int(e.colorIndex), label: e.label, percent: e.percent, amount: e.amount, spoken: e.spoken, groupStacked: currencyStacked)
                    }
                }
            }
        }.stockCard(bordered: false)
    }

    /// Fallback bar when the donut isn't allowed (borrowed cash can exceed 100 %): label · reported % · amount, bar clamped to 0…1.
    private func allocationBar(_ entry: PortfolioAllocationEntry, colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            HStack(spacing: CGFloat(space.sm)) {
                Text(entry.label).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textBody).frame(maxWidth: .infinity, alignment: .leading)
                Text(entry.percent).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .footnote)).foregroundStyle(colors.textValue)
                if let amount = entry.amount { Text(amount).font(StockStepsTheme.font(type.numberLabel, relativeTo: .footnote)).foregroundStyle(colors.textSupporting) }
            }
            Capsule().fill(colors.surfaceSecondary).frame(height: CGFloat(StockStepsTheme.dimensions.rangeBar))
                .overlay(alignment: .leading) {
                    GeometryReader { proxy in Capsule().fill(colors.primary).frame(width: proxy.size.width * CGFloat(entry.fraction)) }
                }
                .accessibilityHidden(true)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(entry.spoken)
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
