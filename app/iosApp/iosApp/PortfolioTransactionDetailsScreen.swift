import SwiftUI
import Shared

/// Phase D3 (mirrors Compose `PortfolioTransactionScene`): Transaction details on the caller's `PortfolioViewModel` (same presenter, no new
/// loading). The transaction is looked up by id on every update, so an edit re-renders here and a transaction that disappears is reported,
/// never shown stale. After its own confirmed delete it goes back; the screen underneath announces "Transaction deleted".
struct PortfolioTransactionDetailsScene: View {
    let accounts: AccountViewModel
    let model: PortfolioViewModel
    let transactionId: String
    var onSignIn: () -> Void = {}
    @Environment(\.dismiss) private var dismiss
    @State private var since: Int64?
    @State private var editing: PortfolioTransaction?

    var body: some View {
        let state = model.state
        PortfolioTransactionDetailsScreen(state: state, transactionId: transactionId, onBack: { dismiss() }, onRefresh: model.refresh,
                                          onSignIn: onSignIn, onEdit: { editing = $0 }, onDelete: model.deleteTransaction, onDismissError: model.clearError)
            .portfolioFeedback(accounts: accounts, result: state?.actionResult,
                               leaving: { $0.kind == .deleted && $0.transactionId == transactionId })
            .navigationTitle(PortfolioTransactionDetailsDisplay.shared.TITLE)
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { if since == nil { since = PortfolioActionFeedback.shared.baseline(result: state?.actionResult) } }
            .onChange(of: state?.actionResult?.id) { _, _ in
                guard let since, let result = model.state?.actionResult, result.id > since, result.kind == .deleted,
                      result.transactionId == transactionId else { return }
                dismiss()
            }
            .sheet(item: $editing) { transaction in
                PortfolioEntryScene(accounts: accounts, model: model, existing: transaction, initialInstrument: nil)
            }
    }
}

/// Transaction details (Phase D3, mirrors Compose `PortfolioTransactionScreen`): summary → details (only the fields this type uses and records,
/// `PortfolioTransactionDetailsDisplay`) → notes → Edit / Delete. Samples: Edit opens the read-only explanation and there's no Delete. Missing
/// values have no row — never "0.00".
struct PortfolioTransactionDetailsScreen: View {
    let state: PortfolioUiState?
    let transactionId: String
    let onBack: () -> Void
    let onRefresh: () -> Void
    let onSignIn: () -> Void
    let onEdit: (PortfolioTransaction) -> Void
    let onDelete: (String) -> Void
    var onDismissError: () -> Void = {}
    @State private var deleting: PortfolioTransaction?
    @State private var sampleDetails = false
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    private let space = StockStepsTheme.spacing
    private let type = StockStepsTheme.typography
    private let display = PortfolioDisplay.shared
    private let rules = PortfolioTransactionDetailsDisplay.shared

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            VStack(alignment: .leading, spacing: CGFloat(space.md)) {
                if let state { content(state, colors: colors) } else { ProgressView(display.RESTORING).frame(maxWidth: .infinity) }
            }.frame(maxWidth: CGFloat(StockStepsTheme.dimensions.contentMaxWidth))
                .padding(CGFloat(space.screen)).frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .confirmationDialog(display.DELETE_TRANSACTION_TITLE, isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
            Button(display.DELETE, role: .destructive) { if let transaction = deleting { onDelete(transaction.id) }; deleting = nil }
        } message: { Text(deleteConfirmation(deleting)) }
    }

    @ViewBuilder private func content(_ state: PortfolioUiState, colors: StockColors) -> some View {
        let readOnly = PortfolioFormRules.shared.readOnly(state: state)
        let large = typeSize.isAccessibilitySize
        if state.mockScenario != nil {
            StockBanner(kind: .sample, message: sampleDetails ? "\(display.SAMPLE_SHORT) \(display.SAMPLE_BANNER)" : display.SAMPLE_SHORT,
                        actionTitle: sampleDetails ? display.SAMPLE_HIDE : display.SAMPLE_DETAILS, action: { sampleDetails.toggle() })
        }
        if state.loading { ProgressView(display.LOADING).frame(maxWidth: .infinity) }
        // A rejected delete (or a sync problem) keeps the server's message, as on the dashboard.
        if state.error != nil || state.offline {
            portfolioErrorBanner(state, onRefresh: onRefresh, onDismiss: onDismissError)
        }
        if !state.signedIn && state.mockScenario == nil {
            StockBanner(kind: .info, message: display.SIGN_IN_PROMPT, actionTitle: "Sign in", action: onSignIn)
        }
        if let transaction = state.transactions.first(where: { $0.id == transactionId }) {
            let details = rules.of(tx: transaction, accounts: state.accounts)
            HStack(spacing: CGFloat(space.md)) {
                if !large { PortfolioTransactionAvatar(transaction: transaction, logoUrl: state.logo(for: transaction)) }
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(details.title).font(StockStepsTheme.font(type.cardTitle, relativeTo: .title3)).foregroundStyle(colors.textTitle)
                    if let security = details.security {
                        Text(security).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textBody)
                    }
                    Text(details.date).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                    if let amount = details.amount {
                        Text(amount).font(StockStepsTheme.font(type.numberEmphasis, relativeTo: .title3)).foregroundStyle(colors.textValue)
                            .padding(.top, CGFloat(space.xs))
                    }
                }.frame(maxWidth: .infinity, alignment: .leading)
            }
            .accessibilityElement(children: .ignore).accessibilityLabel(details.spokenSummary)
            .stockCard(bordered: false)
            Text(rules.DETAILS).font(StockStepsTheme.font(type.sectionTitle)).foregroundStyle(colors.textTitle).accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                ForEach(Array(details.rows.enumerated()), id: \.offset) { index, row in
                    if index > 0 { StockDivider() }
                    detailLine(row, large: large, colors: colors)
                }
            }.stockCard(bordered: false).id(typeSize)
            if let notes = details.notes {
                Text(rules.NOTES).font(StockStepsTheme.font(type.sectionTitle)).foregroundStyle(colors.textTitle).accessibilityAddTraits(.isHeader)
                Text(notes).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
                    .frame(maxWidth: .infinity, alignment: .leading).stockCard(bordered: false)
            }
            let edit = Button { onEdit(transaction) } label: { Text(rules.EDIT).frame(maxWidth: .infinity) }.buttonStyle(.stockSecondary)
            let delete = Button(role: .destructive) { deleting = transaction } label: { Text(rules.DELETE).frame(maxWidth: .infinity) }
                .buttonStyle(.stockPrimary).disabled(state.busy)
            // Side by side at normal sizes; stacked at accessibility sizes (the D1.1 rule for form actions). D3.1: samples can't be changed, so
            // one plain line instead of actions (the SAMPLE banner above says why).
            if readOnly {
                Text(display.SAMPLE_CANNOT_CHANGE).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
            } else if large {
                VStack(spacing: CGFloat(space.sm)) { edit; delete }
            } else {
                HStack(spacing: CGFloat(space.sm)) { edit; delete }
            }
        } else if !state.loading {
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                Text(rules.GONE_TITLE).font(StockStepsTheme.font(type.cardTitle, relativeTo: .title3)).foregroundStyle(colors.textTitle)
                    .accessibilityAddTraits(.isHeader)
                Text(rules.GONE_MESSAGE).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
                Button(action: onBack) { Text(rules.BACK).frame(maxWidth: .infinity) }.buttonStyle(.stockSecondary)
            }.frame(maxWidth: .infinity, alignment: .leading).stockCard(bordered: false)
        }
    }

    /// "Price per share      USD 100.00": label left, value right; value under the label at accessibility sizes.
    private func detailLine(_ row: PortfolioDetailRow, large: Bool, colors: StockColors) -> some View {
        let lines = row.value.components(separatedBy: "\n")
        let label = Text(row.label).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
        let value = VStack(alignment: large ? .leading : .trailing, spacing: 0) {
            Text(verbatim: lines.first ?? "").font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .footnote)).foregroundStyle(colors.textValue)
                .multilineTextAlignment(large ? .leading : .trailing)
            ForEach(Array(lines.dropFirst().enumerated()), id: \.offset) { _, line in
                Text(verbatim: line).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                    .multilineTextAlignment(large ? .leading : .trailing)
            }
        }
        return Group {
            if large {
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) { label; value }.frame(maxWidth: .infinity, alignment: .leading)
            } else {
                HStack(alignment: .top, spacing: CGFloat(space.md)) { label.frame(maxWidth: .infinity, alignment: .leading); value }
            }
        }.accessibilityElement(children: .combine)
    }
}
