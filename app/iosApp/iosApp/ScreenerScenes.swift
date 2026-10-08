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
    @ObservationIgnored let client: IosScreenerClient
    @ObservationIgnored private var subscriptions: [any AccountSubscription] = []

    init(accounts: AccountViewModel, baseURL: @escaping () -> String = { BackendSettings.currentURL }) {
        client = IosScreenerClient(baseUrl: baseURL, account: accounts.client)
        subscriptions = [
            client.observeScreener { [weak self] in self?.screener = $0 },
            client.observeComparison { [weak self] in self?.comparison = $0 }
        ]
        client.screener.start()
    }
    deinit {
        subscriptions.forEach { $0.cancel() }
        client.close()
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

/// Owns navigation and actions for Discover Stocks; rendering is `DiscoverStocksScreen`.
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
            .navigationTitle("Discover")
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

struct DiscoverStocksScreen: View {
    let state: ScreenerUiState?
    let client: IosScreenerClient
    let onOpen: (String) -> Void
    let onCompare: () -> Void
    let onSignIn: () -> Void
    let watchlistAvailable: Bool
    let onWatchlist: (ScreenerRowView) -> Void
    let onPortfolio: (ScreenerRowView) -> Void
    @Environment(\.colorScheme) private var scheme
    @State private var showFilters = false
    @State private var showSaved = false
    @State private var info: MetricInfo?
    @State private var presetInfo: ScreenerPreset?

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.md)) {
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text("Discover Stocks").font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).accessibilityAddTraits(.isHeader)
                    Text("Find companies that match the financial characteristics you're interested in.")
                        .font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
                    if state?.sampleData == true {
                        Text("Sample data for development: generated and captured values, not live markets.").font(.caption).foregroundStyle(colors.textSecondary)
                    }
                }
                if let state { content(state, colors) } else { ProgressView() }
            }
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
            .padding(.bottom, 64)
        }
        .background(colors.appBackground)
        .safeAreaInset(edge: .bottom) {
            if let state, !state.selected.isEmpty {
                HStack {
                    Text(state.selected.map(\.symbol).joined(separator: ", ")).font(.subheadline).lineLimit(1)
                    Spacer()
                    Button(state.canCompare ? "Compare (\(state.selected.count))" : "Select 2 to compare", action: onCompare)
                        .buttonStyle(.borderedProminent).disabled(!state.canCompare)
                }
                .padding(.horizontal, CGFloat(space.screen)).padding(.vertical, CGFloat(space.sm))
                .background(.bar)
            }
        }
        .sheet(isPresented: $showFilters) { if let state { FilterSheet(state: state, client: client, onInfo: { info = $0 }, onDone: { showFilters = false }) } }
        .sheet(isPresented: $showSaved) { if let state { SavedScreensSheet(state: state, client: client, onDone: { showSaved = false }) } }
        .sheet(item: Binding(get: { info.map { IdentifiedInfo(info: $0) } }, set: { info = $0?.info })) { MetricInfoView(info: $0.info) }
        .alert(presetInfo?.name ?? "", isPresented: Binding(get: { presetInfo != nil }, set: { if !$0 { presetInfo = nil } })) {
            Button("Close", role: .cancel) { presetInfo = nil }
        } message: {
            if let preset = presetInfo {
                Text(([preset.description_] + preset.ranges.map { "• \(state?.definition(id: $0.metric)?.label ?? $0.metric): \(client.rangeText(range: $0))" }
                      + ["Data period: \(preset.dataPeriod)", "Missing data: \(preset.missingData)"] + preset.notes
                      + ["Shortcut, not a recommendation."]).joined(separator: "\n"))
            }
        }
        .alert("", isPresented: Binding(get: { state?.message != nil }, set: { if !$0 { client.screener.dismissMessage() } })) {
            Button("OK") { client.screener.dismissMessage() }
        } message: { Text(state?.message ?? "") }
    }

    @ViewBuilder private func content(_ state: ScreenerUiState, _ colors: StockColors) -> some View {
        if state.catalogLoading { ProgressView().accessibilityLabel("Loading screener") }
        else if let error = state.catalogError { Text(error).foregroundStyle(colors.textSecondary) }
        else {
            LazyVGrid(columns: [GridItem(.flexible(), spacing: CGFloat(space.sm)), GridItem(.flexible(), spacing: CGFloat(space.sm))], spacing: CGFloat(space.sm)) {
                ForEach(state.presets, id: \.id) { preset in
                    let selected = state.activePreset?.id == preset.id
                    Button { client.screener.selectPreset(id: preset.id) } label: {
                        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                            HStack(alignment: .top) {
                                Text(preset.name).font(.headline).foregroundStyle(colors.textPrimary).multilineTextAlignment(.leading)
                                Spacer(minLength: 0)
                                Button { presetInfo = preset } label: { Image(systemName: "info.circle") }.accessibilityLabel("\(preset.name) criteria")
                            }
                            Text(preset.description_).font(.caption).foregroundStyle(colors.textSecondary).multilineTextAlignment(.leading)
                        }
                        .frame(maxWidth: .infinity, minHeight: 96, alignment: .topLeading)
                        .padding(CGFloat(space.md))
                        .background(colors.surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                        .overlay(RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)).stroke(selected ? colors.primary : colors.border))
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint("Applies the \(preset.name) screen")
                }
            }
        }
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: CGFloat(space.xs)) {
                Button(state.activeFilterCount > 0 ? "Filters (\(state.activeFilterCount))" : "Filters") { showFilters = true }.buttonStyle(.bordered).disabled(state.metrics.isEmpty)
                Menu("Sort") {
                    Button("Market cap (high to low)") { client.sort(field: "MARKET_CAP", metric: nil, descending: true) }
                    Button("Market cap (low to high)") { client.sort(field: "MARKET_CAP", metric: nil, descending: false) }
                    Button("Name (A–Z)") { client.sort(field: "NAME", metric: nil, descending: false) }
                    Button("Daily change") { client.sort(field: "CHANGE_PERCENT", metric: nil, descending: true) }
                    ForEach(state.displayMetrics, id: \.id) { metric in
                        Button("\(metric.label) (high to low)") { client.sort(field: "METRIC", metric: metric.id, descending: true) }
                        Button("\(metric.label) (low to high)") { client.sort(field: "METRIC", metric: metric.id, descending: false) }
                    }
                }.buttonStyle(.bordered)
                if state.signedIn { Button("Saved (\(state.saved?.screens.count ?? 0))") { showSaved = true }.buttonStyle(.bordered) }
                else { Button("Sign in to save screens", action: onSignIn) }
            }
        }
        if let applied = state.applied, !applied.ranges.isEmpty || !applied.choices.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: CGFloat(space.xs)) {
                    ForEach(applied.ranges, id: \.metric) { range in
                        Button { client.screener.clearFilter(metric: range.metric); client.screener.apply() } label: {
                            Label("\(state.definition(id: range.metric)?.label ?? range.metric): \(client.rangeText(range: range))", systemImage: "xmark")
                        }.buttonStyle(.bordered).accessibilityLabel("Remove filter \(state.definition(id: range.metric)?.label ?? range.metric)")
                    }
                    Button("Reset all") { client.screener.resetAll() }
                }
            }
        }
        if state.applied == nil && !state.catalogLoading {
            Text("Start with a preset or filters. Tap ⓘ on a preset to see its exact criteria.").font(.caption).foregroundStyle(colors.textSecondary)
        } else if state.applied != nil {
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                if state.loading { ProgressView().accessibilityLabel("Loading results") }
                if let error = state.error { Text(error).foregroundStyle(colors.textSecondary); Button("Try again") { client.screener.retry() } }
                if !state.loading && state.error == nil {
                    Text("\(state.total) \(state.total == 1 ? "company matches" : "companies match")").font(.subheadline.weight(.semibold))
                }
                if let universe = state.universe {
                    Text(universe.complete ? "Evaluated across all \(universe.size) companies in the universe." : "Evaluated \(universe.evaluated) of \(universe.size) companies; the rest aren't included yet.")
                        .font(.caption).foregroundStyle(colors.textSecondary)
                }
                if state.excludedForMissingData > 0 {
                    Text("\(state.excludedForMissingData) left out because a filtered metric is missing or not meaningful for them.").font(.caption).foregroundStyle(colors.textSecondary)
                }
                ForEach(state.warnings + (state.activePreset?.notes ?? []), id: \.self) { Text($0).font(.caption).foregroundStyle(colors.textSecondary) }
            }
            if !state.loading && state.error == nil && state.rows.isEmpty {
                VStack(alignment: .leading) {
                    Text("No companies match these filters. Try widening a range or removing a filter.")
                    Button("Reset all") { client.screener.resetAll() }
                }
            }
        }
        ForEach(state.rows, id: \.symbol) { row in
            resultRow(row, state, colors)
                .onAppear { if row.symbol == state.rows.last?.symbol && state.hasMore { client.screener.loadMore() } }
        }
        if state.loadingMore { ProgressView() }
        if state.applied != nil {
            Text("Screens describe financial characteristics. They aren't recommendations to buy or sell.").font(.caption).foregroundStyle(colors.textTertiary)
        }
    }

    private func resultRow(_ row: ScreenerRowView, _ state: ScreenerUiState, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Button { onOpen(row.symbol) } label: {
                HStack {
                    VStack(alignment: .leading) {
                        Text(row.symbol).font(.headline).foregroundStyle(colors.textPrimary)
                        Text(row.name).font(.caption).foregroundStyle(colors.textSecondary).lineLimit(1)
                    }
                    Spacer()
                    VStack(alignment: .trailing) {
                        Text(row.price).font(.subheadline.weight(.semibold)).foregroundStyle(colors.textPrimary)
                        Text(row.change.text).font(.caption).foregroundStyle(toneColor(row.change, colors))
                    }
                }
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(row.accessibility)
            .accessibilityHint("Opens company details")
            HStack(alignment: .top, spacing: CGFloat(space.sm)) {
                ForEach(Array(row.metrics.enumerated()), id: \.offset) { index, pair in
                    let cell = pair.second as! MetricCell
                    Button {
                        if let id = state.displayMetrics[safe: index]?.id { info = client.education(id: id) }
                    } label: {
                        VStack(alignment: .leading) {
                            Text((pair.first as? String) ?? "").font(.caption2).foregroundStyle(colors.textSecondary).lineLimit(2)
                            Text(cell.text).font(.subheadline.weight(.semibold)).foregroundStyle(toneColor(cell, colors))
                        }.frame(maxWidth: .infinity, alignment: .leading)
                    }.buttonStyle(.plain)
                }
            }
            ForEach(row.notes + (row.stale ? ["Financial statements may be out of date."] : []), id: \.self) {
                Text($0).font(.caption2).foregroundStyle(colors.textSecondary)
            }
            HStack {
                Spacer()
                Button(row.selected ? "Comparing" : "Compare") { client.screener.toggleCompare(symbol: row.symbol, name: row.name) }
                    .buttonStyle(.bordered).tint(row.selected ? colors.primary : colors.textSecondary)
                    .accessibilityValue(row.selected ? "Selected for comparison" : "Not selected")
                Menu {
                    Button("Open company details") { onOpen(row.symbol) }
                    if watchlistAvailable { Button("Add to watchlist") { onWatchlist(row) } }
                    Button("Add to portfolio") { onPortfolio(row) }
                } label: { Image(systemName: "ellipsis.circle").frame(minWidth: 44, minHeight: 44) }
                    .accessibilityLabel("More actions for \(row.symbol)")
            }
        }
        .stockCard()
    }
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

private struct FilterSheet: View {
    let state: ScreenerUiState
    let client: IosScreenerClient
    let onInfo: (MetricInfo) -> Void
    let onDone: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section { Text("Leave a box empty for no limit. Nothing is searched until you tap Show results.").font(.caption) }
                Section("Company") {
                    ForEach([ChoiceField.country, .exchange, .sector, .industry], id: \.name) { field in
                        let values = state.choices[field] ?? []
                        if values.count > 1 {
                            let selected = state.draft.choices.first { $0.field == field }?.values ?? []
                            VStack(alignment: .leading) {
                                Text(field.label).font(.subheadline)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack {
                                        ForEach(values, id: \.self) { value in
                                            let on = selected.contains(value)
                                            Button(value) { client.setChoice(field: field.name, values: on ? selected.filter { $0 != value } : selected + [value]) }
                                                .buttonStyle(.bordered).tint(on ? .accentColor : .secondary)
                                                .accessibilityValue(on ? "Selected" : "")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                ForEach([MetricGroup.market, .valuation, .growth, .profitability, .health, .shareholder], id: \.name) { group in
                    let metrics = state.metrics.filter { $0.group == group && $0.filterable }
                    if !metrics.isEmpty {
                        Section(group.title) {
                            ForEach(metrics, id: \.id) { metric in
                                RangeRow(definition: metric, current: state.draft.ranges.first { $0.metric == metric.id }, client: client) { onInfo(client.education(id: metric.id)) }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Filters")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Reset all") { client.screener.resetAll() } }
                ToolbarItem(placement: .confirmationAction) { Button("Show results") { client.screener.apply(); onDone() } }
            }
        }
    }
}

/// Min/max inputs in display units (percent as 12.5, money in billions).
private struct RangeRow: View {
    let definition: MetricDefinition
    let current: RangeFilter?
    let client: IosScreenerClient
    let onInfo: () -> Void
    @State private var minText = ""
    @State private var maxText = ""

    private var scale: Double { definition.unit == .money ? 1e9 : 1 }
    private var unit: String {
        switch definition.unit {
        case .percent: "%"
        case .multiple: "×"
        case .money: "B"
        default: ""
        }
    }
    private func text(_ value: KotlinDouble?) -> String {
        guard let value else { return "" }
        let v = value.doubleValue / scale
        return v == v.rounded() ? String(Int(v)) : String(v)
    }

    var body: some View {
        VStack(alignment: .leading) {
            HStack {
                Text(definition.label + (unit.isEmpty ? "" : " (\(unit))"))
                Spacer()
                Button("What's this?", action: onInfo).font(.caption)
            }
            HStack {
                TextField("Min \(text(definition.suggestedMin))", text: $minText).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
                TextField("Max \(text(definition.suggestedMax))", text: $maxText).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
            }
            if let min = Double(minText), let max = Double(maxText), min > max {
                Text("Minimum is above maximum.").font(.caption).foregroundStyle(.red)
            }
        }
        .onAppear { minText = text(current?.min); maxText = text(current?.max) }
        .onChange(of: minText) { _, _ in push() }
        .onChange(of: maxText) { _, _ in push() }
    }

    private func push() {
        let min = Double(minText).map { $0 * scale }
        let max = Double(maxText).map { $0 * scale }
        client.setRange(metric: definition.id, min: min ?? 0, hasMin: min != nil, max: max ?? 0, hasMax: max != nil)
    }
}

private struct SavedScreensSheet: View {
    let state: ScreenerUiState
    let client: IosScreenerClient
    let onDone: () -> Void
    @State private var name = ""
    @State private var renaming: SavedScreen?
    @State private var newName = ""

    var body: some View {
        NavigationStack {
            List {
                if let saved = state.saved {
                    Section {
                        Text("\(saved.screens.count) of \(saved.limit) used\(saved.plus ? "" : " · StockSteps+ keeps up to 25"). Saved screens store filters, so results stay current.")
                            .font(.caption)
                    }
                }
                if state.applied != nil {
                    Section("Save current filters") {
                        TextField("Name this screen", text: $name)
                        Button("Save") { client.screener.saveScreen(name: name); name = "" }.disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
                Section("Saved") {
                    if state.saved?.screens.isEmpty ?? true { Text("No saved screens yet.") }
                    ForEach(state.saved?.screens ?? [], id: \.id) { screen in
                        Button { client.screener.applySaved(id: screen.id); onDone() } label: {
                            VStack(alignment: .leading) {
                                Text(screen.name)
                                Text("\(screen.query.activeFilterCount) filters").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        .swipeActions {
                            Button("Delete", role: .destructive) { client.screener.deleteScreen(id: screen.id) }
                            Button("Rename") { renaming = screen; newName = screen.name }
                        }
                    }
                }
            }
            .navigationTitle("Saved screens")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done", action: onDone) } }
            .alert("Rename screen", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
                TextField("Name", text: $newName)
                Button("Rename") { if let screen = renaming { client.screener.renameScreen(id: screen.id, name: newName) }; renaming = nil }
                Button("Cancel", role: .cancel) { renaming = nil }
            }
        }
    }
}

// MARK: - Compare Stocks

/// Owns Compare Stocks actions; the selection is shared with Discover and Company Details.
struct CompareStocksScene: View {
    let model: ScreenerModel
    let onOpenStock: (String) -> Void
    var onDiscover: () -> Void = {}

    var body: some View {
        CompareStocksScreen(state: model.comparison, client: model.client, onOpen: onOpenStock, onDiscover: onDiscover)
            .navigationTitle("Compare")
            .navigationBarTitleDisplayMode(.inline)
    }
}

struct CompareStocksScreen: View {
    let state: ComparisonUiState?
    let client: IosScreenerClient
    let onOpen: (String) -> Void
    let onDiscover: () -> Void
    @Environment(\.colorScheme) private var scheme
    @State private var adding = false
    @State private var info: MetricInfo?
    @State private var collapsed: Set<String> = []
    private let labelWidth: CGFloat = 132
    private let columnWidth: CGFloat = 116

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                    Text("Compare Stocks").font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).accessibilityAddTraits(.isHeader)
                    Text("See companies side by side. Differences describe the numbers; they don't say which is a better investment.")
                        .font(.caption).foregroundStyle(colors.textSecondary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(state?.selected ?? [], id: \.symbol) { company in
                                Button { client.comparison.remove(symbol: company.symbol) } label: { Label(company.symbol, systemImage: "xmark") }
                                    .buttonStyle(.bordered).accessibilityLabel("Remove \(company.name) from comparison")
                            }
                            if (state?.selected.count ?? 0) < 4 { Button("+ Add company") { adding = true }.buttonStyle(.bordered) }
                        }
                    }
                    if state?.sampleData == true { Text("Sample data for development, not live markets.").font(.caption).foregroundStyle(colors.textSecondary) }
                }
                if let state { content(state, colors) }
            }
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
        }
        .background(colors.appBackground)
        .sheet(isPresented: $adding) { AddCompanySheet(state: state, client: client) { adding = false } }
        .sheet(item: Binding(get: { info.map { IdentifiedInfo(info: $0) } }, set: { info = $0?.info })) { MetricInfoView(info: $0.info) }
        .alert("", isPresented: Binding(get: { state?.message != nil }, set: { if !$0 { client.comparison.dismissMessage() } })) {
            Button("OK") { client.comparison.dismissMessage() }
        } message: { Text(state?.message ?? "") }
    }

    @ViewBuilder private func content(_ state: ComparisonUiState, _ colors: StockColors) -> some View {
        if state.needsMore {
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                Text("Choose at least two companies to compare (up to 4). Add them here, from Discover Stocks or from a company's page.")
                Button("Discover Stocks", action: onDiscover).buttonStyle(.borderedProminent)
            }
        } else {
            if state.loading { ProgressView().accessibilityLabel("Loading comparison") }
            if let error = state.error { Text(error).foregroundStyle(colors.textSecondary); Button("Try again") { client.comparison.retry() } }
            if !state.observations.isEmpty {
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                    Text("What the numbers show").font(.headline).accessibilityAddTraits(.isHeader)
                    ForEach(state.observations, id: \.text) { o in
                        Text("• \(o.text)").font(.subheadline)
                        if let caveat = o.caveat { Text(caveat).font(.caption).foregroundStyle(colors.textSecondary).padding(.leading, 8) }
                    }
                    Text("Observations compare reported figures only. They aren't rankings or recommendations.").font(.caption2).foregroundStyle(colors.textTertiary)
                }
                .stockCard()
            }
            performance(state, colors)
            if !state.columns.isEmpty {
                // One horizontal scroll for the company header and every row keeps all columns aligned.
                ScrollView(.horizontal, showsIndicators: true) {
                    VStack(alignment: .leading, spacing: 0) {
                        companyHeader(state, colors)
                        ForEach(state.sections, id: \.title) { section in
                            if !section.rows.isEmpty {
                                let open = !collapsed.contains(section.title)
                                let width = labelWidth + columnWidth * CGFloat(state.columns.count)
                                Button { if open { collapsed.insert(section.title) } else { collapsed.remove(section.title) } } label: {
                                    HStack { Text(section.title).font(.headline); Spacer(); Image(systemName: open ? "minus" : "plus") }
                                        .frame(width: width, alignment: .leading).frame(minHeight: 44)
                                }
                                .buttonStyle(.plain)
                                .accessibilityAddTraits(.isHeader)
                                .accessibilityValue(open ? "Expanded" : "Collapsed")
                                if open {
                                    if let note = section.note { Text(note).font(.caption).foregroundStyle(colors.textSecondary).frame(width: width, alignment: .leading) }
                                    ForEach(section.rows, id: \.id) { row in metricRow(row, state, colors) }
                                }
                            }
                        }
                    }
                }
                ForEach(state.notes, id: \.self) { Text($0).font(.caption).foregroundStyle(colors.textSecondary) }
                Text("Not investment advice. Past results don't indicate future returns.").font(.caption2).foregroundStyle(colors.textTertiary)
            }
        }
    }

    private func companyHeader(_ state: ComparisonUiState, _ colors: StockColors) -> some View {
        HStack(alignment: .top, spacing: 0) {
            Color.clear.frame(width: labelWidth, height: 1)
            ForEach(state.columns, id: \.symbol) { column in
                Button { onOpen(column.symbol) } label: {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(column.symbol).font(.subheadline.weight(.semibold))
                        Text(column.name).font(.caption2).foregroundStyle(colors.textSecondary).lineLimit(2).multilineTextAlignment(.trailing)
                        Text(column.price).font(.caption)
                        Text(column.change.text).font(.caption2).foregroundStyle(toneColor(column.change, colors))
                        if let error = column.error { Text(error).font(.caption2).foregroundStyle(colors.textSecondary).lineLimit(3) }
                    }
                    .frame(width: columnWidth, alignment: .trailing)
                }
                .buttonStyle(.plain)
                .accessibilityHint("Opens company details")
            }
        }
        .padding(.vertical, 4)
    }

    private func metricRow(_ row: ComparisonRow, _ state: ComparisonUiState, _ colors: StockColors) -> some View {
        HStack(alignment: .center, spacing: 0) {
            Button { info = client.education(id: row.id) } label: {
                VStack(alignment: .leading, spacing: 1) {
                    Text(row.label + " ⓘ").font(.caption).foregroundStyle(colors.textPrimary).lineLimit(2)
                    if let period = row.period { Text(period).font(.caption2).foregroundStyle(colors.textTertiary).lineLimit(1) }
                }
                .frame(width: labelWidth, alignment: .leading)
            }
            .buttonStyle(.plain)
            ForEach(Array(row.cells.enumerated()), id: \.offset) { _, cell in
                Text(cell.text).font(.caption.monospacedDigit()).foregroundStyle(toneColor(cell, colors))
                    .frame(width: columnWidth, alignment: .trailing)
                    .lineLimit(2)
            }
        }
        .frame(minHeight: 44)
        .overlay(alignment: .bottom) { Divider() }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(row.accessibility(columns: state.columns))
    }

    private struct Point: Identifiable {
        let id: String
        let index: Int
        let value: Double
        let symbol: String
    }

    @ViewBuilder private func performance(_ state: ComparisonUiState, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text("Price performance").font(.headline).accessibilityAddTraits(.isHeader)
            Text("Each line starts at 100, so you compare returns, not share prices.").font(.caption).foregroundStyle(colors.textSecondary)
            Picker("Period", selection: Binding(get: { state.period.label }, set: { client.selectPeriod(label: $0) })) {
                ForEach(["1M", "3M", "1Y", "3Y", "5Y"], id: \.self) { Text($0).tag($0) }
            }.pickerStyle(.segmented)
            if state.chartLoading { ProgressView() }
            else if let error = state.chartError { Text(error).font(.caption) }
            else if let chart = state.chart {
                let drawable = chart.series.filter { $0.error == nil }
                let points = drawable.flatMap { series in
                    series.values.enumerated().compactMap { i, v in (v as? NSNumber).map { Point(id: "\(series.symbol)-\(i)", index: i, value: $0.doubleValue, symbol: series.symbol) } }
                }
                if points.isEmpty { Text("Price history isn't available for this period.").font(.caption) }
                else {
                    Chart(points) { point in
                        LineMark(x: .value("Day", point.index), y: .value("Growth of 100", point.value), series: .value("Company", point.symbol))
                            .foregroundStyle(by: .value("Company", point.symbol))
                            .lineStyle(by: .value("Company", point.symbol))
                    }
                    .chartXAxis(.hidden)
                    .chartYScale(domain: .automatic(includesZero: false))
                    .frame(height: 180)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Growth of 100 over \(chart.period.label): " + drawable.map { "\($0.symbol) \($0.change.map { String(format: "%.1f%%", $0.doubleValue) } ?? "unavailable")" }.joined(separator: "; "))
                }
                ForEach(chart.series.filter { $0.error != nil }, id: \.symbol) { Text("\($0.symbol): \($0.error ?? "")").font(.caption) }
                ForEach(chart.notes, id: \.self) { Text($0).font(.caption2).foregroundStyle(colors.textSecondary) }
            }
        }
        .stockCard()
    }
}

private struct AddCompanySheet: View {
    let state: ComparisonUiState?
    let client: IosScreenerClient
    let onDone: () -> Void
    @State private var query = ""
    @State private var results: [StockSearchResult] = []
    @State private var failed = false

    var body: some View {
        NavigationStack {
            List {
                if failed { Text("Search isn't available right now.") }
                ForEach(results, id: \.symbol) { stock in
                    let already = state?.selected.contains { $0.symbol.caseInsensitiveCompare(stock.symbol) == .orderedSame } ?? false
                    Button("\(stock.symbol) · \(stock.name)\(already ? " (added)" : "")") {
                        client.addToComparison(symbol: stock.symbol, name: stock.name)
                        onDone()
                    }.disabled(already)
                }
            }
            .searchable(text: $query, prompt: "Search by name or symbol")
            .task(id: query) {
                guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { results = []; return }
                try? await Task.sleep(nanoseconds: 350_000_000) // debounce; a newer query cancels this task
                if Task.isCancelled { return }
                do { results = try await client.searchStocks(query: query); failed = false } catch { failed = true }
            }
            .navigationTitle("Add a company")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close", action: onDone) } }
        }
    }
}
