import Observation
import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

extension Notification.Name {
    /// Posted with a brief id (or "") when the user taps a Daily Market Brief notification.
    static let stockStepsOpenBrief = Notification.Name("StockStepsOpenBrief")
    /// Opens Earnings Event Details (object: the event id, e.g. "AAPL:2026-Q4").
    static let stockStepsOpenEarningsEvent = Notification.Name("StockStepsOpenEarningsEvent")
    /// Opens Earnings Results (object: the report id, e.g. "AAPL:2026-Q3").
    static let stockStepsOpenEarningsResults = Notification.Name("StockStepsOpenEarningsResults")
}

/// Mirrors the shared Kotlin Daily Brief presenter (one instance for Home, Markets and the reader).
@MainActor @Observable
final class BriefModel {
    private(set) var state: DailyBriefUiState?
    @ObservationIgnored let client: IosBriefClient
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    init(account: IosAccountClient) {
        client = IosBriefClient(account: account)
        subscription = client.observe { [weak self] in self?.state = $0 }
    }
    deinit {
        subscription?.cancel()
        client.close()
    }
    var presenter: DailyBriefPresenter { client.presenter }
}

/// Identifies a brief destination (nil id = latest).
struct BriefTarget: Hashable, Identifiable {
    let briefId: String?
    var id: String { briefId ?? "latest" }
}

// MARK: - Compact preview (Home, Markets)

struct DailyBriefPreviewCard: View {
    let model: BriefModel
    let onOpen: () -> Void
    var onHistory: (() -> Void)? = nil
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let state = model.state
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            HStack(spacing: CGFloat(space.sm)) {
                Image(systemName: "newspaper").foregroundStyle(colors.primary).accessibilityHidden(true)
                Text("Your Daily Market Brief").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).accessibilityAddTraits(.isHeader)
            }
            if let brief = state?.latest {
                let stale = model.client.isStale(brief: brief)
                Text(model.client.freshness(brief: brief) + (state?.offline == true ? " · Offline copy" : ""))
                    .font(.caption).foregroundStyle(stale || state?.offline == true ? colors.cautionText : colors.textSecondary)
                Text(brief.summaryLine).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody).lineLimit(3)
                HStack {
                    Text("\(brief.readingMinutes) min read" + (brief.sampleData ? " · Sample data" : "")).font(.caption).foregroundStyle(colors.textSecondary)
                    Spacer()
                    if let onHistory { Button("Previous briefs", action: onHistory).font(.subheadline) }
                    Button(stale ? "Read Latest Brief" : "Read Today's Brief", action: onOpen).font(.subheadline.weight(.semibold)).frame(minHeight: CGFloat(dims.touchTarget))
                }
            } else if state?.loading != false {
                ProgressView().accessibilityLabel("Loading the Daily Market Brief")
            } else {
                Text(state?.error ?? "The brief isn't available right now.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
            }
        }
        .stockCard()
        .contentShape(Rectangle())
        .onTapGesture { if state?.latest != nil { onOpen() } }
        .accessibilityElement(children: .contain)
    }
}

// MARK: - Reader

struct DailyBriefScene: View {
    let target: BriefTarget
    let model: BriefModel
    var onOpenStock: (String) -> Void = { _ in }
    var onHistory: () -> Void = {}
    var onWatchlist: () -> Void = {}
    var onLearn: () -> Void = {}
    var onSignIn: () -> Void = {}
    var onUpgrade: () -> Void = {}
    var onOpenEarnings: (String) -> Void = { _ in }
    var onOpenResults: (String) -> Void = { _ in }

    var body: some View {
        DailyBriefScreen(state: model.state, client: model.client, onOpenStock: onOpenStock, onOpenEarnings: onOpenEarnings, onOpenResults: onOpenResults, onHistory: onHistory, onWatchlist: onWatchlist,
                         onLearn: onLearn, onSignIn: onSignIn, onUpgrade: { model.presenter.dismissUpgrade(); onUpgrade() })
            .navigationTitle("Daily Brief")
            .navigationBarTitleDisplayMode(.inline)
            .task(id: target.id) { model.presenter.open(id: target.briefId) }
            .refreshable { model.presenter.loadLatest() }
    }
}

struct DailyBriefScreen: View {
    let state: DailyBriefUiState?
    let client: IosBriefClient
    let onOpenStock: (String) -> Void
    var onOpenEarnings: (String) -> Void = { _ in }
    var onOpenResults: (String) -> Void = { _ in }
    let onHistory: () -> Void
    let onWatchlist: () -> Void
    let onLearn: () -> Void
    let onSignIn: () -> Void
    let onUpgrade: () -> Void
    @Environment(\.colorScheme) private var scheme
    @Environment(\.openURL) private var openURL
    @State private var showPreferences = false
    @State private var concept: EducationEntry?
    @State private var question = ""
    private var presenter: DailyBriefPresenter { client.presenter }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.sectionGap)) {
                header(colors)
                if let brief = state?.current {
                    Text(brief.summaryLine).font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(colors.textPrimary)
                    glance(brief, colors)
                    stories(brief, colors)
                    watchlist(colors)
                    earnings(colors)
                    conceptCard(brief, colors)
                    premium(colors)
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(brief.notes, id: \.self) { Text($0).font(.caption).foregroundStyle(colors.textSecondary) }
                        Text(brief.disclaimer).font(.caption).foregroundStyle(colors.textSecondary)
                        Button("Previous briefs", action: onHistory).frame(minHeight: CGFloat(dims.touchTarget))
                    }
                } else if state?.loading != false {
                    ProgressView().accessibilityLabel("Loading the Daily Market Brief")
                } else {
                    StockSectionMessage(message: state?.error ?? "The brief isn't available right now.", actionTitle: "Try again", action: { presenter.loadLatest() })
                }
            }
            .frame(maxWidth: CGFloat(dims.contentMaxWidth))
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
            .frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .sheet(isPresented: $showPreferences) { preferences(colors).presentationDetents([.medium, .large]) }
        .sheet(item: Binding(get: { concept.map { IdentifiedEntry(entry: $0) } }, set: { concept = $0?.entry })) { BeginnerExplanationSheet(entry: $0.entry) }
        .alert("StockSteps+", isPresented: Binding(get: { state?.upgrade == true }, set: { if !$0 { presenter.dismissUpgrade() } })) {
            Button("See StockSteps+", action: onUpgrade)
            Button("Not now", role: .cancel) { presenter.dismissUpgrade() }
        } message: { Text("AI explanations, the full personalized brief and the complete brief history are part of StockSteps+. The market summary, stories and Concept of the Day stay free.") }
        .alert(state?.message ?? "", isPresented: Binding(get: { state?.message != nil }, set: { if !$0 { presenter.dismissMessage() } })) { Button("OK") { presenter.dismissMessage() } }
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title2)).accessibilityAddTraits(.isHeader)
    }

    @ViewBuilder
    private func header(_ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            HStack {
                Text("Your Daily Market Brief").font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).accessibilityAddTraits(.isHeader)
                Spacer()
                Button("Notify me") { presenter.loadPreferences(); showPreferences = true }
            }
            Text("Understand the market in a few minutes.").font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
            if let b = state?.current {
                Text("\(b.edition.label) · \(client.date(iso: b.briefDate)) · \(b.readingMinutes) min read").font(.caption).foregroundStyle(colors.textSecondary)
                Text("Market data for the \(client.date(iso: b.sessionDate)) session · Updated \(client.published(iso: b.updatedAt) ?? b.updatedAt)").font(.caption).foregroundStyle(colors.textSecondary)
                ForEach(b.sessions, id: \.market) { s in Text("\(s.name): \(client.sessionLabel(s: s))").font(.caption).foregroundStyle(colors.textSecondary) }
                if client.isStale(brief: b) { banner("This is the latest brief available. It isn't from today.", colors) }
                if state?.offline == true { banner("You're offline. This is a copy saved on this device.", colors) }
                if b.sampleData {
                    HStack {
                        Text("Sample data for development").font(.caption).foregroundStyle(colors.cautionText)
                        Spacer()
                        Menu("Scenarios") {
                            Button("Current (no scenario)") { presenter.scenario(name: nil) }
                            ForEach(client.scenarios, id: \.self) { n in Button(n) { presenter.scenario(name: n) } }
                        }
                    }
                }
            }
        }
    }

    private func banner(_ text: String, _ colors: StockColors) -> some View {
        Text(text).font(StockStepsTheme.font(type.small)).padding(CGFloat(space.sm)).frame(maxWidth: .infinity, alignment: .leading)
            .background(colors.warningContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
    }

    @ViewBuilder
    private func glance(_ brief: DailyBrief, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            sectionTitle("Market at a Glance")
            if brief.marketSnapshot.isEmpty { Text("Index values aren't available right now.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary) }
            ForEach(brief.marketSnapshot, id: \.indexId) { i in
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(i.displayName).font(StockStepsTheme.font(type.bodySemiBold))
                        Text(i.stateLabel).font(.caption).foregroundStyle(client.isStaleQuote(i: i) ? colors.cautionText : colors.textSecondary)
                        if let note = i.proxyNote { Text(note).font(.caption).foregroundStyle(colors.textTertiary) }
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(client.value(i: i)).font(StockStepsTheme.font(type.bodySemiBold)).monospacedDigit()
                        if i.value != nil {
                            let sign = client.sign(i: i)
                            Text("\(client.direction(i: i)) \(client.change(i: i))").font(.caption).monospacedDigit()
                                .foregroundStyle(sign > 0 ? colors.positiveText : sign < 0 ? colors.negativeText : colors.textSecondary)
                        }
                    }
                }
                .stockCard()
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(client.accessibility(i: i))
            }
            Text(brief.indexExplainer).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
        }
    }

    @ViewBuilder
    private func stories(_ brief: DailyBrief, _ colors: StockColors) -> some View {
        sectionTitle("Market Stories")
        if brief.stories.isEmpty { Text("No market stories met our source checks for this brief.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary) }
        ForEach(brief.stories, id: \.id) { story in
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                Text(story.topic.uppercased()).font(.caption2.weight(.bold)).foregroundStyle(colors.primaryText)
                Text(story.headline).font(StockStepsTheme.font(type.bodySemiBold)).accessibilityAddTraits(.isHeader)
                if let summary = story.summary { Text(summary).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody) }
                Text([story.publisher ?? "Publisher not provided", client.published(iso: story.publishedAt)].compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(colors.textSecondary)
                if !story.relatedSymbols.isEmpty {
                    HStack { ForEach(story.relatedSymbols, id: \.self) { s in Button(s) { onOpenStock(s) }.buttonStyle(.bordered) } }
                }
                Text(story.evidenceNote).font(.caption).foregroundStyle(colors.textTertiary)
                HStack {
                    Button("Read article") { if let url = URL(string: story.sourceUrl) { openURL(url) } }
                        .accessibilityLabel("Read the original article from \(story.publisher ?? "the publisher")")
                    Button(client.isPlus(state: state!) ? "Understand More" : "Understand More · StockSteps+") { presenter.explain(storyId: story.id) }
                        .disabled(client.ai(state: state!, key: story.id)?.loading == true)
                }
                .frame(minHeight: CGFloat(dims.touchTarget))
                if let ai = state.flatMap({ client.ai(state: $0, key: story.id) }) { aiAnswer(ai, colors) }
            }
            .stockCard()
        }
    }

    @ViewBuilder
    private func aiAnswer(_ ai: BriefAiState, _ colors: StockColors) -> some View {
        if ai.loading {
            ProgressView().accessibilityLabel("Preparing an explanation")
        } else if let error = ai.error {
            Text(error).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.negativeText)
        } else if let a = ai.answer {
            VStack(alignment: .leading, spacing: 4) {
                Text(a.usesAi ? "AI explanation (StockSteps+)" : "Sample explanation (mock)").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.primaryText)
                Text(a.answer)
                ForEach(a.points, id: \.self) { Text("• \($0)").font(StockStepsTheme.font(type.small)) }
                if a.insufficientEvidence { Text("The sources don't contain enough to answer that fully.").font(.caption).foregroundStyle(colors.cautionText) }
                ForEach(a.sources, id: \.id) { s in Button("Source: \(s.publisher ?? s.title ?? "article")") { if let url = URL(string: s.url) { openURL(url) } } }
                if let left = a.remainingToday { Text("\(left.intValue) explanations left today").font(.caption).foregroundStyle(colors.textSecondary) }
            }
        }
    }

    @ViewBuilder
    private func watchlist(_ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            sectionTitle("From Your Watchlist")
            if state?.signedIn != true {
                StockSectionMessage(message: "Sign in to see news, price moves and earnings for companies you follow.", actionTitle: "Sign in", action: onSignIn)
            } else if let p = state?.personal {
                if p.watchlistCount == 0 {
                    StockSectionMessage(message: "Add companies to a watchlist to see their updates in your brief.", actionTitle: "Go to Watchlist", action: onWatchlist)
                } else if p.watchlistHighlights.isEmpty {
                    Text("Nothing notable from the \(p.watchlistCount) companies you follow in this session.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
                } else {
                    ForEach(Array(p.watchlistHighlights.enumerated()), id: \.offset) { _, h in
                        Button {
                            if let u = h.url, let url = URL(string: u) { openURL(url) }
                            else if let id = h.reportId { onOpenResults(id) }
                            else { onOpenStock(h.symbol) }
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(h.symbol)\(h.name.map { " · \($0)" } ?? "")").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSecondary)
                                Text(h.text).foregroundStyle(colors.textPrimary).multilineTextAlignment(.leading)
                                if let p = h.publisher { Text(p).font(.caption).foregroundStyle(colors.textSecondary) }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .stockCard()
                        }
                        .buttonStyle(.plain)
                        .accessibilityElement(children: .combine)
                    }
                    if p.moreHighlights > 0 { Button("\(p.moreHighlights) more updates are included with StockSteps+.") { onUpgrade() } }
                }
                if let note = p.notes.first(where: { $0.hasPrefix("Watching") }) { Text(note).font(.caption).foregroundStyle(colors.textSecondary) }
            } else if state?.personalLoading == true {
                ProgressView()
            } else {
                Text(state?.personalError ?? "Your watchlist updates aren't available right now.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
            }
        }
    }

    @ViewBuilder
    private func earnings(_ colors: StockColors) -> some View {
        if let rows = state?.personal?.earnings, !rows.isEmpty {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                sectionTitle("Upcoming Earnings")
                ForEach(rows, id: \.symbol) { e in
                    Button { if let id = e.eventId { onOpenEarnings(id) } else { onOpenStock(e.symbol) } } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(e.name ?? e.symbol) (\(e.symbol))").font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary)
                            Text(["Expected \(client.date(iso: e.date))", "\(e.dateStatus) date", e.timing,
                                  e.epsEstimate.map { "EPS estimate \(e.currency ?? "") \(client.money(v: $0.doubleValue))" },
                                  e.reason == "portfolio" ? "In your portfolio" : e.reason == "watchlist" ? "On your watchlist" : "General example"]
                                .compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(colors.textSecondary).multilineTextAlignment(.leading)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .stockCard()
                    }
                    .buttonStyle(.plain)
                    .accessibilityElement(children: .combine)
                }
                Text("Report dates can change until a company confirms them.").font(.caption).foregroundStyle(colors.textSecondary)
            }
        }
    }

    private func conceptCard(_ brief: DailyBrief, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text("Concept of the Day").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.primaryText)
            Text(brief.concept.title).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).accessibilityAddTraits(.isHeader)
            Text(brief.concept.explanation).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
            if let example = brief.concept.example { Text("Example: \(example)").font(StockStepsTheme.font(type.small)) }
            Text(brief.concept.whyToday).font(.caption).foregroundStyle(colors.textSecondary)
            HStack {
                Button("Understand more") { concept = client.term(id: brief.concept.learnTermId) }
                Button("Open Learn", action: onLearn)
            }
            .frame(minHeight: CGFloat(dims.touchTarget))
        }
        .padding(CGFloat(space.cardPadding))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.educationContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
    }

    @ViewBuilder
    private func premium(_ colors: StockColors) -> some View {
        if let p = state?.personal {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                Text("StockSteps+ insights").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).accessibilityAddTraits(.isHeader)
                if let insights = p.premiumInsights {
                    ForEach(insights, id: \.self) { Text("• \($0)").font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody) }
                    if !p.companyStories.isEmpty {
                        Text("More from companies you follow").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSecondary)
                        ForEach(p.companyStories, id: \.id) { s in
                            Button("\(s.relatedSymbols.first.map { "\($0): " } ?? "")\(s.headline)") { if let url = URL(string: s.sourceUrl) { openURL(url) } }.multilineTextAlignment(.leading)
                        }
                    }
                    TextField("Ask about this brief", text: $question, axis: .vertical).textFieldStyle(.roundedBorder)
                        .onChange(of: question) { _, v in if v.count > 300 { question = String(v.prefix(300)) } }
                    Button("Ask") { presenter.ask(question: question) }.buttonStyle(.borderedProminent)
                        .disabled(question.trimmingCharacters(in: .whitespaces).count < 3 || state.flatMap { client.ai(state: $0, key: "ask") }?.loading == true)
                    if let ai = state.flatMap({ client.ai(state: $0, key: "ask") }) { aiAnswer(ai, colors) }
                    Text("Answers use only this brief's sources. No predictions or buy/sell advice.").font(.caption).foregroundStyle(colors.textSecondary)
                } else {
                    Text("See how today's market relates to the companies you follow, more company stories, and ask follow-up questions with source-backed AI explanations.")
                        .font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                    Button("Preview StockSteps+", action: onUpgrade)
                }
            }
            .stockCard()
        }
    }

    private func preferences(_ colors: StockColors) -> some View {
        NavigationStack {
            Form {
                if state?.signedIn != true {
                    Text("Sign in to get a notification when your brief is ready.")
                    Button("Sign in") { showPreferences = false; onSignIn() }
                } else if let p = state?.preferences {
                    Toggle("Notify me when the brief is ready", isOn: Binding(get: { p.notificationsEnabled }, set: { client.setNotifications(p: p, on: $0) }))
                        .disabled(state?.preferencesBusy == true)
                    Picker("Delivery time (\(p.timeZone))", selection: Binding(get: { Int(p.deliveryHour) }, set: { client.setHour(p: p, hour: Int32($0)) })) {
                        ForEach([7, 8, 9, 17, 18], id: \.self) { h in Text(h < 12 ? "\(h) AM" : "\(h - 12) PM").tag(h) }
                    }
                    Toggle("Mention companies I follow (StockSteps+)", isOn: Binding(get: { p.personalizedNotifications }, set: { on in
                        if state.map({ client.isPlus(state: $0) }) == true { client.setPersonalized(p: p, on: on) } else { onUpgrade() }
                    })).disabled(!p.notificationsEnabled)
                    Text("Sent on market days only, once per brief. Your device's notification permission also needs to be on. You can turn this off at any time.")
                        .font(.caption).foregroundStyle(colors.textSecondary)
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Daily brief notification")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { showPreferences = false } } }
        }
    }
}

// MARK: - History

struct DailyBriefHistoryScene: View {
    let model: BriefModel
    let onOpenBrief: (String) -> Void
    var onUpgrade: () -> Void = {}
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        List {
            if let history = model.state?.history {
                if history.items.isEmpty { Text("No briefs yet.") }
                ForEach(history.items, id: \.id) { s in
                    Button { onOpenBrief(s.id) } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(s.edition.label) · \(model.client.date(iso: s.briefDate))").font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary)
                            Text(s.summaryLine).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody).lineLimit(2)
                            Text("\(s.readingMinutes) min read").font(.caption).foregroundStyle(colors.textSecondary)
                        }
                    }
                    .accessibilityElement(children: .combine)
                }
                if history.lockedCount > 0 {
                    Button("\(history.lockedCount) older briefs are included with StockSteps+.", action: onUpgrade)
                }
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Previous briefs")
        .navigationBarTitleDisplayMode(.inline)
        .task { model.presenter.loadHistory() }
    }
}
