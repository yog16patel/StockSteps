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

    init(symbol: String, client: IosCompanyDetailsClient) {
        _model = State(initialValue: CompanyValuationModel(symbol: symbol, client: client))
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
                    currentCard(content, colors).padding(.top, CGFloat(space.md))
                    titled("Historical P/E Trend") {
                        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                            if model.historyLoaded {
                                StockPillSelector(options: ValuationRange.entries, selected: model.range, label: { $0.label }) { model.select(range: $0) }
                                if let chart = content.chart {
                                    StockTrendChart(chart: chart, referenceLabel: content.average.map { "\(content.averageLabel): \($0)" })
                                }
                                Text(content.methodology).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
                            } else {
                                StockSectionMessage(message: content.historyMessage ?? "We couldn't load the valuation history right now.", actionTitle: "Try again", action: model.load)
                            }
                        }
                        .stockCard(padding: CGFloat(space.md), bordered: false)
                    }
                    understanding(content, colors).padding(.top, CGFloat(space.xl))
                    titled("Other Valuation Metrics") {
                        VStack(spacing: 0) {
                            if model.fundamentalsFailed {
                                StockSectionMessage(message: "We couldn't load the other valuation metrics right now.", actionTitle: "Try again", action: model.load)
                            } else if content.otherMetrics.isEmpty {
                                StockSectionMessage(message: "Other valuation ratios aren't available for this company.")
                            } else {
                                ForEach(Array(content.otherMetrics.enumerated()), id: \.offset) { index, row in
                                    if index > 0 { StockDivider() }
                                    StockInfoLine(label: row.label, value: row.value, helper: row.helper)
                                }
                            }
                        }
                        .padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0, bordered: false)
                    }
                    if content.growth.count > 1 {
                        titled("Valuation & Growth") {
                            VStack(alignment: .leading, spacing: 0) {
                                ForEach(Array(content.growth.enumerated()), id: \.offset) { index, row in
                                    if index > 0 { StockDivider() }
                                    StockInfoLine(label: row.label, value: row.value)
                                }
                                Text(content.growthNote).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody).padding(.vertical, CGFloat(space.sm))
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
                                HStack(alignment: .top, spacing: CGFloat(space.sm)) {
                                    ForEach([("Lowest", summary.lowest), ("Average", summary.average), ("Highest", summary.highest), ("Current", summary.current ?? "N/A")], id: \.0) { label, value in
                                        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                                            Text(label).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary)
                                            Text(value).font(StockStepsTheme.font(type.numberEmphasis, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
                                        }
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .accessibilityElement(children: .combine)
                                    }
                                }
                                if let percentile = summary.percentile {
                                    Text(percentile).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                                }
                            }
                            .stockCard(padding: CGFloat(space.md), bordered: false)
                        }
                    }
                    StockInsightCard(title: "What Does This Valuation Tell Us?", message: content.insight).padding(.top, CGFloat(space.xl))
                    Text("Educational information, not a recommendation to buy or sell.")
                        .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary).padding(.top, CGFloat(space.lg))
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
        .navigationTitle("\(model.shortName) Valuation")
        .navigationBarTitleDisplayMode(.inline)
        .task { model.start() }
    }

    /// Primary card: large P/E, neutral badge (amber only when above history), meaning, comparison.
    private func currentCard(_ content: ValuationModel, _ colors: StockColors) -> some View {
        let space = StockStepsTheme.spacing
        let type = StockStepsTheme.typography
        return VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            Text("Current P/E Ratio").font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary)
            HStack(spacing: CGFloat(space.sm)) {
                Text(content.currentPe).font(StockStepsTheme.font(type.largeNumber, relativeTo: .largeTitle)).foregroundStyle(colors.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                if let badge = content.badge { StockBadge(text: badge, tone: content.position == .above ? .caution : .neutral) }
            }
            Text(content.meaning).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
            if let average = content.average {
                StockDivider()
                HStack(alignment: .top, spacing: CGFloat(space.sm)) {
                    ForEach([("Current", content.currentPe), (content.averageLabel, average), ("Difference", content.difference ?? "—")], id: \.0) { label, value in
                        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                            Text(label).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary)
                            Text(value).font(StockStepsTheme.font(type.numberEmphasis, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityElement(children: .combine)
                    }
                }
            }
        }
        .stockCard(padding: CGFloat(space.lg), bordered: false)
    }

    private func understanding(_ content: ValuationModel, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.sm)) {
            Text("Understanding P/E Ratio").font(StockStepsTheme.font(StockStepsTheme.typography.cardTitle, relativeTo: .headline).bold())
                .foregroundStyle(colors.textPrimary).accessibilityAddTraits(.isHeader)
            Text(content.understanding).font(StockStepsTheme.font(StockStepsTheme.typography.body)).foregroundStyle(colors.textBody)
            ValuationExplainerView(explainer: content.highReasons)
            ValuationExplainerView(explainer: content.lowReasons)
        }
        .padding(CGFloat(StockStepsTheme.spacing.lg))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.primaryContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.cardLarge)))
    }

    private func titled<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.sm)) {
            StockSectionHeader(title: title)
            content()
        }
        .padding(.top, CGFloat(StockStepsTheme.spacing.xl))
    }
}

private struct ValuationExplainerView: View {
    @Environment(\.colorScheme) private var scheme
    let explainer: ValuationExplainer
    @State private var open = false
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.xs)) {
            Button("\(explainer.title) \(open ? "↑" : "↓")") { open.toggle() }
                .font(StockStepsTheme.font(StockStepsTheme.typography.bodyMedium)).foregroundStyle(colors.primaryText)
                .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
            if open {
                ForEach(explainer.points, id: \.self) { point in
                    Text("• \(point)").font(StockStepsTheme.font(StockStepsTheme.typography.small)).foregroundStyle(colors.textBody)
                }
            }
        }
    }
}
