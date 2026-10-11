import Charts
import Observation
import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography

/// Mirrors the shared Kotlin Screener and Comparison presenters (same state and actions as Android).
@MainActor @Observable
final class ScreenerModel {
    private(set) var screener: ScreenerUiState?
    private(set) var comparison: ComparisonUiState?
    private(set) var research: ResearchUiState?
    private(set) var ai: ComparisonAiUiState?
    @ObservationIgnored private let accounts: AccountViewModel
    @ObservationIgnored private let baseURL: () -> String
    @ObservationIgnored private var made: IosScreenerClient?
    @ObservationIgnored private var subscriptions: [any AccountSubscription] = []

    /// Created on first use: the client's comparison presenter observes the shared selection from construction, so every copy of this model
    /// SwiftUI discarded (AppScene re-runs its init) still answered selection changes — 2× compare/performance/history requests (Phase 5C.1).
    var client: IosScreenerClient {
        if let made { return made }
        let created = IosScreenerClient(baseUrl: baseURL, account: accounts.client)
        made = created
        return created
    }

    /// `start: false` defers observing and loading to `activate()` (AppScene: SwiftUI re-runs its init and keeps only the first model).
    init(accounts: AccountViewModel, baseURL: @escaping () -> String = { BackendSettings.currentURL }, start: Bool = true) {
        self.accounts = accounts
        self.baseURL = baseURL
        if start { activate() }
    }
    func activate() {
        guard subscriptions.isEmpty else { return }
        subscriptions = [
            client.observeScreener { [weak self] in self?.screener = $0 },
            client.observeComparison { [weak self] in self?.comparison = $0 },
            client.observeResearch { [weak self] in self?.research = $0 },
            client.observeAi { [weak self] in self?.ai = $0 }
        ]
        client.screener.start()
    }
    deinit {
        subscriptions.forEach { $0.cancel() }
        made?.close()
    }
}

func toneColor(_ cell: MetricCell, _ colors: StockColors) -> Color {
    if cell.explanation != nil { return colors.textTertiary }
    switch cell.tone.name {
    case "POSITIVE": return colors.positiveText
    case "NEGATIVE": return colors.negativeText
    default: return colors.textPrimary
    }
}

// MARK: - Discover Stocks

/// Owns navigation and actions for Discover Stocks; rendering is `DiscoverStocksScreen` (DiscoverStocksViews.swift).
struct DiscoverStocksScene: View {
    let model: ScreenerModel
    let accounts: AccountViewModel
    var watchlists: WatchlistsModel?
    let onOpenStock: (String) -> Void
    let onCompare: () -> Void
    var onSignIn: () -> Void = {}
    @State private var portfolioInstrument: InstrumentRef?
    @State private var watchlistInstrument: InstrumentRef?
    @State private var portfolioModel = PortfolioViewModel()

    var body: some View {
        DiscoverStocksScreen(state: model.screener, client: model.client, onOpen: onOpenStock, onCompare: onCompare, onSignIn: onSignIn,
                             watchlistAvailable: accounts.state.user != nil,
                             onWatchlist: { row in watchlistInstrument = InstrumentRef(symbol: row.symbol, name: row.name, exchange: row.exchange, currency: row.currency) },
                             onPortfolio: { row in portfolioInstrument = InstrumentRef(symbol: row.symbol, name: row.name, exchange: row.exchange, currency: row.currency) })
            // The screen shows its own large "Discover Stocks" title (Phase 5B); the bar keeps only the back button.
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(item: Binding(get: { portfolioInstrument.map { IdentifiedInstrument(instrument: $0) } }, set: { portfolioInstrument = $0?.instrument })) { item in
                PortfolioEntryScene(accounts: accounts, model: portfolioModel, existing: nil, initialInstrument: item.instrument)
            }
            .sheet(item: Binding(get: { watchlistInstrument.map { IdentifiedInstrument(instrument: $0) } }, set: { watchlistInstrument = $0?.instrument })) { item in
                // Independent of Portfolio: saving to a watchlist never changes holdings, and vice versa.
                WatchlistChooser(symbol: item.instrument.symbol, lists: watchlists?.watchlists ?? []) { list in
                    watchlistInstrument = nil
                    watchlists?.perform { try await $0.addEntry(listId: list.id, instrument: item.instrument) }
                }
            }
    }
}

struct IdentifiedInstrument: Identifiable {
    let instrument: InstrumentRef
    var id: String { instrument.symbol }
}

struct IdentifiedInfo: Identifiable {
    let info: MetricInfo
    var id: String { info.title }
}

/// What a metric means, how it's calculated, why investors look at it and its limits (shared MetricEducation).
struct MetricInfoView: View {
    let info: MetricInfo
    var body: some View {
        NavigationStack {
            List {
                Section { Text(info.meaning) }
                if let calculation = info.calculation { Section("How it's calculated") { Text(calculation) } }
                if let why = info.why { Section("Why investors look at it") { Text(why) } }
                if let period = info.period { Section("Period") { Text(period) } }
                if !info.limitations.isEmpty { Section("Limitations") { ForEach(info.limitations, id: \.self) { Text($0) } } }
            }
            .navigationTitle(info.title)
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }
}

// MARK: - Compare Companies

/// Owns Compare Companies actions; the selection is shared with Discover, Company Details and Watchlist.
struct CompareStocksScene: View {
    let model: ScreenerModel
    let onOpenStock: (String) -> Void
    var onDiscover: () -> Void = {}
    var onUpgrade: () -> Void = {}
    var onSignIn: () -> Void = {}
    @State private var showingResearch = false
    @State private var showingAi = false

    var body: some View {
        CompareStocksScreen(state: model.comparison, client: model.client, onOpen: onOpenStock, onDiscover: onDiscover, onUpgrade: onUpgrade, onSignIn: onSignIn,
                            onResearch: { showingResearch = true }, onAskAi: { showingAi = true })
            .navigationDestination(isPresented: $showingResearch) { ComparisonResearchView(model: model, onUpgrade: onUpgrade, onSignIn: onSignIn) }
            .navigationDestination(isPresented: $showingAi) { ComparisonAiView(model: model, onUpgrade: onUpgrade, onSignIn: onSignIn) }
            .navigationTitle("Compare")
            .navigationBarTitleDisplayMode(.inline)
    }
}

/// Phases 1–2 (free): beginner metrics in Overview, Growth, Profitability, Financial Health, Valuation and
/// Shareholder Returns; each metric's label sits above equal-width company columns (no horizontal
/// scrolling for three companies); extra metrics under "More metrics". Phase 2 adds "What can we learn
/// from this comparison?" and an inline guided explanation per beginner metric ("Explain"); related
/// metrics scroll to their row. Same shared presenter and interpretation engine as Android.
struct CompareStocksScreen: View {
    let state: ComparisonUiState?
    let client: IosScreenerClient
    let onOpen: (String) -> Void
    let onDiscover: () -> Void
    var onUpgrade: () -> Void = {}
    var onSignIn: () -> Void = {}
    var onResearch: () -> Void = {}
    var onAskAi: () -> Void = {}
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// nil = closed; "" = add; otherwise the symbol being replaced.
    @State private var picking: String?
    @State private var info: MetricInfo?
    @State private var why: (String, String)?

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: CGFloat(space.sm), pinnedViews: [.sectionHeaders]) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Compare Companies").font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).accessibilityAddTraits(.isHeader)
                        Text("See companies side by side. The numbers describe each business; they don't say which is a better investment.")
                            .font(.subheadline).foregroundStyle(colors.textSecondary)
                        if state?.sampleData == true { Text("Sample data for development, not live markets.").font(.caption).foregroundStyle(colors.cautionText) }
                    }
                    if let state { content(state, colors) }
                }
                .padding(.horizontal, CGFloat(space.screen))
                .padding(.vertical, CGFloat(space.md))
            }
            // "Explore next" / "Explain this": bring the metric into view (no animation with Reduce Motion), then clear the request.
            .onChange(of: state?.focus) { _, focus in
                guard let focus else { return }
                DispatchQueue.main.async {
                    if reduceMotion { proxy.scrollTo("metric-\(focus)", anchor: .center) }
                    else { withAnimation { proxy.scrollTo("metric-\(focus)", anchor: .center) } }
                    client.comparison.clearFocus()
                }
            }
        }
        .background(colors.appBackground)
        .sheet(item: Binding(get: { picking.map { PickTarget(id: $0) } }, set: { picking = $0?.id })) { target in
            AddCompanySheet(state: state, client: client, replacing: target.id.isEmpty ? nil : target.id) { picking = nil }
        }
        .sheet(item: Binding(get: { info.map { IdentifiedInfo(info: $0) } }, set: { info = $0?.info })) { MetricInfoView(info: $0.info) }
        .alert(why?.0 ?? "", isPresented: Binding(get: { why != nil }, set: { if !$0 { why = nil } })) { Button("OK") { why = nil } } message: { Text(why?.1 ?? "") }
        .alert("", isPresented: Binding(get: { state?.message != nil }, set: { if !$0 { client.comparison.dismissMessage() } })) {
            Button("OK") { client.comparison.dismissMessage() }
        } message: { Text(state?.message ?? "") }
    }

    @ViewBuilder private func content(_ state: ComparisonUiState, _ colors: StockColors) -> some View {
        selection(state, colors)
        if state.needsMore {
            examples(state, colors)
        } else {
            if state.loading { ProgressView().accessibilityLabel("Loading comparison") }
            if let error = state.error { StockSectionMessage(message: error, actionTitle: "Try again") { client.comparison.retry() } }
            if !state.columns.isEmpty {
                if !state.insights.isEmpty { ComparisonLearningSummaryView(insights: state.insights) { client.comparison.openRelated(id: $0) } }
                Section {
                    ForEach(state.sections.filter { !$0.advanced }, id: \.title) { section in sectionView(section, state, colors) }
                    if state.advancedCount > 0 {
                        Button { client.comparison.toggleMore() } label: {
                            Text(state.showMore ? "Hide extra metrics" : "More metrics (\(state.advancedCount))").frame(maxWidth: .infinity, minHeight: 48)
                        }
                        .buttonStyle(.bordered)
                        .accessibilityValue(state.showMore ? "Expanded" : "Collapsed")
                    }
                    if state.showMore { ForEach(state.sections.filter { $0.advanced && !$0.rows.isEmpty }, id: \.title) { section in sectionView(section, state, colors) } }
                } header: { companyHeader(state, colors) }
            }
            performance(state, colors)
            if state.historyEnabled { HistoricalComparisonView(state: state, client: client, onUpgrade: onUpgrade, onSignIn: onSignIn) }
            if state.historyEnabled && !state.columns.isEmpty {
                // Phase 4 entry point (one card; no repeated banners).
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                    Text("Research checklist").font(.headline).accessibilityAddTraits(.isHeader)
                    Text("Work through guided questions about these companies, write private notes and track your progress. Free; StockSteps+ adds advanced research and reports.")
                        .font(.subheadline)
                    Button("Open research checklist", action: onResearch).buttonStyle(.bordered).frame(maxWidth: .infinity, minHeight: 48)
                }
                .stockCard()
            }
            // Phase 5 entry point (StockSteps+; the AI screen shows the preview for free accounts).
            if state.historyEnabled && !state.columns.isEmpty { ComparisonAiEntryCard(onOpen: onAskAi) }
            if !state.observations.isEmpty {
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                    Text("What the numbers show").font(.headline).accessibilityAddTraits(.isHeader)
                    ForEach(state.observations, id: \.text) { o in
                        Text("• \(o.text)").font(.subheadline)
                        if let caveat = o.caveat { Text(caveat).font(.caption).foregroundStyle(colors.textSecondary).padding(.leading, 8) }
                    }
                    Text("These describe reported figures. They aren't rankings or recommendations.").font(.caption2).foregroundStyle(colors.textTertiary)
                }
                .stockCard()
            }
            if !state.columns.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Sources and notes").font(.caption.weight(.semibold)).foregroundStyle(colors.textSecondary).accessibilityAddTraits(.isHeader)
                    ForEach(state.notes, id: \.self) { Text($0).font(.caption).foregroundStyle(colors.textSecondary) }
                    if let asOf = state.asOf { Text("Comparison prepared \(String(asOf.prefix(10))) \(String(asOf.dropFirst(11).prefix(5))) UTC.").font(.caption).foregroundStyle(colors.textSecondary) }
                    Text("Education, not investment advice. Past results don't indicate future returns.").font(.caption2).foregroundStyle(colors.textTertiary)
                }
            }
        }
    }

    private func selection(_ state: ComparisonUiState, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text("Companies (\(state.selected.count) of \(client.maxCompanies()))").font(.headline).accessibilityAddTraits(.isHeader)
            ForEach(state.selected, id: \.symbol) { company in
                let column = state.columns.first { $0.symbol.caseInsensitiveCompare(company.symbol) == .orderedSame }
                HStack {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(column?.name ?? company.name).font(.subheadline.weight(.semibold)).lineLimit(1)
                        Text([company.symbol, column?.listing].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")).font(.caption).foregroundStyle(colors.textSecondary)
                    }
                    Spacer()
                    Button("Replace") { picking = company.symbol }.frame(minHeight: 48).accessibilityLabel("Replace \(company.name)")
                    Button("Remove") { client.comparison.remove(symbol: company.symbol) }.frame(minHeight: 48).accessibilityLabel("Remove \(company.name)")
                }
            }
            if state.canAdd {
                Button { picking = "" } label: { Label("Add a company", systemImage: "magnifyingglass").frame(maxWidth: .infinity, minHeight: 48) }.buttonStyle(.bordered)
            }
            Text(state.crowdedHint ?? "Two or three companies are easiest to read on a phone.").font(.caption).foregroundStyle(colors.textSecondary)
        }
        .stockCard()
    }

    private func examples(_ state: ComparisonUiState, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text(state.selected.isEmpty ? "Pick two companies to start" : "Add one more company to compare").font(.headline).accessibilityAddTraits(.isHeader)
            Text("Search above, use Compare on a company's page or your watchlist, or try an example:").font(.subheadline)
            ForEach(Array(state.examples.enumerated()), id: \.offset) { i, example in
                Button { client.useExample(index: Int32(i)) } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(example.title).font(.subheadline.weight(.semibold)).foregroundStyle(colors.textPrimary)
                        Text(example.description_).font(.caption).foregroundStyle(colors.textSecondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(CGFloat(space.sm))
                    .background(colors.surfaceSecondary, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                }
                .buttonStyle(.plain)
                .accessibilityHint("Compares these companies")
            }
            Button("Discover Stocks", action: onDiscover).frame(minHeight: 48)
        }
        .stockCard()
    }

    /// Equal-width columns pinned while scrolling; tap opens the company.
    private func companyHeader(_ state: ComparisonUiState, _ colors: StockColors) -> some View {
        HStack(alignment: .top, spacing: 8) {
            ForEach(state.columns, id: \.symbol) { column in
                Button { onOpen(column.symbol) } label: {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(column.symbol).font(.subheadline.weight(.semibold)).foregroundStyle(colors.primaryText).lineLimit(1)
                        Text(column.price).font(.caption2).foregroundStyle(colors.textSecondary).lineLimit(1)
                        if column.error != nil { Text("Partial data").font(.caption2).foregroundStyle(colors.cautionText) }
                    }
                    .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                }
                .buttonStyle(.plain)
                .accessibilityHint("Opens company details")
            }
        }
        .padding(.vertical, 4)
        .background(colors.appBackground)
        .overlay(alignment: .bottom) { Divider() }
    }

    @ViewBuilder private func sectionView(_ section: ComparisonSection, _ state: ComparisonUiState, _ colors: StockColors) -> some View {
        if !section.rows.isEmpty {
            VStack(alignment: .leading, spacing: 2) {
                Text(section.title).font(.title3.weight(.semibold)).accessibilityAddTraits(.isHeader)
                if let note = section.note { Text(note).font(.caption).foregroundStyle(colors.textSecondary) }
            }
            .padding(.top, 8)
            ForEach(section.rows, id: \.id) { row in metricRow(row, state, colors) }
        }
    }

    private func metricRow(_ row: ComparisonRow, _ state: ComparisonUiState, _ colors: StockColors) -> some View {
        let guide = state.guide(id: row.id)
        let expanded = state.isExpanded(id: row.id)
        return VStack(alignment: .leading, spacing: 4) {
            HStack {
                VStack(alignment: .leading, spacing: 1) {
                    Text(row.label).font(.subheadline.weight(.medium)).foregroundStyle(colors.textPrimary)
                    if let period = row.period { Text(period).font(.caption2).foregroundStyle(colors.textTertiary) }
                }
                Spacer()
                if guide != nil {
                    // Phase 2: the guided explanation opens inline below the values (columns stay aligned).
                    Button { client.comparison.toggleExplanation(id: row.id) } label: {
                        Label(expanded ? "Hide" : "Explain", systemImage: "info.circle").font(.caption).frame(minWidth: 44, minHeight: 44)
                    }
                    .accessibilityLabel(expanded ? "Hide explanation of \(row.label)" : "Explain \(row.label)")
                    .accessibilityValue(expanded ? "Expanded" : "Collapsed")
                } else {
                    Button { info = client.education(id: row.id) } label: { Image(systemName: "info.circle").frame(width: 44, height: 44) }
                        .accessibilityLabel("About \(row.label)")
                }
            }
            .id("metric-\(row.id)")
            HStack(alignment: .top, spacing: 8) {
                ForEach(Array(row.cells.enumerated()), id: \.offset) { i, cell in
                    VStack(alignment: .leading, spacing: 1) {
                        Text(cell.text + (cell.explanation != nil ? " ⓘ" : "")).font(.subheadline.monospacedDigit())
                            .foregroundStyle(cell.explanation != nil ? colors.textTertiary : toneColor(cell, colors)).lineLimit(2)
                        if let detail = cell.detail { Text(detail).font(.caption2).foregroundStyle(colors.textSecondary).lineLimit(2) }
                    }
                    .frame(maxWidth: .infinity, minHeight: 44, alignment: .topLeading)
                    .contentShape(Rectangle())
                    .onTapGesture { if let e = cell.explanation { why = ("\(row.label): \(state.columns.indices.contains(i) ? state.columns[i].symbol : "")", e) } }
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(row.accessibility(columns: state.columns))
            if let guide, expanded {
                GuidedMetricExplanationView(guide: guide, deeper: state.isDeeper(id: row.id),
                                            onToggleDeeper: { client.comparison.toggleDeeper(id: row.id) },
                                            onRelated: { client.comparison.openRelated(id: $0) },
                                            onClose: { client.comparison.toggleExplanation(id: row.id) })
                    .padding(.bottom, 4)
            }
            Divider()
        }
    }

    private struct Point: Identifiable {
        let id: String
        let index: Int
        let value: Double
        let symbol: String
    }

    @ViewBuilder private func performance(_ state: ComparisonUiState, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text("Share price change").font(.headline).accessibilityAddTraits(.isHeader)
            Text("Each line starts at 100 on the same day, so you compare percentage moves, not share prices. This is price only (no dividends) and says nothing about how the businesses performed.")
                .font(.caption).foregroundStyle(colors.textSecondary)
            Picker("Period", selection: Binding(get: { state.period.label }, set: { client.selectPeriod(label: $0) })) {
                ForEach(["1M", "3M", "1Y", "3Y", "5Y"], id: \.self) { Text($0).tag($0) }
            }.pickerStyle(.segmented)
            if state.chartLoading { ProgressView().accessibilityLabel("Loading price history") }
            else if let error = state.chartError { StockSectionMessage(message: error, actionTitle: "Try again") { client.comparison.retry() } }
            else if let chart = state.chart {
                let drawable = chart.series.filter { $0.error == nil }
                let points = drawable.flatMap { series in
                    series.values.enumerated().compactMap { i, v in (v as? NSNumber).map { Point(id: "\(series.symbol)-\(i)", index: i, value: $0.doubleValue, symbol: series.symbol) } }
                }
                if points.isEmpty { Text("Price history isn't available for this period.").font(.caption) }
                else {
                    Chart {
                        RuleMark(y: .value("Start", 100)).foregroundStyle(colors.textTertiary).lineStyle(StrokeStyle(lineWidth: 1, dash: [6, 4]))
                        ForEach(points) { point in
                            LineMark(x: .value("Day", point.index), y: .value("Growth of 100", point.value), series: .value("Company", point.symbol))
                                .foregroundStyle(by: .value("Company", point.symbol))
                                .lineStyle(by: .value("Company", point.symbol))
                        }
                    }
                    .chartXAxis(.hidden)
                    .chartYScale(domain: .automatic(includesZero: false))
                    .frame(height: 180)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Price change from 100 over \(chart.period.label): " + drawable.map { "\($0.symbol) \($0.change.map { String(format: "%+.1f%%", $0.doubleValue) } ?? "unavailable")" }.joined(separator: "; "))
                    if let first = chart.dates.first, let last = chart.dates.last {
                        HStack { Text(first).font(.caption2); Spacer(); Text(last).font(.caption2) }.foregroundStyle(colors.textTertiary)
                    }
                    ForEach(drawable, id: \.symbol) { s in
                        Text("\(s.symbol): \(s.change.map { String(format: "%+.1f%%", $0.doubleValue) } ?? "—")" + (s.note.map { " · \($0)" } ?? "")).font(.caption)
                    }
                }
                ForEach(chart.series.filter { $0.error != nil }, id: \.symbol) { Text("\($0.symbol): \($0.error ?? "")").font(.caption).foregroundStyle(colors.cautionText) }
                ForEach(chart.notes, id: \.self) { Text($0).font(.caption2).foregroundStyle(colors.textSecondary) }
            }
        }
        .stockCard()
    }
}

/// Company Comparison Phase 2 (free): one metric's guided explanation, inline under its row. Mirrors
/// Android's `GuidedMetricExplanation`; every sentence comes from the shared `ComparisonInterpretationEngine`.
struct GuidedMetricExplanationView: View {
    let guide: MetricInterpretation
    let deeper: Bool
    let onToggleDeeper: () -> Void
    let onRelated: (String) -> Void
    let onClose: () -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            HStack(alignment: .firstTextBaseline) {
                Text(guide.title).font(.headline).accessibilityAddTraits(.isHeader)
                Spacer()
                Text(guide.comparability.label).font(.caption).foregroundStyle(colors.textSecondary)
                    .padding(.horizontal, 8).padding(.vertical, 2)
                    .background(colors.surface, in: Capsule())
                    .accessibilityLabel("Comparability: \(guide.comparability.label)")
            }
            part("What it measures", colors) { Text(guide.definition) }
            part("Your comparison", colors) {
                Text(guide.observation)
                if let meaning = guide.meaning { Text(meaning) }
            }
            if !guide.keyCaveats.isEmpty { part("What to keep in mind", colors) { ForEach(guide.keyCaveats, id: \.self) { Text("• \($0)") } } }
            if deeper {
                part("Why it matters", colors) { Text(guide.whyItMatters) }
                if let calculation = guide.calculation { part("How it's calculated", colors) { Text(calculation) } }
                let more = guide.moreCaveats + guide.learnMore
                if !more.isEmpty { part("More to consider", colors) { ForEach(more, id: \.self) { Text("• \($0)") } } }
                part("Questions to research", colors) { ForEach(guide.questions, id: \.self) { Text("• \($0)") } }
            }
            part("Explore next", colors) {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 8) { relatedButtons }
                    VStack(alignment: .leading, spacing: 4) { relatedButtons }
                }
                if !deeper, let question = guide.questions.first { Text("• \(question)") }
            }
            HStack(spacing: 16) {
                Button(deeper ? "Show less" : "Learn more", action: onToggleDeeper).frame(minHeight: 44)
                    .accessibilityValue(deeper ? "Expanded" : "Collapsed")
                Button("Close", action: onClose).frame(minHeight: 44)
                    .accessibilityLabel("Close explanation of \(guide.label)")
            }
            .font(.subheadline)
            Text("Education, not investment advice. These explanations describe the numbers; they don't rank the companies.")
                .font(.caption2).foregroundStyle(colors.textTertiary)
        }
        .font(.subheadline)
        .padding(CGFloat(space.sm))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.surfaceSecondary, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
    }

    private var relatedButtons: some View {
        ForEach(guide.related, id: \.id) { related in
            Button(related.label) { onRelated(related.id) }
                .buttonStyle(.bordered).controlSize(.small).frame(minHeight: 44)
                .accessibilityLabel("Open \(related.label)")
        }
    }

    @ViewBuilder private func part<Content: View>(_ title: String, _ colors: StockColors, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption.weight(.semibold)).foregroundStyle(colors.textSecondary).accessibilityAddTraits(.isHeader)
            content()
        }
    }
}

/// "What can we learn from this comparison?": two or three grounded lines from the shared engine, never a ranking.
struct ComparisonLearningSummaryView: View {
    let insights: [ComparisonInsight]
    let onOpen: (String) -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text("What can we learn from this comparison?").font(.headline).accessibilityAddTraits(.isHeader)
            ForEach(Array(insights.enumerated()), id: \.offset) { _, insight in
                VStack(alignment: .leading, spacing: 2) {
                    Text(insight.category.title).font(.caption2).foregroundStyle(colors.textTertiary)
                    Text(insight.text).font(.subheadline)
                    if let id = insight.metricId {
                        Button("Explain this") { onOpen(id) }.font(.subheadline).frame(minHeight: 44)
                    }
                }
            }
            Text("Tap Explain on any metric to learn what it measures, what to keep in mind and what to look at next. Free for everyone.")
                .font(.caption).foregroundStyle(colors.textSecondary)
        }
        .stockCard()
    }
}

private struct PickTarget: Identifiable { let id: String }

private struct AddCompanySheet: View {
    let state: ComparisonUiState?
    let client: IosScreenerClient
    let replacing: String?
    let onDone: () -> Void
    @State private var query = ""
    @State private var results: [StockSearchResult] = []
    @State private var failed = false
    @State private var searching = false

    var body: some View {
        NavigationStack {
            List {
                if searching { ProgressView() }
                if failed { Text("Search isn't available right now. Try again.") }
                if !searching && !failed && !query.isEmpty && results.isEmpty { Text("No matching companies.") }
                ForEach(results, id: \.symbol) { stock in
                    let already = state?.selected.contains { $0.symbol.caseInsensitiveCompare(stock.symbol) == .orderedSame } ?? false
                    Button {
                        if let replacing { client.replaceInComparison(old: replacing, symbol: stock.symbol, name: stock.name) }
                        else { client.addToComparison(symbol: stock.symbol, name: stock.name) }
                        onDone()
                    } label: {
                        VStack(alignment: .leading, spacing: 1) {
                            Text(stock.name).lineLimit(1)
                            Text([stock.symbol, stock.exchange, already ? "already added" : nil].compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .disabled(already)
                }
            }
            .searchable(text: $query, prompt: "Search by name or ticker")
            .task(id: query) {
                guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { results = []; return }
                try? await Task.sleep(nanoseconds: 350_000_000) // debounce; a newer query cancels this task
                if Task.isCancelled { return }
                searching = true
                do { results = try await client.searchStocks(query: query); failed = false } catch { failed = true }
                searching = false
            }
            .navigationTitle(replacing.map { "Replace \($0)" } ?? "Add a company")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close", action: onDone) } }
        }
    }
}
