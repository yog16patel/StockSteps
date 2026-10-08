import SwiftUI
import Charts
import Shared

/// Portfolio Intelligence, rendered from the shared `InsightsUiState` (same sections and text as
/// Android). NUMBER → CONTEXT → EXPLANATION → EDUCATION; locked/unavailable sections say why.
struct PortfolioInsightsScreen: View {
    let state: InsightsUiState?
    let onSelectAccount: (String) -> Void
    let onSelectPeriod: (String) -> Void
    let onSelectBenchmark: (String) -> Void
    let onScenario: (String?) -> Void
    let onRefresh: () -> Void
    let onCompany: (String) -> Void
    let onSignIn: () -> Void
    @Environment(\.colorScheme) private var scheme
    @State private var allocationTab = 0
    private let space = StockStepsTheme.spacing
    private let type = StockStepsTheme.typography

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.sectionGap)) {
                header(colors)
                if let state {
                    if state.loading { ProgressView().accessibilityLabel("Loading insights") }
                    if let error = state.error {
                        VStack(alignment: .leading) { Text(error).foregroundStyle(colors.negativeText); Button("Retry", action: onRefresh) }
                    }
                    if !state.signedIn && state.scenario == nil {
                        Text("Sign in to see insights about your portfolio.")
                        Button("Sign in", action: onSignIn).buttonStyle(.borderedProminent)
                    } else if let view = state.view {
                        content(view, state, colors)
                    } else if !state.loading && state.error == nil {
                        Text(state.accounts.isEmpty ? "Create a portfolio and record what you own to see insights." : "No insights yet.")
                            .foregroundStyle(colors.textSecondary)
                    }
                }
            }
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
        }
        .background(colors.appBackground)
    }

    @ViewBuilder private func header(_ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text("Portfolio Intelligence").font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).accessibilityAddTraits(.isHeader)
            if let state {
                Text([state.accountName.isEmpty ? nil : state.accountName, state.view.map { "Amounts in \($0.currency)" }, state.plus ? "StockSteps+" : nil]
                    .compactMap { $0 }.joined(separator: " · "))
                    .font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
                if state.scenario == nil && state.accounts.count > 1 {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(state.accounts, id: \.id) { account in
                                Button("\(account.name) · \(account.reportingCurrency.name)") { onSelectAccount(account.id) }
                                    .buttonStyle(.bordered).tint(account.id == state.selectedAccountId ? colors.primary : colors.textSecondary)
                            }
                        }
                    }
                }
                if state.mockAvailable {
                    Menu("Sample scenario: \(state.scenario ?? "My accounts")") {
                        Button("My accounts") { onScenario(nil) }
                        ForEach(state.scenarios, id: \.self) { id in Button(id) { onScenario(id) } }
                    }
                    if state.scenario != nil {
                        Text("Read-only sample data. Generated prices and index levels, not real markets.").font(.caption).foregroundStyle(colors.textSecondary)
                    }
                }
            }
        }
    }

    @ViewBuilder private func content(_ view: InsightsView, _ state: InsightsUiState, _ colors: StockColors) -> some View {
        section("Portfolio health", subtitle: "Key facts about your portfolio. There's no single score; each number is explained.") {
            ForEach(Array(view.health.enumerated()), id: \.offset) { _, row in metric(row, colors) }
        }
        section("Performance") {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: CGFloat(space.xxs)) {
                    ForEach(view.periods, id: \.label) { option in
                        Button(option.label) { onSelectPeriod(option.label) }
                            .buttonStyle(.bordered)
                            .tint(option.period == view.selectedPeriod ? colors.primary : colors.textSecondary)
                            .disabled(!option.enabled)
                            .accessibilityValue(option.enabled ? (option.period == view.selectedPeriod ? "Selected" : "") : "Not enough history")
                    }
                }
            }
            body(view.performance, colors) {
                if let chart = view.chart { comparisonChart(chart, colors) }
                ForEach(Array(view.performance.rows.enumerated()), id: \.offset) { _, row in metric(row, colors) }
            }
        }
        section("Compared with a market index") {
            if view.benchmark.status != .locked {
                Picker("Benchmark", selection: Binding(get: { state.benchmark.name }, set: { onSelectBenchmark($0) })) {
                    ForEach(state.benchmarks, id: \.id.name) { info in Text(info.id == .tsx ? "TSX" : info.name).tag(info.id.name) }
                }.pickerStyle(.segmented)
            }
            body(view.benchmark, colors) { ForEach(Array(view.benchmark.rows.enumerated()), id: \.offset) { _, row in metric(row, colors) } }
        }
        section("Allocation") {
            Picker("Allocation", selection: $allocationTab) {
                ForEach(Array(view.allocation.enumerated()), id: \.offset) { index, group in Text(group.title).tag(index) }
            }.pickerStyle(.segmented)
            if let group = view.allocation[safe: allocationTab] {
                switch group.status {
                case .locked: locked(group.note, colors)
                case .available:
                    ForEach(Array(group.rows.enumerated()), id: \.offset) { _, row in allocation(row, colors) }
                    if let note = group.note { Text(note).font(.caption).foregroundStyle(colors.textSecondary) }
                default: Text(group.note ?? "Allocation isn't available.").font(.caption).foregroundStyle(colors.textSecondary)
                }
            }
        }
        section("Concentration") {
            body(view.concentration, colors) { ForEach(Array(view.concentration.rows.enumerated()), id: \.offset) { _, row in metric(row, colors) } }
        }
        section("What moved your portfolio", subtitle: "Over \(view.selectedPeriod.label), in \(view.currency)") {
            body(view.contributors, colors) {
                if !view.positiveContributors.isEmpty { Text("Added the most").font(.subheadline.weight(.semibold)) }
                ForEach(view.positiveContributors, id: \.symbol) { contributor($0, colors) }
                if !view.negativeContributors.isEmpty { Text("Subtracted the most").font(.subheadline.weight(.semibold)) }
                ForEach(view.negativeContributors, id: \.symbol) { contributor($0, colors) }
                ForEach(Array(view.contributors.rows.enumerated()), id: \.offset) { _, row in metric(row, colors) }
            }
        }
        section("Dividends") {
            body(view.dividends, colors) {
                ForEach(Array(view.dividends.rows.enumerated()), id: \.offset) { _, row in metric(row, colors) }
                if !view.dividendsByYear.isEmpty {
                    Text("By year").font(.subheadline.weight(.semibold))
                    ForEach(Array(view.dividendsByYear.enumerated()), id: \.offset) { _, row in metric(row, colors) }
                }
                if !view.dividendsByCompany.isEmpty {
                    Text("By company").font(.subheadline.weight(.semibold))
                    ForEach(Array(view.dividendsByCompany.prefix(5).enumerated()), id: \.offset) { _, row in metric(row, colors) }
                }
            }
        }
        section("Currency exposure") {
            body(view.currencyExposure, colors) {
                ForEach(Array(view.currencySlices.enumerated()), id: \.offset) { _, row in allocation(row, colors) }
                ForEach(Array(view.currencyExposure.rows.enumerated()), id: \.offset) { _, row in metric(row, colors) }
            }
        }
        if !view.insights.isEmpty {
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                Text("Insights").font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3)).accessibilityAddTraits(.isHeader)
                ForEach(view.insights, id: \.id) { insight in
                    StockInsightCard(title: insight.title, message: insight.explanation)
                }
            }
        }
        section("How these numbers work") {
            ForEach(AnalyticsEducation.shared.entries, id: \.key) { entry in
                DisclosureGroup(entry.title) { Text(entry.body).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody) }
                    .frame(minHeight: 44)
            }
        }
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            ForEach(view.notes + ["Not investment advice. Past performance doesn't indicate future results."], id: \.self) {
                Text($0).font(.caption).foregroundStyle(colors.textSecondary)
            }
            Text("As of \(String(view.asOf.prefix(16)).replacingOccurrences(of: "T", with: " ")) UTC").font(.caption2).foregroundStyle(colors.textTertiary)
            Button("Refresh insights", action: onRefresh).frame(minHeight: 44)
        }
    }

    // MARK: - Pieces

    @ViewBuilder private func section<Content: View>(_ title: String, subtitle: String? = nil, @ViewBuilder content: () -> Content) -> some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            Text(title).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3)).accessibilityAddTraits(.isHeader)
            if let subtitle { Text(subtitle).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary) }
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .stockCard()
    }

    @ViewBuilder private func body<Content: View>(_ section: SectionView, _ colors: StockColors, @ViewBuilder content: () -> Content) -> some View {
        switch section.status {
        case .locked: locked(section.message, colors)
        case .unavailable: Text(section.message ?? "Not available.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
        default:
            content()
            ForEach(Array(section.notes.prefix(3).enumerated()), id: \.offset) { _, note in
                Text(note).font(.caption).foregroundStyle(colors.textSecondary)
            }
        }
    }

    private func locked(_ message: String?, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            Text("StockSteps+").font(.subheadline.weight(.semibold)).foregroundStyle(colors.primaryText)
            Text(message ?? "Available with StockSteps+.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(CGFloat(space.md))
        .background(colors.primaryContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
        .accessibilityElement(children: .combine)
    }

    private func metric(_ row: MetricRow, _ colors: StockColors) -> some View {
        let tint: Color = row.tone == .positive ? colors.positiveText : row.tone == .negative ? colors.negativeText : colors.textPrimary
        return VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .firstTextBaseline) {
                Text(row.label).foregroundStyle(colors.textBody)
                Spacer(minLength: CGFloat(space.sm))
                Text(row.value).fontWeight(.semibold).foregroundStyle(tint).multilineTextAlignment(.trailing)
            }
            if let detail = row.detail { Text(detail).font(.caption).foregroundStyle(colors.textSecondary) }
        }
        .frame(minHeight: 36)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([row.accessibility, row.detail].compactMap { $0 }.joined(separator: ". "))
    }

    private func allocation(_ row: AllocationRowView, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(row.label).lineLimit(2)
                Spacer()
                Text("\(row.percent) · \(row.value)").font(.caption).foregroundStyle(colors.textSecondary)
            }
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(colors.surfaceSecondary)
                    Capsule().fill(colors.primary).frame(width: proxy.size.width * CGFloat(row.fraction))
                }
            }.frame(height: 6)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(row.accessibility)
    }

    private func contributor(_ row: ContributorView, _ colors: StockColors) -> some View {
        Button { onCompany(row.symbol) } label: {
            HStack {
                VStack(alignment: .leading) {
                    Text(row.symbol).fontWeight(.semibold).foregroundStyle(colors.textPrimary)
                    if let name = row.name { Text(name).font(.caption).foregroundStyle(colors.textSecondary).lineLimit(1) }
                }
                Spacer()
                VStack(alignment: .trailing) {
                    Text(row.amount).fontWeight(.semibold).foregroundStyle(row.tone == .negative ? colors.negativeText : colors.positiveText)
                    Text([row.percent, row.fx.map { "FX \($0)" }].compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(colors.textSecondary)
                }
            }
            .frame(minHeight: 44)
        }
        .accessibilityLabel(row.accessibility)
        .accessibilityHint("Opens \(row.symbol)")
    }

    private struct ChartPoint: Identifiable {
        let id: String
        let index: Int
        let value: Double
        let series: String
    }

    private func comparisonChart(_ chart: ComparisonChart, _ colors: StockColors) -> some View {
        // Kotlin List<Double?> arrives as [Any]: nulls (gaps) are skipped, never drawn as zero.
        let portfolio = chart.portfolio.enumerated().compactMap { i, v in (v as? NSNumber).map { ChartPoint(id: "p\(i)", index: i, value: $0.doubleValue, series: chart.portfolioLabel) } }
        let benchmarkValues: [Any] = (chart.benchmark as? [Any]) ?? []
        let benchmark = benchmarkValues.enumerated().compactMap { i, v in (v as? NSNumber).map { ChartPoint(id: "b\(i)", index: i, value: $0.doubleValue, series: chart.benchmarkLabel ?? "Benchmark") } }
        return VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Chart {
                RuleMark(y: .value("Start", 100)).foregroundStyle(colors.textTertiary).lineStyle(StrokeStyle(lineWidth: 1, dash: [4, 4]))
                ForEach(benchmark) { point in
                    LineMark(x: .value("Day", point.index), y: .value("Growth of 100", point.value), series: .value("Series", point.series))
                        .foregroundStyle(colors.textSecondary).lineStyle(StrokeStyle(lineWidth: 2, dash: [6, 4]))
                }
                ForEach(portfolio) { point in
                    LineMark(x: .value("Day", point.index), y: .value("Growth of 100", point.value), series: .value("Series", point.series))
                        .foregroundStyle(colors.primary).lineStyle(StrokeStyle(lineWidth: 2))
                }
            }
            .chartXAxis(.hidden)
            .chartYScale(domain: .automatic(includesZero: false))
            .frame(height: 160)
            HStack {
                Text("— \(chart.portfolioLabel)").foregroundStyle(colors.primary)
                if let label = chart.benchmarkLabel { Text("- - \(label)").foregroundStyle(colors.textSecondary) }
            }.font(.caption)
            HStack { Text(chart.dates.first ?? ""); Spacer(); Text(chart.dates.last ?? "") }.font(.caption2).foregroundStyle(colors.textTertiary)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(chart.description)
    }
}
