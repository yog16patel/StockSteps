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

    @ObservationIgnored let reminders: EarningsRemindersModel

    /// `start: false` defers the reminders subscription to `activate()` (see ScreenerModel).
    init(accounts: AccountViewModel, baseURL: @escaping () -> String = { BackendSettings.currentURL }, start: Bool = true) {
        client = IosEarningsClient(baseUrl: baseURL, account: accounts.client)
        reminders = EarningsRemindersModel(client: client, start: start)
    }
    func activate() { reminders.activate() }
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
    @ObservationIgnored let client: IosEarningsClient
    @ObservationIgnored private let target: EarningsCalendarTarget
    @ObservationIgnored private var made: EarningsCalendarPresenter?
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    /// Created on first use, not in `init`: SwiftUI re-runs the scene `init` on every parent update while it is shown, and each discarded copy loaded the calendar (5 identical requests per open, Phase 5C.1).
    var presenter: EarningsCalendarPresenter {
        if let made { return made }
        let created = client.calendar(date: target.date, filter: target.filter)
        made = created
        return created
    }

    init(target: EarningsCalendarTarget, client: IosEarningsClient) {
        self.client = client
        self.target = target
    }
    func activate() {
        guard subscription == nil else { return }
        subscription = client.observeCalendar(presenter: presenter) { [weak self] in self?.state = $0 }
    }
    deinit {
        subscription?.cancel()
        if let made { client.release(presenter: made) }
    }
}

/// One earnings event's shared presenter.
@MainActor @Observable
final class EarningsEventModel {
    private(set) var state: EarningsEventState?
    @ObservationIgnored let client: IosEarningsClient
    @ObservationIgnored private let eventId: String
    @ObservationIgnored private var made: EarningsEventPresenter?
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    /// Created on first use, not in `init`: see `EarningsCalendarModel` (Phase 5C.1).
    var presenter: EarningsEventPresenter {
        if let made { return made }
        let created = client.event(id: eventId)
        made = created
        return created
    }

    init(eventId: String, client: IosEarningsClient) {
        self.client = client
        self.eventId = eventId
    }
    func activate() {
        guard subscription == nil else { return }
        subscription = client.observeEvent(presenter: presenter) { [weak self] in self?.state = $0 }
    }
    deinit {
        subscription?.cancel()
        if let made { client.release(presenter: made) }
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
    @ObservationIgnored let client: IosEarningsClient
    @ObservationIgnored private let symbol: String
    @ObservationIgnored private var made: EarningsDetailsPresenter?
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    /// The presenter (which loads as soon as it exists) is created on first use, i.e. when the screen renders — not in `init`: SwiftUI builds
    /// `navigationDestination` content eagerly, which loaded `/me/earnings/{symbol}` twice for every Company Details visit (Phase 5C.1).
    var presenter: EarningsDetailsPresenter {
        if let made { return made }
        let created = client.details(symbol: symbol)
        made = created
        return created
    }

    init(symbol: String, client: IosEarningsClient) {
        self.client = client
        self.symbol = symbol
    }
    func activate() {
        guard subscription == nil else { return }
        subscription = client.observeDetails(presenter: presenter) { [weak self] in self?.state = $0 }
    }
    deinit {
        subscription?.cancel()
        if let made { client.release(presenter: made) }
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
    var reminders: EarningsRemindersModel? = nil
    var onDigest: (() -> Void)? = nil

    init(target: EarningsCalendarTarget, client: IosEarningsClient, onOpen: @escaping (String) -> Void, onSignIn: @escaping () -> Void = {},
         onOpenResults: @escaping (String) -> Void = { _ in }, reminders: EarningsRemindersModel? = nil, onDigest: (() -> Void)? = nil) {
        _model = State(initialValue: EarningsCalendarModel(target: target, client: client))
        self.onOpen = onOpen
        self.onSignIn = onSignIn
        self.onOpenResults = onOpenResults
        self.reminders = reminders
        self.onDigest = onDigest
    }

    var body: some View {
        EarningsCalendarScreen(state: model.state, presenter: model.presenter, client: model.client, onOpen: onOpen, onSignIn: onSignIn, onOpenResults: onOpenResults, reminders: reminders,
                               onDigest: onDigest)
            .navigationTitle("Earnings")
            .navigationBarTitleDisplayMode(.inline)
            .task { model.activate() }
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
    var reminders: EarningsRemindersModel? = nil
    var onDigest: (() -> Void)? = nil
    @Environment(\.colorScheme) private var scheme
    @State private var showHelp = false
    @State private var reminderTarget: ReminderTargetInfo?
    @State private var picking = false
    @State private var pickedDate = Date()

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                Text("Earnings Calendar").font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).accessibilityAddTraits(.isHeader)
                Text("See when companies are expected to report earnings.").font(.subheadline).foregroundStyle(colors.textSecondary)
                Button("What are earnings?", systemImage: "questionmark.circle") { showHelp = true }.font(.subheadline)
                // Phase 5: the StockSteps+ digest, plus the free watchlist shortcut (existing filter and Reported tab).
                if let onDigest {
                    VStack(alignment: .leading, spacing: 2) {
                        Button(action: onDigest) {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Your Earnings Digest").font(.headline).foregroundStyle(colors.textPrimary)
                                    Text("What happened this week with the companies on your watchlists.").font(.subheadline).foregroundStyle(colors.textSecondary)
                                }
                                Spacer()
                                PlusBadge()
                            }
                        }
                        .buttonStyle(.plain)
                        .accessibilityHint("Opens your earnings digest")
                        Button("Recent Earnings From Your Watchlist") {
                            client.selectFilter(presenter: presenter, name: "WATCHLIST"); client.selectTab(presenter: presenter, name: "REPORTED")
                        }
                        .frame(minHeight: 48)
                    }
                    .stockCard()
                }
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
                            if let reminders, row.status.name == "SCHEDULED" {
                                ReminderControlButton(control: reminders.control(row.id), compact: true) {
                                    reminderTarget = ReminderTargetInfo(eventId: row.id, symbol: row.symbol, name: row.name, dateText: row.dateText, timingText: row.timingText,
                                                                        dateNote: row.statusText.components(separatedBy: " · ").dropFirst().first)
                                }
                                .accessibilityLabel("Earnings reminder for \(row.symbol): \(reminders.control(row.id) == "ON" ? "on" : "off")")
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
        .sheet(item: $reminderTarget) { target in if let reminders { EarningsReminderSheet(target: target, model: reminders, onSignIn: onSignIn) { reminderTarget = nil } } }
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
    var reminders: EarningsRemindersModel? = nil
    @State private var reminderTarget: ReminderTargetInfo?

    init(eventId: String, client: IosEarningsClient, accounts: AccountViewModel, onCompany: @escaping (String) -> Void,
         onCalendar: @escaping (String?) -> Void, onResults: @escaping (String) -> Void, onSignIn: @escaping () -> Void = {}, reminders: EarningsRemindersModel? = nil) {
        _model = State(initialValue: EarningsEventModel(eventId: eventId, client: client))
        self.reminders = reminders
        self.accounts = accounts
        self.onCompany = onCompany
        self.onCalendar = onCalendar
        self.onResults = onResults
        self.onSignIn = onSignIn
    }

    var body: some View {
        EarningsEventScreen(state: model.state, client: model.client, accounts: accounts, onRetry: { model.presenter.refresh() },
                            onCompany: onCompany, onCalendar: onCalendar, onResults: onResults, onSignIn: onSignIn,
                            reminderControl: reminders.map { $0.control(model.presenter.eventId) },
                            reminderNote: reminders.flatMap { r in [r.note(model.presenter.eventId), r.state?.deliveryWarning].compactMap { $0 }.joined(separator: " ") }.flatMap { $0.isEmpty ? nil : $0 },
                            onRemind: {
                                if let st = model.state { reminderTarget = ReminderTargetInfo(eventId: model.presenter.eventId, symbol: st.symbol, name: st.name,
                                    dateText: st.dateText ?? "", timingText: st.timingText, dateNote: st.statusText?.components(separatedBy: " · ").dropFirst().first) }
                            })
            .sheet(item: $reminderTarget) { target in if let reminders { EarningsReminderSheet(target: target, model: reminders, onSignIn: onSignIn) { reminderTarget = nil } } }
            .navigationTitle("Earnings event")
            .navigationBarTitleDisplayMode(.inline)
            .task { model.activate() }
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
    var reminderControl: String? = nil
    var reminderNote: String? = nil
    var onRemind: () -> Void = {}
    @Environment(\.colorScheme) private var scheme
    @State private var topic: EarningsEducation.Topic?

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.md)) {
                if let state {
                    if state.loading { ProgressView().accessibilityLabel("Loading earnings event") }
                    if let error = state.error {
                        StockSectionMessage(message: error, actionTitle: "Try again", action: onRetry)
                        // A missing event (e.g. from an old notification) falls back to the calendar.
                        Button("Open Earnings Calendar") { onCalendar(nil) }.frame(minHeight: 48)
                    }
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
            if let reportId = state.reportId { Button("View Results") { onResults(reportId) }.buttonStyle(.stockSecondary).frame(minHeight: 48) }
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
            if let reminderControl, state.status?.name == "SCHEDULED" {
                ReminderControlButton(control: reminderControl, action: onRemind)
                if let reminderNote { Text(reminderNote).font(.caption).foregroundStyle(colors.textSecondary) }
            }
            Button { onCompany(state.symbol) } label: { Text("View Company Details").frame(maxWidth: .infinity, minHeight: 48) }.buttonStyle(.stockPrimary)
            // The same watchlist as Company Details and the Watchlist tab.
            if !accounts.state.initializing {
                let saved = accounts.state.items.contains { $0.symbol == state.symbol }
                Button {
                    let stock = StockSearchResult(symbol: state.symbol, name: state.name, currency: nil, exchange: state.exchange, exchangeFullName: nil)
                    Task { await accounts.toggle(stock: stock) }
                } label: {
                    Label(saved ? "Remove from Watchlist" : "Add to Watchlist", systemImage: saved ? "star.fill" : "star").frame(maxWidth: .infinity, minHeight: 48)
                }.buttonStyle(.stockSecondary)
            } else {
                Button { onSignIn() } label: { Text("Sign in to use watchlists").frame(maxWidth: .infinity, minHeight: 48) }.buttonStyle(.stockSecondary)
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
            .task { model.activate() }
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
    @State private var actionsWidth: CGFloat = 0
    /// Read in `body`: live Dynamic Type refresh for the token fonts, and the header action decision.
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let _ = typeSize
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
        .sheet(isPresented: $showReminder) {
            if let next = state?.details?.next {
                EarningsReminderSheet(target: ReminderTargetInfo(eventId: next.id, symbol: next.symbol, name: state?.details?.name ?? next.name, dateText: client.date(date: next.date)),
                                      model: EarningsRemindersModel(client: client), onSignIn: onSignIn) { showReminder = false }
            }
        }
        .alert("StockSteps+", isPresented: Binding(get: { state?.upgradeRequired == true }, set: { if !$0 { presenter.dismissUpgrade() } })) {
            Button("See StockSteps+") { presenter.dismissUpgrade(); onUpgrade() }
            Button("Not now", role: .cancel) { presenter.dismissUpgrade() }
        } message: { Text("AI earnings research is part of StockSteps+. Nothing was sent.") }
        .alert("", isPresented: Binding(get: { state?.message != nil }, set: { if !$0 { presenter.dismissMessage() } })) {
            Button("OK") { presenter.dismissMessage() }
        } message: { Text(state?.message ?? "") }
    }

    /// Phase 5D.2 hierarchy (as Compose): company → status and dates → the quarter's answer (summary + EPS/revenue) → reaction → insights → history.
    @ViewBuilder private func content(_ state: EarningsDetailsState, _ colors: StockColors) -> some View {
        let d = state.details
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            HStack(spacing: CGFloat(space.sm)) {
                StockTickerAvatar(symbol: state.symbol, logoUrl: d?.event?.logoUrl)
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(d?.name ?? state.symbol).font(StockStepsTheme.font(type.screenTitle, relativeTo: .title1)).foregroundStyle(colors.textPrimary)
                        .fixedSize(horizontal: false, vertical: true).accessibilityAddTraits(.isHeader)
                    // The quarter is the summary card's eyebrow when there is one, so it isn't repeated here.
                    Text([state.symbol, d?.event?.exchange, d?.summary == nil ? state.headerPeriod : nil].compactMap { $0 }.joined(separator: " · "))
                        .font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textSecondary)
                }
            }
            // D-1 (owner-approved, 5D.3): status + a compact Sample badge; the full sample sentence stays in the page notes and the app strip.
            if let d {
                HStack(spacing: CGFloat(space.xs)) {
                    StockStatusBadge(text: d.status.label, kind: .neutral)
                    if d.sampleData { StockStatusBadge(text: EarningsDisplay.shared.SAMPLE_BADGE, kind: .sample, accessibilityText: EarningsDisplay.shared.SAMPLE_SENTENCE) }
                }
            }
            // 5D.4.1: the reported quarter and the next report on separate labelled lines.
            if let reported = EarningsDisplay.shared.reportedLine(status: d?.status, date: state.headerDate, session: state.headerSession, dateStatus: state.headerDateStatus) {
                Text(reported).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
            }
            if let nextLine = state.nextLine {
                VStack(alignment: .leading, spacing: 0) {
                    Text(nextLine).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.primaryText)
                    if let detail = state.nextDetail { Text(detail).font(StockStepsTheme.font(type.caption, relativeTo: .subheadline)).foregroundStyle(colors.textSecondary) }
                }
                .accessibilityElement(children: .ignore).accessibilityLabel(state.next ?? nextLine)
            }
            if d?.stale == true { StockBanner(kind: .warning, message: "The next date hasn't been updated recently and may have changed.") }
            headerActions(remind: d?.next != nil).padding(.top, CGFloat(space.xs))
        }
        if state.loading { ProgressView().accessibilityLabel("Loading earnings") }
        if let error = state.error { StockSectionMessage(message: error, actionTitle: "Try again") { presenter.refresh() } }
        if let summary = d?.summary {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                if let period = state.headerPeriod { Text(period).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.primaryText) }
                // 5D.3 takeaway: the EPS headline from the server's classification (the server summary when there is none), badge,
                // reported vs estimated EPS, one explanation, one lesson.
                StockTitleWithBadge {
                    Text(state.headline ?? summary).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3)).foregroundStyle(colors.textPrimary).accessibilityAddTraits(.isHeader)
                } badge: { if let eps = state.eps { earningsClassificationBadge(eps.classification) } }
                // 5D4-3: the EPS card merged here — Reported · Estimate · Surprise, then the rest of the EPS lines folded (nothing removed).
                if let eps = state.eps, !eps.keyMetrics.isEmpty {
                    EarningsKeyMetrics(metrics: eps.keyMetrics).accessibilityElement(children: .ignore).accessibilityLabel(eps.accessibility)
                } else {
                    EarningsKeyMetrics(metrics: EarningsDisplay.shared.takeawayMetrics(eps: state.eps))
                }
                if let caveat = EarningsDisplay.shared.visibleCaveat(note: state.eps?.note) { EarningsNotes(lines: [caveat]) }
                // 5D.4.1: one "Show details" (rest of the explanation, Difference/Basis, the note, the lesson) instead of three controls.
                let eps = state.eps
                EarningsShowDetails(explanation: d?.summaryExplanation,
                                    hasMore: eps != nil && (!(eps!.secondary.isEmpty) || EarningsDisplay.shared.hiddenNote(note: eps!.note) != nil)) {
                    if let eps {
                        EarningsLines(lines: eps.secondary)
                        if let note = EarningsDisplay.shared.hiddenNote(note: eps.note) { EarningsNotes(lines: [note]) }
                        if let lesson = EarningsDisplay.shared.cardTopic(title: eps.title) { learnLink(lesson.title, systemImage: "lightbulb") { topic = lesson } }
                    }
                }
                if let lesson = client.topic(key: "eps") { learnLink("Learn more", systemImage: "arrow.right") { topic = lesson } }
            }.frame(maxWidth: .infinity, alignment: .leading).stockCard()
        }
        // 5D4-3: with a takeaway card, EPS lives there; the separate EPS card only remains without one.
        ForEach([d?.summary == nil ? state.eps : nil, state.revenue].compactMap { $0 }, id: \.title) { card in
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                    StockTitleWithBadge {
                        Text(EarningsDisplay.shared.cardTitle(title: card.title)).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
                    } badge: {
                        // 5D.1 (E-01): the badge shared with Earnings Results (`EarningsDisplay`), so a result reads the same on both screens.
                        earningsClassificationBadge(card.classification)
                    }
                    // 5D.3: the three key numbers first; the rest (difference, basis, quarter-over-quarter) smaller below.
                    if card.keyMetrics.isEmpty { EarningsLines(lines: card.lines) } else { EarningsKeyMetrics(metrics: card.keyMetrics) }
                    if let caveat = EarningsDisplay.shared.visibleCaveat(note: card.note) { EarningsNotes(lines: [caveat]) }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(card.accessibility)
                // 5D.4.1: secondary lines, the no-estimate reason and the lesson behind one "Show details" (key numbers stay visible).
                EarningsShowDetails(explanation: nil, hasMore: true) {
                    if !card.keyMetrics.isEmpty { EarningsLines(lines: card.secondary) }
                    if let note = EarningsDisplay.shared.hiddenNote(note: card.note) { EarningsNotes(lines: [note]) }
                    // The label is the title of the lesson it opens (was "What does this mean?").
                    if let lesson = EarningsDisplay.shared.cardTopic(title: card.title) { learnLink(lesson.title, systemImage: "lightbulb") { topic = lesson } }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading).stockCard()
        }
        if state.eps == nil, d?.next != nil {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                Text("Not reported yet").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).accessibilityAddTraits(.isHeader)
                // 5D.1 (E-02): the shared sentence (currency and M/B scaling from `EarningsFormatter`; only the estimates that exist).
                if let sentence = state.nextEstimate { Text(sentence).stockFont(type.small, relativeTo: .subheadline).foregroundStyle(colors.textBody) }
            }.frame(maxWidth: .infinity, alignment: .leading).stockCard()
        }
        if !state.reaction.isEmpty || state.reactionSentence != nil {
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                // 5D.3: the measured change first, then Before/After and the non-causal sentence; window, source and method folded below.
                StockTitleWithBadge {
                    Text(EarningsDisplay.shared.REACTION_TITLE).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).accessibilityAddTraits(.isHeader)
                } badge: { if let headline = state.reactionHeadline { EarningsReactionHeadline(headline: headline, direction: state.reactionDirection) } }
                EarningsLines(lines: state.reactionKey.isEmpty && state.reactionHeadline == nil ? state.reaction : state.reactionKey)
                if let sentence = state.reactionSentence { Text(sentence).stockFont(type.small, relativeTo: .subheadline).foregroundStyle(colors.textBody) }
                if !state.reactionDetails.isEmpty {
                    EarningsDisclosure(title: EarningsDisplay.shared.REACTION_DETAILS_TITLE) {
                        EarningsLines(lines: state.reactionDetails)
                        if let label = EarningsDisplay.shared.topicAction(key: "reaction") { learnLink(label, systemImage: "lightbulb") { topic = client.topic(key: "reaction") } }
                    }
                }
            }.frame(maxWidth: .infinity, alignment: .leading).stockCard()
        }
        if let d, !d.insights.isEmpty || d.lockedInsights > 0 {
            Text("Insights").font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3)).accessibilityAddTraits(.isHeader)
            // 5D.4.1 (option a): compact rows in one card — icon, period, title; the explanation and lesson behind "Show details".
            if !d.insights.isEmpty {
                VStack(spacing: 0) {
                    ForEach(Array(d.insights.enumerated()), id: \.element.id) { index, insight in
                        if index > 0 { StockDivider() }
                        let lesson = client.topic(key: insight.methodology)
                        EarningsInsightRow(category: insight.category, period: insight.period, title: insight.title, explanation: insight.explanation,
                                           lessonTitle: lesson?.title, onLesson: lesson.map { t in { topic = t } })
                    }
                }
                .padding(.horizontal, CGFloat(space.md))
                .stockCard(padding: 0)
            }
            if d.lockedInsights > 0 {
                Text("\(d.lockedInsights) more trend insight\(d.lockedInsights == 1 ? "" : "s") with StockSteps+.").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
            }
        }
        if !state.history.isEmpty {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                Text("Earnings history").font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3)).accessibilityAddTraits(.isHeader)
                if let note = state.historyNote { Text(note).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary) } // 5D.1B
                let reported: [Double?] = (state.epsChart.first?.values ?? []).map { ($0 as? NSNumber)?.doubleValue }
                if EarningsDisplay.shared.historyChartShown(reported: state.epsChart.first?.values ?? []) {
                    // Phase 5D.2: the chart Compose already draws, with the same tooltips (5D.1 E-03 currency) and legend.
                    EarningsHistoryChart(reported: reported,
                                         estimate: (state.epsChart.count > 1 ? state.epsChart[1].values : []).map { ($0 as? NSNumber)?.doubleValue },
                                         details: state.epsChartDetails, labels: EarningsDisplay.shared.historyXLabels(labels: state.chartLabels, largeText: typeSize >= .xxxLarge),
                                         yLabels: state.epsChartYLabels,
                                         description: "Reported EPS versus estimate for \(state.chartLabels.count) quarters.")
                } else {
                    Text(EarningsDisplay.shared.HISTORY_CHART_TOO_SHORT).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
                }
            }
            ForEach(state.history, id: \.id) { row in historyRow(row, colors) }
            if d?.historyLocked == true {
                Text("Older quarters are available with StockSteps+.").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
            }
        }
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockTitleWithBadge {
                Text("Ask about these earnings").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).accessibilityAddTraits(.isHeader)
            } badge: { if !state.plus { PlusBadge() } }
            Text("Answers use only the verified figures above and say when the data can't explain a cause.").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
            StockTextField(label: "Your question", text: $question, placeholder: "e.g. Explain this earnings report")
            Button { presenter.ask(question: question) } label: { Text(state.plus ? "Ask" : "Ask with StockSteps+").frame(maxWidth: .infinity) }
                .buttonStyle(.stockPrimary).disabled(question.trimmingCharacters(in: .whitespaces).isEmpty || state.asking)
            if state.asking { ProgressView().accessibilityLabel("Asking") }
            if let answer = state.answer {
                Text(answer.answer).stockFont(type.small, relativeTo: .subheadline).foregroundStyle(colors.textBody)
                if !answer.sources.isEmpty { Text("Sources: " + answer.sources.joined(separator: ", ")).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary) }
                if let left = answer.remainingToday { Text("\(left) questions left today.").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary) }
            }
        }.frame(maxWidth: .infinity, alignment: .leading).stockCard()
        Text("Learn").font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3)).accessibilityAddTraits(.isHeader)
        // E-19: the Watchlist/Portfolio grouped navigation rows instead of a column of plain buttons (same topics, same sheets).
        VStack(spacing: 0) {
            ForEach(Array(client.topics.enumerated()), id: \.element.key) { index, t in
                if index > 0 { StockDivider() }
                StockNavigationRow(title: t.title, hint: "Opens \(t.title)") { topic = t }
            }
        }
        .padding(.horizontal, CGFloat(space.md))
        .stockCard(padding: 0)
        // 5D.4: the notes and disclaimer folded like Results (each once; the Sample badge stays in the header).
        if let d {
            EarningsDisclosure(title: EarningsDisplay.shared.DISCLOSURES_TITLE, systemImage: "doc.text") {
                EarningsNotes(lines: EarningsDisplay.shared.detailsDisclosures(notes: d.notes, sampleData: d.sampleData))
            }
        }
    }

    /// Remind me (secondary) and Company details (primary) share a row only while both labels fit their equal share (shared `StockActionLayout`,
    /// as Compose); otherwise, and always at accessibility sizes, each takes a full-width row.
    @ViewBuilder private func headerActions(remind: Bool) -> some View {
        let font = StockStepsTheme.uiFont(type.bodySemiBold, weight: .semibold, typeSize: typeSize)
        let titles = (remind ? ["Remind me"] : []) + ["Company details"]
        let widths = titles.map { t in KotlinFloat(value: Float((t as NSString).size(withAttributes: [.font: font]).width + (t == "Remind me" ? font.pointSize * 1.2 + CGFloat(space.sm) : 0))) }
        let pair = remind && actionsWidth > 0 && StockActionLayout.shared.sideBySide(labelWidths: widths, availableWidth: Float(actionsWidth), horizontalPadding: Float(space.lg),
                                                                                  gap: Float(space.sm), accessibilityText: typeSize >= .xxxLarge)
        let remindButton = Button { showReminder = true } label: { Label("Remind me", systemImage: "bell").frame(maxWidth: .infinity) }.buttonStyle(.stockSecondary)
        let companyButton = Button(action: onCompany) { Text("Company details").frame(maxWidth: .infinity) }.buttonStyle(.stockPrimary)
        Group {
            if pair {
                HStack(spacing: CGFloat(space.sm)) { remindButton; companyButton }
            } else {
                VStack(spacing: CGFloat(space.sm)) { if remind { remindButton }; companyButton }
            }
        }
        .frame(maxWidth: .infinity)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { actionsWidth = $0 }
    }

    /// A learning link whose words are the title of the lesson it opens.
    private func learnLink(_ title: String, systemImage: String = "info.circle", action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: systemImage).font(StockStepsTheme.font(type.bodyMedium, relativeTo: .subheadline)).multilineTextAlignment(.leading)
                .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget), alignment: .leading).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .foregroundStyle(StockStepsTheme.colors(scheme).primaryText)
    }

    /// One history quarter (as Compose `HistoryRow`): period + classification badge, the EPS comparison with the chart tooltip's amounts, revenue,
    /// a chevron, and the remaining lines when expanded.
    private func historyRow(_ row: HistoryRowView, _ colors: StockColors) -> some View {
        let open = expanded.contains(row.id)
        let shown = EarningsDisplay.shared.classification(classification: row.classification)
        return Button { if open { expanded.remove(row.id) } else { expanded.insert(row.id) } } label: {
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                HStack(spacing: CGFloat(space.sm)) {
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        StockTitleWithBadge {
                            Text(row.period).font(StockStepsTheme.font(type.bodySemiBold, relativeTo: .subheadline)).foregroundStyle(colors.textPrimary)
                        } badge: { StockStatusBadge(text: shown.label, kind: shown.kind, size: .compact) }
                        Text(EarningsDisplay.shared.historyEpsLine(row: row)).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textBody)
                        Text(EarningsDisplay.shared.historyRevenueLine(row: row)).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSecondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: open ? "chevron.down" : "chevron.right").font(StockStepsTheme.font(type.caption, relativeTo: .caption1).weight(.semibold))
                        .foregroundStyle(colors.iconSecondary)
                }
                if open { EarningsLines(lines: EarningsDisplay.shared.historyDetailLines(row: row)).padding(.top, CGFloat(space.xs)) }
                Divider().padding(.top, CGFloat(space.xs))
            }
            .padding(.top, CGFloat(space.xs))
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(row.accessibility)
        .accessibilityValue(open ? "Expanded" : "Collapsed")
    }
}


// MARK: - Earnings Results (Phase 2)

/// One report's shared presenter (calculations come from the server).
@MainActor @Observable
final class EarningsResultsModel {
    private(set) var state: EarningsResultsState?
    private(set) var reaction: EarningsPriceReactionState?
    @ObservationIgnored let client: IosEarningsClient
    @ObservationIgnored private let reportId: String
    @ObservationIgnored private var made: (EarningsResultsPresenter, EarningsPriceReactionPresenter)?
    @ObservationIgnored private var subscriptions: [any AccountSubscription] = []

    /// Presenters load as soon as they exist, so they're created on first use (`activate()` from `.task`), not in `init`: SwiftUI re-runs the
    /// scene's `init` on every `AppScene` update while it's shown, which loaded the report, price reaction and premium state 3× (Phase 5C.1).
    private var presenters: (EarningsResultsPresenter, EarningsPriceReactionPresenter) {
        if let made { return made }
        let created = (client.results(reportId: reportId), client.reaction(reportId: reportId))
        made = created
        return created
    }
    var presenter: EarningsResultsPresenter { presenters.0 }
    var reactionPresenter: EarningsPriceReactionPresenter { presenters.1 }

    init(reportId: String, client: IosEarningsClient) {
        self.client = client
        self.reportId = reportId
    }
    func activate() {
        guard subscriptions.isEmpty else { return }
        subscriptions = [client.observeResults(presenter: presenter) { [weak self] in self?.state = $0 },
                         client.observeReaction(presenter: reactionPresenter) { [weak self] in self?.reaction = $0 }]
    }
    deinit {
        subscriptions.forEach { $0.cancel() }
        if let made { client.release(presenter: made.0); client.release(presenter: made.1) }
    }
}

struct EarningsResultsScene: View {
    @State private var model: EarningsResultsModel
    /// Phase 5: StockSteps+ sections (plan and quotas from the server).
    @State private var premium: EarningsPremiumModel
    let reportId: String
    let onCompany: (String) -> Void
    let onLearn: () -> Void
    let onUpgrade: () -> Void
    let onSignIn: () -> Void

    init(reportId: String, client: IosEarningsClient, onCompany: @escaping (String) -> Void, onLearn: @escaping () -> Void,
         onUpgrade: @escaping () -> Void = {}, onSignIn: @escaping () -> Void = {}) {
        _model = State(initialValue: EarningsResultsModel(reportId: reportId, client: client))
        _premium = State(initialValue: EarningsPremiumModel(reportId: reportId, client: client))
        self.reportId = reportId
        self.onCompany = onCompany
        self.onLearn = onLearn
        self.onUpgrade = onUpgrade
        self.onSignIn = onSignIn
    }

    var body: some View {
        EarningsResultsScreen(state: model.state, onRetry: { model.presenter.refresh() }, onToggle: { model.presenter.toggle(key: $0) },
                              onCompany: { onCompany(model.client.symbolOf(reportId: reportId)) }, onLearn: onLearn,
                              reaction: model.reaction, onWindow: { model.client.selectWindow(presenter: model.reactionPresenter, name: $0) },
                              onRetryReaction: { model.reactionPresenter.refresh() }, onToggleChart: { model.reactionPresenter.toggleChart() },
                              premium: premium.state, client: model.client,
                              onPremium: { premium.handle($0, onUpgrade: onUpgrade, onSignIn: onSignIn) })
            .navigationTitle("Earnings results")
            .navigationBarTitleDisplayMode(.inline)
            .task { model.activate(); premium.activate() }
            .refreshable { model.presenter.refresh() }
            .onReceive(NotificationCenter.default.publisher(for: .stockStepsPlanMayHaveChanged)) { _ in premium.presenter.refresh() }
            .premiumUpgradeAlert(premium.state?.upgrade, benefits: model.client.premiumBenefits, fairUse: model.client.fairUse, onUpgrade: onUpgrade,
                                 onDismiss: { premium.presenter.dismissUpgrade() })
            .alert("Sign in to continue", isPresented: Binding(get: { premium.state?.signInRequired == true }, set: { if !$0 { premium.presenter.dismissSignIn() } })) {
                Button("Sign In") { premium.presenter.dismissSignIn(); onSignIn() }
                Button("Not now", role: .cancel) { premium.presenter.dismissSignIn() }
            } message: { Text("Sign in to use StockSteps+ earnings features. Basic earnings results don't need an account.") }
    }
}

/// Header → EPS → revenue → growth → previous quarter → takeaway → learn → sources. Renders only.
struct EarningsResultsScreen: View {
    let state: EarningsResultsState?
    let onRetry: () -> Void
    let onToggle: (String) -> Void
    let onCompany: () -> Void
    let onLearn: () -> Void
    var reaction: EarningsPriceReactionState? = nil
    var onWindow: (String) -> Void = { _ in }
    var onRetryReaction: () -> Void = {}
    var onToggleChart: () -> Void = {}
    var premium: EarningsPremiumState? = nil
    var client: IosEarningsClient? = nil
    var onPremium: (PremiumAction) -> Void = { _ in }
    @Environment(\.colorScheme) private var scheme
    @State private var lesson: LearnLink?
    /// 5D.4: read in `body` so a live Dynamic Type change re-resolves every token font on this screen (seen on device: cards kept the old size).
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let _ = typeSize
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.md)) {
                if let state {
                    if state.loading || state.refreshing { ProgressView().accessibilityLabel("Loading earnings results") }
                    if state.notPublished {
                        StockSectionMessage(message: state.error ?? "Results for this period haven't been published yet.", actionTitle: EarningsDisplay.shared.COMPANY_ACTION, action: onCompany)
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
                    Text(state.companyName).font(StockStepsTheme.font(type.bodySemiBold, relativeTo: .headline)).foregroundStyle(colors.textPrimary).fixedSize(horizontal: false, vertical: true)
                    Text(state.symbolLine).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
                }
            }
            // 5D.4: "Q2 FY2026 results" at section size (the bar already says "Earnings results").
            Text(EarningsDisplay.shared.resultsTitle(title: state.title)).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title2)).accessibilityAddTraits(.isHeader)
            ForEach(state.periodLines, id: \.self) { Text($0).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary) }
            // D-1 (owner-approved, 5D.3): a compact Sample badge; the full sentence is in "Sources and disclosures" and the app strip stays.
            if state.sampleData { StockStatusBadge(text: EarningsDisplay.shared.SAMPLE_BADGE, kind: .sample, accessibilityText: EarningsDisplay.shared.SAMPLE_SENTENCE) }
            // E-06: data caveats are banners (icon + words), not bare caution captions.
            if let f = state.freshnessText { StockBanner(kind: .warning, message: f) }
        }
        // 5D.4.1: the quarter's one-line answer right under the header (first sentence; the rest behind "Show details").
        if let takeaway = state.takeaway {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                Text(EarningsDisplay.shared.TAKEAWAY_TITLE).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).accessibilityAddTraits(.isHeader)
                EarningsShowDetails(explanation: takeaway, hasMore: false) { EmptyView() }
                if !state.warnings.isEmpty { StockBanner(kind: .warning, message: state.warnings.joined(separator: "\n")) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(CGFloat(space.cardPadding))
            .background(colors.educationContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
        }
        if let eps = state.eps { comparison(eps, expanded: state.expanded.contains(eps.key), colors) }
        if let revenue = state.revenue { comparison(revenue, expanded: state.expanded.contains(revenue.key), comparisonNotes: state.comparisonNotes, colors) }
        // 5D.3: YoY growth is revenue's third key metric when available; the card only remains for its "not available" reason.
        if let yoy = state.yearOverYear, !state.yearOverYearFolded { growth(yoy, colors) }
        // 5D.4: the previous-quarter comparison is a secondary revenue row when available; the card only remains for its reason.
        if let qoq = state.quarterOverQuarter, !state.quarterOverQuarterFolded { growth(qoq, colors) }
        if let reaction { PriceReactionSection(state: reaction, onWindow: onWindow, onRetry: onRetryReaction, onToggleChart: onToggleChart, onLesson: { lesson = $0 }) }
        // Phase 5 (StockSteps+): after every free section, so nothing free is pushed down or hidden.
        if let premium, let client, !state.offline { PremiumEarningsSections(state: premium, client: client, onAction: onPremium) }
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text(EarningsDisplay.shared.LEARN_TITLE).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3)).accessibilityAddTraits(.isHeader)
            // 5D.3: grouped navigation rows (same lessons; a missing one still falls back to the Learn tab).
            VStack(spacing: 0) {
                ForEach(Array(state.learn.enumerated()), id: \.element.title) { index, link in
                    if index > 0 { StockDivider() }
                    StockNavigationRow(title: link.title, icon: "lightbulb", hint: "Opens \(link.title)") { if link.body != nil { lesson = link } else { onLearn() } }
                }
            }
            .padding(.horizontal, CGFloat(space.md))
            .stockCard(padding: 0)
            Button { onCompany() } label: { Text(EarningsDisplay.shared.COMPANY_ACTION).frame(maxWidth: .infinity) }.buttonStyle(.stockSecondary)
        }
        // 5D.3: sources, publication time, the sample sentence and the education/price notes, once, in one collapsed section.
        EarningsDisclosure(title: EarningsDisplay.shared.DISCLOSURES_TITLE, systemImage: "doc.text") {
            EarningsNotes(lines: EarningsDisplay.shared.resultsDisclosures(publication: state.publication, sources: state.sources, sampleData: state.sampleData))
        }
    }

    /// One card for EPS and revenue; the classification is a word, colour is secondary.
    private func comparison(_ card: ComparisonCardView, expanded: Bool, comparisonNotes: [String] = [], _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                StockTitleWithBadge {
                    Text(card.title).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
                } badge: { earningsClassificationBadge(card.classification) } // 5D.1 (E-01)
                if card.keyMetrics.isEmpty { EarningsLines(lines: card.lines) } else { EarningsKeyMetrics(metrics: card.keyMetrics) }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(card.accessibility)
            if let caveat = EarningsDisplay.shared.visibleCaveat(note: card.basisNote) { EarningsNotes(lines: [caveat]) }
            // 5D.4.1: one "Show details", collapsed: rest of the explanation, reason, basis, difference / prior periods, comparison notes.
            let secondary: [MetricLine] = card.keyMetrics.isEmpty ? [] : card.secondary
            EarningsShowDetails(explanation: card.explanation,
                                hasMore: card.reason != nil || card.basisNote != nil || !secondary.isEmpty || !comparisonNotes.isEmpty) {
                if let r = card.reason { EarningsNotes(lines: [r]) }
                if let b = EarningsDisplay.shared.hiddenNote(note: card.basisNote) { EarningsNotes(lines: [b]) }
                EarningsLines(lines: secondary)
                EarningsNotes(lines: comparisonNotes)
            }
            Button { onToggle(card.key) } label: {
                HStack { Image(systemName: "info.circle"); Text(card.infoTitle).multilineTextAlignment(.leading); Spacer(); Image(systemName: expanded ? "chevron.down" : "chevron.right") }
                    .font(StockStepsTheme.font(type.label, relativeTo: .subheadline))
                    .frame(minHeight: 48).contentShape(Rectangle())
            }
            .buttonStyle(.plain).foregroundStyle(colors.primaryText)
            .accessibilityValue(expanded ? "Expanded" : "Collapsed")
            if expanded { Text(card.infoBody).stockFont(type.small, relativeTo: .subheadline).foregroundStyle(colors.textBody) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .stockCard()
    }

    private func growth(_ card: GrowthCardView, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text(card.title).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
            EarningsLines(lines: card.lines)
            Text(card.explanation).stockFont(type.small, relativeTo: .subheadline).foregroundStyle(colors.textBody)
            if let r = card.reason { Text(r).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .stockCard()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(card.accessibility)
    }

}

// MARK: - Post-earnings price reaction (Phase 3)

/// "How did the stock react?": window choice, closes, change, Phase 2 context, a non-causal explanation
/// and a daily-close chart. Unavailable (honest status) and failed (retryable) look different.
struct PriceReactionSection: View {
    let state: EarningsPriceReactionState
    let onWindow: (String) -> Void
    let onRetry: () -> Void
    let onToggleChart: () -> Void
    let onLesson: (LearnLink) -> Void
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let _ = typeSize
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            StockTitleWithBadge {
                Text(EarningsDisplay.shared.REACTION_TITLE).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).accessibilityAddTraits(.isHeader)
            } badge: { if let headline = state.headline { EarningsReactionHeadline(headline: headline, direction: state.direction) } }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(ReactionWindow.entries, id: \.name) { w in
                        let pending = state.windows.first { $0.window.name == w.name }?.status.name == "WINDOW_INCOMPLETE"
                        StockChip(title: w.label + (pending ? " · not yet" : ""), selected: state.window.name == w.name) { onWindow(w.name) }
                    }
                }
            }
            if state.loading || state.refreshing { ProgressView().accessibilityLabel("Loading price reaction") }
            if let error = state.error { StockSectionMessage(message: error, actionTitle: "Try again", action: onRetry) }
            if !state.lines.isEmpty {
                // Phase 5D.2: the shared label/value rule (labels never squeezed into letters; long values wrap or the group stacks).
                EarningsLines(lines: state.keyLines.isEmpty ? state.lines : state.keyLines)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(state.accessibility)
            }
            if let status = state.statusText { Text(status).stockFont(type.small, relativeTo: .subheadline).foregroundStyle(colors.textSecondary) }
            if let summary = state.summary { Text(summary).stockFont(type.small, relativeTo: .subheadline).foregroundStyle(colors.textBody) }
            if let chart = state.chart {
                ReactionChart(chart: chart, height: state.expandedChart ? 240 : 150)
                if let missing = chart.missingNote { Text(missing).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.cautionText) }
                Button(state.expandedChart ? "Smaller chart" : "Larger chart", action: onToggleChart).font(StockStepsTheme.font(type.bodyMedium, relativeTo: .subheadline)).frame(minHeight: 48)
            }
            if let mixed = state.mixedNote { Text(mixed).stockFont(type.small, relativeTo: .subheadline).foregroundStyle(colors.textSecondary) }
            // E-06: one warning banner for the section's caveats (the words are unchanged).
            let caveats = state.warnings + (state.stale ? ["Showing saved price data; the price provider isn't responding."] : [])
            if !caveats.isEmpty { StockBanner(kind: .warning, message: caveats.joined(separator: "\n")) }
            // 5D.3: the measured window, the chart key, the context line, lessons and price sources, folded (nothing removed).
            EarningsDisclosure(title: EarningsDisplay.shared.RESULTS_REACTION_DETAILS_TITLE) {
                EarningsLines(lines: state.detailLines)
                if let chart = state.chart {
                    EarningsNotes(lines: ["Dashed line: \(chart.eventLabel). Rings: baseline and endpoint closes. Daily closes only, so the marker shows the session, not the exact time."])
                }
                if let context = state.contextLine { EarningsNotes(lines: [context]) }
                // 5D.4 (R4-04): the server's reaction explanation moved here from between the numbers and the chart (unchanged text).
                if let explanation = state.explanation { Text(explanation).stockFont(type.small, relativeTo: .subheadline).foregroundStyle(colors.textBody) }
                ForEach(state.learn, id: \.title) { link in
                    Button(link.title, systemImage: "lightbulb") { onLesson(link) }.font(StockStepsTheme.font(type.bodyMedium, relativeTo: .subheadline)).frame(minHeight: 48)
                }
                EarningsNotes(lines: state.sources.map { "Prices: \($0) · regular-session closes, split-adjusted" })
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .stockCard()
    }
}

/// Daily closes with gaps (nil slots aren't joined), a dashed earnings marker and rings on the
/// baseline/endpoint. Drag to read a session's close; VoiceOver reads the summary.
struct ReactionChart: View {
    let chart: ReactionChartView
    var height: CGFloat = 150
    @Environment(\.colorScheme) private var scheme
    @State private var selected: Int?

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let values: [Double?] = chart.values.map { ($0 as? NSNumber)?.doubleValue }
        let valid = values.compactMap { $0 }
        let high = valid.max() ?? 1, low = valid.min() ?? 0
        let span = high - low > 0 ? high - low : 1
        let focus = selected ?? (values.lastIndex { $0 != nil } ?? 0)
        VStack(alignment: .leading, spacing: 4) {
            Text(chart.details.indices.contains(focus) ? chart.details[focus] : "").font(StockStepsTheme.font(type.label, relativeTo: .footnote))
            GeometryReader { proxy in
                let size = proxy.size
                let step = values.count > 1 ? size.width / CGFloat(values.count - 1) : 0
                let y: (Double) -> CGFloat = { 6 + (size.height - 12) * CGFloat(1 - ($0 - low) / span) }
                ZStack(alignment: .topLeading) {
                    if chart.eventIndex >= 0 {
                        Path { p in p.move(to: CGPoint(x: CGFloat(chart.eventIndex) * step, y: 0)); p.addLine(to: CGPoint(x: CGFloat(chart.eventIndex) * step, y: size.height)) }
                            .stroke(colors.cautionText, style: StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                    }
                    Path { p in
                        var drawing = false
                        for (i, v) in values.enumerated() {
                            guard let v else { drawing = false; continue }
                            let pt = CGPoint(x: CGFloat(i) * step, y: y(v))
                            if drawing { p.addLine(to: pt) } else { p.move(to: pt); drawing = true }
                        }
                    }
                    .stroke(colors.primary, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                    ForEach([Int(chart.baselineIndex), Int(chart.endpointIndex)].filter { $0 >= 0 }, id: \.self) { i in
                        if let v = values[i] { Circle().stroke(colors.primary, lineWidth: 2).frame(width: 12, height: 12).position(x: CGFloat(i) * step, y: y(v)) }
                    }
                    if let v = values.indices.contains(focus) ? values[focus] : nil {
                        Circle().fill(colors.primary).frame(width: 7, height: 7).position(x: CGFloat(focus) * step, y: y(v))
                    }
                }
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0).onChanged { g in
                    guard values.count > 1 else { return }
                    selected = min(max(Int((g.location.x / size.width * CGFloat(values.count - 1)).rounded()), 0), values.count - 1)
                })
            }
            .frame(height: height)
            HStack {
                Text(chart.xLabels.first ?? "").font(StockStepsTheme.font(type.tiny, relativeTo: .caption2)).foregroundStyle(colors.textTertiary)
                Spacer()
                Text(chart.xLabels.last ?? "").font(StockStepsTheme.font(type.tiny, relativeTo: .caption2)).foregroundStyle(colors.textTertiary)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(chart.description_)
    }
}

/// Phase 5D.1 (E-01): an estimate comparison as the shared status badge, from `EarningsDisplay` (the same words and style as Compose).
@MainActor func earningsClassificationBadge(_ classification: Classification) -> some View {
    let shown = EarningsDisplay.shared.classification(classification: classification)
    return StockStatusBadge(text: shown.label, kind: shown.kind, accessibilityText: shown.spoken)
}

/// Phase 5D.2: one group of label/value rows (a card's lines) with the shared `StockLayout.labelColumn` decision, as Compose `EarningsLines`.
/// Side by side, labels keep the group's widest label on one line and long values wrap between words in the rest; otherwise every row stacks
/// (value under its label). Label, value and detail scale on the same Dynamic Type curve (`relativeTo: .footnote`). Nothing is truncated.
struct EarningsLines: View {
    let lines: [MetricLine]
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var width: CGFloat = 0

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let labelFont = StockStepsTheme.uiFont(type.small, typeSize: typeSize)
        let valueFont = StockStepsTheme.uiFont(type.small, weight: .medium, typeSize: typeSize)
        let detailFont = StockStepsTheme.uiFont(type.caption, typeSize: typeSize)
        let measure: (String, UIFont) -> CGFloat = { ($0 as NSString).size(withAttributes: [.font: $1]).width }
        let longest: (String, UIFont) -> CGFloat = { text, font in text.split(separator: " ").map { measure(String($0), font) }.max() ?? 0 }
        // Text widths only, never this view's own frame (which would feed back).
        let column = StockLayout.shared.labelColumn(
            widestLabel: Float(lines.map { measure($0.label, labelFont) }.max() ?? 0),
            longestLabelWord: Float(lines.map { longest($0.label, labelFont) }.max() ?? 0),
            longestValueWord: Float(lines.map { max(longest($0.value, valueFont), $0.detail.map { longest($0, detailFont) } ?? 0) }.max() ?? 0),
            availableWidth: Float(width), gap: Float(space.md))?.floatValue
        VStack(alignment: .leading, spacing: CGFloat(column == nil ? space.sm : space.xs)) {
            ForEach(lines, id: \.label) { line in
                Group {
                    if let column {
                        HStack(alignment: .firstTextBaseline, spacing: CGFloat(space.md)) {
                            Text(line.label).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                                .frame(width: CGFloat(column) + 1, alignment: .leading)
                            value(line, colors, trailing: true)
                        }
                    } else {
                        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                            Text(line.label).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textSecondary)
                            value(line, colors, trailing: false)
                        }
                    }
                }
                .accessibilityElement(children: .combine)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
    }

    private func value(_ line: MetricLine, _ colors: StockColors, trailing: Bool) -> some View {
        VStack(alignment: trailing ? .trailing : .leading, spacing: 0) {
            Text(line.value).font(StockStepsTheme.font(type.small, relativeTo: .footnote).weight(.medium)).foregroundStyle(colors.textPrimary)
            if let detail = line.detail { Text(detail).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSecondary) }
        }
        .multilineTextAlignment(trailing ? .trailing : .leading)
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: .infinity, alignment: trailing ? .trailing : .leading)
    }
}

/// Phase 5D.2: the Details EPS history chart on iOS (Compose already draws one). Reported EPS solid, estimate dashed (shared
/// `StockChartSeriesStyle`, swatch legend — never colour alone); drag to select a quarter, whose tooltip comes from `epsChartDetails` (the same
/// amounts and currency as its history row, 5D.1 E-03). Only the first and last quarter labels, so narrow screens never overlap. Missing values
/// leave gaps (never drawn as zero). Display only: values and order come from the presenter.
struct EarningsHistoryChart: View {
    let reported: [Double?]
    let estimate: [Double?]
    let details: [String]
    let labels: [String]
    /// 5D.4: highest / middle / lowest of the shown values (`EarningsDetailsState.epsChartYLabels`); empty draws no axis.
    var yLabels: [String] = []
    let description: String
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var selected: Int?

    var body: some View {
        let _ = typeSize
        let colors = StockStepsTheme.colors(scheme)
        let main = StockStepsTheme.chartLine(0, scheme), est = StockStepsTheme.chartLine(1, scheme)
        let all = (reported + estimate).compactMap { $0 }
        let high = all.max() ?? 1, low = all.min() ?? 0
        let span = high - low > 0 ? high - low : 1
        let count = max(reported.count, estimate.count)
        let focus = selected ?? (reported.lastIndex { $0 != nil } ?? 0)
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: CGFloat(space.md)) { legend(main, "Reported EPS", colors); legend(est, "Estimate", colors) }
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) { legend(main, "Reported EPS", colors); legend(est, "Estimate", colors) }
            }
            Text(details.indices.contains(focus) ? details[focus] : "").font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: CGFloat(space.sm)) {
            GeometryReader { proxy in
                let size = proxy.size
                let step = count > 1 ? size.width / CGFloat(count - 1) : 0
                let y: (Double) -> CGFloat = { 8 + (size.height - 16) * CGFloat(1 - ($0 - low) / span) }
                ZStack(alignment: .topLeading) {
                    path(estimate, step: step, y: y).stroke(StockStepsTheme.color(est.color), style: StockStepsTheme.strokeStyle(est, minWidth: 1.5))
                    path(reported, step: step, y: y).stroke(StockStepsTheme.color(main.color), style: StockStepsTheme.strokeStyle(main, minWidth: 2))
                    if selected != nil {
                        Path { p in p.move(to: CGPoint(x: CGFloat(focus) * step, y: 0)); p.addLine(to: CGPoint(x: CGFloat(focus) * step, y: size.height)) }
                            .stroke(colors.border, lineWidth: 1)
                    }
                    if reported.indices.contains(focus), let v = reported[focus] {
                        Circle().fill(StockStepsTheme.color(main.color)).frame(width: 9, height: 9)
                            .overlay(Circle().stroke(colors.surface, lineWidth: 2)).position(x: CGFloat(focus) * step, y: y(v))
                    }
                }
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0).onChanged { g in
                    guard count > 1 else { return }
                    selected = min(max(Int((g.location.x / max(step, 1)).rounded()), 0), count - 1)
                })
            }
            if !yLabels.isEmpty {
                VStack(alignment: .trailing) {
                    ForEach(Array(yLabels.enumerated()), id: \.offset) { index, label in
                        if index > 0 { Spacer(minLength: 0) }
                        Text(label)
                    }
                }
                .font(StockStepsTheme.font(type.tiny, relativeTo: .caption2)).foregroundStyle(colors.textTertiary)
            }
            }
            .frame(height: 140)
            HStack {
                ForEach(Array(labels.enumerated()), id: \.offset) { index, label in
                    if index > 0 { Spacer(minLength: CGFloat(space.xs)) }
                    Text(label).lineLimit(1)
                }
            }
            .font(StockStepsTheme.font(type.tiny, relativeTo: .caption2)).foregroundStyle(colors.textTertiary)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(description)
        .accessibilityValue(details.indices.contains(focus) ? details[focus] : "")
    }

    private func legend(_ line: StockChartSeriesStyle.Line, _ title: String, _ colors: StockColors) -> some View {
        HStack(spacing: CGFloat(space.xs)) {
            StockChartSwatch(line: line)
            Text(title).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
        }
    }

    /// Each run of consecutive values is its own segment, so gaps stay visible.
    private func path(_ values: [Double?], step: CGFloat, y: (Double) -> CGFloat) -> Path {
        Path { p in
            var drawing = false
            for (i, value) in values.enumerated() {
                guard let value else { drawing = false; continue }
                let point = CGPoint(x: CGFloat(i) * step, y: y(value))
                if drawing { p.addLine(to: point) } else { p.move(to: point); drawing = true }
            }
        }
    }
}
