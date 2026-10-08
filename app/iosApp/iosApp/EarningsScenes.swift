import Observation
import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography

/// Mirrors the shared Kotlin Earnings Center presenter (same state and actions as Android).
@MainActor @Observable
final class EarningsModel {
    private(set) var center: EarningsCenterState?
    @ObservationIgnored let client: IosEarningsClient
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    init(accounts: AccountViewModel, baseURL: @escaping () -> String = { BackendSettings.currentURL }) {
        client = IosEarningsClient(baseUrl: baseURL, account: accounts.client)
        subscription = client.observeCenter { [weak self] in self?.center = $0 }
        client.center.start()
    }
    deinit {
        subscription?.cancel()
        client.close()
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
    deinit { subscription?.cancel() }
}

struct IdentifiedTopic: Identifiable {
    let topic: EarningsEducation.Topic
    var id: String { topic.key }
}

// MARK: - Earnings Center

struct EarningsCenterScene: View {
    let model: EarningsModel
    let onOpen: (String) -> Void
    var onSignIn: () -> Void = {}

    var body: some View {
        EarningsCenterScreen(state: model.center, client: model.client, onOpen: onOpen, onSignIn: onSignIn)
            .navigationTitle("Earnings")
            .navigationBarTitleDisplayMode(.inline)
            .refreshable { model.client.center.refresh() }
    }
}

struct EarningsCenterScreen: View {
    let state: EarningsCenterState?
    let client: IosEarningsClient
    let onOpen: (String) -> Void
    let onSignIn: () -> Void
    @Environment(\.colorScheme) private var scheme
    @State private var topic: EarningsEducation.Topic?

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                Text("Earnings Center").font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).accessibilityAddTraits(.isHeader)
                Text("When companies report, what analysts expect and what was reported.").font(.subheadline).foregroundStyle(colors.textSecondary)
                if let state {
                    if state.sampleData { Text("Sample earnings data for development, not real announcements.").font(.caption).foregroundStyle(colors.textSecondary) }
                    Picker("Section", selection: Binding(get: { state.tab.name }, set: { client.selectTab(name: $0) })) {
                        Text("Upcoming").tag("UPCOMING"); Text("Results").tag("RESULTS"); Text("Following").tag("FOLLOWING")
                    }.pickerStyle(.segmented)
                    Picker("Range", selection: Binding(get: { state.range.name }, set: { client.selectRange(name: $0) })) {
                        ForEach(state.ranges, id: \.name) { Text($0.label).tag($0.name) }
                    }.pickerStyle(.segmented)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            chip("US", on: state.filters.markets.contains("US")) { client.toggleMarket(code: "US") }
                            chip("Canada", on: state.filters.markets.contains("CA")) { client.toggleMarket(code: "CA") }
                            ForEach(["BEFORE_OPEN", "AFTER_CLOSE", "UNKNOWN"], id: \.self) { s in
                                chip(client.sessionShort(name: s), on: state.filters.sessions.contains { $0.name == s }) { client.toggleSession(name: s) }
                            }
                            ForEach(["NASDAQ", "NYSE", "TSX"], id: \.self) { e in
                                chip(e, on: state.filters.exchanges.contains(e)) { client.toggleExchange(code: e) }
                            }
                        }
                    }
                    Text("\(client.date(date: state.from)) – \(client.date(date: state.to))").font(.caption).foregroundStyle(colors.textSecondary)
                    if state.loading { ProgressView().accessibilityLabel("Loading earnings") }
                    if let error = state.error { Text(error).foregroundStyle(colors.textSecondary); Button("Try again") { client.center.refresh() } }
                    if state.tab.name == "FOLLOWING" && !state.signedIn {
                        Text("Sign in to see earnings for companies in your watchlists and portfolios.")
                        Button("Sign in", action: onSignIn).buttonStyle(.borderedProminent)
                    } else if !state.loading && state.error == nil && state.rows.isEmpty {
                        Text(state.tab.name == "FOLLOWING" ? "None of the companies you follow report in this period." : state.tab.name == "RESULTS" ? "No reported results in this period." : "No earnings announcements in this period.")
                            .foregroundStyle(colors.textSecondary)
                    }
                    ForEach(Array(state.days.enumerated()), id: \.offset) { _, day in
                        let date = day.first as? String ?? ""
                        Text(client.date(date: date)).font(.subheadline.weight(.semibold)).foregroundStyle(colors.textSecondary)
                            .accessibilityAddTraits(.isHeader).accessibilityLabel(client.spokenDate(date: date)).padding(.top, 6)
                        ForEach((day.second as? [EarningsRowView]) ?? [], id: \.id) { row in
                            Button { onOpen(row.symbol) } label: { rowView(row, colors) }
                                .buttonStyle(.plain)
                                .accessibilityElement(children: .ignore)
                                .accessibilityLabel(row.accessibility)
                                .accessibilityHint("Opens earnings details")
                                .onAppear { if row.id == state.rows.last?.id && state.hasMore { client.center.loadMore() } }
                        }
                    }
                    ForEach(state.notes, id: \.self) { Text($0).font(.caption).foregroundStyle(colors.textSecondary) }
                    Text("Learn the basics").font(.headline).padding(.top, 8)
                    ForEach(Array(client.topics.prefix(6)), id: \.key) { t in Button(t.title) { topic = t } }
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

    private func chip(_ label: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(label, action: action).buttonStyle(.bordered).tint(on ? .accentColor : .secondary).accessibilityValue(on ? "Selected" : "")
    }

    private func rowView(_ row: EarningsRowView, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                VStack(alignment: .leading) {
                    Text("\(row.symbol)\(row.exchange.map { " · \($0)" } ?? "")").font(.headline).foregroundStyle(colors.textPrimary)
                    Text(row.name).font(.caption).foregroundStyle(colors.textSecondary).lineLimit(1)
                }
                Spacer()
                Text(row.reminder ? "Reminder on" : row.status.label).font(.caption2).padding(.horizontal, 8).padding(.vertical, 2)
                    .background(colors.surfaceSecondary, in: Capsule())
            }
            Text("\(row.sessionText) · \(row.dateStatusText)").font(.caption).foregroundStyle(colors.textSecondary)
            if let previous = row.previousDate { Text("Date moved from \(client.date(date: previous))").font(.caption).foregroundStyle(colors.cautionText) }
            if row.epsResult != nil || row.revenueResult != nil {
                Text([row.epsResult.map { "EPS \($0)" }, row.revenueResult.map { "Revenue \($0)" }].compactMap { $0 }.joined(separator: " · ")).font(.subheadline)
            } else {
                Text(row.expectation).font(.subheadline)
            }
            if let countdown = row.countdown { Text(countdown).font(.caption).foregroundStyle(colors.primary) }
            if let following = row.following { Text(following).font(.caption).foregroundStyle(colors.textSecondary) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .stockCard()
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
