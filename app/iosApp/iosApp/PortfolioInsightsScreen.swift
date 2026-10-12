import SwiftUI
import Charts
import Shared

/// Portfolio insights (named "Portfolio Intelligence" before Portfolio Phase A), rendered from the shared `InsightsUiState` (same sections and text as
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
    /// Phase C: metric rows stack at accessibility sizes; read in body so a live text-size change re-renders.
    @Environment(\.dynamicTypeSize) private var typeSize
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
                        Button("Sign in", action: onSignIn).buttonStyle(.stockPrimary)
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
            // C.1: the only visible heading (the navigation bar shows just the back button).
            Text(PortfolioDisplay.shared.INSIGHTS_TITLE).font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).foregroundStyle(colors.textTitle).accessibilityAddTraits(.isHeader)
            if let state {
                Text([state.accountName.isEmpty ? nil : state.accountName, state.view.map { "Amounts in \($0.currency)" }, state.plus ? "StockSteps+" : nil]
                    .compactMap { $0 }.joined(separator: " · "))
                    .font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                // Phase C: design-system chips (was .bordered buttons); account labels as on the dashboard.
                if state.scenario == nil && state.accounts.count > 1 {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: CGFloat(space.xs)) {
                            ForEach(state.accounts, id: \.id) { account in
                                StockChip(title: PortfolioPresentation.shared.accountLabel(account: account), selected: account.id == state.selectedAccountId) { onSelectAccount(account.id) }
                            }
                        }
                    }
                }
                if state.mockAvailable {
                    Menu {
                        Button("My accounts") { onScenario(nil) }
                        ForEach(state.scenarios, id: \.self) { id in Button(PortfolioDisplay.shared.scenarioLabel(id: id)) { onScenario(id) } }
                    } label: {
                        // C.1 closure: leading-aligned like the rest of the header (the default label centred its wrapped lines at AX5).
                        Text("Sample scenario: \(state.scenario.map { PortfolioDisplay.shared.scenarioLabel(id: $0) } ?? "My accounts")")
                            .multilineTextAlignment(.leading).frame(maxWidth: .infinity, alignment: .leading)
                    }
                    if state.scenario != nil {
                        Text("Read-only sample data. Generated prices and index levels, not real markets.").font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                    }
                }
            }
        }
    }

    @ViewBuilder private func content(_ view: InsightsView, _ state: InsightsUiState, _ colors: StockColors) -> some View {
        section("Portfolio health", subtitle: "Key facts about your portfolio. There's no single score; each number is explained.") {
            ForEach(Array(view.health.enumerated()), id: \.offset) { i, row in if i > 0 { StockDivider() }; metric(row, colors) }
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
                ForEach(Array(view.performance.rows.enumerated()), id: \.offset) { i, row in if i > 0 { StockDivider() }; metric(row, colors) }
            }
        }
        section("Compared with a market index") {
            if view.benchmark.status != .locked {
                // C.1 closure: the shared pill selector (as on Android) — the system segmented picker cut "NASDAQ Com…" even at Large.
                StockPillSelector(options: state.benchmarks.map { $0.id.name }, selected: state.benchmark.name,
                                  label: { name in state.benchmarks.first { $0.id.name == name }.map { $0.id == .tsx ? "TSX" : $0.name } ?? name },
                                  onSelect: { onSelectBenchmark($0) })
            }
            body(view.benchmark, colors) { ForEach(Array(view.benchmark.rows.enumerated()), id: \.offset) { _, row in metric(row, colors) } }
        }
        section("Allocation") {
            // C.1 closure: pill selector (equal width while every label fits, else a fading scroll row) instead of truncating segments.
            StockPillSelector(options: Array(view.allocation.indices), selected: allocationTab, label: { view.allocation[$0].title }, onSelect: { allocationTab = $0 })
            if let group = view.allocation[safe: allocationTab] {
                switch group.status {
                case .locked: locked(group.note, colors)
                case .available:
                    ForEach(Array(group.rows.enumerated()), id: \.offset) { _, row in allocation(row, colors) }
                    if let note = group.note { Text(note).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting) }
                default: Text(group.note ?? "Allocation isn't available.").font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                }
            }
        }
        section("Concentration") {
            body(view.concentration, colors) { ForEach(Array(view.concentration.rows.enumerated()), id: \.offset) { _, row in metric(row, colors) } }
        }
        section("What moved your portfolio", subtitle: "Over \(view.selectedPeriod.label), in \(view.currency)") {
            body(view.contributors, colors) {
                if !view.positiveContributors.isEmpty { Text("Added the most").font(StockStepsTheme.font(type.label, relativeTo: .footnote).weight(.semibold)).foregroundStyle(colors.textSupporting).accessibilityAddTraits(.isHeader).padding(.top, CGFloat(space.xs)) }
                ForEach(view.positiveContributors, id: \.symbol) { contributor($0, colors) }
                if !view.negativeContributors.isEmpty { Text("Subtracted the most").font(StockStepsTheme.font(type.label, relativeTo: .footnote).weight(.semibold)).foregroundStyle(colors.textSupporting).accessibilityAddTraits(.isHeader).padding(.top, CGFloat(space.xs)) }
                ForEach(view.negativeContributors, id: \.symbol) { contributor($0, colors) }
                ForEach(Array(view.contributors.rows.enumerated()), id: \.offset) { _, row in metric(row, colors) }
            }
        }
        section("Dividends") {
            body(view.dividends, colors) {
                ForEach(Array(view.dividends.rows.enumerated()), id: \.offset) { _, row in metric(row, colors) }
                if !view.dividendsByYear.isEmpty {
                    Text("By year").font(StockStepsTheme.font(type.label, relativeTo: .footnote).weight(.semibold)).foregroundStyle(colors.textSupporting).accessibilityAddTraits(.isHeader).padding(.top, CGFloat(space.xs))
                    ForEach(Array(view.dividendsByYear.enumerated()), id: \.offset) { _, row in metric(row, colors) }
                }
                if !view.dividendsByCompany.isEmpty {
                    Text("By company").font(StockStepsTheme.font(type.label, relativeTo: .footnote).weight(.semibold)).foregroundStyle(colors.textSupporting).accessibilityAddTraits(.isHeader).padding(.top, CGFloat(space.xs))
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
                Text(PortfolioDisplay.shared.INSIGHTS_LIST_TITLE).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3)).accessibilityAddTraits(.isHeader)
                ForEach(view.insights, id: \.id) { insight in
                    StockInsightCard(title: insight.title, message: insight.explanation, systemImage: insightSymbol(insight.category))
                }
            }
        }
        section("How these numbers work") {
            ForEach(AnalyticsEducation.shared.entries, id: \.key) { entry in
                // C.1: leading-aligned label in the body colour (the default centred tint label hyphenated "complete-ness" at AX5).
                DisclosureGroup {
                    Text(entry.body).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textBody)
                } label: {
                    Text(entry.title).font(StockStepsTheme.font(type.label, relativeTo: .footnote).weight(.semibold)).foregroundStyle(colors.textTitle)
                        .multilineTextAlignment(.leading).frame(maxWidth: .infinity, alignment: .leading)
                }
                .tint(colors.iconSecondary)
                .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
            }
        }
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            ForEach(view.notes + ["Not investment advice. Past performance doesn't indicate future results."], id: \.self) {
                Text($0).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
            }
            Text(PortfolioDisplay.shared.asOf(instant: view.asOf)).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textMeta)
            Button("Refresh insights", action: onRefresh).frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
        }
    }

    // MARK: - Pieces

    @ViewBuilder private func section<Content: View>(_ title: String, subtitle: String? = nil, @ViewBuilder content: () -> Content) -> some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            Text(title).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textTitle).accessibilityAddTraits(.isHeader)
            if let subtitle { Text(subtitle).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting) }
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .stockCard(bordered: false)   // Phase C: borderless like the Portfolio dashboard
    }

    @ViewBuilder private func body<Content: View>(_ section: SectionView, _ colors: StockColors, @ViewBuilder content: () -> Content) -> some View {
        switch section.status {
        case .locked: locked(section.message, colors)
        case .unavailable: Text(section.message ?? "Not available.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
        default:
            content()
            ForEach(Array(section.notes.prefix(3).enumerated()), id: \.offset) { _, note in
                Text(note).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
            }
        }
    }

    private func locked(_ message: String?, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            Text("StockSteps+").font(StockStepsTheme.font(type.label, relativeTo: .footnote).weight(.semibold)).foregroundStyle(colors.primaryText)
            Text(message ?? "Available with StockSteps+.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(CGFloat(space.md))
        .background(colors.primaryContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
        .accessibilityElement(children: .combine)
    }

    /// Phase C: label in the supporting colour, value strong (was all body text); at accessibility sizes the value goes under its label.
    private func metric(_ row: MetricRow, _ colors: StockColors) -> some View {
        let tint: Color = row.tone == .positive ? colors.positiveText : row.tone == .negative ? colors.negativeText : colors.textValue
        let label = Text(row.label).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
        let value = Text(row.value).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .footnote)).foregroundStyle(tint)
        return VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            if typeSize.isAccessibilitySize {
                label; value
            } else {
                HStack(alignment: .firstTextBaseline) {
                    label
                    Spacer(minLength: CGFloat(space.sm))
                    value.multilineTextAlignment(.trailing)
                }
            }
            if let detail = row.detail { Text(detail).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting) }
        }
        .padding(.vertical, CGFloat(space.xxs))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([row.accessibility, row.detail].compactMap { $0 }.joined(separator: ". "))
    }

    private func allocation(_ row: AllocationRowView, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            // C.1: value under the label at accessibility sizes — side by side the ticker broke mid-word ("SHOP.T / O") at AX5.
            if typeSize.isAccessibilitySize {
                Text(row.label).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textBody)
                Text("\(row.percent) · \(row.value)").font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
            } else {
                HStack {
                    Text(row.label).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textBody)
                    Spacer()
                    Text("\(row.percent) · \(row.value)").font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                }
            }
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(colors.surfaceSecondary)
                    Capsule().fill(colors.primary).frame(width: proxy.size.width * CGFloat(row.fraction))
                }
            }.frame(height: CGFloat(StockStepsTheme.dimensions.rangeBar))
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(row.accessibility)
    }

    /// C.1 (mirrors Compose): at accessibility sizes the amounts go under the name, so names wrap on whole words.
    private func contributor(_ row: ContributorView, _ colors: StockColors) -> some View {
        let amountColor = row.tone == .negative ? colors.negativeText : colors.positiveText
        let detail = [row.percent, row.fx.map { "FX \($0)" }].compactMap { $0 }.joined(separator: " · ")
        let name = VStack(alignment: .leading, spacing: 0) {
            Text(row.symbol).font(StockStepsTheme.font(type.label, relativeTo: .footnote).weight(.semibold)).foregroundStyle(colors.textTitle)
            if let n = row.name { Text(n).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting) }
        }
        return Button { onCompany(row.symbol) } label: {
            Group {
                if typeSize.isAccessibilitySize {
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        name
                        Text(row.amount).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .footnote)).foregroundStyle(amountColor)
                        if !detail.isEmpty { Text(detail).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting) }
                    }.frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    HStack {
                        name
                        Spacer()
                        VStack(alignment: .trailing) {
                            Text(row.amount).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .footnote)).foregroundStyle(amountColor)
                            Text(detail).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                        }
                    }
                }
            }
            .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
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
                        .foregroundStyle(StockStepsTheme.color(StockStepsTheme.chartLine(1, scheme).color))
                        .lineStyle(StockStepsTheme.strokeStyle(StockStepsTheme.chartLine(1, scheme)))
                }
                ForEach(portfolio) { point in
                    LineMark(x: .value("Day", point.index), y: .value("Growth of 100", point.value), series: .value("Series", point.series))
                        .foregroundStyle(colors.primary).lineStyle(StrokeStyle(lineWidth: 2))
                }
            }
            .chartXAxis(.hidden)
            // C.1 closure: fewer y marks and room around the plot at accessibility sizes — the AX5 axis labels ran into the legend ("90" over
            // "S&P/TSX"). Labels keep their full size.
            .chartYAxis { AxisMarks(values: .automatic(desiredCount: typeSize.isAccessibilitySize ? 3 : 4)) }
            .chartYScale(domain: .automatic(includesZero: false))
            .frame(height: 160)
            .padding(.vertical, typeSize.isAccessibilitySize ? CGFloat(space.md) : 0)
            // Phase C: drawn legend swatches (shared StockChartSeriesStyle) instead of "—" / "- -" text. C.1 closure: one series per line at
            // accessibility sizes, so a name wraps between words ("S&P/TSX Composi/te" broke mid-word side by side at AX5).
            let legend = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: CGFloat(space.xs)))
                : AnyLayout(HStackLayout(spacing: CGFloat(space.md)))
            legend {
                HStack(spacing: CGFloat(space.xs)) { StockChartSwatch(line: StockStepsTheme.chartLine(0, scheme)); Text(chart.portfolioLabel) }
                if let label = chart.benchmarkLabel { HStack(spacing: CGFloat(space.xs)) { StockChartSwatch(line: StockStepsTheme.chartLine(1, scheme)); Text(label) } }
            }.font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
            HStack { Text(chart.dates.first.map { PortfolioDates.shared.shortDate(value: $0) } ?? ""); Spacer(); Text(chart.dates.last.map { PortfolioDates.shared.shortDate(value: $0) } ?? "") }
                .font(Font(StockStepsTheme.uiFont(type.tiny, typeSize: min(typeSize, .xxxLarge)))).foregroundStyle(colors.textMeta)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(chart.description)
    }

    /// Insight category symbol (shared mapping `PortfolioInsightIcons`), decorative — the card title says what it is.
    private func insightSymbol(_ category: InsightCategory) -> String {
        switch PortfolioInsightIcons.shared.icon(category: category) {
        case .layers: "square.3.layers.3d"
        case .pie: "chart.pie"
        case .trend: "chart.line.uptrend.xyaxis"
        case .bar: "chart.bar"
        case .speed: "gauge.with.dots.needle.50percent"
        case .coin: "dollarsign.circle"
        case .globe: "globe"
        default: "info.circle"
        }
    }
}
