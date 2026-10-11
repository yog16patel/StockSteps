import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

/// Discover Stocks (Global UI Refinement Phase 5B), mirroring Compose `ScreenerScreen`: title + help → (no search yet) preset cards and
/// "Create custom screen" → (searched) filter bar, applied filters, results header with sort, one grouped list of compact quote rows → more tools
/// → disclaimer. Text, categories and spoken labels come from the shared `ScreenerDisplay`; layout decisions from `StockCardGridLayout` and
/// `StockQuoteRowLayout`. Presets are shortcuts with visible criteria, never recommendations. Loading, no matches and failures are separate states.
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
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var showFilters = false
    @State private var showSaved = false
    @State private var showHelp = false
    @State private var showMetricsHelp = false
    @State private var expanded = false
    @State private var info: MetricInfo?
    @State private var width: CGFloat = 0
    private var display: ScreenerDisplay { ScreenerDisplay.shared }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                header(colors)
                if let state { content(state, colors) } else { ProgressView().padding(.top, CGFloat(space.lg)).accessibilityLabel("Loading screener") }
            }
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.top, CGFloat(space.md))
            .padding(.bottom, CGFloat(space.xxl))
            .onGeometryChange(for: CGFloat.self) { $0.size.width - CGFloat(space.screen) * 2 } action: { width = $0 }
        }
        .background(colors.appBackground)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            // Above the content (never over it), so the last row, tools and disclaimer stay reachable.
            if let state, !state.selected.isEmpty { compareBar(state, colors) }
        }
        .sheet(isPresented: $showFilters) {
            if let state { DiscoverFilterEditor(state: state, client: client, onInfo: { info = $0 }, onDone: { showFilters = false }) }
        }
        .sheet(isPresented: $showSaved) { if let state { SavedScreensSheet(state: state, client: client, onDone: { showSaved = false }) } }
        .sheet(isPresented: $showHelp) {
            if let state { ScreeningHelpView(state: state, onPreset: { id in showHelp = false; client.screener.selectPreset(id: id) }, onDone: { showHelp = false }) }
        }
        .sheet(isPresented: $showMetricsHelp) {
            if let state { MetricsInfoList(infos: state.displayMetrics.map { client.education(id: $0.id) }) }
        }
        .sheet(item: Binding(get: { info.map { IdentifiedInfo(info: $0) } }, set: { info = $0?.info })) { MetricInfoView(info: $0.info) }
        .alert("", isPresented: Binding(get: { state?.message != nil }, set: { if !$0 { client.screener.dismissMessage() } })) {
            Button("OK") { client.screener.dismissMessage() }
        } message: { Text(state?.message ?? "") }
    }

    private func header(_ colors: StockColors) -> some View {
        HStack(alignment: .top, spacing: CGFloat(space.sm)) {
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                Text(display.TITLE).font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).foregroundStyle(colors.textTitle)
                    .accessibilityAddTraits(.isHeader)
                Text(display.SUBTITLE).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textSupporting)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button { showHelp = true } label: {
                Image(systemName: "questionmark.circle").font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3)).foregroundStyle(colors.iconSecondary)
                    .frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
            }
            .accessibilityLabel("How screening works")
        }
    }

    @ViewBuilder private func content(_ state: ScreenerUiState, _ colors: StockColors) -> some View {
        let active = display.activeFilters(query: state.applied)
        if state.catalogLoading {
            VStack(spacing: CGFloat(space.sm)) { ForEach(0..<2, id: \.self) { _ in StockSkeleton(width: max(width, 1), height: CGFloat(space.xxxl) * 3) } }
                .padding(.top, CGFloat(space.lg)).accessibilityElement(children: .ignore).accessibilityLabel("Loading screener")
        } else if let error = state.catalogError {
            StockStateMessage(title: display.CATALOG_ERROR_TITLE, message: error, systemImage: "exclamationmark.triangle", isError: true,
                              actionTitle: "Try again", action: { client.screener.start() })
        } else if state.applied == nil {
            presetGrid(state.presets, colors).padding(.top, CGFloat(space.lg))
            Button(display.CUSTOM_SCREEN) { showFilters = true }.buttonStyle(.stockPrimary).frame(maxWidth: .infinity)
                .disabled(state.metrics.isEmpty).padding(.top, CGFloat(space.md))
        } else {
            filterBar(active.count, colors).padding(.top, CGFloat(space.lg))
            if !active.isEmpty { activeFilters(active, colors).padding(.top, CGFloat(space.sm)) }
            resultsHeader(state, colors).padding(.top, CGFloat(space.lg))
            results(state, active: active, colors)
        }
        if state.loadingMore { ProgressView().frame(maxWidth: .infinity).padding(.top, CGFloat(space.sm)).accessibilityLabel("Loading more results") }
        if !state.catalogLoading && state.catalogError == nil { tools(state, colors).padding(.top, CGFloat(space.xxl)) }
        if state.applied != nil {
            Text(display.DISCLAIMER).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
                .padding(.top, CGFloat(space.lg))
        }
    }

    // MARK: Presets

    private func presetIcon(_ id: String) -> String {
        switch id {
        case ScreenerDefinitions.shared.GROWING: "chart.line.uptrend.xyaxis"
        case ScreenerDefinitions.shared.DIVIDEND: "dollarsign.circle"
        case ScreenerDefinitions.shared.STRONG: "shield"
        default: "tag"
        }
    }

    /// Two columns while every title word fits one column (shared `StockCardGridLayout`, same rule as Android), else one; heights follow content.
    private func presetGrid(_ presets: [ScreenerPreset], _ colors: StockColors) -> some View {
        let font = StockStepsTheme.uiFont(type.cardTitle, typeSize: typeSize)
        let widest = presets.flatMap { StockCardGridLayout.shared.words(title: $0.name) }
            .map { ($0 as NSString).size(withAttributes: [.font: font]).width }.max() ?? 0
        let columns = Int(StockCardGridLayout.shared.columns(widestTitleWord: Float(widest), availableWidth: Float(width), gap: Float(space.sm),
                                                              cardPadding: Float(space.cardPaddingCompact), accessibilityText: typeSize.isAccessibilitySize))
        let rows = stride(from: 0, to: presets.count, by: columns).map { Array(presets[$0..<min($0 + columns, presets.count)]) }
        return VStack(spacing: CGFloat(space.sm)) {
            ForEach(rows.indices, id: \.self) { index in
                HStack(alignment: .top, spacing: CGFloat(space.sm)) {
                    ForEach(rows[index], id: \.id) { presetCard($0, colors) }
                    ForEach(0..<(columns - rows[index].count), id: \.self) { _ in Color.clear.frame(maxWidth: .infinity) }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private func presetCard(_ preset: ScreenerPreset, _ colors: StockColors) -> some View {
        Button { client.screener.selectPreset(id: preset.id) } label: {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                StockIconTile(systemName: presetIcon(preset.id), container: colors.primaryContainer, content: colors.primaryText)
                Text(preset.name).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textTitle)
                    .multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
                Text(display.presetSummary(preset: preset)).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting)
                    .multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
            }
            .padding(CGFloat(space.cardPaddingCompact))
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(colors.surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint("Shows companies matching \(preset.name)")
    }

    // MARK: Filters

    /// "3 filters" (shows or hides the full filter list) and "Edit filters"; they move onto two lines when they don't fit side by side.
    private func filterBar(_ count: Int, _ colors: StockColors) -> some View {
        let pill = Button { expanded.toggle() } label: {
            HStack(spacing: CGFloat(space.xs)) {
                Text(display.filterCount(count: Int32(count))).font(StockStepsTheme.font(type.label, relativeTo: .subheadline))
                if count > 0 { Image(systemName: expanded ? "chevron.up" : "chevron.down").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).accessibilityHidden(true) }
            }
            .foregroundStyle(colors.primaryText)
            .padding(.horizontal, CGFloat(space.lg))
            .frame(minHeight: CGFloat(dims.touchTarget))
            .background(colors.primaryContainer, in: Capsule())
        }
        .buttonStyle(.plain)
        .disabled(count == 0)
        .accessibilityValue(count == 0 ? "" : expanded ? "Expanded" : "Collapsed")
        .accessibilityHint(count == 0 ? "" : expanded ? "Hides the filter list" : "Shows the filter list")
        let edit = Button("Edit filters") { showFilters = true }.buttonStyle(.stockSecondary)
        return ViewThatFits(in: .horizontal) {
            HStack(spacing: CGFloat(space.sm)) { pill; Spacer(minLength: CGFloat(space.sm)); edit }
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) { pill; edit }
        }
    }

    private func remove(_ filter: ActiveFilterView) {
        if let metric = filter.metric { client.screener.clearFilter(metric: metric) }
        else if let field = filter.field { client.screener.setChoice(field: field, values: []) }
        client.screener.apply()
    }

    /// Applied filters: compact removable chips in a fading row, or (expanded) one row per filter with a 44 pt remove button.
    @ViewBuilder private func activeFilters(_ filters: [ActiveFilterView], _ colors: StockColors) -> some View {
        if expanded {
            VStack(spacing: 0) {
                ForEach(Array(filters.enumerated()), id: \.element.id) { index, filter in
                    if index > 0 { StockDivider() }
                    HStack(spacing: CGFloat(space.sm)) {
                        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                            Text(filter.label).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle)
                            Text(verbatim: filter.value).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityElement(children: .combine)
                        Button { remove(filter) } label: {
                            Image(systemName: "xmark").foregroundStyle(colors.iconSecondary).frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
                        }
                        .accessibilityLabel("Remove filter: \(filter.spoken)")
                    }
                    .padding(.vertical, CGFloat(space.xxs))
                }
            }
            .padding(.leading, CGFloat(space.cardPadding))
            .background(colors.surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
        } else {
            let byId = Dictionary(uniqueKeysWithValues: filters.map { ($0.id, $0) })
            StockFadingScrollRow(items: filters.map(\.id), selected: nil) { id in
                if let filter = byId[id] {
                    Button { remove(filter) } label: {
                        HStack(spacing: CGFloat(space.xs)) {
                            Text(verbatim: filter.chipText).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textBody).lineLimit(1)
                            Image(systemName: "xmark").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.iconSecondary)
                        }
                        .padding(.leading, CGFloat(space.md)).padding(.trailing, CGFloat(space.sm))
                        .frame(minHeight: CGFloat(dims.touchTarget))
                        .background(colors.surfaceSecondary, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(filter.spoken)
                    .accessibilityHint("Removes this filter")
                }
            }
        }
    }

    // MARK: Results

    private func resultsHeader(_ state: ScreenerUiState, _ colors: StockColors) -> some View {
        let sort = (state.applied ?? state.draft).sort
        // While a search runs, no count is shown (never a misleading "0 companies match").
        let title = state.loading ? "Finding companies…" : state.error != nil ? "Results" : display.matchLine(total: state.total)
        let count = HStack(spacing: CGFloat(space.sm)) {
            Text(title).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3)).foregroundStyle(colors.textTitle).accessibilityAddTraits(.isHeader)
            if state.sampleData { StockStatusBadge(text: "Sample", kind: .sample, size: .compact) }
        }
        let menu = Menu {
            ForEach(display.sortOptions(shown: state.displayMetrics), id: \.label) { option in
                Button { client.screener.sort(sort: option.sort) } label: {
                    if option.sort == sort { Label(option.label, systemImage: "checkmark") } else { Text(option.label) }
                }
            }
        } label: {
            Text(display.sortLabel(sort: sort)).font(StockStepsTheme.font(type.label, relativeTo: .subheadline)).foregroundStyle(colors.primaryText)
                .frame(minHeight: CGFloat(dims.touchTarget))
        }
        .accessibilityLabel(display.sortSpoken(sort: sort))
        .accessibilityHint("Changes the sort order")
        return VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: CGFloat(space.sm)) { count; Spacer(minLength: CGFloat(space.sm)); menu }
                VStack(alignment: .leading, spacing: 0) { count; menu }
            }
            if !state.loading && state.error == nil, let coverage = display.coverageLine(universe: state.universe, excluded: state.excludedForMissingData) {
                Text(coverage).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
            }
            if !state.loading && state.error == nil && !state.rows.isEmpty, let explanation = display.resultsExplanation(applied: state.applied, shown: state.displayMetrics) {
                Text(explanation).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting)
                Button("What these numbers mean") { showMetricsHelp = true }
                    .font(StockStepsTheme.font(type.label, relativeTo: .subheadline)).foregroundStyle(colors.primaryText).frame(minHeight: CGFloat(dims.touchTarget))
            }
            ForEach(state.warnings + (state.activePreset?.notes ?? []), id: \.self) {
                Text($0).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting)
            }
        }
    }

    @ViewBuilder private func results(_ state: ScreenerUiState, active: [ActiveFilterView], _ colors: StockColors) -> some View {
        if state.loading {
            VStack(spacing: 0) { ForEach(0..<3, id: \.self) { _ in StockRowSkeleton() } }
                .background(colors.surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                .padding(.top, CGFloat(space.sm))
                .accessibilityElement(children: .ignore).accessibilityLabel("Loading results")
        } else if let error = state.error {
            StockStateMessage(title: display.ERROR_TITLE, message: error, systemImage: "wifi.exclamationmark", isError: true,
                              actionTitle: "Try again", action: { client.screener.retry() })
                .padding(.horizontal, CGFloat(space.cardPadding))
                .background(colors.surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                .padding(.top, CGFloat(space.sm))
        } else if state.rows.isEmpty {
            VStack(spacing: CGFloat(space.sm)) {
                StockStateMessage(title: display.EMPTY_TITLE, message: display.EMPTY_MESSAGE, systemImage: "magnifyingglass",
                                  actionTitle: "Edit filters", action: { showFilters = true })
                if !active.isEmpty {
                    Button("Clear all filters") { client.screener.resetAll() }.buttonStyle(.stockSecondary).padding(.bottom, CGFloat(space.lg))
                }
            }
            .padding(.horizontal, CGFloat(space.cardPadding))
            .background(colors.surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
            .padding(.top, CGFloat(space.sm))
        } else {
            // One grouped list; each row is its own lazy child so "load more" fires only when the last row appears.
            ForEach(Array(state.rows.enumerated()), id: \.element.symbol) { index, row in
                let radius = CGFloat(StockStepsTheme.corners.card)
                let first = index == 0, last = index == state.rows.count - 1
                VStack(spacing: 0) {
                    if !first { StockDivider().padding(.leading, CGFloat(space.cardPadding)) }
                    resultRow(row, items: display.metricItems(row: row, shown: state.displayMetrics, applied: state.applied), colors)
                }
                .background(colors.surface, in: UnevenRoundedRectangle(topLeadingRadius: first ? radius : 0, bottomLeadingRadius: last ? radius : 0,
                                                                       bottomTrailingRadius: last ? radius : 0, topTrailingRadius: first ? radius : 0))
                .padding(.top, first ? CGFloat(space.sm) : 0)
                .onAppear { if last && state.hasMore { client.screener.loadMore() } }
            }
        }
    }

    private func direction(_ cell: MetricCell) -> PriceDirection {
        if cell.explanation != nil { return .unavailable }
        switch cell.tone.name {
        case "POSITIVE": return .up
        case "NEGATIVE": return .down
        default: return .unchanged
        }
    }

    @ViewBuilder private func values(_ row: ScreenerRowView, _ colors: StockColors) -> some View {
        if row.priceAvailable {
            Text(verbatim: row.price).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .subheadline)).foregroundStyle(colors.textValue).lineLimit(1)
            if row.change.available { StockPriceChange(percentage: row.change.text, direction: direction(row.change)) }
        } else {
            Text(display.UNAVAILABLE).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary).lineLimit(1)
        }
    }

    /// Shared `StockQuoteRowLayout` decision from measured values and the width beside the logo (minus the ⋮ button), as on Watchlist.
    private func valuesStacked(_ row: ScreenerRowView) -> Bool {
        let price = StockStepsTheme.uiFont(type.numberLabelStrong, typeSize: typeSize)
        let change = StockStepsTheme.uiFont(type.numberLabel, typeSize: typeSize)
        let widths = [row.priceAvailable ? (row.price as NSString).size(withAttributes: [.font: price]).width : nil,
                      row.change.available ? (row.change.text as NSString).size(withAttributes: [.font: change]).width + CGFloat(dims.iconSmall) : nil].compactMap { $0 }
        let beside = width - CGFloat(space.cardPadding) - CGFloat(dims.logo) - CGFloat(space.sm) - CGFloat(dims.touchTarget)
        return StockQuoteRowLayout.shared.stacksValues(valuesWidth: Float(widths.max() ?? 0), availableWidth: Float(beside), accessibilityText: typeSize.isAccessibilitySize)
    }

    /// One result: logo, symbol and two-line name with price/change beside or under them, then the shown metrics as wrapping label/value pairs.
    /// The row is one element that opens Company Details; ⋮ holds Compare, Watchlist and Portfolio. Missing values read "No data available".
    private func resultRow(_ row: ScreenerRowView, items: [ScreenerMetricItem], _ colors: StockColors) -> some View {
        let stacked = valuesStacked(row)
        let indent = CGFloat(dims.logo) + CGFloat(space.sm)
        return HStack(alignment: .top, spacing: 0) {
            Button { onOpen(row.symbol) } label: {
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    StockRow(symbol: row.symbol, name: row.name, logoUrl: row.logoUrl, horizontalPadding: 0, nameLineLimit: 2, action: {}) {
                        if !stacked { values(row, colors) }
                    }
                    .allowsHitTesting(false)
                    if stacked {
                        ViewThatFits(in: .horizontal) {
                            HStack(spacing: CGFloat(space.sm)) { values(row, colors) }
                            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) { values(row, colors) }
                        }
                        .padding(.leading, indent)
                    }
                    if !items.isEmpty || row.selected {
                        // Each pair keeps its label and value together; pairs move to the next line instead of overflowing.
                        ViewThatFits(in: .horizontal) {
                            HStack(spacing: CGFloat(space.md)) { metricPairs(items, row: row, colors) }
                            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) { metricPairs(items, row: row, colors) }
                        }
                        .padding(.leading, indent)
                    }
                    ForEach(row.notes + (row.stale ? ["Financial statements may be out of date."] : []), id: \.self) {
                        Text($0).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting).padding(.leading, indent)
                    }
                }
                .padding(.leading, CGFloat(space.cardPadding))
                .padding(.bottom, CGFloat(space.sm))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(row.accessibility + (items.contains { $0.filtered } ? " Matches your filters." : ""))
            .accessibilityHint("Opens company details")
            .accessibilityAddTraits(.isButton)
            Menu {
                Button("View details", systemImage: "chart.line.uptrend.xyaxis") { onOpen(row.symbol) }
                Button(row.selected ? "Remove from comparison" : "Add to comparison", systemImage: "square.split.2x1") {
                    client.screener.toggleCompare(symbol: row.symbol, name: row.name)
                }
                if watchlistAvailable { Button("Add to watchlist", systemImage: "star") { onWatchlist(row) } }
                Button("Add to portfolio", systemImage: "briefcase") { onPortfolio(row) }
            } label: {
                Image(systemName: "ellipsis").foregroundStyle(colors.iconSecondary).frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
            }
            .accessibilityLabel("More actions for \(row.symbol)")
            .padding(.top, CGFloat(space.xs))
        }
    }

    @ViewBuilder private func metricPairs(_ items: [ScreenerMetricItem], row: ScreenerRowView, _ colors: StockColors) -> some View {
        ForEach(items, id: \.label) { item in
            HStack(spacing: CGFloat(space.xs)) {
                Text(item.label).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting)
                Text(verbatim: item.text).font(StockStepsTheme.font(item.available ? type.numberLabel : type.caption, relativeTo: .caption1))
                    .foregroundStyle(!item.available ? colors.textTertiary : item.tone.name == "POSITIVE" ? colors.positiveText
                                     : item.tone.name == "NEGATIVE" ? colors.negativeText : colors.textValue)
            }
        }
        if row.selected { StockStatusBadge(text: "Comparing", kind: .info, size: .compact) }
    }

    // MARK: Tools and compare bar

    /// Related tools as one grouped list of navigation rows (the Watchlist "Alerts and more" pattern). Guests see the sign-in row.
    private func tools(_ state: ScreenerUiState, _ colors: StockColors) -> some View {
        let maxCompanies = Int(client.maxCompanies())
        return VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: "More tools")
            VStack(spacing: 0) {
                if state.signedIn {
                    StockNavigationRow(title: "Saved screens", detail: state.saved.map { "\($0.screens.count) of \($0.limit) saved" } ?? "Keep filters to reuse later",
                                       icon: "star") { showSaved = true }
                } else {
                    StockNavigationRow(title: "Sign in to save screens", detail: "Keep filters to reuse later", icon: "person.crop.circle", action: onSignIn)
                }
                StockDivider()
                StockNavigationRow(title: "Compare companies",
                                   detail: state.selected.isEmpty ? "See up to \(maxCompanies) side by side" : "\(state.selected.count) selected · up to \(maxCompanies)",
                                   icon: "square.split.2x1", action: onCompare)
                StockDivider()
                StockNavigationRow(title: "How screening works", detail: "Presets, criteria and missing data", icon: "questionmark.circle") { showHelp = true }
            }
            .padding(.horizontal, CGFloat(space.md))
            .background(colors.surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
        }
    }

    private func compareBar(_ state: ScreenerUiState, _ colors: StockColors) -> some View {
        HStack(spacing: CGFloat(space.sm)) {
            Text(state.selected.map(\.symbol).joined(separator: ", ")).font(StockStepsTheme.font(type.small, relativeTo: .subheadline))
                .foregroundStyle(colors.textBody).lineLimit(2).frame(maxWidth: .infinity, alignment: .leading)
            Button(state.canCompare ? "Compare (\(state.selected.count))" : "Select 2 to compare", action: onCompare)
                .buttonStyle(.stockPrimary).disabled(!state.canCompare)
        }
        .padding(.horizontal, CGFloat(space.screen)).padding(.vertical, CGFloat(space.sm))
        .background(colors.surface)
        .overlay(alignment: .top) { StockDivider() }
    }
}

// MARK: - Filter editor

/// Filter editor (Phase 5B, same structure as Android): categories → filters in a category → one filter ("Any value / At least / At most /
/// Between" and values). Edits change only the draft; nothing is searched until "Show results", pinned at the bottom (above the keyboard).
struct DiscoverFilterEditor: View {
    let state: ScreenerUiState
    let client: IosScreenerClient
    let onInfo: (MetricInfo) -> Void
    let onDone: () -> Void
    @Environment(\.colorScheme) private var scheme
    private var display: ScreenerDisplay { ScreenerDisplay.shared }

    private func icon(_ id: String) -> String {
        switch id {
        case "COMPANY": "building.columns"
        case "VALUATION": "tag"
        case "GROWTH": "chart.line.uptrend.xyaxis"
        case "PROFITABILITY": "chart.pie"
        case "HEALTH": "shield"
        default: "dollarsign.circle"
        }
    }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let categories = display.categories(metrics: state.metrics, choices: state.choices, draft: state.draft)
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: CGFloat(space.md)) {
                    Text("Choose what matters to you. Nothing is searched until you tap Show results.")
                        .font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textSupporting)
                    groupedCard(colors) {
                        ForEach(Array(categories.enumerated()), id: \.element.id) { index, category in
                            if index > 0 { StockDivider() }
                            NavigationLink(value: category.id) {
                                navigationLabel(category.title, detail: category.detail,
                                                secondary: category.activeCount > 0 ? "\(display.filterCount(count: category.activeCount)) set" : nil,
                                                icon: icon(category.id), colors)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(category.accessibility)
                        }
                    }
                    StockSectionHeader(title: "Or start from a preset")
                    groupedCard(colors) {
                        ForEach(Array(state.presets.enumerated()), id: \.element.id) { index, preset in
                            if index > 0 { StockDivider() }
                            StockNavigationRow(title: preset.name, detail: display.presetSummary(preset: preset), hint: "Shows companies matching \(preset.name)") {
                                client.screener.selectPreset(id: preset.id); onDone()
                            }
                        }
                    }
                }
                .padding(.horizontal, CGFloat(space.screen)).padding(.vertical, CGFloat(space.sm))
            }
            .background(colors.surface)
            .navigationTitle("Filters")
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: String.self) { id in
                if let category = categories.first(where: { $0.id == id }) {
                    DiscoverCategoryView(category: category, state: state, client: client, onInfo: onInfo)
                        .safeAreaInset(edge: .bottom, spacing: 0) { showResults(colors) }
                }
            }
            .toolbar { toolbar }
            .safeAreaInset(edge: .bottom, spacing: 0) { showResults(colors) }
        }
    }

    @ToolbarContentBuilder private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) { Button("Close", action: onDone) }
        ToolbarItem(placement: .primaryAction) {
            Button("Reset all") { client.screener.resetAll() }.disabled(state.draft.activeFilterCount == 0 && (state.applied?.activeFilterCount ?? 0) == 0)
        }
    }

    private func showResults(_ colors: StockColors) -> some View {
        let count = state.draft.activeFilterCount
        return Button(count > 0 ? "Show results (\(display.filterCount(count: count)))" : "Show all companies") { client.screener.apply(); onDone() }
            .buttonStyle(.stockPrimary).frame(maxWidth: .infinity)
            .padding(.horizontal, CGFloat(space.screen)).padding(.vertical, CGFloat(space.sm))
            .background(colors.surface)
    }

    private func groupedCard<Content: View>(_ colors: StockColors, @ViewBuilder content: () -> Content) -> some View {
        VStack(spacing: 0) { content() }
            .padding(.horizontal, CGFloat(space.md))
            .background(colors.surfaceSecondary, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
    }
}

/// A navigation-row look for `NavigationLink` labels (same layout as `StockNavigationRow`).
private func navigationLabel(_ title: String, detail: String?, secondary: String?, detailColor: Color? = nil, icon: String?, _ colors: StockColors) -> some View {
    HStack(spacing: CGFloat(space.md)) {
        if let icon {
            Image(systemName: icon).font(StockStepsTheme.font(type.body, relativeTo: .body)).foregroundStyle(colors.primaryText)
                .frame(width: CGFloat(dims.icon)).accessibilityHidden(true)
        }
        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            Text(title).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle).multilineTextAlignment(.leading)
            if let detail { Text(verbatim: detail).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(detailColor ?? colors.textSupporting).multilineTextAlignment(.leading) }
            if let secondary { Text(secondary).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.primaryText) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        Image(systemName: "chevron.right").font(StockStepsTheme.font(type.caption, relativeTo: .caption1).weight(.semibold)).foregroundStyle(colors.textTertiary).accessibilityHidden(true)
    }
    .padding(.vertical, CGFloat(space.sm))
    .frame(minHeight: CGFloat(dims.rowCompactMinHeight))
    .contentShape(Rectangle())
}

/// One category: attribute choices (wrapping chips) and the category's filters with their current values.
private struct DiscoverCategoryView: View {
    let category: FilterCategoryView
    let state: ScreenerUiState
    let client: IosScreenerClient
    let onInfo: (MetricInfo) -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            VStack(alignment: .leading, spacing: CGFloat(space.md)) {
                ForEach(category.choices, id: \.name) { field in
                    let selected = state.draft.choices.first { $0.field == field }?.values ?? []
                    VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                        Text(field.label).font(StockStepsTheme.font(type.label, relativeTo: .subheadline)).foregroundStyle(colors.textTitle).accessibilityAddTraits(.isHeader)
                        StockFadingScrollRow(items: state.choices[field] ?? [], selected: nil) { value in
                            let on = selected.contains(value)
                            StockChip(title: value, selected: on) {
                                client.screener.setChoice(field: field, values: on ? selected.filter { $0 != value } : selected + [value])
                            }
                            .accessibilityValue(on ? "Selected" : "Not selected")
                        }
                    }
                }
                if !category.metrics.isEmpty {
                    VStack(spacing: 0) {
                        ForEach(Array(category.metrics.enumerated()), id: \.element.id) { index, metric in
                            if index > 0 { StockDivider() }
                            let range = state.draft.ranges.first { $0.metric == metric.id }
                            NavigationLink {
                                DiscoverMetricEditor(definition: metric, current: range, client: client, onInfo: { onInfo(client.education(id: metric.id)) })
                            } label: {
                                navigationLabel(metric.label, detail: range.map { ScreenerDisplay.shared.rangeValue(range: $0) } ?? ScreenerDisplay.shared.operatorOf(range: nil).label,
                                                secondary: nil, detailColor: range == nil ? nil : colors.primaryText, icon: nil, colors)
                            }
                            .buttonStyle(.plain)
                            .accessibilityHint("Edits \(metric.label)")
                        }
                    }
                    .padding(.horizontal, CGFloat(space.md))
                    .background(colors.surfaceSecondary, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                }
            }
            .padding(.horizontal, CGFloat(space.screen)).padding(.vertical, CGFloat(space.sm))
        }
        .background(colors.surface)
        .navigationTitle(category.title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// One filter: what it means, the operator and its values in display units. Pushes the draft on every change (as before Phase 5B).
private struct DiscoverMetricEditor: View {
    let definition: MetricDefinition
    let current: RangeFilter?
    let client: IosScreenerClient
    let onInfo: () -> Void
    @Environment(\.colorScheme) private var scheme
    @State private var operatorName = ""
    @State private var first = ""
    @State private var second = ""
    @State private var loaded = false
    private var display: ScreenerDisplay { ScreenerDisplay.shared }
    private var selected: ScreenerDisplay.Operator {
        ScreenerDisplay.Operator.entries.first { $0.name == operatorName } ?? display.operatorOf(range: current)
    }

    private func push() {
        let bounds = display.bounds(operator: selected, first: display.parseInput(text: first, definition: definition),
                                    second: display.parseInput(text: second, definition: definition))
        client.setRange(metric: definition.id, min: bounds.min?.doubleValue ?? 0, hasMin: bounds.min != nil, max: bounds.max?.doubleValue ?? 0, hasMax: bounds.max != nil)
    }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let unit = display.inputUnit(definition: definition)
        let suffix = unit.isEmpty ? "" : " (\(unit))"
        ScrollView {
            VStack(alignment: .leading, spacing: CGFloat(space.md)) {
                HStack(alignment: .top) {
                    Text(definition.label).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3)).foregroundStyle(colors.textTitle)
                        .accessibilityAddTraits(.isHeader).frame(maxWidth: .infinity, alignment: .leading)
                    Button(action: onInfo) {
                        Image(systemName: "info.circle").foregroundStyle(colors.iconSecondary).frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
                    }
                    .accessibilityLabel("About \(definition.label)")
                }
                let meaning = client.education(id: definition.id).meaning
                if !meaning.isEmpty { Text(meaning).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textSupporting) }
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(ScreenerDisplay.Operator.entries, id: \.name) { option in
                        let on = option.name == selected.name
                        Button { operatorName = option.name; push() } label: {
                            HStack(spacing: CGFloat(space.sm)) {
                                Image(systemName: on ? "largecircle.fill.circle" : "circle").foregroundStyle(on ? colors.primaryText : colors.iconSecondary)
                                Text(option.label).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
                            }
                            .frame(maxWidth: .infinity, minHeight: CGFloat(dims.touchTarget), alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(option.label)
                        .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
                    }
                }
                switch selected.name {
                case "ANY":
                    Text("No limit: companies aren't filtered on \(definition.label).").font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textSupporting)
                case "BETWEEN":
                    HStack(alignment: .top, spacing: CGFloat(space.sm)) {
                        StockTextField(label: "From\(suffix)", text: $first, placeholder: display.inputText(value: definition.suggestedMin, definition: definition), keyboard: .decimalPad)
                        StockTextField(label: "To\(suffix)", text: $second, placeholder: display.inputText(value: definition.suggestedMax, definition: definition), keyboard: .decimalPad)
                    }
                    if let error = display.boundsError(low: display.parseInput(text: first, definition: definition), high: display.parseInput(text: second, definition: definition)) {
                        Text(error).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.negativeText)
                    }
                default:
                    StockTextField(label: "\(selected.label)\(suffix)", text: $first,
                                   placeholder: display.inputText(value: selected.name == "AT_LEAST" ? definition.suggestedMin : definition.suggestedMax, definition: definition),
                                   keyboard: .decimalPad)
                }
                Text("Includes the values you type. Period: \(definition.period). \(definition.missing)")
                    .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
                if let note = definition.currencyNote { Text(note).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta) }
            }
            .padding(.horizontal, CGFloat(space.screen)).padding(.vertical, CGFloat(space.sm))
        }
        .scrollDismissesKeyboard(.interactively)
        .background(colors.surface)
        .navigationTitle(definition.label)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            guard !loaded else { return }
            loaded = true
            operatorName = display.operatorOf(range: current).name
            first = display.inputText(value: display.firstValue(range: current), definition: definition)
            second = display.inputText(value: display.secondValue(range: current), definition: definition)
        }
        .onChange(of: first) { _, _ in if loaded { push() } }
        .onChange(of: second) { _, _ in if loaded { push() } }
    }
}

// MARK: - Help sheets

/// "How screening works": what a screen is, then every preset with its exact criteria, data period and missing-data rule (was the ⓘ alert).
struct ScreeningHelpView: View {
    let state: ScreenerUiState
    let onPreset: (String) -> Void
    let onDone: () -> Void
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let caption = StockStepsTheme.font(type.caption, relativeTo: .caption1)
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                    Text("A screen lists the companies whose latest reported values fall inside every range you set. Companies without a valid value for a filtered metric are left out, never counted as zero.")
                        .font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
                    if let universe = state.universe {
                        Text(universe.complete ? "Results cover all \(universe.size) companies StockSteps checks." : "Results cover \(universe.evaluated) of \(universe.size) companies StockSteps checks.")
                            .font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textSupporting)
                    }
                    Text("Presets are shortcuts with visible criteria, not recommendations. You can change any filter after applying one.")
                        .font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textSupporting)
                    ForEach(state.presets, id: \.id) { preset in
                        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                            Text(preset.name).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textTitle).accessibilityAddTraits(.isHeader)
                            Text(preset.description_).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
                            ForEach(ScreenerDisplay.shared.presetCriteria(preset: preset), id: \.self) {
                                Text(verbatim: "• \($0)").font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
                            }
                            Text("Data period: \(preset.dataPeriod)").font(caption).foregroundStyle(colors.textSupporting)
                            Text("Missing data: \(preset.missingData)").font(caption).foregroundStyle(colors.textSupporting)
                            ForEach(preset.notes, id: \.self) { Text($0).font(caption).foregroundStyle(colors.textSupporting) }
                            Button("Use this preset") { onPreset(preset.id) }.buttonStyle(.stockSecondary).padding(.top, CGFloat(space.xs))
                        }
                        .padding(CGFloat(space.cardPadding))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(colors.surfaceSecondary, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                    }
                }
                .padding(.horizontal, CGFloat(space.screen)).padding(.vertical, CGFloat(space.sm))
            }
            .background(colors.surface)
            .navigationTitle("How screening works")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done", action: onDone) } }
        }
    }
}

/// "What these numbers mean": the explanation of each value shown on the result rows (shared MetricEducation).
struct MetricsInfoList: View {
    let infos: [MetricInfo]
    var body: some View {
        NavigationStack {
            List {
                ForEach(infos, id: \.title) { info in
                    Section(info.title) {
                        Text(info.meaning)
                        if let calculation = info.calculation { LabeledContent("How it's calculated") { Text(calculation) } }
                        if let period = info.period { LabeledContent("Period") { Text(period) } }
                        ForEach(info.limitations, id: \.self) { Text($0).font(.footnote) }
                    }
                }
            }
            .navigationTitle("What these numbers mean")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }
}

struct SavedScreensSheet: View {
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
                                Text(ScreenerDisplay.shared.filterCount(count: screen.query.activeFilterCount)).font(.caption).foregroundStyle(.secondary)
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
