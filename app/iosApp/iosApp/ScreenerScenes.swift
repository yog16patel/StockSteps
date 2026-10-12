import Charts
import Observation
import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

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

/// Compare Companies (Global UI Refinement Phase 5C), mirroring Compose `ComparisonScreen`: title (+ Sample badge) → selected companies
/// (⋮ = Replace / Remove) → examples while fewer than two → learning summary → metric sections → "More metrics" → share price chart →
/// history → "More tools" → observations → sources. Values sit in equal company columns only while every value and word fits a column
/// (shared `StockComparisonLayout`, same rule as Android); otherwise each metric lists one line per company, so four companies, compact
/// widths and large text never cut or break a value. Text and spoken labels come from the shared `ComparisonDisplay`.
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
    @Environment(\.dynamicTypeSize) private var typeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// nil = closed; "" = add; otherwise the symbol being replaced.
    @State private var picking: String?
    @State private var info: MetricInfo?
    @State private var why: (String, String)?
    @State private var width: CGFloat = 0
    /// Phase 5C.1: sections the user opened or closed relative to the shared defaults (Growth and Profitability open).
    @State private var toggled: Set<String> = []
    /// Sections whose "What to keep in mind" notes are open (final polish).
    @State private var openNotes: Set<String> = []
    /// "View all insights" in What the numbers show.
    @State private var showAllObservations = false
    private var display: ComparisonDisplay { ComparisonDisplay.shared }
    private func isOpen(_ title: String) -> Bool { display.expandedByDefault(title: title) != toggled.contains(title) }
    private func toggle(_ title: String) { if toggled.contains(title) { toggled.remove(title) } else { toggled.insert(title) } }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: CGFloat(space.md), pinnedViews: [.sectionHeaders]) {
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        StockTitleWithBadge {
                            Text(display.TITLE).font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).foregroundStyle(colors.textTitle).accessibilityAddTraits(.isHeader)
                        } badge: { if state?.sampleData == true { StockStatusBadge(text: "Sample", kind: .sample, size: .compact) } }
                        Text(display.SUBTITLE).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textSupporting)
                    }
                    if let state { content(state, colors) }
                }
                .padding(.horizontal, CGFloat(space.screen))
                .padding(.top, CGFloat(space.md))
                .padding(.bottom, CGFloat(space.xxl))
                .onGeometryChange(for: CGFloat.self) { $0.size.width - CGFloat(space.screen) * 2 } action: { width = $0 }
            }
            .background(colors.appBackground)
            // "Explore next" / "Explain this": bring the metric into view (no animation with Reduce Motion), then clear the request.
            .onChange(of: state?.focus) { _, focus in
                guard let focus else { return }
                // A related metric in a collapsed section: open it first, then scroll once it is laid out.
                if let state, let section = display.sectionOf(sections: state.sections, rowId: focus), !isOpen(section.title),
                   !section.advanced || state.showMore { toggle(section.title) }
                DispatchQueue.main.async {
                    if reduceMotion { proxy.scrollTo("metric-\(focus)", anchor: .center) }
                    else { withAnimation { proxy.scrollTo("metric-\(focus)", anchor: .center) } }
                    client.comparison.clearFocus()
                }
            }
        }
        .sheet(item: Binding(get: { picking.map { PickTarget(id: $0) } }, set: { picking = $0?.id })) { target in
            AddCompanySheet(state: state, client: client, replacing: target.id.isEmpty ? nil : target.id) { picking = nil }
        }
        .sheet(item: Binding(get: { info.map { IdentifiedInfo(info: $0) } }, set: { info = $0?.info })) { MetricInfoView(info: $0.info) }
        .alert(why?.0 ?? "", isPresented: Binding(get: { why != nil }, set: { if !$0 { why = nil } })) { Button("OK") { why = nil } } message: { Text(why?.1 ?? "") }
        .alert("", isPresented: Binding(get: { state?.message != nil }, set: { if !$0 { client.comparison.dismissMessage() } })) {
            Button("OK") { client.comparison.dismissMessage() }
        } message: { Text(state?.message ?? "") }
    }

    /// Shared `StockComparisonLayout` decision from the measured widest value/period word (and symbols) of the rows on screen.
    private func visibleRows(_ state: ComparisonUiState) -> [ComparisonRow] {
        state.sections.filter { (!$0.advanced || state.showMore) && isOpen($0.title) }.flatMap(\.rows)
    }

    private func sideBySide(_ state: ComparisonUiState) -> Bool {
        let rows = visibleRows(state)
        let font = StockStepsTheme.uiFont(type.numberLabel, typeSize: typeSize)
        let tokens = display.layoutTexts(rows: rows).flatMap { StockComparisonLayout.shared.tokens(text: $0) } + state.columns.map(\.symbol)
        let widest = Set(tokens).map { ($0 as NSString).size(withAttributes: [.font: font]).width }.max() ?? 0
        let noData = (display.NO_DATA as NSString).size(withAttributes: [.font: font]).width + CGFloat(dims.iconSmall) + CGFloat(space.xxs)
        return StockComparisonLayout.shared.sideBySide(widestToken: Float(max(widest, noData)), companies: Int32(state.columns.count),
                                                       availableWidth: Float(width - CGFloat(space.cardPadding) * 2), gap: Float(space.xs),
                                                       accessibilityText: typeSize.isAccessibilitySize)
    }

    @ViewBuilder private func content(_ state: ComparisonUiState, _ colors: StockColors) -> some View {
        selection(state, colors)
        if state.needsMore {
            examples(state, colors)
        } else {
            if state.loading {
                VStack(spacing: 0) { ForEach(0..<3, id: \.self) { _ in StockRowSkeleton() } }.stockCard(padding: 0, bordered: false)
                    .accessibilityElement(children: .ignore).accessibilityLabel("Loading comparison")
            }
            if let error = state.error {
                StockStateMessage(title: display.ERROR_TITLE, message: error, systemImage: "exclamationmark.triangle", isError: true,
                                  actionTitle: "Try again", action: { client.comparison.retry() })
                    .padding(.horizontal, CGFloat(space.cardPadding)).stockCard(padding: 0, bordered: false)
            }
            if !state.columns.isEmpty {
                let columns = sideBySide(state)
                if !state.insights.isEmpty { ComparisonLearningSummaryView(insights: state.insights) { client.comparison.openRelated(id: $0) } }
                // Metric sections in display order (nil = the "More metrics" button). The company header is pinned (a Section header) only from
                // the first open section whose values sit in unlabelled columns (5C.1 closure: bar rows and stacked lines name each company).
                let items = metricItems(state)
                let pinFrom = display.companyHeaderSection(openSections: items.compactMap { $0 }.filter { isOpen($0.title) }, sideBySide: columns)
                let split = pinFrom.flatMap { title in items.firstIndex { $0?.title == title } } ?? items.count
                Section { metricItemViews(Array(items[..<split]), state, columns, colors) }
                if split < items.count {
                    Section { metricItemViews(Array(items[split...]), state, columns, colors) } header: { companyHeader(state, colors) }
                }
            }
            performance(state, colors)
            if state.historyEnabled {
                HistoricalComparisonView(state: state, client: client, onUpgrade: onUpgrade, onSignIn: onSignIn,
                                         expanded: isOpen(display.HISTORY_TITLE), onToggle: { toggle(display.HISTORY_TITLE) })
            }
            if state.historyEnabled && !state.columns.isEmpty { tools(colors) }
            if !state.observations.isEmpty { observationsCard(ComparisonObservationsDisplay.shared.view(observations: state.observations, columns: state.columns), colors) }
            if !state.columns.isEmpty {
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text("Sources and notes").font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary).accessibilityAddTraits(.isHeader)
                    ForEach(state.notes, id: \.self) { Text($0).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary) }
                    if let asOf = state.asOf {
                        Text("Comparison prepared \(String(asOf.prefix(10))) \(String(asOf.dropFirst(11).prefix(5))) UTC.").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
                    }
                    Text("Education, not investment advice. Past results don't indicate future returns.").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
                }
            }
        }
    }

    /// "What the numbers show" (Comparison polish): up to three distinct insights (heading + concise text, metric caveat only when needed),
    /// "View all N insights" for the rest and one shared note — same shared `ComparisonObservationsDisplay` as Compose.
    private func observationsCard(_ view: ObservationsView, _ colors: StockColors) -> some View {
        let shared = ComparisonObservationsDisplay.shared
        let items = showAllObservations ? view.top + view.more : view.top
        return VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                Text(shared.TITLE).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textTitle).accessibilityAddTraits(.isHeader)
                Text(view.subtitle).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting)
            }
            ForEach(Array(items.enumerated()), id: \.element.metric) { index, item in
                if index > 0 { StockDivider() }
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(item.heading).font(StockStepsTheme.font(type.bodySemiBold, relativeTo: .subheadline)).foregroundStyle(colors.textTitle)
                    Text(item.text).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
                    if let caveat = item.caveat {
                        Text(caveat).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(item.spoken)
            }
            if let label = shared.toggle(total: Int32(view.total), open: showAllObservations) {
                Button { showAllObservations.toggle() } label: {
                    HStack(spacing: CGFloat(space.xs)) {
                        Text(label)
                        Image(systemName: showAllObservations ? "chevron.up" : "chevron.down").accessibilityHidden(true)
                    }
                    .font(StockStepsTheme.font(type.label, relativeTo: .subheadline)).foregroundStyle(colors.primaryText)
                    .frame(minHeight: CGFloat(dims.touchTarget)).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityValue(showAllObservations ? "Expanded" : "Collapsed")
            }
            Text(view.note).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .stockCard(bordered: false)
    }

    /// Metric sections in display order: the main sections, the "More metrics" button (nil), then the extra sections while shown.
    private func metricItems(_ state: ComparisonUiState) -> [ComparisonSection?] {
        let main: [ComparisonSection?] = state.sections.filter { !$0.advanced && !$0.rows.isEmpty }
        let extra: [ComparisonSection?] = state.showMore ? state.sections.filter { $0.advanced && !$0.rows.isEmpty } : []
        return main + (state.advancedCount > 0 ? [nil] : []) + extra
    }

    @ViewBuilder private func metricItemViews(_ items: [ComparisonSection?], _ state: ComparisonUiState, _ columns: Bool, _ colors: StockColors) -> some View {
        ForEach(items, id: \.?.title) { item in
            if let section = item {
                sectionView(section, state, columns, colors)
            } else {
                // Final polish: one caption says the extra sections are optional; label, live count and toggle unchanged.
                VStack(spacing: CGFloat(space.xs)) {
                    Button(display.moreMetricsLabel(count: state.advancedCount, showing: state.showMore)) { client.comparison.toggleMore() }
                        .buttonStyle(.stockSecondary).frame(maxWidth: .infinity)
                        .accessibilityValue(state.showMore ? "Expanded" : "Collapsed")
                    if !state.showMore {
                        Text(display.MORE_METRICS_NOTE).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting)
                            .multilineTextAlignment(.center).frame(maxWidth: .infinity)
                    }
                }
            }
        }
    }

    /// The chosen companies: logo, ticker, two-line name and listing; ⋮ holds Replace and Remove (44 pt target at any text size).
    private func selection(_ state: ComparisonUiState, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            // Final polish: at accessibility sizes "Add a company" goes under the title, so "Companies (2 of 4)" never breaks mid-word.
            let header = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 0)) : AnyLayout(HStackLayout(spacing: CGFloat(space.sm)))
            header {
                Text("Companies (\(state.selected.count) of \(client.maxCompanies()))").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline))
                    .foregroundStyle(colors.textTitle).accessibilityAddTraits(.isHeader).frame(maxWidth: .infinity, alignment: .leading)
                if state.canAdd {
                    Button("Add a company") { picking = "" }.font(StockStepsTheme.font(type.label, relativeTo: .subheadline)).foregroundStyle(colors.primaryText)
                        .frame(minHeight: CGFloat(dims.touchTarget)).padding(.trailing, CGFloat(space.sm))
                }
            }
            .frame(minHeight: CGFloat(dims.touchTarget))
            ForEach(Array(state.selected.enumerated()), id: \.element.symbol) { index, company in
                let column = state.columns.first { $0.symbol.caseInsensitiveCompare(company.symbol) == .orderedSame }
                if index > 0 { StockDivider().padding(.leading, CGFloat(dims.logoCompact) + CGFloat(space.sm)) }
                HStack(spacing: CGFloat(space.sm)) {
                    HStack(spacing: CGFloat(space.sm)) {
                        StockTickerAvatar(symbol: company.symbol, logoUrl: column?.logoUrl, size: CGFloat(dims.logoCompact))
                        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                            Text(company.symbol).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle)
                            Text(column?.name ?? company.name).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting).lineLimit(2)
                            if column?.error != nil { StockStatusBadge(text: display.PARTIAL, kind: .warning, size: .compact) }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(display.selectionSpoken(company: company, column: column))
                    Menu {
                        Button("Replace", systemImage: "arrow.left.arrow.right") { picking = company.symbol }
                        Button("Remove", systemImage: "trash", role: .destructive) { client.comparison.remove(symbol: company.symbol) }
                    } label: {
                        Image(systemName: "ellipsis").foregroundStyle(colors.iconSecondary).frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
                    }
                    .accessibilityLabel("Replace or remove \(column?.name ?? company.name)")
                }
                .padding(.vertical, CGFloat(space.xxs))
            }
            Text(state.crowdedHint ?? "Two or three companies are easiest to read on a phone.").font(StockStepsTheme.font(type.caption, relativeTo: .caption1))
                .foregroundStyle(colors.textSupporting).padding(.top, CGFloat(space.xs))
        }
        .padding(.leading, CGFloat(space.cardPadding)).padding(.trailing, CGFloat(space.xs)).padding(.vertical, CGFloat(space.sm))
        .background(colors.surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
    }

    private func examples(_ state: ComparisonUiState, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            Text(state.selected.isEmpty ? display.FEWER_TITLE : "Add one more company to compare").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline))
                .foregroundStyle(colors.textTitle).accessibilityAddTraits(.isHeader)
            Text(display.FEWER_MESSAGE).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
            VStack(spacing: 0) {
                ForEach(Array(state.examples.enumerated()), id: \.offset) { i, example in
                    if i > 0 { StockDivider() }
                    StockNavigationRow(title: example.title, detail: example.description_, hint: "Compares these companies") { client.useExample(index: Int32(i)) }
                }
            }
            .padding(.horizontal, CGFloat(space.md))
            .background(colors.surfaceSecondary, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
            Button(action: onDiscover) { Text("Discover Stocks").frame(maxWidth: .infinity) }.buttonStyle(.stockPrimary)
        }
        .stockCard(bordered: false)
    }

    /// Compact company columns pinned while the metrics scroll (side-by-side layout only); each opens the company.
    private func companyHeader(_ state: ComparisonUiState, _ colors: StockColors) -> some View {
        HStack(alignment: .top, spacing: CGFloat(space.xs)) {
            ForEach(state.columns, id: \.symbol) { column in
                Button { onOpen(column.symbol) } label: {
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        HStack(spacing: CGFloat(space.xs)) {
                            StockTickerAvatar(symbol: column.symbol, logoUrl: column.logoUrl, size: CGFloat(dims.iconSmall))
                            Text(column.symbol).font(StockStepsTheme.font(type.label, relativeTo: .subheadline)).foregroundStyle(colors.primaryText).lineLimit(1)
                        }
                        if column.price != ScreenerPresentationKt.MISSING_PRICE {
                            Text(verbatim: column.price).font(StockStepsTheme.font(type.tiny, relativeTo: .footnote)).foregroundStyle(colors.textSupporting).lineLimit(1)
                        }
                        if column.error != nil { StockStatusBadge(text: display.PARTIAL, kind: .warning, size: .compact) }
                    }
                    .frame(maxWidth: .infinity, minHeight: CGFloat(dims.touchTarget), alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel([column.name, column.symbol, column.price == ScreenerPresentationKt.MISSING_PRICE ? nil : "price \(column.price)",
                                     column.error == nil ? nil : display.PARTIAL.lowercased()].compactMap { $0 }.joined(separator: ", "))
                .accessibilityHint("Opens company details")
            }
        }
        .padding(.horizontal, CGFloat(space.cardPadding)).padding(.vertical, CGFloat(space.xxs))
        .background(colors.appBackground)
        .overlay(alignment: .bottom) { StockDivider() }
    }

    /// One metric section as a card (Phase 5C.1: collapsible). Header = one 44 pt button with title, metric count and chevron; when open, the shared
    /// period (once), the note and the bar note follow, then the rows.
    private func sectionView(_ section: ComparisonSection, _ state: ComparisonUiState, _ columns: Bool, _ colors: StockColors) -> some View {
        let open = isOpen(section.title)
        let shared = display.sharedPeriod(section: section)
        let notesShown = openNotes.contains(section.title)
        return VStack(alignment: .leading, spacing: 0) {
            Button { toggle(section.title) } label: {
                // Final polish: the shared period sits under the title inside the header (no row of its own); at accessibility sizes the metric
                // count moves under the title instead of squeezing it to one word per line.
                let stackSummary = typeSize.isAccessibilitySize
                HStack(spacing: CGFloat(space.sm)) {
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        Text(section.title).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textTitle)
                        if open, let shared {
                            Text(display.shortPeriod(text: shared)).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
                        }
                        if !open && stackSummary {
                            Text(display.sectionSummary(section: section)).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    if !open && !stackSummary { Text(display.sectionSummary(section: section)).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta) }
                    Image(systemName: open ? "chevron.up" : "chevron.down").font(StockStepsTheme.font(type.caption, relativeTo: .caption1).weight(.semibold))
                        .foregroundStyle(colors.iconSecondary)
                }
                .frame(minHeight: CGFloat(dims.touchTarget))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(display.sectionLabel(section: section, open: open))
            .accessibilityValue(open ? "Expanded" : "Collapsed")
            .accessibilityHint(open ? "Collapses the section" : "Expands the section")
            .accessibilityAddTraits([.isHeader, .isButton])
            if open {
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    // Final polish: the section's notes sit behind one "What to keep in mind" control instead of always-visible paragraphs.
                    let notes = display.sectionNotes(section: section)
                    if !notes.isEmpty {
                        let shown = notesShown
                        Button { if shown { openNotes.remove(section.title) } else { openNotes.insert(section.title) } } label: {
                            HStack(spacing: CGFloat(space.xs)) {
                                Image(systemName: "lightbulb").accessibilityHidden(true)
                                Text(display.NOTES_LABEL)
                                Image(systemName: shown ? "chevron.up" : "chevron.down").accessibilityHidden(true)
                            }
                            .font(StockStepsTheme.font(type.label, relativeTo: .subheadline)).foregroundStyle(colors.primaryText)
                            .frame(minHeight: CGFloat(dims.touchTarget)).contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(display.notesSpoken(section: section, open: shown))
                        if shown {
                            ForEach(notes, id: \.self) { Text($0).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting) }
                        }
                    }
                }
                // 5C.1 closure: only expanded notes get a gap below them; the first metric follows the header or notes control directly.
                .padding(.bottom, notesShown && !display.sectionNotes(section: section).isEmpty ? CGFloat(space.sm) : 0)
                ForEach(Array(section.rows.enumerated()), id: \.element.id) { index, row in
                    metricRow(row, state, columns, showPeriod: shared == nil, colors)
                    if index < section.rows.count - 1 { StockDivider() }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, CGFloat(space.cardPadding)).padding(.vertical, CGFloat(space.xs))
        .background(colors.surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
    }

    private func valueColor(_ cell: MetricCell, _ colors: StockColors) -> Color {
        cell.explanation != nil ? colors.textTertiary : toneColor(cell, colors)
    }

    /// One metric: name and period, Explain / ⓘ, then the values — in equal columns or one line per company with its logo and ticker. A missing
    /// value reads "No data" with a real info control ("Market cap unavailable for TSLA. Explain why."), never "N/A ⓘ".
    private func metricRow(_ row: ComparisonRow, _ state: ComparisonUiState, _ columns: Bool, showPeriod: Bool, _ colors: StockColors) -> some View {
        let bars = display.bars(row: row)
        let guide = state.guide(id: row.id)
        let expanded = state.isExpanded(id: row.id)
        let pairs = Array(zip(state.columns, row.cells))
        return VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            // Centred (5C.1 closure, as Compose): the ⓘ's 44 pt frame no longer sits below a one-line title.
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(row.label).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle)
                    if showPeriod, let period = row.period {
                        Text(display.shortPeriod(text: period)).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                // One info action per metric (final polish): guided metrics toggle the inline explanation, others open the info sheet. 44 pt target.
                Button { if guide != nil { client.comparison.toggleExplanation(id: row.id) } else { info = client.education(id: row.id) } } label: {
                    Image(systemName: expanded ? "info.circle.fill" : "info.circle").foregroundStyle(colors.primaryText)
                        .frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
                }
                .accessibilityLabel(display.infoLabel(row: row, guided: guide != nil, expanded: expanded))
                .accessibilityValue(guide == nil ? "" : expanded ? "Expanded" : "Collapsed")
            }
            .id("metric-\(row.id)")
            if let bars {
                barLines(row, state, bars, colors)
            } else if columns {
                HStack(alignment: .top, spacing: CGFloat(space.xs)) {
                    ForEach(Array(pairs.enumerated()), id: \.offset) { _, pair in
                        let (column, cell) = pair
                        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                            HStack(spacing: CGFloat(space.xxs)) {
                                Text(verbatim: display.cellText(cell: cell)).font(StockStepsTheme.font(type.numberLabel, relativeTo: .footnote)).foregroundStyle(valueColor(cell, colors))
                                if cell.explanation != nil { Image(systemName: "info.circle").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.iconSecondary) }
                            }
                            if let detail = cell.detail { Text(verbatim: display.shortPeriod(text: detail)).font(StockStepsTheme.font(type.tiny, relativeTo: .footnote)).foregroundStyle(colors.textSupporting) }
                        }
                        .frame(maxWidth: .infinity, minHeight: cell.explanation != nil ? CGFloat(dims.touchTarget) : 0, alignment: .topLeading)
                        .contentShape(Rectangle())
                        .onTapGesture { if let e = cell.explanation { why = ("\(row.label) · \(column.symbol)", e) } }
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(display.cellSpoken(column: column, row: row, cell: cell))
                        .accessibilityAddTraits(cell.explanation != nil ? .isButton : [])
                        .accessibilityHint(cell.explanation != nil ? display.missingAction(row: row, column: column) : "")
                    }
                }
            } else {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(pairs.enumerated()), id: \.offset) { _, pair in
                        let (column, cell) = pair
                        HStack(spacing: CGFloat(space.sm)) {
                            HStack(spacing: CGFloat(space.sm)) {
                                StockTickerAvatar(symbol: column.symbol, logoUrl: column.logoUrl, size: CGFloat(dims.icon))
                                Text(column.symbol).font(StockStepsTheme.font(type.label, relativeTo: .subheadline)).foregroundStyle(colors.textTitle)
                                VStack(alignment: .trailing, spacing: CGFloat(space.xxs)) {
                                    Text(verbatim: display.cellText(cell: cell)).font(StockStepsTheme.font(type.numberLabel, relativeTo: .footnote))
                                        .foregroundStyle(valueColor(cell, colors)).multilineTextAlignment(.trailing)
                                    if let detail = cell.detail {
                                        Text(verbatim: display.shortPeriod(text: detail)).font(StockStepsTheme.font(type.tiny, relativeTo: .footnote)).foregroundStyle(colors.textSupporting).multilineTextAlignment(.trailing)
                                    }
                                }
                                .frame(maxWidth: .infinity, alignment: .trailing)
                            }
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel(display.cellSpoken(column: column, row: row, cell: cell))
                            if let e = cell.explanation {
                                Button { why = ("\(row.label) · \(column.symbol)", e) } label: {
                                    Image(systemName: "info.circle").foregroundStyle(colors.primaryText).frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
                                }
                                .accessibilityLabel(display.missingAction(row: row, column: column))
                            }
                        }
                        .padding(.vertical, CGFloat(space.xs))
                    }
                }
            }
            if let guide, expanded {
                GuidedMetricExplanationView(guide: guide, deeper: state.isDeeper(id: row.id),
                                            onToggleDeeper: { client.comparison.toggleDeeper(id: row.id) },
                                            onRelated: { client.comparison.openRelated(id: $0) },
                                            onClose: { client.comparison.toggleExplanation(id: row.id) })
                    .padding(.bottom, CGFloat(space.xxs))
            }
        }
        // Final polish: a hairline gap above every row (the 44 pt title row already gives room) and `xs` below, before the divider.
        .padding(.top, CGFloat(space.xxs)).padding(.bottom, CGFloat(space.xs))
    }

    /// Compact comparison bars (Phase 5C.1, percentage metrics only; same shared `ComparisonDisplay.bars` as Android): ticker, a bar scaled to the
    /// longest value, then the value and short period. Negatives keep the minus sign and the negative colour; missing values show "No data" + reason.
    private func barLines(_ row: ComparisonRow, _ state: ComparisonUiState, _ bars: [Any], _ colors: StockColors) -> some View {
        let pairs = Array(zip(state.columns, row.cells))
        return VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            ForEach(Array(pairs.enumerated()), id: \.offset) { i, pair in
                let (column, cell) = pair
                let bar = i < bars.count ? bars[i] as? ComparisonBar : nil
                HStack(spacing: CGFloat(space.xs)) {
                    // Final polish: ticker and short period on the left, value on the right, then a full-width bar — nothing competes for width.
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        HStack(alignment: .lastTextBaseline, spacing: CGFloat(space.sm)) {
                            VStack(alignment: .leading, spacing: 0) {
                                Text(column.symbol).font(StockStepsTheme.font(type.label, relativeTo: .subheadline)).foregroundStyle(colors.textTitle)
                                if let detail = cell.detail {
                                    Text(verbatim: display.shortPeriod(text: detail)).font(StockStepsTheme.font(type.tiny, relativeTo: .footnote)).foregroundStyle(colors.textMeta)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            Text(verbatim: display.cellText(cell: cell)).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .footnote)).foregroundStyle(valueColor(cell, colors))
                        }
                        GeometryReader { proxy in
                            ZStack(alignment: .leading) {
                                Capsule().fill(colors.surfaceSecondary)
                                if let bar, bar.fraction > 0 {
                                    Capsule().fill(bar.negative ? colors.negativeText : colors.primaryText).frame(width: proxy.size.width * CGFloat(bar.fraction))
                                }
                            }
                        }
                        .frame(height: CGFloat(space.xs))
                    }
                    .padding(.vertical, CGFloat(space.xxs))
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(display.cellSpoken(column: column, row: row, cell: cell))
                    if let e = cell.explanation {
                        Button { why = ("\(row.label) · \(column.symbol)", e) } label: {
                            Image(systemName: "info.circle").foregroundStyle(colors.primaryText).frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
                        }
                        .accessibilityLabel(display.missingAction(row: row, column: column))
                    }
                }
            }
        }
    }

    /// Research checklist, AI assistant and Discover Stocks as one grouped list (were two separate cards).
    private func tools(_ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: "More tools")
            VStack(spacing: 0) {
                StockNavigationRow(title: "Research checklist", detail: "Guided questions and private notes. Free; StockSteps+ adds advanced research and reports.",
                                   icon: "checklist", action: onResearch)
                StockDivider()
                StockNavigationRow(title: "Ask StockSteps AI", detail: "Answers use only the verified figures on this screen, with every number linked to its data.",
                                   icon: "lightbulb", badge: "StockSteps+", action: onAskAi)
                StockDivider()
                StockNavigationRow(title: "Discover Stocks", detail: "Find more companies to compare", icon: "magnifyingglass", action: onDiscover)
            }
            .padding(.horizontal, CGFloat(space.md))
            .background(colors.surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
        }
    }

    private struct Point: Identifiable {
        let id: String
        let index: Int
        let value: Double
        let symbol: String
    }

    @ViewBuilder private func performance(_ state: ComparisonUiState, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            Text("Share price change").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textTitle).accessibilityAddTraits(.isHeader)
            Text("Each line starts at 100 on the same day, so you compare percentage moves, not share prices. This is price only (no dividends) and says nothing about how the businesses performed.")
                .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting)
            StockPillSelector(options: PerformancePeriod.entries, selected: state.period, label: { $0.label }) { client.selectPeriod(label: $0.label) }
            if state.chartLoading {
                StockSkeleton(width: max(width - CGFloat(space.cardPadding) * 2, 1), height: 180).accessibilityLabel("Loading price history")
            } else if let error = state.chartError {
                // A chart failure stays in this card; the comparison above and below is unaffected.
                StockStateMessage(title: ComparisonDisplay.shared.CHART_ERROR_TITLE, message: error, systemImage: "exclamationmark.triangle", isError: true,
                                  actionTitle: "Try again", action: { client.comparison.retry() })
                Text(ComparisonDisplay.shared.CHART_ERROR_NOTE).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting)
            } else if let chart = state.chart {
                let drawable = chart.series.filter { $0.error == nil }
                let points = drawable.flatMap { series in
                    series.values.enumerated().compactMap { i, v in (v as? NSNumber).map { Point(id: "\(series.symbol)-\(i)", index: i, value: $0.doubleValue, symbol: series.symbol) } }
                }
                if points.isEmpty { Text("Price history isn't available for this period.").font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody) }
                else {
                    // Final polish: each company keeps the shared StockChartSeriesStyle colour and dash (same as Compose and the legend below).
                    let symbols = drawable.map(\.symbol)
                    let reference = StockStepsTheme.chartReference(scheme)
                    Chart {
                        RuleMark(y: .value("Start", 100)).foregroundStyle(StockStepsTheme.color(reference.color)).lineStyle(StockStepsTheme.strokeStyle(reference))
                        ForEach(points) { point in
                            let line = StockStepsTheme.chartLine(symbols.firstIndex(of: point.symbol) ?? 0, scheme)
                            LineMark(x: .value("Day", point.index), y: .value("Growth of 100", point.value), series: .value("Company", point.symbol))
                                .foregroundStyle(StockStepsTheme.color(line.color))
                                .lineStyle(StockStepsTheme.strokeStyle(line))
                        }
                    }
                    .chartLegend(.hidden)
                    .chartXAxis(.hidden)
                    .chartYScale(domain: .automatic(includesZero: false))
                    .frame(height: 180)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Price change from 100 over \(chart.period.label): " + drawable.map { s in
                        "\(s.symbol) " + (s.change.map { v in let d = v.doubleValue; return (d > 0 ? "up " : d < 0 ? "down " : "") + MetricFormatter.shared.decimals(value: abs(d), places: 1) + "%" } ?? "unavailable")
                    }.joined(separator: "; "))
                    if let first = chart.dates.first, let last = chart.dates.last {
                        // Start and end dates side by side; stacked when both don't fit (accessibility sizes broke "2025-10-0 / 7").
                        ViewThatFits(in: .horizontal) {
                            HStack { Text(verbatim: first).fixedSize(); Spacer(); Text(verbatim: last).fixedSize() }
                            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) { Text(verbatim: "From \(first)"); Text(verbatim: "To \(last)") }
                        }
                        .font(StockStepsTheme.font(type.tiny, relativeTo: .footnote)).foregroundStyle(colors.textTertiary)
                    }
                    // Legend: swatch + ticker + change as whole items (same shared formatter and line styles as Android), then the reference line;
                    // they move to the next line, never split.
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: CGFloat(space.md)) { legend(drawable, colors) }
                        VStack(alignment: .leading, spacing: CGFloat(space.xs)) { legend(drawable, colors) }
                    }
                    HStack(spacing: CGFloat(space.xs)) {
                        StockChartSwatch(line: reference)
                        Text("Start (100)").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
                    }
                    .accessibilityElement(children: .combine)
                    ForEach(drawable.filter { $0.note != nil }, id: \.symbol) { s in
                        Text("\(s.symbol): \(s.note ?? "")").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting)
                    }
                }
                ForEach(chart.series.filter { $0.error != nil }, id: \.symbol) {
                    Text("\($0.symbol): \($0.error ?? "")").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.cautionText)
                }
                ForEach(chart.notes, id: \.self) { Text($0).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting) }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .stockCard(bordered: false)
    }

    @ViewBuilder private func legend(_ series: [PerformanceSeries], _ colors: StockColors) -> some View {
        ForEach(Array(series.enumerated()), id: \.element.symbol) { index, s in
            let cell = s.change.map { MetricFormatter.shared.change(percent: $0) }
            HStack(spacing: CGFloat(space.xs)) {
                StockChartSwatch(line: StockStepsTheme.chartLine(index, scheme))
                Text(s.symbol).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textTitle)
                Text(verbatim: cell?.text ?? ComparisonDisplay.shared.NO_DATA).font(StockStepsTheme.font(type.numberLabel, relativeTo: .footnote))
                    .foregroundStyle(cell.map { toneColor($0, colors) } ?? colors.textTertiary)
            }
            .accessibilityElement(children: .combine)
        }
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
    @State private var showAll = false

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            HStack(spacing: CGFloat(space.sm)) {
                Image(systemName: "lightbulb.fill").foregroundStyle(colors.cautionText).accessibilityHidden(true)
                Text("What can we learn from this comparison?").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textTitle)
                    .accessibilityAddTraits(.isHeader)
            }
            // Final polish: the first insight, then "Show all N insights" for the rest and the footer.
            ForEach(Array((showAll ? insights : Array(insights.prefix(Int(ComparisonDisplay.shared.INSIGHTS_PREVIEW)))).enumerated()), id: \.offset) { _, insight in
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(insight.category.title).font(StockStepsTheme.font(type.tiny, relativeTo: .footnote)).foregroundStyle(colors.textTertiary)
                    Text(insight.text).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
                    if let id = insight.metricId {
                        Button("Explain this") { onOpen(id) }.font(StockStepsTheme.font(type.label, relativeTo: .subheadline)).foregroundStyle(colors.primaryText)
                            .frame(minHeight: CGFloat(dims.touchTarget))
                    }
                }
            }
            if let toggle = ComparisonDisplay.shared.insightsToggle(total: Int32(insights.count), open: showAll) {
                Button(toggle) { showAll.toggle() }.font(StockStepsTheme.font(type.label, relativeTo: .subheadline)).foregroundStyle(colors.primaryText)
                    .frame(minHeight: CGFloat(dims.touchTarget)).accessibilityValue(showAll ? "Expanded" : "Collapsed")
            }
            if showAll || insights.count <= Int(ComparisonDisplay.shared.INSIGHTS_PREVIEW) {
                Text("Tap ⓘ on any metric to learn what it measures, what to keep in mind and what to look at next. Free for everyone.")
                    .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(CGFloat(space.cardPadding))
        .background(colors.surfaceSecondary, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
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
    /// The text the latest finished search was for: "No matching companies." only appears for that text, not while typing (Phase 5C).
    @State private var searched = ""

    var body: some View {
        NavigationStack {
            List {
                if searching { ProgressView() }
                if failed { Text("Search isn't available right now. Try again.") }
                if !searching && !failed && !query.isEmpty && searched == query && results.isEmpty { Text("No matching companies.") }
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
                searched = query
            }
            .navigationTitle(replacing.map { "Replace \($0)" } ?? "Add a company")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close", action: onDone) } }
        }
    }
}
