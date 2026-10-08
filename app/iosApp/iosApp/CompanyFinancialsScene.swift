import Observation
import Shared
import SwiftUI

/// Financials for one company, mirroring Android's CompanyFinancialsViewModel: annual statements
/// and the profile load first, Quarterly on demand; both are kept, and ranges only re-slice.
@MainActor
@Observable
final class CompanyFinancialsModel {
    let symbol: String
    private(set) var name: String?
    private(set) var listing: String?
    private(set) var frequency: FinancialPeriod = .annual
    private(set) var selectedRange: FinancialRange?
    private(set) var loaded: [FinancialPeriod: CompanyFundamentals] = [:]
    private(set) var loading = true
    private(set) var failed = false
    @ObservationIgnored private let client: IosCompanyDetailsClient
    @ObservationIgnored private var task: Task<Void, Never>?

    init(symbol: String, client: IosCompanyDetailsClient) {
        self.symbol = symbol
        self.client = client
    }

    var shortName: String { name.map { CompanyOverviewPresenter.shared.shortName(name: $0) } ?? symbol }
    var fundamentals: CompanyFundamentals? { loaded[frequency] }
    var range: FinancialRange {
        selectedRange ?? FinancialStatementsPresenter.shared.defaultRange(frequency: frequency, periods: Int32(fundamentals?.history.count ?? 0))
    }
    var model: FinancialStatementsModel? {
        fundamentals.map { FinancialStatementsPresenter.shared.build(fundamentals: $0, frequency: frequency, range: range, companyName: shortName) }
    }
    var valuation: [FinancialMetric] { loaded[.annual].map { client.valuation(symbol: symbol, fundamentals: $0) } ?? [] }

    func start() {
        guard loaded.isEmpty, task == nil else { return }
        Task { [weak self] in
            guard let self, let profile = try? await client.getProfile(symbol: symbol) else { return }
            name = profile.companyName
            listing = [symbol, profile.exchange].compactMap { $0 }.joined(separator: " · ")
        }
        load()
    }

    func select(_ newFrequency: FinancialPeriod) {
        guard newFrequency != frequency else { return }
        frequency = newFrequency
        selectedRange = nil
        failed = false
        if loaded[newFrequency] == nil { load() } else { task?.cancel(); loading = false }
    }

    func select(range: FinancialRange) { selectedRange = range }

    func load() {
        let requested = frequency
        task?.cancel()
        loading = true   // current content stays visible while the new frequency loads
        failed = false
        task = Task {
            do {
                let result = try await client.getFundamentals(symbol: symbol, period: requested)
                loaded[requested] = result
                // A response for a frequency the user already left is cached but doesn't end the newer load.
                if frequency == requested { loading = false }
            } catch {
                guard !Task.isCancelled, frequency == requested else { return }
                loading = false
                failed = true
            }
        }
    }
}

struct CompanyFinancialsScene: View {
    @Environment(\.colorScheme) private var scheme
    @State private var model: CompanyFinancialsModel

    init(symbol: String, client: IosCompanyDetailsClient) {
        _model = State(initialValue: CompanyFinancialsModel(symbol: symbol, client: client))
    }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let space = StockStepsTheme.spacing
        let type = StockStepsTheme.typography
        let content = model.model
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(model.listing ?? model.symbol).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
                    Text("Understand how this company earns, grows and manages its money.").font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
                    if let period = content?.periodLabel {
                        Text(period).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
                    }
                }
                VStack(spacing: 0) {
                    StockPillSelector(options: FinancialPeriod.entries, selected: model.frequency, label: { $0.label }) { model.select($0) }
                    StockDivider()
                    StockPillSelector(options: FinancialRange.entries, selected: model.range, label: { $0.label }) { model.select(range: $0) }
                }
                .padding(.horizontal, CGFloat(space.sm))
                .stockCard(padding: 0, bordered: false)
                .padding(.top, CGFloat(space.md))
                if model.loading && content != nil { ProgressView().frame(maxWidth: .infinity).padding(.top, CGFloat(space.xs)) }
                if let note = content?.rangeNote {
                    Text(note).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary).padding(.top, CGFloat(space.xs))
                }
                if let content {
                    if let empty = content.emptyMessage {
                        StockSectionMessage(message: empty, actionTitle: "Try again", action: model.load).stockCard(bordered: false).padding(.top, CGFloat(space.xl))
                    } else {
                        ForEach(content.sections, id: \.id) { section in
                            FinancialSectionView(section: section).padding(.top, CGFloat(space.xl))
                        }
                        if !content.summary.isEmpty { FinancialSummaryView(model: content).padding(.top, CGFloat(space.xl)) }
                        if let table = content.table {
                            FinancialExpandable(title: "View Detailed Financial Data") { FinancialTableView(table: table) }.padding(.top, CGFloat(space.xl))
                        }
                        if !content.advanced.isEmpty {
                            FinancialExpandable(title: "Advanced Metrics") {
                                ForEach(content.advanced, id: \.label) { StockInfoLine(label: $0.label, value: $0.value, compact: true) }
                            }
                            .padding(.top, CGFloat(space.xl))
                        }
                        if !model.valuation.isEmpty {
                            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                                StockSectionHeader(title: "Valuation")
                                VStack(spacing: 0) {
                                    ForEach(Array(model.valuation.enumerated()), id: \.offset) { index, metric in
                                        if index > 0 { StockDivider() }
                                        StockInfoLine(label: metric.label, value: CompanyDetailPresenter.shared.metricValue(metric: metric),
                                                      helper: CompanyDetailPresenter.shared.historicalContext(metric: metric))
                                    }
                                }
                                .padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0, bordered: false)
                            }
                            .padding(.top, CGFloat(space.xl))
                        }
                    }
                } else if model.loading {
                    VStack(spacing: CGFloat(space.sm)) {
                        ForEach(0..<3, id: \.self) { _ in StockSkeleton(width: 1, height: CGFloat(StockStepsTheme.dimensions.chartHeight)).frame(maxWidth: .infinity) }
                    }
                    .padding(.top, CGFloat(space.xl)).accessibilityLabel("Loading")
                } else {
                    StockSectionMessage(message: "We couldn't load the financial statements. Try again.", actionTitle: "Try again", action: model.load)
                        .stockCard(bordered: false).padding(.top, CGFloat(space.xl))
                }
            }
            .frame(maxWidth: CGFloat(StockStepsTheme.dimensions.contentMaxWidth), alignment: .leading)
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.bottom, CGFloat(space.xl))
            .frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .navigationTitle("\(model.shortName) Financials")
        .navigationBarTitleDisplayMode(.inline)
        .task { model.start() }
    }
}

/// One financial area: headline + change, meaning, chart, rows, comparisons, explanation.
private struct FinancialSectionView: View {
    @Environment(\.colorScheme) private var scheme
    let section: FinancialStatementSection
    @State private var learn = false
    @State private var more = false

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let space = StockStepsTheme.spacing
        let type = StockStepsTheme.typography
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: section.title)
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                if section.availability != .content {
                    Text(section.meaning ?? (section.availability == .temporarilyUnavailable ? "Financial data is temporarily unavailable." : "This company doesn't report this metric."))
                        .font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textSecondary)
                } else {
                    if let label = section.headlineLabel { Text(label).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary) }
                    if let headline = section.headline {
                        Text(headline).font(StockStepsTheme.font(type.largeNumber, relativeTo: .largeTitle)).foregroundStyle(colors.textPrimary)
                    }
                    if let change = section.change, let direction = section.changeDirection {
                        StockPriceChange(percentage: change, direction: direction, style: type.numberMedium)
                    }
                    if let meaning = section.meaning { Text(meaning).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody) }
                    if let chart = section.chart { StockBarChart(chart: chart) }
                    if !section.rows.isEmpty {
                        VStack(spacing: 0) {
                            ForEach(Array(section.rows.enumerated()), id: \.offset) { index, row in
                                if index > 0 { StockDivider() }
                                StockInfoLine(label: row.label, value: row.value, helper: row.helper, change: row.change, direction: row.changeDirection)
                            }
                        }
                    }
                    ForEach(Array(section.comparisons.enumerated()), id: \.offset) { _, comparison in StockComparisonBars(comparison: comparison) }
                    if !section.moreRows.isEmpty {
                        toggle(more ? "Hide ratios" : "Show ratios") { more.toggle() }
                        if more { ForEach(section.moreRows, id: \.label) { StockInfoLine(label: $0.label, value: $0.value, helper: $0.helper, compact: true) } }
                    }
                    if let note = section.note { Text(note).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary) }
                }
                toggle(learn ? "Hide explanation" : "What is this?") { learn.toggle() }
                if learn {
                    VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                        Text(section.whatIsIt).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                        Text("Why it matters").font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.primaryText)
                        Text(section.whyItMatters).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                    }
                    .padding(CGFloat(space.sm)).frame(maxWidth: .infinity, alignment: .leading)
                    .background(colors.primaryContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                }
            }
            .stockCard(padding: CGFloat(space.md), bordered: false)
        }
    }

    private func toggle(_ title: String, action: @escaping () -> Void) -> some View {
        Button("\(title) →", action: action)
            .font(StockStepsTheme.font(StockStepsTheme.typography.bodyMedium)).foregroundStyle(StockStepsTheme.colors(scheme).primaryText)
            .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
    }
}

private struct FinancialSummaryView: View {
    @Environment(\.colorScheme) private var scheme
    let model: FinancialStatementsModel
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let type = StockStepsTheme.typography
        VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.sm)) {
            Text("Understanding the Financial Picture").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline).bold())
                .foregroundStyle(colors.textPrimary).accessibilityAddTraits(.isHeader)
            ForEach(model.summary, id: \.title) { line in
                VStack(alignment: .leading, spacing: 0) {
                    Text(line.title).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary)
                    Text(line.text).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                }
                .accessibilityElement(children: .combine)
            }
            if let limits = model.summaryLimitations {
                Text(limits).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
            }
            Text("Educational information, not a recommendation to buy or sell.")
                .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
        }
        .padding(CGFloat(StockStepsTheme.spacing.lg))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.primaryContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.cardLarge)))
    }
}

private struct FinancialExpandable<Content: View>: View {
    @Environment(\.colorScheme) private var scheme
    let title: String
    @ViewBuilder let content: () -> Content
    @State private var open = false
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button("\(open ? "Hide" : title) →") { open.toggle() }
                .font(StockStepsTheme.font(StockStepsTheme.typography.bodyMedium)).foregroundStyle(StockStepsTheme.colors(scheme).primaryText)
                .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
            if open { content().padding(.bottom, CGFloat(StockStepsTheme.spacing.sm)) }
        }
        .padding(.horizontal, CGFloat(StockStepsTheme.spacing.md))
        .stockCard(padding: 0, bordered: false)
    }
}

/// Metric × period table: labels fixed, value columns scroll horizontally inside the section.
private struct FinancialTableView: View {
    @Environment(\.colorScheme) private var scheme
    let table: FinancialTable
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let type = StockStepsTheme.typography
        let height = CGFloat(StockStepsTheme.dimensions.touchTarget - StockStepsTheme.spacing.sm)
        HStack(alignment: .top, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Metric").font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary).frame(height: height)
                ForEach(table.rows.indices, id: \.self) { index in
                    Text(table.rows[index].first as String? ?? "").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                        .lineLimit(2).frame(height: height, alignment: .leading)
                }
            }
            .frame(width: 128, alignment: .leading)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 0) {
                    ForEach(Array(table.columns.enumerated()), id: \.offset) { column, title in
                        VStack(alignment: .trailing, spacing: 0) {
                            Text(title).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary).frame(height: height)
                            ForEach(table.rows.indices, id: \.self) { index in
                                let values = table.rows[index].second as? [String] ?? []
                                Text(values.indices.contains(column) ? values[column] : "—")
                                    .font(StockStepsTheme.font(type.numberLabel)).foregroundStyle(colors.textPrimary).frame(height: height)
                            }
                        }
                        .frame(width: 84, alignment: .trailing)
                    }
                }
            }
        }
    }
}
