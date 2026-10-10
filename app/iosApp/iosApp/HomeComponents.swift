import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

/// Inviting learning banner: soft gradient, decorative illustration, indigo title and a round
/// arrow cue. The whole banner is one button; Learn owns the content.
struct HomeLearnBanner: View {
    @Environment(\.colorScheme) private var scheme
    let action: () -> Void
    /// Overrides for "Continue learning" (real saved progress only).
    var title = "Learn the Basics"
    var message = "Research a company in five simple steps."
    var hint = "Start learning"
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        Button(action: action) {
            HStack(spacing: CGFloat(space.md)) {
                Image("LearnBasics")
                    .resizable()
                    .frame(width: CGFloat(dims.learnIllustration), height: CGFloat(dims.learnIllustration))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(title)
                        .font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline).bold())
                        .foregroundStyle(colors.learnAccent)
                    Text(message)
                        .font(StockStepsTheme.font(type.caption, relativeTo: .caption1))
                        .foregroundStyle(colors.textBody)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "arrow.right")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(colors.onLearnAccent)
                    .frame(width: CGFloat(dims.learnAction), height: CGFloat(dims.learnAction))
                    .background(colors.learnAccent, in: Circle())
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, CGFloat(space.md))
            .padding(.vertical, CGFloat(space.sm))
            .background(
                LinearGradient(colors: [colors.learnContainerStart, colors.learnContainerEnd], startPoint: .leading, endPoint: .trailing),
                in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.cardLarge))
            )
            .contentShape(RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.cardLarge)))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint(hint)
    }
}

private let home = HomeDashboardPresentation.shared

/// "Markets today" (Phase 4A): market sessions, the brief's first indices as compact rows (value, signed change with arrow and words,
/// quote state) and the Daily Market Brief entry. Uses the brief already loaded on Home — no extra requests, never placeholder values.
struct HomeMarketOverview: View {
    let model: BriefModel
    let onOpenBrief: () -> Void
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let state = model.state
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            StockSectionHeader(title: "Markets today") {
                if state?.latest?.sampleData == true { StockStatusBadge(text: "Sample", kind: .sample, size: .compact) }
            }
            if let brief = state?.latest {
                ForEach(home.sessionLines(brief: brief), id: \.self) {
                    Text($0).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting)
                }
                let indices = home.indices(brief: brief)
                if indices.isEmpty {
                    Text(home.INDICES_UNAVAILABLE).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                } else {
                    VStack(spacing: 0) {
                        ForEach(Array(indices.enumerated()), id: \.element.indexId) { n, index in
                            if n > 0 { StockDivider() }
                            HomeIndexRow(index: index, client: model.client)
                        }
                    }
                }
                StockDivider()
                let now = model.client.now()
                let caution = model.client.isStale(brief: brief) || state?.offline == true
                HomeLinkRow(title: "Your Daily Market Brief", detail: home.briefMeta(brief: brief, nowMillis: now, offline: state?.offline == true),
                            detailColor: caution ? colors.cautionText : colors.textMeta,
                            hint: home.briefAction(brief: brief, nowMillis: now), action: onOpenBrief)
            } else if state?.loading != false {
                ProgressView().frame(maxWidth: .infinity).accessibilityLabel("Loading market overview")
            } else {
                Text(state?.error ?? "Market overview isn't available right now.")
                    .font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
            }
        }
        .stockCard()
    }
}

/// Index name and quote state — value and change; one spoken summary. At large Dynamic Type sizes the values move under the name.
private struct HomeIndexRow: View {
    let index: BriefIndex
    let client: IosBriefClient
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let name = VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            Text(index.displayName).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle)
            Text(index.stateLabel).font(StockStepsTheme.font(type.caption, relativeTo: .caption1))
                .foregroundStyle(home.indexCaution(index: index) ? colors.cautionText : colors.textMeta)
            if let note = index.proxyNote {
                Text(note).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
            }
        }
        let stacked = typeSize >= .xxxLarge
        let values = VStack(alignment: stacked ? .leading : .trailing, spacing: CGFloat(space.xxs)) {
            Text(client.value(i: index)).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .footnote))
                .foregroundStyle(colors.textValue).monospacedDigit()
            if let change = home.indexChange(index: index) {
                StockPriceChange(percentage: change, direction: home.indexDirection(index: index), lineLimit: nil)
            } else {
                Text("Change unavailable").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
            }
        }
        Group {
            if stacked {
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) { name; values }
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                HStack(spacing: CGFloat(space.md)) {
                    name.frame(maxWidth: .infinity, alignment: .leading)
                    values.fixedSize(horizontal: true, vertical: false)
                }
            }
        }
        .padding(.vertical, CGFloat(space.sm))
        .frame(minHeight: CGFloat(dims.rowCompactMinHeight))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(client.accessibility(i: index))
    }
}

/// A full-width tappable row inside a Home card (Upcoming & alerts, brief entry); the shared `StockNavigationRow` without an icon.
struct HomeLinkRow: View {
    let title: String
    let detail: String?
    var detailColor: Color? = nil
    var hint: String? = nil
    let action: () -> Void
    var body: some View {
        StockNavigationRow(title: title, detail: detail, detailColor: detailColor, hint: hint, action: action)
    }
}

/// Compact portfolio summary in the Portfolio (Phase 3) language: total value dominant, today's change (or that it is unavailable), one
/// metric row and one freshness line. The whole card opens Portfolio; without an account it explains and offers to create one.
struct HomePortfolioSummary: View {
    let state: PortfolioUiState
    let onOpen: () -> Void
    @Environment(\.colorScheme) private var scheme
    private let present = PortfolioPresentation.shared
    var body: some View {
        if home.hasPortfolio(state: state) {
            Button(action: onOpen) { content.contentShape(Rectangle()) }
                .buttonStyle(.plain)
                .accessibilityHint(home.portfolioAction(state: state))
        } else {
            content
        }
    }
    private var content: some View {
        let colors = StockStepsTheme.colors(scheme)
        let owned = home.hasPortfolio(state: state)
        return VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            StockSectionHeader(title: "Your portfolio") {
                if owned {
                    Image(systemName: "chevron.right").font(StockStepsTheme.font(type.caption, relativeTo: .caption1).weight(.semibold))
                        .foregroundStyle(colors.textTertiary).accessibilityHidden(true)
                }
            }
            if owned {
                Text(home.portfolioLabel(state: state)).font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSupporting)
                Text("\(state.currency) \(PortfolioFormat.shared.amount(value: state.total))")
                    .font(StockStepsTheme.font(type.largeNumber)).foregroundStyle(colors.textValue)
                    .minimumScaleFactor(0.7).lineLimit(2)
                if let today = present.todayChange(state: state) {
                    StockPriceChange(percentage: "\(today) today", direction: present.direction(amount: state.dailyGain), style: type.numberMedium, lineLimit: nil)
                } else {
                    Text(present.TODAY_UNAVAILABLE).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                }
                StockDivider().padding(.vertical, CGFloat(space.xs))
                StockMetricGrid(items: [
                    StockMetricItem(label: "Invested cost", value: PortfolioFormat.shared.amount(value: state.basis)),
                    StockMetricItem(label: "Unrealized P/L", value: present.signedAmount(amount: state.unrealized) ?? "—", direction: present.direction(amount: state.unrealized)),
                    StockMetricItem(label: "Realized P/L", value: present.signedAmount(amount: state.realized) ?? "—", direction: present.direction(amount: state.realized))
                ], rowsWhenNarrow: true)
                if let notice = state.notice { Text(notice).font(StockStepsTheme.font(type.caption)).foregroundStyle(colors.cautionText) }
                if let fresh = home.portfolioFreshness(state: state) {
                    Text(fresh).font(StockStepsTheme.font(type.caption)).foregroundStyle(colors.textMeta)
                }
            } else if state.loading {
                ProgressView().frame(maxWidth: .infinity).accessibilityLabel("Loading your portfolio")
            } else {
                Text(home.PORTFOLIO_EMPTY).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                Button(home.portfolioAction(state: state), action: onOpen).buttonStyle(.stockSecondary)
            }
            if let sample = state.mockScenario { StockStatusBadge(text: "Sample · \(sample)", kind: .sample, size: .compact) }
        }
        .stockCard()
    }
}
