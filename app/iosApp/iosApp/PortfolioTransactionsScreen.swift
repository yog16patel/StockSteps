import SwiftUI
import Shared

/// Phase D3 delete confirmation text (mirrors Compose `PortfolioDeleteTransactionDialog`): the explanation, then which transaction it is.
func deleteConfirmation(_ transaction: PortfolioTransaction?) -> String {
    let explanation = PortfolioTransactionDetailsDisplay.shared.DELETE_MESSAGE
    guard let transaction else { return explanation }
    let row = PortfolioTransactionDetailsDisplay.shared.row(tx: transaction)
    return explanation + "\n\n" + [row.title, row.security, row.date, row.amount].compactMap { $0 }.joined(separator: " · ")
}

/// SF Symbol for a transaction type icon key (shared mapping `PortfolioTransactionDetailsDisplay.icon`); decorative.
func transactionSymbol(_ icon: PortfolioTransactionIcon) -> String {
    switch icon {
    case .dividend: "dollarsign.circle"
    case .fee: "tag"
    case .transfer: "arrow.right"
    default: "building.columns"
    }
}

/// Logo / ticker avatar for a security, type icon tile otherwise (mirrors Compose `PortfolioTransactionAvatar`). Decorative.
struct PortfolioTransactionAvatar: View {
    let transaction: PortfolioTransaction
    let logoUrl: String?
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let icon = PortfolioTransactionDetailsDisplay.shared.icon(tx: transaction)
        Group {
            if icon == .security, let symbol = transaction.instrument?.symbol {
                StockTickerAvatar(symbol: symbol, logoUrl: logoUrl, size: CGFloat(StockStepsTheme.dimensions.iconTile))
            } else {
                StockIconTile(systemName: transactionSymbol(icon), container: colors.primaryContainer, content: colors.primaryText)
            }
        }.accessibilityHidden(true)
    }
}

/// Transaction row shared by the dashboard preview and Transaction history (Phase D3, mirrors Compose `PortfolioTransactionRow`). Normal
/// sizes: avatar · type, security, date · price per share · the recorded amount right-aligned · ⋯. Accessibility sizes: no avatar, each group
/// on its own line. The row opens details (`onOpen`); ⋯ stays a separate 44 pt target (Edit, and Delete unless `onDelete` is nil — samples).
struct PortfolioTransactionRow: View {
    let transaction: PortfolioTransaction
    var logoUrl: String? = nil
    var onOpen: (() -> Void)? = nil
    let onEdit: (() -> Void)?
    let onDelete: (() -> Void)?
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    private let space = StockStepsTheme.spacing
    private let type = StockStepsTheme.typography

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let text = PortfolioTransactionDetailsDisplay.shared.row(tx: transaction)
        let large = typeSize.isAccessibilitySize
        let content = HStack(spacing: CGFloat(space.md)) {
            if !large { PortfolioTransactionAvatar(transaction: transaction, logoUrl: logoUrl) }
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                // The amount shares the title's line, so the lines below get the full width (as in Compose).
                HStack(alignment: .firstTextBaseline, spacing: CGFloat(space.sm)) {
                    Text(text.title).font(StockStepsTheme.font(type.bodySemiBold, relativeTo: .body)).foregroundStyle(colors.textTitle)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if !large, let amount = text.amount {
                        Text(amount).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .footnote)).foregroundStyle(colors.textValue)
                            .multilineTextAlignment(.trailing)
                    }
                }
                if let security = text.security {
                    Text(security).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textBody)
                }
                // Date and price per share on their own lines (as in Compose).
                Text(text.date).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                if let price = text.unitPrice { Text(price).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textSupporting) }
                if large, let amount = text.amount {
                    Text(amount).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .footnote)).foregroundStyle(colors.textValue)
                }
            }.frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.leading, CGFloat(space.cardPadding)).padding(.vertical, CGFloat(space.xs))
        .contentShape(Rectangle())
        HStack(spacing: CGFloat(space.xxs)) {
            Group {
                if let onOpen {
                    Button(action: onOpen) { content }.buttonStyle(.plain)
                        .accessibilityHint(PortfolioTransactionDetailsDisplay.shared.OPEN)
                } else { content }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(text.spoken)
            .accessibilityAddTraits(onOpen != nil ? .isButton : [])
            // D3.1: samples have no actions, so no ⋯ at all (the row still opens details).
            if onEdit != nil || onDelete != nil { Menu {
                if let onEdit { Button("Edit", systemImage: "pencil", action: onEdit) }
                if let onDelete { Button("Delete…", systemImage: "trash", role: .destructive, action: onDelete) }
            } label: {
                // C.1 closure: fixed-size symbol inside its fixed 44 pt target (at AX5 the body-sized glyph overflowed to the card edge).
                Image(systemName: "ellipsis").font(.system(size: CGFloat(StockStepsTheme.dimensions.icon))).foregroundStyle(colors.iconSecondary)
                    .frame(width: CGFloat(StockStepsTheme.dimensions.touchTarget), height: CGFloat(StockStepsTheme.dimensions.touchTarget))
            }.accessibilityLabel("Actions for \(text.title)\(text.security.map { ", " + $0.replacingOccurrences(of: "\u{00A0}", with: " ") } ?? "")") }
        }
    }
}

/// Phase D3.1 status banner (mirrors Compose `PortfolioErrorBanner`): sync problems keep Retry; a rejected save / delete / account change is titled
/// by that action with the server's message unchanged, and offers Dismiss. E.2: an ended session is titled "Session expired" and offers
/// "Sign in again" (`onSignInAgain`); title, message and action come from `PortfolioDisplay`.
@ViewBuilder func portfolioErrorBanner(_ state: PortfolioUiState, onRefresh: @escaping () -> Void, onDismiss: @escaping () -> Void,
                                       onSignInAgain: @escaping () -> Void) -> some View {
    let display = PortfolioDisplay.shared
    let action = display.errorAction(state: state)
    let title = display.errorTitle(state: state)
    let message = display.errorMessage(state: state)
    // Phase E (E-01): announced when it appears or changes, like Compose's polite live region; a rejected save is announced by its form
    // instead (E-05, `errorAnnounced`).
    let announce = { if display.errorAnnounced(state: state) { AccessibilityNotification.Announcement("\(title). \(message)").post() } }
    StockBanner(kind: state.error != nil ? .error : .warning, message: message, title: title,
                actionTitle: display.errorActionLabel(action: action),
                action: action == .signIn ? onSignInAgain : action == .dismiss ? onDismiss : onRefresh)
        .onAppear(perform: announce)
        .onChange(of: state.error) { _, _ in announce() }
}

/// Phase D3 success feedback (mirrors Compose `PortfolioActionBanner`): while this screen is visible it takes the presenter's newest confirmed
/// result once (shared tracker via `takePortfolioFeedback`) and shows a nonblocking `StockBanner(.success)` above the content for a few seconds,
/// announced to VoiceOver. A screen covered by a push isn't visible (`onDisappear`), so it never takes a result meant for the screen on top.
private struct PortfolioFeedbackModifier: ViewModifier {
    let accounts: AccountViewModel
    let result: PortfolioActionResult?
    let leaving: (PortfolioActionResult) -> Bool
    @State private var visible = false
    @State private var message: String?

    func body(content: Content) -> some View {
        content
            .safeAreaInset(edge: .top, spacing: 0) {
                if let message {
                    StockBanner(kind: .success, message: message, onDismiss: { self.message = nil })
                        .padding(.horizontal, CGFloat(StockStepsTheme.spacing.screen)).padding(.vertical, CGFloat(StockStepsTheme.spacing.xs))
                        .transition(.opacity)
                }
            }
            .onAppear { visible = true; take() }
            .onDisappear { visible = false }
            .onChange(of: result?.id) { _, _ in take() }
    }

    private func take() {
        guard visible, let result, !leaving(result), let text = accounts.client?.takePortfolioFeedback() else { return }
        message = text
        AccessibilityNotification.Announcement(text).post()
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(PortfolioActionFeedback.shared.VISIBLE_MILLIS) * 1_000_000)
            if message == text { withAnimation { message = nil } }
        }
    }
}

extension View {
    /// Phase D3: confirmed add / edit / delete feedback for a Portfolio screen.
    func portfolioFeedback(accounts: AccountViewModel, result: PortfolioActionResult?, leaving: @escaping (PortfolioActionResult) -> Bool = { _ in false }) -> some View {
        modifier(PortfolioFeedbackModifier(accounts: accounts, result: result, leaving: leaving))
    }
}

/// "Personal · CAD ▾" with a menu of the accounts (name, type, currency): the dashboard's selector (moved here unchanged), reused by Transaction
/// history.
struct PortfolioAccountMenu: View {
    let accounts: [PortfolioAccount]
    let selectedId: String?
    let onSelect: (String) -> Void
    @Environment(\.colorScheme) private var scheme
    private let space = StockStepsTheme.spacing
    private let type = StockStepsTheme.typography
    private let present = PortfolioPresentation.shared

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let selected = accounts.first { $0.id == selectedId }
        Menu {
            ForEach(accounts, id: \.id) { account in
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
    }
}

/// Phase D2 (mirrors Compose `PortfolioTransactionsScene`): uses the dashboard's `PortfolioViewModel` (same presenter, no second load) and wires
/// its actions. The holding scope applies only while its account is selected; switching account drops it for good.
struct PortfolioTransactionsScene: View {
    let accounts: AccountViewModel
    let model: PortfolioViewModel
    let symbol: String?
    let accountId: String?
    var onSignIn: () -> Void = {}
    @State private var scopeCleared = false
    @State private var editing: PortfolioTransaction?
    @State private var openTransactionId: String?

    var body: some View {
        let state = model.state
        let active = scopeCleared ? nil : PortfolioTransactionsDisplay.shared.activeSymbol(symbol: symbol, scopeAccountId: accountId, selectedAccountId: state?.selectedAccountId)
        PortfolioTransactionsScreen(state: state, symbol: active, onClearSymbol: { scopeCleared = true }, onSelectAccount: model.select,
                                    onRefresh: model.refresh, onSignIn: onSignIn,
                                    onEdit: { editing = $0 }, onDelete: model.deleteTransaction, onOpen: { openTransactionId = $0 },
                                    onDismissError: model.clearError, onSignInAgain: portfolioSignInAgain(accounts, then: onSignIn))
            .portfolioFeedback(accounts: accounts, result: state?.actionResult)
            .navigationTitle(PortfolioTransactionsDisplay.shared.TITLE)
            .navigationBarTitleDisplayMode(.inline)
            .onChange(of: state?.selectedAccountId) { _, selected in
                // Account switched away from the holding's account: the symbol no longer belongs here.
                if let accountId, let selected, selected != accountId { scopeCleared = true }
            }
            .sheet(item: $editing) { transaction in
                PortfolioEntryScene(accounts: accounts, model: model, existing: transaction, initialInstrument: nil)
            }
            // Phase D3: Transaction details on the same model (no second load).
            .navigationDestination(item: $openTransactionId) { id in
                PortfolioTransactionDetailsScene(accounts: accounts, model: model, transactionId: id, onSignIn: onSignIn)
            }
    }
}

/// Transaction history (Portfolio Phase D2, mirrors Compose `PortfolioTransactionsScreen`): account → holding scope → type filter → count →
/// month groups of the dashboard's transaction rows. Display only: `state.transactions` filtered and grouped by `PortfolioTransactionsDisplay`
/// in the presenter's order; nothing is summed or computed.
struct PortfolioTransactionsScreen: View {
    let state: PortfolioUiState?
    let symbol: String?
    let onClearSymbol: () -> Void
    let onSelectAccount: (String) -> Void
    let onRefresh: () -> Void
    let onSignIn: () -> Void
    let onEdit: (PortfolioTransaction) -> Void
    let onDelete: (String) -> Void
    var onOpen: ((String) -> Void)? = nil
    var onDismissError: () -> Void = {}
    /// E.2: "Sign in again" for an ended session (`portfolioSignInAgain`).
    var onSignInAgain: () -> Void = {}
    @State private var filter: PortfolioTransactionFilter = .all
    @State private var sampleDetails = false
    @State private var deleting: PortfolioTransaction?
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    private let space = StockStepsTheme.spacing
    private let type = StockStepsTheme.typography
    private let display = PortfolioDisplay.shared
    private let rules = PortfolioTransactionsDisplay.shared

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            VStack(alignment: .leading, spacing: CGFloat(space.md)) {
                if let state { content(state, colors: colors) } else { ProgressView(display.RESTORING).frame(maxWidth: .infinity) }
            }.frame(maxWidth: CGFloat(StockStepsTheme.dimensions.contentMaxWidth))
                .padding(CGFloat(space.screen)).frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .refreshable { onRefresh() }
        .confirmationDialog(display.DELETE_TRANSACTION_TITLE, isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
            Button(display.DELETE, role: .destructive) { if let transaction = deleting { onDelete(transaction.id) }; deleting = nil }
        } message: { Text(deleteConfirmation(deleting)) }
    }

    @ViewBuilder private func content(_ state: PortfolioUiState, colors: StockColors) -> some View {
        let readOnly = PortfolioFormRules.shared.readOnly(state: state)
        let active = state.accounts.filter { !$0.archived }
        let scoped = rules.filter(transactions: state.transactions, filter: .all, symbol: symbol)
        let shown = rules.filter(transactions: scoped, filter: filter, symbol: nil)
        // Status: the dashboard's banners, same copy and order (sample, loading, sync problems, sign-in).
        if state.mockScenario != nil {
            StockBanner(kind: .sample, message: sampleDetails ? "\(display.SAMPLE_SHORT) \(display.SAMPLE_BANNER)" : display.SAMPLE_SHORT,
                        actionTitle: sampleDetails ? display.SAMPLE_HIDE : display.SAMPLE_DETAILS, action: { sampleDetails.toggle() })
        }
        if state.loading { ProgressView(display.LOADING).frame(maxWidth: .infinity) }
        if state.error != nil || state.offline {
            portfolioErrorBanner(state, onRefresh: onRefresh, onDismiss: onDismissError, onSignInAgain: onSignInAgain)
        }
        if !state.signedIn && state.mockScenario == nil {
            StockBanner(kind: .info, message: display.SIGN_IN_PROMPT, actionTitle: "Sign in", action: onSignIn)
        }
        if !active.isEmpty { PortfolioAccountMenu(accounts: active, selectedId: state.selectedAccountId, onSelect: onSelectAccount) }
        if let symbol {
            // Stacked (line, then its action) so neither squeezes the other at large text.
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                Text(rules.scopeLine(symbol: symbol)).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle)
                Button(rules.SHOW_ALL_HOLDINGS, action: onClearSymbol).font(StockStepsTheme.font(type.bodyMedium))
                    .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
            }
        }
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            StockPillSelector(options: rules.filters, selected: filter, label: { rules.label(filter: $0) }, onSelect: { filter = $0 })
                .accessibilityLabel(rules.FILTERS_LABEL)
            if let note = rules.note(filter: filter) {
                Text(note).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
            }
            // The count is a row count (never an amount).
            Text(rules.summary(count: Int32(shown.count), filter: filter)).font(StockStepsTheme.font(type.small, relativeTo: .footnote))
                .foregroundStyle(colors.textSupporting)
                .accessibilityLabel(rules.spokenFilter(filter: filter, count: Int32(shown.count)))
        }
        .onChange(of: filter) { _, new in
            let count = rules.filter(transactions: scoped, filter: new, symbol: nil).count
            AccessibilityNotification.Announcement(rules.spokenFilter(filter: new, count: Int32(count))).post()
        }
        if shown.isEmpty {
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                if scoped.isEmpty && symbol == nil {
                    Text(display.TRANSACTIONS_EMPTY).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                } else {
                    Text(rules.emptyMessage(filter: filter, symbol: symbol)).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                    if filter != .all {
                        Button(rules.SHOW_ALL_FILTER) { filter = .all }.font(StockStepsTheme.font(type.bodyMedium))
                            .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
                    }
                }
            }.frame(maxWidth: .infinity, alignment: .leading).stockCard(bordered: false)
        }
        // LazyVStack: a long history builds only the visible months.
        LazyVStack(alignment: .leading, spacing: CGFloat(space.md)) {
            ForEach(rules.groups(transactions: shown), id: \.key) { month in
                Text(month.title).font(StockStepsTheme.font(type.label, relativeTo: .subheadline)).foregroundStyle(colors.textSecondary)
                    .accessibilityAddTraits(.isHeader).padding(.top, CGFloat(space.xs))
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(month.transactions.enumerated()), id: \.element.id) { index, transaction in
                        if index > 0 { StockDivider().padding(.horizontal, CGFloat(space.cardPadding)) }
                        PortfolioTransactionRow(transaction: transaction, logoUrl: state.logo(for: transaction),
                                                onOpen: onOpen.map { open in { open(transaction.id) } }, onEdit: readOnly ? nil : { onEdit(transaction) },
                                                onDelete: readOnly ? nil : { deleting = transaction })
                    }
                }.stockCard(padding: 0, bordered: false)
            }
        }.id(typeSize) // live Dynamic Type changes refresh the month groups (their data doesn't change)
    }
}

extension PortfolioTransaction: @retroactive Identifiable {}

extension PortfolioUiState {
    /// Phase D3: the holding's existing logo for a transaction row (no lookup, no new data).
    func logo(for transaction: PortfolioTransaction) -> String? {
        guard let symbol = transaction.instrument?.symbol else { return nil }
        return holdings.first { $0.symbol == symbol }?.logoUrl
    }
}
