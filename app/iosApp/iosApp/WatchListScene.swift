import Observation
import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

/// Mirrors Android's WatchListViewModel: server watchlists (cached for offline reading), one batched
/// quote/earnings request per visible list, and server-confirmed mutations with clear failures.
@MainActor
@Observable
final class WatchlistsModel {
    private(set) var user: UserDataSnapshot?
    private(set) var data: WatchDataRepository.Result?
    private(set) var dataLoading = false
    private(set) var busy = false
    var message: String?
    var selectedId: String?
    @ObservationIgnored let client: IosAccountClient?
    @ObservationIgnored private var subscription: (any AccountSubscription)?
    @ObservationIgnored private var loadedKey: String?
    @ObservationIgnored private var dataTask: Task<Void, Never>?

    init(client: IosAccountClient?) {
        self.client = client
        subscription = client?.observeUserData { [weak self] snapshot in
            Task { @MainActor in self?.user = snapshot }
        }
    }
    deinit { subscription?.cancel() }

    var watchlists: [Watchlist] { user?.watchlists ?? [] }

    func model(guestItems: [WatchlistItem]) -> WatchlistModel? {
        guard let client else { return nil }
        if user?.uid == nil {
            let entries = guestItems.enumerated().map { index, item in
                WatchlistEntry(id: item.symbol, instrument: InstrumentRef(symbol: item.symbol, name: item.name, exchange: item.exchange, currency: item.currency),
                               order: Int32(index), addedAt: item.addedAt, note: nil, noteUpdatedAt: nil)
            }
            let guest = Watchlist(id: "guest", name: "My Stocks", order: 0, createdAt: 0, updatedAt: 0, isDefault: true, entries: entries)
            return client.watchlistModel(lists: [guest], selectedId: "guest", data: data?.data, alerts: [], offlineSince: offlineSince)
        }
        guard user?.listsLoaded == true else { return nil }
        return client.watchlistModel(lists: watchlists, selectedId: selectedId, data: data?.data, alerts: user?.alerts ?? [], offlineSince: offlineSince)
    }

    private var offlineSince: KotlinLong? {
        if let data, data.fromCache { return KotlinLong(value: data.savedAt) }
        if user?.offline == true { return user?.savedAt }
        return nil
    }

    var selected: Watchlist? {
        let id = selectedId ?? watchlists.first(where: { $0.isDefault })?.id
        return watchlists.first { $0.id == id } ?? watchlists.first
    }

    /// Loads quotes when the visible symbols change (keyed so recomposition never re-requests).
    func loadData(symbols: [String], force: Bool = false) {
        let key = symbols.joined(separator: ",") + "|" + (client?.environment ?? "")
        guard force || key != loadedKey else { return }
        loadedKey = key
        dataTask?.cancel()
        guard let client, !symbols.isEmpty else { data = nil; return }
        dataLoading = true
        dataTask = Task {
            let result = try? await client.watchData(symbols: symbols)
            if Task.isCancelled { return }
            if let result { data = result }
            dataLoading = false
        }
    }

    func refresh(symbols: [String]) async {
        try? await client?.refreshUserData()
        loadData(symbols: symbols, force: true)
    }

    func perform(_ action: @escaping (IosAccountClient) async throws -> Void) {
        guard let client, !busy else { return }
        busy = true
        message = nil
        Task {
            do { try await action(client) } catch { message = userMessage(error) }
            busy = false
        }
    }

    func shift(entryId: String, by offset: Int) {
        guard let list = selected else { return }
        var ids = list.entries.map(\.id)
        guard let from = ids.firstIndex(of: entryId), ids.indices.contains(from + offset) else { return }
        ids.swapAt(from, from + offset)
        perform { try await $0.reorderEntries(listId: list.id, ids: ids) }
    }
}

/// A user-facing message from a failed Kotlin call (the shared repositories already phrase them).
func userMessage(_ error: Error) -> String {
    if let message = ((error as NSError).userInfo["KotlinException"] as? KotlinThrowable)?.message { return message }
    return "Something went wrong. Try again."
}

private enum WatchSheet: Identifiable {
    case note(WatchlistRowModel)
    case alert(InstrumentRef, String?)
    var id: String {
        switch self {
        case .note(let row): "note-\(row.entryId)"
        case .alert(let instrument, _): "alert-\(instrument.symbol)"
        }
    }
}

/// Watchlist tab: list chips, today's summary (equal-weighted, documented), insights, rows with
/// alert indicators and notes, a menu per row, and list management. Guests see the device list.
struct WatchListScene: View {
    @Environment(\.colorScheme) private var scheme
    let accounts: AccountViewModel
    let model: WatchlistsModel
    let onSignIn: () -> Void
    let onSearch: () -> Void
    let onExplore: (String) -> Void
    let onOpenAlerts: (String?) -> Void
    var onEarnings: () -> Void = {}
    var onEarningsReminders: () -> Void = {}
    /// Company Comparison with the first three companies of the shown list (symbols, names).
    var onCompare: ([String], [String]) -> Void = { _, _ in }
    /// 5D.5.6B landing bar: the all-alerts history and Settings.
    var onNotifications: () -> Void = {}
    var onSettings: () -> Void = {}
    @State private var portfolioInstrument: InstrumentRef?
    @State private var portfolioModel = PortfolioViewModel()
    @State private var sheet: WatchSheet?
    @State private var naming: Watchlist??
    @State private var name = ""
    @State private var confirmDelete: Watchlist?
    @State private var listWidth: CGFloat = 0
    private static let newList = "__new_list__"
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let content = model.model(guestItems: accounts.state.items)
        let signedIn = accounts.state.user != nil
        let symbols = content?.rows.map(\.symbol) ?? []
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                // Phase 5A order: header → watchlist selector → stocks → alerts and related actions → education.
                header(content, signedIn: signedIn, colors).padding(.top, CGFloat(space.lg))
                if let content {
                    if signedIn {
                        // Full names (the row scrolls instead of truncating); the shared fading row shows that more lists and "+" lie beyond a long
                        // name and keeps the selected list in view (Phase 5A.1). Counts are spoken; the header shows the selected list's count.
                        StockFadingScrollRow(items: content.tabs.map(\.id) + [Self.newList], selected: content.selectedId) { id in
                            if let tab = content.tabs.first(where: { $0.id == id }) {
                                StockChip(title: tab.name, selected: tab.selected) { model.selectedId = tab.id }
                                    .accessibilityLabel("\(tab.name), \(tab.count) \(tab.count == 1 ? "company" : "companies")")
                            } else {
                                StockChip(title: "+", selected: false) { name = ""; naming = .some(nil) }.accessibilityLabel("New watchlist")
                            }
                        }
                        .padding(.top, CGFloat(space.md))
                    }
                    if signedIn, model.user?.offline == true, let error = model.user?.listsError {
                        Text(error).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.cautionText).padding(.top, CGFloat(space.sm))
                    }
                    if content.emptyMessage != nil {
                        let presenter = WatchlistPresenter.shared
                        StockStateMessage(title: content.emptyTitle ?? "Your watchlist is empty",
                                          message: signedIn ? presenter.EMPTY_MESSAGE : "\(presenter.EMPTY_MESSAGE) They're saved on this device.",
                                          systemImage: "star", actionTitle: "Add a company", action: onSearch, titleAccessibilityLabel: content.emptySpokenTitle)
                            .stockCard(padding: CGFloat(space.md), bordered: false).padding(.top, CGFloat(space.md))
                    }
                    if !content.rows.isEmpty {
                        // One grouped card of compact rows (no card per stock).
                        VStack(spacing: 0) {
                            ForEach(Array(content.rows.enumerated()), id: \.element.entryId) { index, row in
                                if index > 0 { StockDivider() }
                                rowView(row, index: index, count: content.rows.count, signedIn: signedIn, colors)
                            }
                        }
                        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { listWidth = $0 }
                        .stockCard(padding: 0, bordered: false).padding(.top, CGFloat(space.md))
                        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                            let presenter = WatchlistPresenter.shared
                            if let unavailable = presenter.pricesUnavailableMessage(hasRows: true, hasData: model.data != nil, loading: model.dataLoading) {
                                StockSectionMessage(message: unavailable, actionTitle: "Try again") { model.loadData(symbols: symbols, force: true) }
                            } else {
                                Text(presenter.CHANGE_CONTEXT).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
                            }
                            Button(action: onSearch) { Label("Add a company", systemImage: "plus").frame(maxWidth: .infinity) }.buttonStyle(.stockSecondary)
                        }
                        .padding(.top, CGFloat(space.sm))
                    }
                    relatedActions(signedIn: signedIn, colors).padding(.top, CGFloat(space.sectionGap))
                    if !content.insights.isEmpty {
                        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                            Text("Your watchlist today").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
                                .accessibilityAddTraits(.isHeader)
                            ForEach(content.insights, id: \.self) { Text("• \($0)").font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody) }
                        }
                        .padding(CGFloat(space.md)).frame(maxWidth: .infinity, alignment: .leading)
                        .background(colors.educationContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                        .padding(.top, CGFloat(space.md))
                    }
                } else if signedIn && (model.user?.listsLoading ?? true) {
                    VStack(spacing: 0) { ForEach(0..<4, id: \.self) { _ in StockRowSkeleton() } }.padding(.top, CGFloat(space.lg)).accessibilityLabel("Loading")
                } else if signedIn {
                    StockStateMessage(title: "Couldn't load your watchlist", message: model.user?.listsError ?? "Your watchlists couldn't be loaded.",
                                      systemImage: "wifi.exclamationmark", isError: true, actionTitle: "Try again") {
                        Task { await model.refresh(symbols: symbols) }
                    }
                    .stockCard(padding: CGFloat(space.md), bordered: false).padding(.top, CGFloat(space.lg))
                }
                Text("A watchlist tracks companies you're interested in. It isn't a portfolio, and nothing here is a recommendation to buy or sell.")
                    .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary).padding(.top, CGFloat(space.lg))
            }
            .frame(maxWidth: CGFloat(dims.contentMaxWidth), alignment: .leading)
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.bottom, CGFloat(space.xl))
            .frame(maxWidth: .infinity)
        }
        .scrollsToTopOnReselect(.watchlist) // 5D.5.6C2 (D12)
        .refreshable { await model.refresh(symbols: symbols) }
        .background(colors.appBackground.ignoresSafeArea())
        .onChange(of: symbols, initial: true) { _, value in model.loadData(symbols: value) }
        .sheet(isPresented: Binding(get: { portfolioInstrument != nil }, set: { if !$0 { portfolioInstrument = nil } })) {
            PortfolioEntryScene(accounts: accounts, model: portfolioModel, existing: nil, initialInstrument: portfolioInstrument)
        }
        .sheet(item: $sheet) { open in
            switch open {
            case .note(let row): NoteEditor(row: row) { note in
                if let list = model.selected { model.perform { try await $0.saveNote(listId: list.id, entryId: row.entryId, note: note) } }
                sheet = nil
            }
            case .alert(let instrument, let price): AlertEditorSheet(client: model.client, instrument: instrument, currentPrice: price,
                                                                   deliveryNote: model.user?.deliveryNote) { sheet = nil }
            }
        }
        .alert(naming == .some(nil) ? "New watchlist" : "Rename watchlist", isPresented: Binding(get: { naming != nil }, set: { if !$0 { naming = nil } })) {
            TextField("Name", text: $name)
            Button("Cancel", role: .cancel) { naming = nil }
            Button("Save") {
                let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
                if let existing = naming ?? nil { model.perform { try await $0.renameWatchlist(id: existing.id, name: clean) } }
                else { model.perform { try await $0.createWatchlist(name: clean) } }
                naming = nil
            }
            .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || name.count > 40)
        } message: { Text("Up to 40 characters.") }
        .confirmationDialog(confirmDelete.map { "Delete \($0.name)?" } ?? "", isPresented: Binding(get: { confirmDelete != nil }, set: { if !$0 { confirmDelete = nil } }), titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                if let list = confirmDelete { model.perform { try await $0.deleteWatchlist(id: list.id) }; model.selectedId = nil }
                confirmDelete = nil
            }
        } message: { Text("The \(confirmDelete?.entries.count ?? 0) stocks and their notes in this list will be removed. Your alerts stay.") }
        .alert(model.message ?? "", isPresented: Binding(get: { model.message != nil }, set: { if !$0 { model.message = nil } })) {
            Button("OK", role: .cancel) { model.message = nil }
        }
    }

    private func stateInstrument(_ row: WatchlistRowModel) -> InstrumentRef {
        model.watchlists.flatMap { $0.entries }.first { $0.instrument.symbol == row.symbol }?.instrument
            ?? InstrumentRef(symbol: row.symbol, name: row.name, exchange: nil, currency: row.currency)
    }

    /// "Watchlist", the company count and update time, then search and (signed in) the watchlist menu. Alerts, earnings and comparison moved to
    /// the related-actions group under the stocks (Phase 5A), so the header stays compact.
    /// 5D.5.6B: the shared landing bar ("Watchlist", Search, the list menu when signed in, then Notifications and Settings); the count and
    /// freshness line stays under it.
    private func header(_ content: WatchlistModel?, signedIn: Bool, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            StockLandingTopBar(tab: .watchlist, actionCount: signedIn && model.selected != nil ? 2 : 1, onNotifications: onNotifications, onSettings: onSettings) {
                StockLandingIconButton(systemImage: "magnifyingglass", label: "Add a company", action: onSearch)
                manageMenu(signedIn: signedIn, colors)
            }
            // Count and freshness under the title; wraps only between parts, never inside "5:16 PM ET" (Phase 5A.1).
            if let line = content?.headerLine {
                Text(verbatim: line).font(StockStepsTheme.font(type.small, relativeTo: .subheadline))
                    .foregroundStyle(content?.headerCaution == true ? colors.cautionText : colors.textSecondary)
            }
        }
    }

    @ViewBuilder private func manageMenu(signedIn: Bool, _ colors: StockColors) -> some View {
        HStack(spacing: 0) {
            if signedIn, let selected = model.selected {
                Menu {
                    Button("New watchlist", systemImage: "plus") { name = ""; naming = .some(nil) }
                    Button("Rename watchlist", systemImage: "pencil") { name = selected.name; naming = .some(selected) }
                    if model.watchlists.count > 1 {
                        Button("Delete watchlist", systemImage: "trash", role: .destructive) {
                            if selected.entries.isEmpty { model.perform { try await $0.deleteWatchlist(id: selected.id) }; model.selectedId = nil }
                            else { confirmDelete = selected }
                        }
                    }
                } label: {
                    Image(systemName: "ellipsis").font(.system(size: 20, weight: .regular)).foregroundStyle(colors.textPrimary)
                        .frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
                }
                .accessibilityLabel("Manage watchlist")
            }
        }
    }

    /// Alerts and related actions under the stocks: what used to be header links, as one grouped list of navigation rows.
    @ViewBuilder
    private func relatedActions(signedIn: Bool, _ colors: StockColors) -> some View {
        let first = (model.selected?.entries.sorted { $0.order < $1.order } ?? []).prefix(3)
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            if !signedIn {
                StockInsightCard(title: "Sign in for more", message: "Create several watchlists, add private notes and get price, earnings and news alerts.",
                                 actionTitle: "Sign in", action: onSignIn)
            }
            if signedIn || first.count >= 2 {
                StockSectionHeader(title: signedIn ? "Alerts and more" : "More")
                VStack(spacing: 0) {
                    if signedIn {
                        StockNavigationRow(title: "My alerts", detail: "Price, earnings and news alerts", icon: "bell") { onOpenAlerts(nil) }
                        StockDivider()
                        StockNavigationRow(title: "Earnings dates", detail: "When your companies report next", icon: "calendar", action: onEarnings)
                        StockDivider()
                        StockNavigationRow(title: "Earnings reminders", detail: "Get a reminder before results", icon: "bell.badge", action: onEarningsReminders)
                    }
                    if first.count >= 2 {
                        if signedIn { StockDivider() }
                        StockNavigationRow(title: "Compare these companies", detail: "See up to three side by side", icon: "square.split.2x1") {
                            onCompare(first.map { $0.instrument.symbol }, first.map { $0.instrument.name ?? $0.instrument.symbol })
                        }
                    }
                }
                .padding(.horizontal, CGFloat(space.md))
                .stockCard(padding: 0, bordered: false)
            }
        }
    }

    private func iconButton(_ systemName: String, label: String, _ colors: StockColors, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemName).font(.title3).foregroundStyle(colors.textPrimary)
                .frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
        }
        .accessibilityLabel(label)
    }

    /// Compact quote row (Phase 5A): identity on the left; price and "+$3.04 (+0.91%)" on the right — or on their own line under the company
    /// when they would crowd the name (shared `StockQuoteRowLayout`, same rule as Android). Missing quotes say "No data available" (never $0.00);
    /// a placeholder shows while prices load. The whole row is one element that opens Company Details; bell and ⋮ are separate 44 pt targets.
    private func rowView(_ row: WatchlistRowModel, index: Int, count: Int, signedIn: Bool, _ colors: StockColors) -> some View {
        let alertsButton = signedIn && row.activeAlerts > 0
        let stacked = valuesStacked(row, buttons: alertsButton ? 2 : 1)
        return HStack(spacing: 0) {
            Button { onExplore(row.symbol) } label: {
                VStack(alignment: .leading, spacing: 0) {
                    StockRow(symbol: row.symbol, name: row.name, logoUrl: row.logoUrl, horizontalPadding: 0, nameLineLimit: 2, action: {}) {
                        if !stacked { values(row, colors) }
                    }
                    .allowsHitTesting(false)
                    if stacked {
                        // Stacked: price and change share one line when they fit, else one under the other (rows stay compact at large text).
                        ViewThatFits(in: .horizontal) {
                            HStack(spacing: CGFloat(space.sm)) { values(row, colors) }
                            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) { values(row, colors) }
                        }
                            .padding(.leading, CGFloat(dims.logo) + CGFloat(space.sm)).padding(.bottom, CGFloat(space.sm))
                    }
                    if let note = row.notePreview {
                        Text("“\(note)”").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary).lineLimit(1)
                            .padding(.leading, CGFloat(dims.logo) + CGFloat(space.sm)).padding(.bottom, CGFloat(space.sm))
                    }
                }
                .padding(.leading, CGFloat(space.cardPadding))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(row.accessibilityLabel)
            .accessibilityHint("Opens company details")
            .accessibilityAddTraits(.isButton)
            if alertsButton {
                Button { onOpenAlerts(row.symbol) } label: {
                    Image(systemName: "bell.fill").foregroundStyle(colors.primaryText).frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
                }
                .accessibilityLabel("\(row.activeAlerts) active alerts for \(row.symbol)")
            }
            rowMenu(row, index: index, count: count, signedIn: signedIn, colors)
        }
    }

    @ViewBuilder
    private func values(_ row: WatchlistRowModel, _ colors: StockColors) -> some View {
        if row.quoteAvailable {
            Text(verbatim: row.price ?? "").font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .subheadline)).foregroundStyle(colors.textValue).lineLimit(1)
            if let change = row.changeLine { StockPriceChange(percentage: change, direction: row.direction) }
        } else if model.dataLoading {
            StockSkeleton(width: CGFloat(dims.touchTarget) * 1.5).accessibilityHidden(true)
        } else {
            Text(WatchlistPresenter.shared.UNAVAILABLE).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary).lineLimit(1)
        }
        if let stale = row.staleLabel {
            Text(stale).font(StockStepsTheme.font(type.tiny, relativeTo: .caption2)).foregroundStyle(colors.cautionText).lineLimit(1)
        }
    }

    /// Shared `StockQuoteRowLayout` decision from measured values and the width beside the logo (minus the row's icon buttons).
    private func valuesStacked(_ row: WatchlistRowModel, buttons: Int) -> Bool {
        let price = StockStepsTheme.uiFont(type.numberLabelStrong, typeSize: typeSize)
        let change = StockStepsTheme.uiFont(type.numberLabel, typeSize: typeSize)
        let widths = [row.price.map { ($0 as NSString).size(withAttributes: [.font: price]).width },
                      row.changeLine.map { ($0 as NSString).size(withAttributes: [.font: change]).width + CGFloat(dims.iconSmall) }].compactMap { $0 }
        let beside = listWidth - CGFloat(space.cardPadding) - CGFloat(dims.logo) - CGFloat(space.sm) - CGFloat(dims.touchTarget) * CGFloat(buttons)
        return StockQuoteRowLayout.shared.stacksValues(valuesWidth: Float(widths.max() ?? 0), availableWidth: Float(beside),
                                                       accessibilityText: typeSize.isAccessibilitySize)
    }

    private func rowMenu(_ row: WatchlistRowModel, index: Int, count: Int, signedIn: Bool, _ colors: StockColors) -> some View {
        Menu {
            Button("View details", systemImage: "chart.line.uptrend.xyaxis") { onExplore(row.symbol) }
            if signedIn, let list = model.selected {
                Button("Add alert", systemImage: "bell") { sheet = .alert(InstrumentRef(symbol: row.symbol, name: row.name, exchange: nil, currency: row.currency), row.price) }
                Button("Add to portfolio", systemImage: "briefcase") {
                    portfolioInstrument = stateInstrument(row)
                }
                Button(row.note == nil ? "Add note" : "Edit note", systemImage: "note.text") { sheet = .note(row) }
                let others = model.watchlists.filter { $0.id != list.id }
                if !others.isEmpty {
                    Menu("Move to", systemImage: "arrow.right.square") {
                        ForEach(others, id: \.id) { target in Button(target.name) { model.perform { try await $0.moveEntry(listId: list.id, entryId: row.entryId, targetId: target.id, copy: false) } } }
                    }
                    Menu("Copy to", systemImage: "doc.on.doc") {
                        ForEach(others, id: \.id) { target in Button(target.name) { model.perform { try await $0.moveEntry(listId: list.id, entryId: row.entryId, targetId: target.id, copy: true) } } }
                    }
                }
                if index > 0 { Button("Move up", systemImage: "arrow.up") { model.shift(entryId: row.entryId, by: -1) } }
                if index < count - 1 { Button("Move down", systemImage: "arrow.down") { model.shift(entryId: row.entryId, by: 1) } }
                Button("Remove from watchlist", systemImage: "trash", role: .destructive) { model.perform { try await $0.removeEntry(listId: list.id, entryId: row.entryId) } }
            } else {
                Button("Remove from watchlist", systemImage: "trash", role: .destructive) { Task { await accounts.remove(symbol: row.symbol) } }
            }
        } label: {
            Image(systemName: "ellipsis").foregroundStyle(colors.iconSecondary).frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
        }
        .accessibilityLabel("More actions for \(row.symbol)")
    }
}

/// Private multiline note (per watchlist entry); never sent anywhere else.
private struct NoteEditor: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dismiss) private var dismiss
    let row: WatchlistRowModel
    let onSave: (String?) -> Void
    @State private var text: String

    init(row: WatchlistRowModel, onSave: @escaping (String?) -> Void) {
        self.row = row
        self.onSave = onSave
        _text = State(initialValue: row.note ?? "")
    }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        NavigationStack {
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                TextEditor(text: $text)
                    .frame(minHeight: 160)
                    .padding(CGFloat(space.xs))
                    .background(colors.surfaceSecondary, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.chip)))
                    .accessibilityLabel("Private note")
                Text("\(text.count) / 1000 · Only you can see this").font(StockStepsTheme.font(type.caption, relativeTo: .caption1))
                    .foregroundStyle(text.count > 1000 ? colors.negativeText : colors.textTertiary)
                if row.note != nil {
                    Button("Delete note", role: .destructive) { onSave(nil) }.frame(minHeight: CGFloat(dims.touchTarget))
                }
                Spacer()
            }
            .padding(CGFloat(space.screen))
            .navigationTitle("Why I'm watching \(row.symbol)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
                        onSave(clean.isEmpty ? nil : clean)
                    }
                    .disabled(text.count > 1000)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

/// Create-alert form; asks before notifying when a price condition already holds, and explains
/// notifications before asking iOS for permission (the alert is kept even if permission is denied).
struct AlertEditorSheet: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dismiss) private var dismiss
    let client: IosAccountClient?
    let instrument: InstrumentRef
    let currentPrice: String?
    let deliveryNote: String?
    let onDone: () -> Void
    @State private var kind: AlertType = .priceAbove
    @State private var value = ""
    @State private var direction: MoveDirection = .either
    @State private var timing: EarningsTiming = .both
    @State private var repeatEveryCross = false
    @State private var error: String?
    @State private var alreadyMet: String?
    @State private var submitting = false
    @State private var pushAllowed = true

    private let kinds: [(AlertType, String)] = [(.priceAbove, "Price above"), (.priceBelow, "Price below"), (.dailyMove, "Daily move"), (.news, "News")]

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let currency = instrument.currency ?? "USD"
        NavigationStack {
            Form {
                Section {
                    Picker("Alert", selection: $kind) { ForEach(kinds, id: \.0) { Text($0.1).tag($0.0) } }
                    switch kind {
                    case .priceAbove, .priceBelow:
                        HStack {
                            Text(currency).foregroundStyle(colors.textSecondary)
                            TextField("Target price", text: $value).keyboardType(.decimalPad)
                        }
                        Toggle("Notify every time it crosses", isOn: $repeatEveryCross)
                    case .dailyMove:
                        HStack { TextField("Move from previous close", text: $value).keyboardType(.decimalPad); Text("%").foregroundStyle(colors.textSecondary) }
                        Picker("Direction", selection: $direction) {
                            Text("Up").tag(MoveDirection.up); Text("Down").tag(MoveDirection.down); Text("Either way").tag(MoveDirection.either)
                        }
                        .pickerStyle(.segmented)
                    case .earnings:
                        Picker("Remind me", selection: $timing) {
                            Text("Day before").tag(EarningsTiming.dayBefore); Text("Day of").tag(EarningsTiming.dayOf); Text("Both").tag(EarningsTiming.both)
                        }
                        .pickerStyle(.segmented)
                    default: EmptyView()
                    }
                } header: {
                    Text([instrument.name, currentPrice.map { "Now \($0) \(currency)" }].compactMap { $0 }.joined(separator: " · "))
                } footer: {
                    Text(help).font(StockStepsTheme.font(type.caption, relativeTo: .caption1))
                }
                if !pushAllowed {
                    Section {
                        Text("Turn on notifications to hear about alerts even when StockSteps is closed. You can still see triggered alerts in the app without them.")
                        Button("Turn on notifications") { Task { pushAllowed = await PushCoordinator.shared.requestAuthorization() } }
                    }
                }
                if let alreadyMet {
                    Section {
                        Text(alreadyMet)
                        Text("Notify you right away, or wait until the price moves back and crosses again?").foregroundStyle(colors.textSecondary)
                        Button("Notify me now") { submit(.notifyNow) }.disabled(submitting)
                        Button("Wait for the next cross") { submit(.waitForCross) }.disabled(submitting)
                    }
                }
                if let error { Section { Text(error).foregroundStyle(colors.negativeText) } }
                if let deliveryNote { Section { Text(deliveryNote).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary) } }
            }
            .navigationTitle("Alert for \(instrument.symbol)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { onDone() } }
                ToolbarItem(placement: .confirmationAction) { Button("Create") { submit(nil) }.disabled(submitting || alreadyMet != nil) }
            }
            .onChange(of: kind) { _, _ in error = nil; alreadyMet = nil }
            .task { pushAllowed = await PushCoordinator.shared.enabled }
        }
    }

    private var help: String {
        switch kind {
        case .priceAbove: "You'll be notified when the price reaches or goes above this value."
        case .priceBelow: "You'll be notified when the price reaches or goes below this value."
        case .dailyMove: "At most one notification per trading day, during regular market hours."
        case .earnings: "Reminders use the provider's earnings calendar. Estimated dates are labeled and may change."
        default: "You'll be notified about concrete company events (results, deals, products, regulatory decisions) from verified news sources, not every article."
        }
    }

    private func submit(_ choice: AlreadyMetChoice?) {
        guard let client else { return }
        let needsNumber = kind == .priceAbove || kind == .priceBelow || kind == .dailyMove
        let number = Double(value.replacingOccurrences(of: ",", with: "").trimmingCharacters(in: .whitespaces))
        if needsNumber && (number == nil || number! <= 0) {
            error = kind == .dailyMove ? "Enter a percentage, for example 5." : "Enter a target price greater than zero."
            return
        }
        submitting = true
        error = nil
        let request = client.alertRequest(instrument: instrument, type: kind, threshold: number.map { KotlinDouble(value: $0) },
                                          direction: direction, timing: timing, repeatEveryCross: repeatEveryCross, choice: choice)
        Task {
            let outcome = try? await client.createAlert(request: request)
            submitting = false
            switch outcome?.status {
            case "CREATED": onDone()
            case "ALREADY_MET": alreadyMet = outcome?.message
            default: error = outcome?.message ?? "Couldn't create the alert."
            }
        }
    }
}

/// My Alerts: Active / Triggered / Paused, history with delivery status, and how alerts are checked.
struct AlertsScene: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.openURL) private var openURL
    let model: WatchlistsModel
    let symbol: String?
    let onOpenStock: (String) -> Void
    /// 5D.5.6D: alerts need an account. A guest gets the sign-in card (Android parity) instead of placeholders that never finished loading.
    var signedIn = true
    var onSignIn: () -> Void = {}
    @State private var filter: AlertFilter = .active
    @State private var pushAllowed = true
    @State private var confirmDelete: AlertCardModel?

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let rules = (model.user?.alerts ?? []).filter { symbol == nil || $0.instrument.symbol == symbol }
        let history = (model.user?.history ?? []).filter { symbol == nil || $0.symbol == symbol }
        let cards = model.client?.alertCards(alerts: rules, filter: filter) ?? []
        let rows = model.client?.alertHistory(events: history) ?? []
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                if !signedIn {
                    VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                        Text("Sign in for more").font(StockStepsTheme.font(type.cardTitle)).foregroundStyle(colors.textTitle).accessibilityAddTraits(.isHeader)
                        StockSectionMessage(message: "Sign in to create price, earnings and news alerts.", actionTitle: "Sign in", action: onSignIn)
                    }
                    .stockCard(padding: CGFloat(space.md), bordered: false)
                } else {
                if !pushAllowed {
                    VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                        Text("Notifications are off on this device. Your alerts still run and appear in the history below.")
                            .font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
                        Button("Turn on notifications") { Task { pushAllowed = await PushCoordinator.shared.requestAuthorization() } }
                            .font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(colors.primaryText).frame(minHeight: CGFloat(dims.touchTarget))
                    }
                    .padding(CGFloat(space.md)).frame(maxWidth: .infinity, alignment: .leading)
                    .background(colors.warningContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                }
                StockPillSelector(options: AlertFilter.entries, selected: filter, label: { option in
                    "\(option.label) \(rules.filter { $0.status.name == option.name }.count)"
                }) { filter = $0 }
                if model.user?.alertsLoaded != true {
                    if let error = model.user?.alertsError {
                        StockSectionMessage(message: error, actionTitle: "Try again") { Task { try? await model.client?.refreshUserData() } }.stockCard(padding: CGFloat(space.md), bordered: false)
                    } else {
                        ForEach(0..<3, id: \.self) { _ in StockRowSkeleton() }
                    }
                } else if cards.isEmpty {
                    StockSectionMessage(message: "No \(filter.label.lowercased()) alerts.").stockCard(padding: CGFloat(space.md), bordered: false)
                } else {
                    ForEach(cards, id: \.id) { card in alertCard(card, colors) }
                }
                if let note = model.user?.deliveryNote {
                    Text(note).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
                }
                if !rows.isEmpty {
                    StockSectionHeader(title: "Alert history").padding(.top, CGFloat(space.lg))
                    ForEach(rows, id: \.id) { row in
                        Button { if let url = row.sourceUrl.flatMap(URL.init(string:)) { openURL(url) } } label: {
                            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                                Text(row.title).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary)
                                Text(row.body).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody).multilineTextAlignment(.leading)
                                Text("\(row.time) · \(row.delivery)").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .stockCard(padding: CGFloat(space.md), bordered: false)
                        }
                        .buttonStyle(.plain)
                        .disabled(row.sourceUrl == nil)
                        .accessibilityElement(children: .combine)
                    }
                }
                }
            }
            .frame(maxWidth: CGFloat(dims.contentMaxWidth), alignment: .leading)
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
            .frame(maxWidth: .infinity)
        }
        .refreshable { try? await model.client?.refreshUserData() }
        .background(colors.appBackground.ignoresSafeArea())
        .navigationTitle(symbol.map { "\($0) alerts" } ?? "My Alerts")
        .navigationBarTitleDisplayMode(.inline)
        .task { pushAllowed = await PushCoordinator.shared.enabled }
        .confirmationDialog("Delete this alert?", isPresented: Binding(get: { confirmDelete != nil }, set: { if !$0 { confirmDelete = nil } }), titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                if let card = confirmDelete { model.perform { try await $0.deleteAlert(id: card.id) } }
                confirmDelete = nil
            }
        }
        .alert(model.message ?? "", isPresented: Binding(get: { model.message != nil }, set: { if !$0 { model.message = nil } })) {
            Button("OK", role: .cancel) { model.message = nil }
        }
    }

    private func alertCard(_ card: AlertCardModel, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            HStack {
                Button(card.symbol) { onOpenStock(card.symbol) }.font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.primaryText)
                Spacer()
                StockBadge(text: card.statusLabel, tone: card.waitingForCross ? .caution : card.status == .active ? .positive : .neutral)
            }
            Text(card.title).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textPrimary)
            Text(card.detail).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
            HStack(spacing: CGFloat(space.md)) {
                if card.status == .active {
                    Button("Pause") { model.perform { try await $0.setAlertStatus(id: card.id, status: .paused) } }
                } else {
                    Button(card.status == .triggered ? "Turn on again" : "Resume") { model.perform { try await $0.setAlertStatus(id: card.id, status: .active) } }
                }
                Button("Delete", role: .destructive) { confirmDelete = card }
            }
            .font(StockStepsTheme.font(type.bodyMedium))
            .frame(minHeight: CGFloat(dims.touchTarget))
            .disabled(model.busy)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .stockCard(padding: CGFloat(space.md), bordered: false)
    }
}

/// "Save to which watchlist?" when the user has more than one list (Company Details).
struct WatchlistChooser: View {
    let symbol: String
    let lists: [Watchlist]
    let onChoose: (Watchlist) -> Void
    var body: some View {
        NavigationStack {
            List(lists, id: \.id) { list in
                Button("\(list.name) (\(list.entries.count))") { onChoose(list) }.frame(minHeight: CGFloat(dims.touchTarget))
            }
            .navigationTitle("Save \(symbol) to")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium])
    }
}
