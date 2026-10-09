import Observation
import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography

/// App-wide earnings client (one per backend URL provider) plus the Markets entry summary.
@MainActor @Observable
final class EarningsModel {
    private(set) var summary: EarningsSummaryState?
    @ObservationIgnored let client: IosEarningsClient
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    init(accounts: AccountViewModel, baseURL: @escaping () -> String = { BackendSettings.currentURL }) {
        client = IosEarningsClient(baseUrl: baseURL, account: accounts.client)
    }
    /// Starts the Markets "Earnings Center" counts on first use.
    func startSummary() {
        guard subscription == nil else { return }
        subscription = client.observeSummary { [weak self] in self?.summary = $0 }
    }
    deinit {
        subscription?.cancel()
        client.close()
    }
}

/// Where the Earnings Calendar opens: a date (nil = today) and filter ("ALL" | "WATCHLIST").
struct EarningsCalendarTarget: Hashable, Identifiable {
    var date: String? = nil
    var filter: String = "ALL"
    var id: String { "\(date ?? "today")|\(filter)" }
}

/// One Earnings Calendar screen's shared presenter.
@MainActor @Observable
final class EarningsCalendarModel {
    private(set) var state: EarningsCalendarState?
    @ObservationIgnored let presenter: EarningsCalendarPresenter
    @ObservationIgnored let client: IosEarningsClient
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    init(target: EarningsCalendarTarget, client: IosEarningsClient) {
        self.client = client
        presenter = client.calendar(date: target.date, filter: target.filter)
        subscription = client.observeCalendar(presenter: presenter) { [weak self] in self?.state = $0 }
    }
    deinit {
        subscription?.cancel()
        client.release(presenter: presenter)
    }
}

/// One earnings event's shared presenter.
@MainActor @Observable
final class EarningsEventModel {
    private(set) var state: EarningsEventState?
    @ObservationIgnored let presenter: EarningsEventPresenter
    @ObservationIgnored let client: IosEarningsClient
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    init(eventId: String, client: IosEarningsClient) {
        self.client = client
        presenter = client.event(id: eventId)
        subscription = client.observeEvent(presenter: presenter) { [weak self] in self?.state = $0 }
    }
    deinit {
        subscription?.cancel()
        client.release(presenter: presenter)
    }
}

/// Company Details' next earnings date.
@MainActor @Observable
final class CompanyEarningsModel {
    private(set) var state: CompanyEarningsState?
    @ObservationIgnored let presenter: CompanyEarningsPresenter
    @ObservationIgnored let client: IosEarningsClient
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    init(symbol: String, client: IosEarningsClient) {
        self.client = client
        presenter = client.company(symbol: symbol)
        subscription = client.observeCompany(presenter: presenter) { [weak self] in self?.state = $0 }
    }
    deinit {
        subscription?.cancel()
        client.release(presenter: presenter)
    }
}

/// One company's Earnings Details state (shared presenter).
@MainActor @Observable
final class EarningsDetailsModel {
    private(set) var state: EarningsDetailsState?
    @ObservationIgnored let presenter: EarningsDetailsPresenter
    @ObservationIgnored let client: IosEarningsClient
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    init(symbol: String, client: IosEarningsClient) {
        self.client = client
        presenter = client.details(symbol: symbol)
        subscription = client.observeDetails(presenter: presenter) { [weak self] in self?.state = $0 }
    }
    deinit {
        subscription?.cancel()
        client.release(presenter: presenter)
    }
}

struct IdentifiedTopic: Identifiable {
    let topic: EarningsEducation.Topic
    var id: String { topic.key }
}

private func ymd(_ date: Date) -> String {
    let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
    return String(format: "%04d-%02d-%02d", c.year ?? 1970, c.month ?? 1, c.day ?? 1)
}
private func dateFrom(_ ymd: String) -> Date {
    let p = ymd.split(separator: "-").compactMap { Int($0) }
    guard p.count == 3 else { return Date() }
    return Calendar.current.date(from: DateComponents(year: p[0], month: p[1], day: p[2])) ?? Date()
}

// MARK: - Earnings Calendar

struct EarningsCalendarScene: View {
    @State private var model: EarningsCalendarModel
    let onOpen: (String) -> Void
    var onSignIn: () -> Void = {}
    var onOpenResults: (String) -> Void = { _ in }

    init(target: EarningsCalendarTarget, client: IosEarningsClient, onOpen: @escaping (String) -> Void, onSignIn: @escaping () -> Void = {},
         onOpenResults: @escaping (String) -> Void = { _ in }) {
        _model = State(initialValue: EarningsCalendarModel(target: target, client: client))
        self.onOpen = onOpen
        self.onSignIn = onSignIn
        self.onOpenResults = onOpenResults
    }

    var body: some View {
        EarningsCalendarScreen(state: model.state, presenter: model.presenter, client: model.client, onOpen: onOpen, onSignIn: onSignIn, onOpenResults: onOpenResults)
            .navigationTitle("Earnings")
            .navigationBarTitleDisplayMode(.inline)
            .refreshable { model.presenter.refresh() }
    }
}

/// Week strip with day counts, Upcoming/Reported, All/My Watchlist, day or week view and search.
/// Cards show dates, timing and status only (no figures). Same presenter as Android.
struct EarningsCalendarScreen: View {
    let state: EarningsCalendarState?
    let presenter: EarningsCalendarPresenter
    let client: IosEarningsClient
    let onOpen: (String) -> Void
    let onSignIn: () -> Void
    var onOpenResults: (String) -> Void = { _ in }
    @Environment(\.colorScheme) private var scheme
    @State private var showHelp = false
    @State private var picking = false
    @State private var pickedDate = Date()

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                Text("Earnings Calendar").font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).accessibilityAddTraits(.isHeader)
                Text("See when companies are expected to report earnings.").font(.subheadline).foregroundStyle(colors.textSecondary)
                Button("What are earnings?", systemImage: "questionmark.circle") { showHelp = true }.font(.subheadline)
                if let state {
                    if state.sampleData {
                        Text("Sample earnings data for development, not real announcements.").font(.caption).foregroundStyle(colors.cautionText)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack {
                                ForEach(client.scenarios, id: \.self) { pair in
                                    StockChip(title: pair[1], selected: (state.scenario ?? "") == pair[0]) { client.setScenario(presenter: presenter, id: pair[0]) }
                                }
                            }
                        }
                    }
                    weekStrip(state, colors)
                    Picker("Section", selection: Binding(get: { state.selection.tab.name }, set: { client.selectTab(presenter: presenter, name: $0) })) {
                        Text("Upcoming").tag("UPCOMING"); Text("Reported").tag("REPORTED")
                    }.pickerStyle(.segmented)
                    HStack {
                        StockChip(title: "All Companies", selected: state.selection.filter.name == "ALL") { client.selectFilter(presenter: presenter, name: "ALL") }
                        StockChip(title: "My Watchlist", selected: state.selection.filter.name == "WATCHLIST") { client.selectFilter(presenter: presenter, name: "WATCHLIST") }
                    }
                    if !state.searching {
                        Picker("Show", selection: Binding(get: { state.selection.mode.name }, set: { client.selectMode(presenter: presenter, name: $0) })) {
                            Text("Selected day").tag("DAY"); Text("Whole week").tag("WEEK")
                        }.pickerStyle(.segmented)
                    }
                    HStack {
                        Image(systemName: "magnifyingglass").foregroundStyle(colors.iconSecondary).accessibilityHidden(true)
                        TextField("Search by company or ticker", text: Binding(get: { state.selection.query }, set: { presenter.setQuery(text: $0) }))
                            .textInputAutocapitalization(.never).autocorrectionDisabled()
                        if !state.selection.query.isEmpty {
                            Button { presenter.clearQuery() } label: { Image(systemName: "xmark.circle.fill") }.accessibilityLabel("Clear search")
                        }
                    }
                    .padding(.horizontal, CGFloat(space.md)).frame(minHeight: 48)
                    .background(colors.surfaceSecondary, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.chip)))
                    if let range = state.rangeText { Text(range).font(.caption).foregroundStyle(colors.textSecondary) }
                    if let freshness = state.freshnessText { Text(freshness).font(.caption).foregroundStyle(colors.cautionText) }
                    if state.sourceStale { Text("Some dates haven't been updated by the data provider recently and may have changed.").font(.caption).foregroundStyle(colors.cautionText) }
                    if state.loading { ProgressView().accessibilityLabel("Loading earnings") }
                    if let error = state.error {
                        StockSectionMessage(message: error, actionTitle: "Try again") { presenter.refresh() }
                    }
                    if let empty = state.emptyMessage {
                        StockSectionMessage(message: empty, actionTitle: state.needsSignIn ? "Sign in" : nil, action: state.needsSignIn ? onSignIn : nil)
                    }
                    ForEach(Array(state.days.enumerated()), id: \.offset) { _, day in
                        let date = day.first as? String ?? ""
                        Text(client.date(date: date)).font(.subheadline.weight(.semibold)).foregroundStyle(colors.textSecondary)
                            .accessibilityAddTraits(.isHeader).accessibilityLabel(client.spokenDate(date: date)).padding(.top, 6)
                        ForEach((day.second as? [EarningsEventRow]) ?? [], id: \.id) { row in
                            Button { onOpen(row.id) } label: { card(row, colors) }
                                .buttonStyle(.plain)
                                .accessibilityElement(children: .ignore)
                                .accessibilityLabel(row.accessibility)
                                .accessibilityHint("Opens the earnings event")
                                .onAppear { if row.id == state.rows.last?.id && state.hasMore { presenter.loadMore() } }
                            // Only verified reported events with a published report link to results.
                            if let reportId = row.reportId {
                                Button("View Results") { onOpenResults(reportId) }.frame(minHeight: 48)
                                    .accessibilityLabel("View \(row.symbol) earnings results")
                            }
                        }
                    }
                    if state.loadingMore { ProgressView().accessibilityLabel("Loading more earnings") }
                    ForEach(state.notes, id: \.self) { Text($0).font(.caption).foregroundStyle(colors.textSecondary) }
                    Text("Not investment advice. Report dates can change until a company confirms them.").font(.caption).foregroundStyle(colors.textTertiary)
                }
            }
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
        }
        .background(colors.appBackground)
        .alert("What are earnings?", isPresented: $showHelp) { Button("Got it", role: .cancel) {} } message: { Text(client.whatAreEarnings) }
        .sheet(isPresented: $picking) {
            NavigationStack {
                DatePicker("Date", selection: $pickedDate, displayedComponents: .date).datePickerStyle(.graphical).padding()
                    .navigationTitle("Choose date").navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) { Button("Cancel") { picking = false } }
                        ToolbarItem(placement: .confirmationAction) { Button("Show date") { presenter.selectDate(date: ymd(pickedDate)); picking = false } }
                    }
            }
            .presentationDetents([.large])
        }
    }

    @ViewBuilder private func weekStrip(_ state: EarningsCalendarState, _ colors: StockColors) -> some View {
        VStack(spacing: CGFloat(space.xs)) {
            HStack {
                Button { presenter.previousWeek() } label: { Image(systemName: "chevron.left").frame(minWidth: 48, minHeight: 48) }.accessibilityLabel("Previous week")
                Text(state.weekLabel).font(.headline).frame(maxWidth: .infinity)
                    .accessibilityAddTraits(.isHeader).accessibilityLabel("Week of \(state.weekLabel)")
                Button { presenter.nextWeek() } label: { Image(systemName: "chevron.right").frame(minWidth: 48, minHeight: 48) }.accessibilityLabel("Next week")
            }
            HStack(spacing: CGFloat(space.xxs)) {
                ForEach(state.week, id: \.date) { day in
                    Button { presenter.selectDate(date: day.date) } label: { dayCell(day, colors) }
                        .buttonStyle(.plain)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(day.accessibility)
                        .accessibilityAddTraits(day.selected ? [.isSelected, .isButton] : [.isButton])
                }
            }
            HStack {
                Button("Today") { presenter.goToToday() }.disabled(state.selection.date == state.today).frame(minHeight: 48)
                Button("Choose date") { pickedDate = dateFrom(state.selection.date); picking = true }.frame(minHeight: 48)
                Spacer()
            }
        }
    }

    /// Selected: filled and underlined (not color alone); today: outlined; counts as numbers.
    private func dayCell(_ day: WeekDayView, _ colors: StockColors) -> some View {
        let shape = RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.chip))
        let fg = day.selected ? colors.onPrimary : colors.textPrimary
        return VStack(spacing: 2) {
            Text(day.weekday).font(.caption2).foregroundStyle(day.selected ? colors.onPrimary : colors.textSecondary)
            Text(day.day).font(.body.weight(.semibold)).foregroundStyle(fg)
            Text(day.count.map { $0.intValue > 0 ? "\($0.intValue)" : " " } ?? " ").font(.caption2).foregroundStyle(day.selected ? colors.onPrimary : colors.primaryText)
            Rectangle().fill(day.selected ? colors.onPrimary : Color.clear).frame(width: 16, height: 2)
        }
        .lineLimit(1).minimumScaleFactor(0.7)
        .frame(maxWidth: .infinity, minHeight: 48)
        .padding(.vertical, CGFloat(space.xs))
        .background(day.selected ? colors.primaryDark : colors.surfaceSecondary, in: shape)
        .overlay(shape.stroke(day.today && !day.selected ? colors.primary : Color.clear, lineWidth: 1))
    }

    private func card(_ row: EarningsEventRow, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: CGFloat(space.sm)) {
                StockTickerAvatar(symbol: row.symbol, logoUrl: row.logoUrl, size: CGFloat(StockStepsTheme.dimensions.logoCompact))
                VStack(alignment: .leading) {
                    Text(row.name).font(.headline).foregroundStyle(colors.textPrimary).lineLimit(1)
                    Text([row.symbol, row.exchange].compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(colors.textSecondary)
                }
                Spacer()
                Text(row.status.label).font(.caption2).padding(.horizontal, 8).padding(.vertical, 2)
                    .background(colors.surfaceSecondary, in: Capsule())
            }
            Text("\(row.dateText) · \(row.timingText)").font(.caption).foregroundStyle(colors.textSecondary)
            if row.status.name == "SCHEDULED", let detail = row.statusText.components(separatedBy: " · ").last {
                Text(detail).font(.caption).foregroundStyle(colors.textSecondary)
            }
            if let previous = row.previousDate { Text("Date moved from \(client.date(date: previous))").font(.caption).foregroundStyle(colors.cautionText) }
            if row.watchlisted { Label("On your watchlist", systemImage: "star.fill").font(.caption).foregroundStyle(colors.textSecondary) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .stockCard()
    }
}

// MARK: - Earnings Event Details

struct EarningsEventScene: View {
    @State private var model: EarningsEventModel
    let accounts: AccountViewModel
    let onCompany: (String) -> Void
    let onCalendar: (String?) -> Void
    let onResults: (String) -> Void
    var onSignIn: () -> Void = {}

    init(eventId: String, client: IosEarningsClient, accounts: AccountViewModel, onCompany: @escaping (String) -> Void,
         onCalendar: @escaping (String?) -> Void, onResults: @escaping (String) -> Void, onSignIn: @escaping () -> Void = {}) {
        _model = State(initialValue: EarningsEventModel(eventId: eventId, client: client))
        self.accounts = accounts
        self.onCompany = onCompany
        self.onCalendar = onCalendar
        self.onResults = onResults
        self.onSignIn = onSignIn
    }

    var body: some View {
        EarningsEventScreen(state: model.state, client: model.client, accounts: accounts, onRetry: { model.presenter.refresh() },
                            onCompany: onCompany, onCalendar: onCalendar, onResults: onResults, onSignIn: onSignIn)
            .navigationTitle("Earnings event")
            .navigationBarTitleDisplayMode(.inline)
            .refreshable { model.presenter.refresh() }
    }
}

/// One event: date, timing, status and provenance, a beginner explanation and existing actions. No figures.
struct EarningsEventScreen: View {
    let state: EarningsEventState?
    let client: IosEarningsClient
    let accounts: AccountViewModel
    let onRetry: () -> Void
    let onCompany: (String) -> Void
    let onCalendar: (String?) -> Void
    let onResults: (String) -> Void
    let onSignIn: () -> Void
    @Environment(\.colorScheme) private var scheme
    @State private var topic: EarningsEducation.Topic?

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.md)) {
                if let state {
                    if state.loading { ProgressView().accessibilityLabel("Loading earnings event") }
                    if let error = state.error { StockSectionMessage(message: error, actionTitle: "Try again", action: onRetry) }
                    if state.status != nil { content(state, colors) }
                }
            }
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
        }
        .background(colors.appBackground)
        .sheet(item: Binding(get: { topic.map { IdentifiedTopic(topic: $0) } }, set: { topic = $0?.topic })) { item in
            NavigationStack { ScrollView { Text(item.topic.body).padding() }.navigationTitle(item.topic.title).navigationBarTitleDisplayMode(.inline) }
                .presentationDetents([.medium])
        }
    }

    @ViewBuilder private func content(_ state: EarningsEventState, _ colors: StockColors) -> some View {
        HStack(spacing: CGFloat(space.sm)) {
            StockTickerAvatar(symbol: state.symbol, logoUrl: state.logoUrl)
            VStack(alignment: .leading) {
                Text(state.name).font(StockStepsTheme.font(type.screenTitle, relativeTo: .title1)).lineLimit(3).accessibilityAddTraits(.isHeader)
                Text([state.symbol, state.exchange, state.period].compactMap { $0 }.joined(separator: " · ")).font(.subheadline).foregroundStyle(colors.textSecondary)
            }
        }
        if state.sampleData { Text("Sample earnings data for development, not real announcements.").font(.caption).foregroundStyle(colors.cautionText) }
        if let freshness = state.freshnessText { Text(freshness).font(.caption).foregroundStyle(colors.cautionText) }
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text("Earnings report").font(.headline).accessibilityAddTraits(.isHeader)
            line("Date", state.dateText ?? "", colors).accessibilityLabel("Date: \(state.spokenDate ?? "")")
            line("Expected timing", state.timingText ?? "", colors)
            line("Status", state.statusText ?? "", colors)
            if let previous = state.previousDate { Text("Date moved from \(client.date(date: previous))").font(.caption).foregroundStyle(colors.cautionText) }
            if let explanation = state.statusExplanation { Text(explanation).font(.subheadline).foregroundStyle(colors.textBody) }
            // Only a verified report links to Earnings Results; never a placeholder.
            if let reportId = state.reportId { Button("View Results") { onResults(reportId) }.buttonStyle(.bordered).frame(minHeight: 48) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .stockCard()
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text("What is an earnings report?").font(.headline).accessibilityAddTraits(.isHeader)
            Text(client.eventExplanation).font(.body).foregroundStyle(colors.textBody)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(CGFloat(space.cardPadding))
        .background(colors.educationContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Button { onCompany(state.symbol) } label: { Text("View Company Details").frame(maxWidth: .infinity, minHeight: 48) }.buttonStyle(.borderedProminent)
            // The same watchlist as Company Details and the Watchlist tab.
            if !accounts.state.initializing {
                let saved = accounts.state.items.contains { $0.symbol == state.symbol }
                Button {
                    let stock = StockSearchResult(symbol: state.symbol, name: state.name, currency: nil, exchange: state.exchange, exchangeFullName: nil)
                    Task { await accounts.toggle(stock: stock) }
                } label: {
                    Label(saved ? "Remove from Watchlist" : "Add to Watchlist", systemImage: saved ? "star.fill" : "star").frame(maxWidth: .infinity, minHeight: 48)
                }.buttonStyle(.bordered)
            } else {
                Button { onSignIn() } label: { Text("Sign in to use watchlists").frame(maxWidth: .infinity, minHeight: 48) }.buttonStyle(.bordered)
            }
            Button("Learn About Earnings", systemImage: "lightbulb") { topic = client.topic(key: "quarterly") }.frame(minHeight: 48)
            Button("View in Earnings Calendar") { onCalendar(state.date) }.frame(minHeight: 48)
        }
        VStack(alignment: .leading, spacing: 2) {
            ForEach([state.updatedText, state.sourceText].compactMap { $0 }, id: \.self) { Text($0).font(.caption).foregroundStyle(colors.textSecondary) }
            ForEach(state.notes, id: \.self) { Text($0).font(.caption).foregroundStyle(colors.textSecondary) }
            Text("Not investment advice. Earnings dates don't predict how a stock will move.").font(.caption).foregroundStyle(colors.textTertiary)
        }
    }

    private func line(_ label: String, _ value: String, _ colors: StockColors) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label).font(.subheadline).foregroundStyle(colors.textSecondary)
            Spacer()
            Text(value).font(.subheadline.weight(.semibold)).foregroundStyle(colors.textPrimary).multilineTextAlignment(.trailing)
        }
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Earnings Details

struct EarningsDetailsScene: View {
    @State private var model: EarningsDetailsModel
    let onCompany: () -> Void
    var onSignIn: () -> Void = {}
    var onUpgrade: () -> Void = {}

    init(symbol: String, client: IosEarningsClient, onCompany: @escaping () -> Void, onSignIn: @escaping () -> Void = {}, onUpgrade: @escaping () -> Void = {}) {
        _model = State(initialValue: EarningsDetailsModel(symbol: symbol, client: client))
        self.onCompany = onCompany
        self.onSignIn = onSignIn
        self.onUpgrade = onUpgrade
    }

    var body: some View {
        EarningsDetailsScreen(state: model.state, presenter: model.presenter, client: model.client, onCompany: onCompany, onSignIn: onSignIn, onUpgrade: onUpgrade)
            .navigationTitle("Earnings")
            .navigationBarTitleDisplayMode(.inline)
            .refreshable { model.presenter.refresh() }
    }
}

struct EarningsDetailsScreen: View {
    let state: EarningsDetailsState?
    let presenter: EarningsDetailsPresenter
    let client: IosEarningsClient
    let onCompany: () -> Void
    let onSignIn: () -> Void
    let onUpgrade: () -> Void
    @Environment(\.colorScheme) private var scheme
    @State private var topic: EarningsEducation.Topic?
    @State private var expanded: Set<String> = []
    @State private var showReminder = false
    @State private var question = ""

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.md)) {
                if let state { content(state, colors) } else { ProgressView() }
            }
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
        }
        .background(colors.appBackground)
        .sheet(item: Binding(get: { topic.map { IdentifiedTopic(topic: $0) } }, set: { topic = $0?.topic })) { item in
            NavigationStack { ScrollView { Text(item.topic.body).padding() }.navigationTitle(item.topic.title).navigationBarTitleDisplayMode(.inline) }
                .presentationDetents([.medium])
        }
        .sheet(isPresented: $showReminder) { if let state { ReminderSheet(state: state, presenter: presenter, client: client) { showReminder = false } } }
        .alert("StockSteps+", isPresented: Binding(get: { state?.upgradeRequired == true }, set: { if !$0 { presenter.dismissUpgrade() } })) {
            Button("See StockSteps+") { presenter.dismissUpgrade(); onUpgrade() }
            Button("Not now", role: .cancel) { presenter.dismissUpgrade() }
        } message: { Text("AI earnings research is part of StockSteps+. Nothing was sent.") }
        .alert("", isPresented: Binding(get: { state?.message != nil }, set: { if !$0 { presenter.dismissMessage() } })) {
            Button("OK") { presenter.dismissMessage() }
        } message: { Text(state?.message ?? "") }
    }

    @ViewBuilder private func content(_ state: EarningsDetailsState, _ colors: StockColors) -> some View {
        let d = state.details
        VStack(alignment: .leading, spacing: 4) {
            Text(d?.name ?? state.symbol).font(StockStepsTheme.font(type.screenTitle, relativeTo: .title1)).accessibilityAddTraits(.isHeader)
            Text([state.symbol, d?.event?.exchange, state.headerPeriod].compactMap { $0 }.joined(separator: " · ")).font(.subheadline).foregroundStyle(colors.textSecondary)
            if let d { Text(d.status.label).font(.caption).padding(.horizontal, 8).padding(.vertical, 2).background(colors.surfaceSecondary, in: Capsule()) }
            let header = [state.headerDate, state.headerSession, state.headerDateStatus].compactMap { $0 }
            if !header.isEmpty { Text(header.joined(separator: " · ")).font(.subheadline).foregroundStyle(colors.textSecondary) }
            if let next = state.next { Text(next).font(.caption).foregroundStyle(colors.primary) }
            if d?.sampleData == true { Text("Sample earnings data for development, not real results.").font(.caption).foregroundStyle(colors.textSecondary) }
            if d?.stale == true { Text("The next date hasn't been updated recently and may have changed.").font(.caption).foregroundStyle(colors.cautionText) }
            HStack {
                Button(state.reminder != nil ? "Reminder on" : "Remind me") { if state.signedIn { showReminder = true } else { onSignIn() } }
                    .buttonStyle(.bordered).disabled(state.reminderBusy)
                Button("Company details", action: onCompany)
            }
        }
        if state.loading { ProgressView().accessibilityLabel("Loading earnings") }
        if let error = state.error { Text(error).foregroundStyle(colors.textSecondary); Button("Try again") { presenter.refresh() } }
        if let summary = d?.summary {
            VStack(alignment: .leading, spacing: 4) {
                Text(summary).font(.headline).accessibilityAddTraits(.isHeader)
                if let explanation = d?.summaryExplanation { Text(explanation).font(.subheadline) }
            }.frame(maxWidth: .infinity, alignment: .leading).stockCard()
        }
        ForEach([state.eps, state.revenue].compactMap { $0 }, id: \.title) { card in
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(card.title).font(.headline)
                    Spacer()
                    // Words and a neutral accent: a beat isn't presented as good for the stock.
                    Text(card.headline).font(.subheadline.weight(.semibold)).foregroundStyle(card.classification.name == "UNAVAILABLE" ? colors.textSecondary : colors.primary)
                }
                ForEach(card.lines, id: \.label) { line($0, colors) }
                if let note = card.note { Text(note).font(.caption).foregroundStyle(colors.textSecondary) }
                Button("What does this mean?") { topic = client.topic(key: card.title == "EPS" ? "surprise" : "revenue") }.font(.caption)
            }
            .frame(maxWidth: .infinity, alignment: .leading).stockCard()
            .accessibilityElement(children: .combine)
        }
        if state.eps == nil, let next = d?.next {
            VStack(alignment: .leading, spacing: 4) {
                Text("Not reported yet").font(.headline)
                Text("Analysts expect EPS of \(next.estimate?.eps.map { String(format: "%.2f", $0.doubleValue) } ?? "—") and revenue of \(next.estimate?.revenue.map { String(format: "%.2fB", $0.doubleValue / 1e9) } ?? "—").")
                    .font(.subheadline)
            }.frame(maxWidth: .infinity, alignment: .leading).stockCard()
        }
        if !state.reaction.isEmpty || state.reactionSentence != nil {
            VStack(alignment: .leading, spacing: 4) {
                HStack { Text("Price reaction").font(.headline); Spacer(); Button("How it's measured") { topic = client.topic(key: "reaction") }.font(.caption) }
                ForEach(state.reaction, id: \.label) { line($0, colors) }
                if let sentence = state.reactionSentence { Text(sentence).font(.subheadline) }
            }.frame(maxWidth: .infinity, alignment: .leading).stockCard()
        }
        if let d, !d.insights.isEmpty || d.lockedInsights > 0 {
            Text("Insights").font(.title3.weight(.semibold)).accessibilityAddTraits(.isHeader)
            ForEach(d.insights, id: \.id) { insight in
                StockInsightCard(title: insight.title, message: insight.explanation, actionTitle: "Learn more") { topic = client.topic(key: insight.methodology) }
            }
            if d.lockedInsights > 0 { Text("\(d.lockedInsights) more trend insight\(d.lockedInsights == 1 ? "" : "s") with StockSteps+.").font(.caption).foregroundStyle(colors.textSecondary) }
        }
        if !state.history.isEmpty {
            Text("Earnings history").font(.title3.weight(.semibold)).accessibilityAddTraits(.isHeader)
            ForEach(state.history, id: \.id) { row in
                let open = expanded.contains(row.id)
                Button { if open { expanded.remove(row.id) } else { expanded.insert(row.id) } } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack { Text(row.period).font(.subheadline.weight(.semibold)); Spacer(); Text("EPS \(row.eps)").font(.caption).foregroundStyle(colors.textSecondary) }
                        Text("Revenue \(row.revenue)\(row.growth.map { " · \($0) YoY" } ?? "")").font(.caption).foregroundStyle(colors.textSecondary)
                        if open { ForEach(row.lines, id: \.label) { line($0, colors) } }
                        Divider()
                    }.frame(minHeight: 44)
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(row.accessibility)
                .accessibilityValue(open ? "Expanded" : "Collapsed")
            }
            if d?.historyLocked == true { Text("Older quarters are available with StockSteps+.").font(.caption).foregroundStyle(colors.textSecondary) }
        }
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Ask about these earnings").font(.headline)
                Spacer()
                if !state.plus { Text("StockSteps+").font(.caption2).padding(.horizontal, 8).padding(.vertical, 2).background(colors.surfaceSecondary, in: Capsule()) }
            }
            Text("Answers use only the verified figures above and say when the data can't explain a cause.").font(.caption).foregroundStyle(colors.textSecondary)
            TextField("e.g. Explain this earnings report", text: $question).textFieldStyle(.roundedBorder)
            Button(state.plus ? "Ask" : "Ask with StockSteps+") { presenter.ask(question: question) }
                .buttonStyle(.borderedProminent).disabled(question.trimmingCharacters(in: .whitespaces).isEmpty || state.asking)
            if state.asking { ProgressView() }
            if let answer = state.answer {
                Text(answer.answer).font(.subheadline)
                if !answer.sources.isEmpty { Text("Sources: " + answer.sources.joined(separator: ", ")).font(.caption).foregroundStyle(colors.textSecondary) }
            }
        }.frame(maxWidth: .infinity, alignment: .leading).stockCard()
        Text("Learn").font(.title3.weight(.semibold)).accessibilityAddTraits(.isHeader)
        ForEach(client.topics, id: \.key) { t in Button(t.title) { topic = t } }
        ForEach(d?.notes ?? [], id: \.self) { Text($0).font(.caption).foregroundStyle(colors.textSecondary) }
        Text("Not investment advice. Earnings results don't predict how a stock will move.").font(.caption2).foregroundStyle(colors.textTertiary)
    }

    private func line(_ line: MetricLine, _ colors: StockColors) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(line.label).font(.subheadline).foregroundStyle(colors.textSecondary)
            Spacer()
            VStack(alignment: .trailing) {
                Text(line.value).font(.subheadline.monospacedDigit())
                if let detail = line.detail { Text(detail).font(.caption2).foregroundStyle(colors.textTertiary) }
            }
        }
        .accessibilityElement(children: .combine)
    }
}

private struct ReminderSheet: View {
    let state: EarningsDetailsState
    let presenter: EarningsDetailsPresenter
    let client: IosEarningsClient
    let onDone: () -> Void
    @State private var timing = "BOTH"
    @State private var lead = 1
    @State private var results = false

    var body: some View {
        NavigationStack {
            Form {
                Picker("Remind me", selection: $timing) {
                    Text("Day before").tag("DAY_BEFORE"); Text("On the day").tag("DAY_OF"); Text("Both").tag("BOTH")
                }
                Section("StockSteps+ options") {
                    Stepper("Days before: \(lead)", value: $lead, in: 1...7).disabled(!state.plus)
                    Toggle("Notify when results are out", isOn: $results).disabled(!state.plus)
                    if !state.plus { Text("Day-before and day-of reminders are free. Earlier reminders and results notifications are part of StockSteps+.").font(.caption) }
                }
                Section {
                    Text("If the date is only estimated or the time isn't announced, reminders use the date and may move if the company changes it. You won't get a reminder for both the old and new date.")
                        .font(.caption)
                }
                if state.reminder != nil {
                    Button("Turn off reminder", role: .destructive) { client.setReminder(presenter: presenter, timing: nil, leadDays: 1, results: false); onDone() }
                }
            }
            .navigationTitle("Earnings reminder")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: onDone) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { client.setReminder(presenter: presenter, timing: timing, leadDays: Int32(state.plus ? lead : 1), results: state.plus && results); onDone() }
                }
            }
            .onAppear {
                timing = state.reminder?.earningsTiming?.name ?? "BOTH"
                lead = Int(truncating: state.reminder?.earningsLeadDays ?? 1)
                results = state.reminder?.earningsResults ?? false
            }
        }
    }
}

// MARK: - Earnings Results (Phase 2)

/// One report's shared presenter (calculations come from the server).
@MainActor @Observable
final class EarningsResultsModel {
    private(set) var state: EarningsResultsState?
    @ObservationIgnored let presenter: EarningsResultsPresenter
    @ObservationIgnored let client: IosEarningsClient
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    init(reportId: String, client: IosEarningsClient) {
        self.client = client
        presenter = client.results(reportId: reportId)
        subscription = client.observeResults(presenter: presenter) { [weak self] in self?.state = $0 }
    }
    deinit {
        subscription?.cancel()
        client.release(presenter: presenter)
    }
}

struct EarningsResultsScene: View {
    @State private var model: EarningsResultsModel
    let reportId: String
    let onCompany: (String) -> Void
    let onLearn: () -> Void

    init(reportId: String, client: IosEarningsClient, onCompany: @escaping (String) -> Void, onLearn: @escaping () -> Void) {
        _model = State(initialValue: EarningsResultsModel(reportId: reportId, client: client))
        self.reportId = reportId
        self.onCompany = onCompany
        self.onLearn = onLearn
    }

    var body: some View {
        EarningsResultsScreen(state: model.state, onRetry: { model.presenter.refresh() }, onToggle: { model.presenter.toggle(key: $0) },
                              onCompany: { onCompany(model.client.symbolOf(reportId: reportId)) }, onLearn: onLearn)
            .navigationTitle("Earnings results")
            .navigationBarTitleDisplayMode(.inline)
            .refreshable { model.presenter.refresh() }
    }
}

/// Header → EPS → revenue → growth → previous quarter → takeaway → learn → sources. Renders only.
struct EarningsResultsScreen: View {
    let state: EarningsResultsState?
    let onRetry: () -> Void
    let onToggle: (String) -> Void
    let onCompany: () -> Void
    let onLearn: () -> Void
    @Environment(\.colorScheme) private var scheme
    @State private var lesson: LearnLink?

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.md)) {
                if let state {
                    if state.loading || state.refreshing { ProgressView().accessibilityLabel("Loading earnings results") }
                    if state.notPublished {
                        StockSectionMessage(message: state.error ?? "Results for this period haven't been published yet.", actionTitle: "View Company Details", action: onCompany)
                    } else if let error = state.error {
                        StockSectionMessage(message: error, actionTitle: "Try again", action: onRetry)
                    }
                    if state.response != nil { content(state, colors) }
                }
            }
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
        }
        .background(colors.appBackground)
        .alert(lesson?.title ?? "", isPresented: Binding(get: { lesson != nil }, set: { if !$0 { lesson = nil } })) {
            Button("Got it", role: .cancel) { lesson = nil }
            Button("Open Learn") { lesson = nil; onLearn() }
        } message: { Text(lesson?.body ?? "") }
    }

    @ViewBuilder private func content(_ state: EarningsResultsState, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: CGFloat(space.sm)) {
                StockTickerAvatar(symbol: state.symbolLine.components(separatedBy: " ·").first ?? "", logoUrl: state.logoUrl)
                VStack(alignment: .leading) {
                    Text(state.companyName).font(.headline).foregroundStyle(colors.textPrimary).lineLimit(2)
                    Text(state.symbolLine).font(.caption).foregroundStyle(colors.textSecondary)
                }
            }
            Text(state.title).font(StockStepsTheme.font(type.screenTitle, relativeTo: .title1)).accessibilityAddTraits(.isHeader)
            ForEach(state.periodLines, id: \.self) { Text($0).font(.caption).foregroundStyle(colors.textSecondary) }
            if state.sampleData { Text("Sample earnings data for development, not real results.").font(.caption).foregroundStyle(colors.cautionText) }
            if let f = state.freshnessText { Text(f).font(.caption).foregroundStyle(colors.cautionText) }
        }
        if let eps = state.eps { comparison(eps, expanded: state.expanded.contains(eps.key), colors) }
        if let revenue = state.revenue { comparison(revenue, expanded: state.expanded.contains(revenue.key), colors) }
        if let yoy = state.yearOverYear { growth(yoy, colors) }
        if let qoq = state.quarterOverQuarter { growth(qoq, colors) }
        if let takeaway = state.takeaway {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                Text("Beginner Takeaway").font(.headline).accessibilityAddTraits(.isHeader)
                Text(takeaway).foregroundStyle(colors.textBody)
                ForEach(state.warnings, id: \.self) { Text($0).font(.caption).foregroundStyle(colors.cautionText) }
                Text("This is education, not investment advice.").font(.caption).foregroundStyle(colors.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(CGFloat(space.cardPadding))
            .background(colors.educationContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
        }
        VStack(alignment: .leading, spacing: 2) {
            Text("Learn More").font(.headline).accessibilityAddTraits(.isHeader)
            ForEach(state.learn, id: \.title) { link in
                // An existing lesson opens in place; a missing one falls back to the Learn tab.
                Button(link.title, systemImage: "lightbulb") { if link.body != nil { lesson = link } else { onLearn() } }.frame(minHeight: 48)
            }
            Button { onCompany() } label: { Text("View Company Details").frame(maxWidth: .infinity, minHeight: 48) }.buttonStyle(.bordered)
        }
        VStack(alignment: .leading, spacing: 2) {
            Text("Sources").font(.caption.weight(.semibold)).foregroundStyle(colors.textSecondary)
            ForEach(state.sources, id: \.self) { Text($0).font(.caption).foregroundStyle(colors.textSecondary) }
        }
    }

    /// One card for EPS and revenue; the classification is a word, colour is secondary.
    private func comparison(_ card: ComparisonCardView, expanded: Bool, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                HStack {
                    Text(card.title).font(.headline).foregroundStyle(colors.textPrimary)
                    Spacer()
                    Text(card.classificationText).font(.caption.weight(.semibold)).padding(.horizontal, 8).padding(.vertical, 3)
                        .foregroundStyle(card.classification.name == "BEAT" ? colors.positiveText : card.classification.name == "MISS" ? colors.negativeText : colors.textPrimary)
                        .background(card.classification.name == "BEAT" ? colors.positiveContainer : card.classification.name == "MISS" ? colors.negativeContainer : colors.surfaceSecondary, in: Capsule())
                }
                ForEach(card.lines, id: \.label) { line(line: $0, colors) }
                if let e = card.explanation { Text(e).font(.subheadline).foregroundStyle(colors.textBody) }
                if let r = card.reason { Text(r).font(.caption).foregroundStyle(colors.textSecondary) }
                if let b = card.basisNote { Text(b).font(.caption).foregroundStyle(colors.textTertiary) }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(card.accessibility)
            Button { onToggle(card.key) } label: {
                HStack { Image(systemName: "info.circle"); Text(card.infoTitle); Spacer(); Image(systemName: expanded ? "chevron.down" : "chevron.right") }
                    .frame(minHeight: 48).contentShape(Rectangle())
            }
            .buttonStyle(.plain).foregroundStyle(colors.primaryText)
            .accessibilityValue(expanded ? "Expanded" : "Collapsed")
            if expanded { Text(card.infoBody).font(.subheadline).foregroundStyle(colors.textBody) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .stockCard()
    }

    private func growth(_ card: GrowthCardView, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text(card.title).font(.headline).foregroundStyle(colors.textPrimary)
            ForEach(card.lines, id: \.label) { line(line: $0, colors) }
            Text(card.explanation).font(.subheadline).foregroundStyle(colors.textBody)
            if let r = card.reason { Text(r).font(.caption).foregroundStyle(colors.textSecondary) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .stockCard()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(card.accessibility)
    }

    private func line(line: MetricLine, _ colors: StockColors) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(line.label).font(.subheadline).foregroundStyle(colors.textSecondary)
            Spacer()
            VStack(alignment: .trailing) {
                Text(line.value).font(.subheadline.weight(.semibold)).foregroundStyle(colors.textPrimary)
                if let d = line.detail { Text(d).font(.caption2).foregroundStyle(colors.textTertiary) }
            }
        }
    }
}
