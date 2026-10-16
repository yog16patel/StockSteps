import Observation
import Shared
import SwiftUI

/// Valuation for one company, mirroring Android's CompanyValuationViewModel: the full P/E history,
/// annual fundamentals (ratios and growth) and the profile load in parallel; ranges only re-slice.
@MainActor
@Observable
final class CompanyValuationModel {
    let symbol: String
    private(set) var profile: CompanyProfile?
    private(set) var history: SectionState<ValuationHistory> = .loading
    private(set) var fundamentals: SectionState<CompanyFundamentals> = .loading
    private(set) var selectedRange: ValuationRange?
    @ObservationIgnored private let client: IosCompanyDetailsClient
    @ObservationIgnored private var started = false

    init(symbol: String, client: IosCompanyDetailsClient) {
        self.symbol = symbol
        self.client = client
    }

    var shortName: String { profile?.companyName.map { CompanyOverviewPresenter.shared.shortName(name: $0) } ?? symbol }
    var listing: String { [symbol, profile?.exchange].compactMap { $0 }.joined(separator: " · ") }
    var range: ValuationRange { selectedRange ?? ValuationPresenter.shared.defaultRange(history: history.value) }
    var model: ValuationModel? {
        if case .loading = history { return nil }
        if case .loading = fundamentals { return nil }
        return ValuationPresenter.shared.build(history: history.value, fundamentals: fundamentals.value, range: range,
                                               companyName: shortName, priceCurrency: profile?.currency)
    }
    var historyLoaded: Bool { history.value != nil }
    var fundamentalsFailed: Bool { if case .unavailable = fundamentals { true } else { false } }

    func start() {
        guard !started else { return }
        started = true
        Task { [weak self] in
            guard let self, let profile = try? await client.getProfile(symbol: symbol) else { return }
            self.profile = profile
        }
        load()
    }

    func select(range: ValuationRange) { selectedRange = range }

    func load() {
        history = .loading
        fundamentals = .loading
        Task { history = (try? await client.getValuationHistory(symbol: symbol)).map(SectionState.content) ?? .unavailable }
        Task { fundamentals = (try? await client.getFundamentals(symbol: symbol, period: .annual)).map(SectionState.content) ?? .unavailable }
    }
}

struct CompanyValuationScene: View {
    @Environment(\.colorScheme) private var scheme
    @State private var model: CompanyValuationModel
    /// 5D.5.3: the open metric explanation and a Learn term pushed from it (Back returns here).
    @State private var explaining: MetricExplanation?
    @Environment(TabRouter.self) private var router: TabRouter?
    var onSearchCompanies: () -> Void = {}

    init(symbol: String, client: IosCompanyDetailsClient, onSearchCompanies: @escaping () -> Void = {}) {
        _model = State(initialValue: CompanyValuationModel(symbol: symbol, client: client))
        self.onSearchCompanies = onSearchCompanies
    }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let space = StockStepsTheme.spacing
        let type = StockStepsTheme.typography
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(model.listing).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
                    Text("Understanding what investors pay for this company.").font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
                }
                if let content = model.model {
                    // 5D.5.4C C2: ratio tiles first (D-V1), then P/E vs History (D-V3), one Learn link, the P/E explanation folded.
                    VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                        StockSectionHeader(title: ValuationDisplay.shared.KEY_METRICS_TITLE)
                        StockMetricGrid(items: ValuationDisplay.shared.keyMetrics(model: content).map { m in
                            let title = ValuationDisplay.shared.plain(text: m.label)
                            let explanation = m.metricId.flatMap { MetricLearnTerms.shared.explain(metricId: $0, title: title) }
                            return StockMetricItem(label: m.label, value: m.value, helper: m.caption, action: explanation.map { e in { explaining = e } },
                                                   hint: "Explain \(title)", info: m.metricId.map { MetricLearnTerms.shared.hasTerm(metricId: $0) } ?? false)
                        }, maxColumns: 2, tiled: true)
                        if model.fundamentalsFailed {
                            StockSectionMessage(message: "We couldn't load the other valuation metrics right now.", actionTitle: "Try again", action: model.load)
                                .stockCard(bordered: false)
                        } else if content.otherMetrics.isEmpty {
                            Text("Other valuation ratios aren't available for this company.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
                        }
                    }
                    .padding(.top, CGFloat(space.md))
                    titled(ValuationDisplay.shared.HISTORY_TITLE) { history(content, colors) }
                    UnderstandingView(content: content).padding(.top, CGFloat(space.md))
                    // C3: shown when it was before, without the model's P/E row (P/E leads the tiles and P/E vs History). The rows share one
                    // decision (`StockInfoLineGroup`): each value under its label when any row can't share a line ("+75.5" / "%" at AX5).
                    if ValuationDisplay.shared.growthVisible(model: content) {
                        titled("Valuation & Growth") {
                            VStack(alignment: .leading, spacing: 0) {
                                StockInfoLineGroup(entries: ValuationDisplay.shared.growthRows(model: content).map {
                                    .init(label: $0.label, value: $0.value, info: metricExplanation($0)?.term != nil)
                                }) { stacked in growthRows(content, stacked: stacked) }
                                Text(keepTogether(content.growthNote)).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody).padding(.vertical, CGFloat(space.sm))
                            }
                            .padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0, bordered: false)
                        }
                    }
                    if let summary = content.rangeSummary {
                        titled("Historical Valuation Range") {
                            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                                if let position = summary.position {
                                    StockRangeBar(title: "\(content.range.label) P/E", range: RangeSummary(low: summary.lowest, high: summary.highest, position: position.floatValue))
                                }
                                // C3: a 2 × 2 grid, aligned label/value rows at large text (as Android); four equal columns split words at AX5.
                                StockMetricGrid(items: [StockMetricItem(label: "Lowest", value: summary.lowest), StockMetricItem(label: "Average", value: summary.average),
                                                        StockMetricItem(label: "Highest", value: summary.highest),
                                                        StockMetricItem(label: "Current", value: summary.current ?? "N/A")], maxColumns: 2, rowsWhenNarrow: true)
                                if let percentile = summary.percentile {
                                    Text(percentile).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                                }
                            }
                            .stockCard(padding: CGFloat(space.md), bordered: false)
                        }
                    }
                    StockInsightCard(title: "What Does This Valuation Tell Us?", message: keepTogether(content.insight)).padding(.top, CGFloat(space.xl))
                    Text(ValuationDisplay.shared.FOOTNOTE).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary).padding(.top, CGFloat(space.lg))
                    Text("Educational information, not a recommendation to buy or sell.")
                        .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary).padding(.top, CGFloat(space.xs))
                } else {
                    VStack(spacing: CGFloat(space.sm)) {
                        ForEach(0..<3, id: \.self) { _ in StockSkeleton(width: 1, height: CGFloat(StockStepsTheme.dimensions.chartHeight)).frame(maxWidth: .infinity) }
                    }
                    .padding(.top, CGFloat(space.xl)).accessibilityLabel("Loading")
                }
            }
            .frame(maxWidth: CGFloat(StockStepsTheme.dimensions.contentMaxWidth), alignment: .leading)
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.bottom, CGFloat(space.xl))
            .frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .navigationTitle(ValuationDisplay.shared.title(symbol: model.symbol)) // KAN-16: ticker title, never truncated
        // 5D.5.6C3: terms open on Learn (canonical tab); from the sheet only once it has closed.
        .sheet(item: $explaining) { e in MetricExplanationSheet(explanation: e) { id in explaining = nil; router?.open(.learn(.term(id)), afterSheet: true) } }
        .navigationBarTitleDisplayMode(.inline)
        .task { model.start() }
    }

    /// P/E vs History (5D.5.4C C2): current beside its historical average and the difference (only with a reliable average), the existing badge and
    /// meaning, then the range pills, chart and methodology on one borderless surface; then the single "Learn more about P/E ratio" link.
    @ViewBuilder
    private func history(_ content: ValuationModel, _ colors: StockColors) -> some View {
        let space = StockStepsTheme.spacing
        let type = StockStepsTheme.typography
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            if let c = ValuationDisplay.shared.comparison(model: content) {
                StockMetricGrid(items: [StockMetricItem(label: c.currentLabel, value: c.current), StockMetricItem(label: c.averageLabel, value: c.average),
                                        StockMetricItem(label: "Difference", value: c.difference ?? "—")], maxColumns: 3, rowsWhenNarrow: true)
            }
            if let badge = content.badge {
                // The badge stays one line while it fits (regular, then compact); only when neither fits the screen (AX5) does the same text
                // wrap on the badge's colours, so it never widens the column past the screen.
                let tone: FactTone = content.position == .above ? .caution : .neutral
                ViewThatFits(in: .horizontal) {
                    StockBadge(text: badge, tone: tone)
                    StockStatusBadge(text: badge, kind: tone.badgeKind, size: .compact)
                    wrappingBadge(badge, kind: tone.badgeKind)
                }
            }
            Text(keepTogether(content.meaning)).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
            StockDivider()
            if model.historyLoaded {
                StockPillSelector(options: ValuationRange.entries, selected: model.range, label: { $0.label }) { model.select(range: $0) }
                if let chart = content.chart {
                    StockTrendChart(chart: chart, referenceLabel: content.average.map { keepTogether("\(content.averageLabel): \($0)") })
                }
                Text(keepTogether(content.methodology)).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
            } else {
                StockSectionMessage(message: content.historyMessage.map(keepTogether) ?? "We couldn't load the valuation history right now.", actionTitle: "Try again", action: model.load)
            }
        }
        .stockCard(padding: CGFloat(space.md), bordered: false)
        // 5D.5.3: the full P/E lesson one tap away — once on this screen.
        if let term = MetricLearnTerms.shared.termFor(metricId: "pe") {
            Button { router?.open(.learn(.term(term.id))) } label: {
                Label(keepTogether(MetricLearnTerms.shared.learnMoreLabel(entry: term)), systemImage: "graduationcap")
                    .font(StockStepsTheme.font(type.label, relativeTo: .subheadline)).foregroundStyle(colors.primaryText)
                    .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget)).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    /// The badge's text and colours (`StockSemanticStyles`) on a rounded rectangle that may wrap — the last resort at the largest sizes.
    private func wrappingBadge(_ text: String, kind: StockBadgeKind) -> some View {
        let tone = StockSemanticStyles.shared.badge(kind: kind, p: StockStepsTheme.palette(scheme))
        let c = StockStepsTheme.color
        return Text(verbatim: text)
            .font(StockStepsTheme.font(StockStepsTheme.typography.label))
            .foregroundStyle(c(tone.content))
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, CGFloat(StockStepsTheme.spacing.sm))
            .padding(.vertical, CGFloat(StockStepsTheme.spacing.xxs))
            .background(c(tone.container), in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.chip)))
    }

    private func growthRows(_ content: ValuationModel, stacked: Bool) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(ValuationDisplay.shared.growthRows(model: content).enumerated()), id: \.offset) { index, row in
                if index > 0 { StockDivider() }
                ExplainableInfoLine(label: row.label, value: row.value, stacked: stacked, explanation: metricExplanation(row)) { explaining = $0 }
            }
        }
    }

    private func titled<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.sm)) {
            StockSectionHeader(title: title)
            content()
        }
        .padding(.top, CGFloat(StockStepsTheme.spacing.xl))
    }
}

/// Understanding P/E Ratio, folded (5D.5.4C C2): the existing explanation and the Why high / Why low points behind one toggle.
private struct UnderstandingView: View {
    @Environment(\.colorScheme) private var scheme
    let content: ValuationModel
    @State private var open = false
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let space = StockStepsTheme.spacing
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Button { open.toggle() } label: {
                Text(verbatim: "\(keepTogether(ValuationDisplay.shared.UNDERSTANDING_TITLE)) \(open ? "↑" : "↓")")
                    .font(StockStepsTheme.font(StockStepsTheme.typography.bodySemiBold)).foregroundStyle(colors.textPrimary)
                    .frame(maxWidth: .infinity, minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget), alignment: .leading).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(.isHeader)
            .accessibilityValue(open ? "Expanded" : "Collapsed")
            if open {
                Text(keepTogether(content.understanding)).font(StockStepsTheme.font(StockStepsTheme.typography.small)).foregroundStyle(colors.textBody)
                ValuationExplainerView(explainer: content.highReasons)
                ValuationExplainerView(explainer: content.lowReasons)
            }
        }
        .padding(.horizontal, CGFloat(space.md)).padding(.vertical, CGFloat(space.xs))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.primaryContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
    }
}

private struct ValuationExplainerView: View {
    @Environment(\.colorScheme) private var scheme
    let explainer: ValuationExplainer
    @State private var open = false
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.xs)) {
            Button("\(keepTogether(explainer.title)) \(open ? "↑" : "↓")") { open.toggle() }
                .font(StockStepsTheme.font(StockStepsTheme.typography.bodyMedium)).foregroundStyle(colors.primaryText)
                .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
            if open {
                ForEach(explainer.points, id: \.self) { point in
                    Text(verbatim: "• \(keepTogether(point))").font(StockStepsTheme.font(StockStepsTheme.typography.small)).foregroundStyle(colors.textBody)
                }
            }
        }
    }
}

/// C3: the Valuation page's model sentences keep "P/E" whole (invisible word joiners; the text is unchanged).
private func keepTogether(_ text: String) -> String { ValuationDisplay.shared.keepTogether(text: text) }
