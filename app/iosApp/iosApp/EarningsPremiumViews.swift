import Observation
import Shared
import SwiftUI

// Earnings Intelligence Lite, Phase 5 (StockSteps+): premium Earnings Results sections, the
// personalized digest and its settings. The same shared presenters as Android via IosEarningsClient;
// plan, quotas and every AI sentence come from the server. AI text is plain text only.

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography

extension Notification.Name {
    /// Posted when Settings closes (the StockSteps+ plan may have changed): premium sections re-check the server.
    static let stockStepsPlanMayHaveChanged = Notification.Name("stockStepsPlanMayHaveChanged")
    /// Weekly digest notification tap.
    static let stockStepsOpenEarningsDigest = Notification.Name("stockStepsOpenEarningsDigest")
}

enum PremiumAction {
    case explain, toggleExplanation, ask(String), resetConversation, loadHistory, metric(String), upgrade, signIn, retry, scenario(String)
}

/// One report's premium presenter (released with the screen).
@MainActor @Observable
final class EarningsPremiumModel {
    private(set) var state: EarningsPremiumState?
    @ObservationIgnored let client: IosEarningsClient
    @ObservationIgnored private let reportId: String
    @ObservationIgnored private var made: EarningsPremiumPresenter?
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    /// Created on first use (`activate()` from the scene's `.task`), not in `init` — see `EarningsResultsModel` (Phase 5C.1).
    var presenter: EarningsPremiumPresenter {
        if let made { return made }
        let created = client.premium(reportId: reportId)
        made = created
        return created
    }

    init(reportId: String, client: IosEarningsClient) {
        self.client = client
        self.reportId = reportId
    }
    func activate() {
        guard subscription == nil else { return }
        subscription = client.observePremium(presenter: presenter) { [weak self] in self?.state = $0 }
    }
    deinit {
        subscription?.cancel()
        if let made { client.release(presenter: made) }
    }

    func handle(_ action: PremiumAction, onUpgrade: () -> Void, onSignIn: () -> Void) {
        switch action {
        case .explain: presenter.explain(refresh: false)
        case .toggleExplanation: presenter.toggleExpanded()
        case .ask(let q): presenter.ask(text: q)
        case .resetConversation: presenter.resetConversation()
        case .loadHistory: client.loadHistory(presenter: presenter)
        case .metric(let name): client.selectMetric(presenter: presenter, name: name)
        case .upgrade: onUpgrade()
        case .signIn: onSignIn()
        case .retry: presenter.refresh()
        case .scenario(let id): client.setPremiumScenario(presenter: presenter, id: id)
        }
    }
}

/// "StockSteps+" in words (never colour alone).
struct PlusBadge: View {
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        Text("StockSteps+").font(StockStepsTheme.font(type.label, relativeTo: .caption1)).foregroundStyle(colors.primaryText)
            .padding(.horizontal, CGFloat(space.sm)).padding(.vertical, CGFloat(space.xxs))
            .background(colors.primaryContainer, in: Capsule())
    }
}

private struct PremiumTitle: View {
    let title: String
    var subtitle: String? = nil
    var plus = true
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            HStack {
                Text(title).font(.headline).foregroundStyle(colors.textPrimary).accessibilityAddTraits(.isHeader)
                Spacer()
                if plus { PlusBadge() }
            }
            if let subtitle { Text(subtitle).font(.subheadline).foregroundStyle(colors.textSecondary) }
        }
    }
}

private struct Caption: View {
    let text: String
    var caution = false
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        Text(text).font(.caption).foregroundStyle(caution ? colors.cautionText : colors.textSecondary)
    }
}

private struct PremiumErrorView: View {
    let error: PremiumError
    let onRetry: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Caption(text: error.message, caution: true)
            if error.retryable { Button("Try Again", action: onRetry).frame(minHeight: 48) }
        }
        .accessibilityElement(children: .combine)
    }
}

/// Contextual StockSteps+ prompt (the existing upgrade path: Settings). Never shown automatically.
extension View {
    func premiumUpgradeAlert(_ reason: UpgradeReason?, benefits: [String], fairUse: String, onUpgrade: @escaping () -> Void, onDismiss: @escaping () -> Void) -> some View {
        alert(reason?.title ?? "", isPresented: Binding(get: { reason != nil }, set: { if !$0 { onDismiss() } })) {
            Button("See StockSteps+") { onDismiss(); onUpgrade() }
            Button("Not now", role: .cancel) { onDismiss() }
        } message: {
            Text(([reason?.body ?? ""] + benefits.map { "• \($0)" } + [fairUse, "Basic earnings results, history and reminders stay free."]).joined(separator: "\n"))
        }
    }
}

// MARK: - Earnings Results premium sections

struct PremiumEarningsSections: View {
    let state: EarningsPremiumState
    let client: IosEarningsClient
    let onAction: (PremiumAction) -> Void

    var body: some View {
        UnderstandSection(state: state, client: client, onAction: onAction)
        AskSection(state: state, onAction: onAction)
        HistorySection(state: state, onAction: onAction)
    }
}

private struct UnderstandSection: View {
    let state: EarningsPremiumState
    let client: IosEarningsClient
    let onAction: (PremiumAction) -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            PremiumTitle(title: client.premiumTitle, subtitle: client.premiumSubtitle)
            if state.sampleData {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack { ForEach(client.premiumScenarios, id: \.self) { s in StockChip(title: s[1], selected: (state.scenario ?? "") == s[0]) { onAction(.scenario(s[0])) } } }
                }
            }
            if !state.signedIn {
                Text("An explanation of what was reported, how it compared with expectations and what's still uncertain.").font(.subheadline).foregroundStyle(colors.textBody)
                Button { onAction(.signIn) } label: { Text("Sign In to Explain With AI").frame(maxWidth: .infinity, minHeight: 48) }.buttonStyle(.bordered)
            } else {
                if state.loading && state.overview == nil { ProgressView().accessibilityLabel("Loading StockSteps+ features") }
                if let error = state.overviewError { PremiumErrorView(error: error) { onAction(.retry) } }
                if let overview = state.overview { content(overview, colors) }
            }
        }
        .stockCard()
    }

    @ViewBuilder private func content(_ overview: PremiumEarningsOverview, _ colors: StockColors) -> some View {
        if let note = state.planNote { Caption(text: note, caution: true) }
        if let e = state.explanation {
            if e.sample { Caption(text: "Sample explanation from a template (MOCK). No AI service was used.", caution: true) }
            if e.cached { Caption(text: "Saved explanation from \(String(e.generatedAt.prefix(10))); it matches the current data.") }
            Text(e.summary.text).foregroundStyle(colors.textBody)
            VStack(alignment: .leading, spacing: 4) {
                Text("Key Takeaways").font(.subheadline.weight(.semibold)).accessibilityAddTraits(.isHeader)
                ForEach(e.beginnerTakeaways, id: \.self) { Text("• \($0)").font(.subheadline).foregroundStyle(colors.textBody) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(CGFloat(space.cardPadding))
            .background(colors.educationContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
            Button(state.expanded ? "Show less" : "Show full explanation") { onAction(.toggleExplanation) }
                .frame(minHeight: 48).accessibilityValue(state.expanded ? "Expanded" : "Collapsed")
            if state.expanded {
                if let c = e.epsExplanation { claim("EPS compared with expectations", c, e.citations, colors) }
                if let c = e.revenueExplanation { claim("Revenue compared with expectations", c, e.citations, colors) }
                if let c = e.growthExplanation { claim("Compared with earlier periods", c, e.citations, colors) }
                if let c = e.priceReactionExplanation { claim("What the stock did after earnings", c, e.citations, colors) }
                if !e.uncertaintyNotes.isEmpty {
                    Text("What remains uncertain").font(.subheadline.weight(.semibold)).accessibilityAddTraits(.isHeader)
                    ForEach(e.uncertaintyNotes, id: \.self) { Text("• \($0)").font(.subheadline).foregroundStyle(colors.textBody) }
                }
                if !e.keyTerms.isEmpty {
                    Text("Key terms").font(.subheadline.weight(.semibold)).accessibilityAddTraits(.isHeader)
                    ForEach(e.keyTerms, id: \.term) { t in Text("\(t.term): \(t.meaning)").font(.subheadline).foregroundStyle(colors.textBody) }
                }
                let sources = e.citations.filter { $0.kind != .lesson }
                if !sources.isEmpty {
                    Text("Sources").font(.caption.weight(.semibold)).foregroundStyle(colors.textSecondary)
                    ForEach(sources, id: \.sourceId) { c in Caption(text: "\(c.label) · \(c.provider)" + (c.retrievedAt.map { " · retrieved \(String($0.prefix(10)))" } ?? "")) }
                }
            }
            ForEach(e.dataWarnings, id: \.self) { Caption(text: $0, caution: true) }
            Caption(text: e.disclaimer)
            if let usage = state.explainUsage { Caption(text: usage) }
        } else {
            Text(overview.preview).font(.subheadline).foregroundStyle(colors.textBody)
            if overview.explanation == .stale { Caption(text: "An earlier explanation used figures that have since changed. A new one will use the current data.", caution: true) }
            if !state.plus { Caption(text: "Explanations use only the verified figures on this screen. Part of StockSteps+.") }
            Button { onAction(.explain) } label: {
                Label(state.plus ? "Explain With AI" : "Explain With AI · StockSteps+", systemImage: "lightbulb").frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .disabled(state.explaining || !overview.aiAvailable)
            if state.explaining {
                HStack { ProgressView(); Caption(text: "Building an explanation from the verified figures…") }.accessibilityElement(children: .combine)
            }
            if let error = state.explainError { PremiumErrorView(error: error) { onAction(.explain) } }
            if state.plus, let usage = state.explainUsage { Caption(text: usage) }
            ForEach(overview.notes, id: \.self) { Caption(text: $0) }
        }
    }

    private func claim(_ title: String, _ claim: ExplainedClaim, _ citations: [EarningsCitation], _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.subheadline.weight(.semibold)).accessibilityAddTraits(.isHeader)
            Text(claim.text).font(.subheadline).foregroundStyle(colors.textBody)
            let labels = claim.sourceIds.compactMap { id in citations.first { $0.sourceId == id }?.label }
            if !labels.isEmpty { Caption(text: "Source: " + labels.joined(separator: "; ")) }
        }
    }
}

private struct AskSection: View {
    let state: EarningsPremiumState
    let onAction: (PremiumAction) -> Void
    @State private var text = ""
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            PremiumTitle(title: "Ask About These Results", subtitle: "Answers use this report's verified figures and StockSteps lessons.")
            if !state.signedIn || state.overview != nil {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach(state.chips, id: \.id) { chip in
                            Button(chip.text) { onAction(state.signedIn ? .ask(chip.text) : .signIn) }.buttonStyle(.bordered).frame(minHeight: 48).disabled(state.asking)
                        }
                    }
                }
                ForEach(state.conversation, id: \.id) { turn in
                    VStack(alignment: .leading, spacing: 2) {
                        Text("You asked: \(turn.question)").font(.subheadline.weight(.semibold)).foregroundStyle(colors.textPrimary)
                        if turn.pending { Caption(text: "Thinking…") }
                        else if let error = turn.error { PremiumErrorView(error: error) { onAction(.ask(turn.question)) } }
                        else if let a = turn.answer {
                            if a.sample { Caption(text: "Sample answer (MOCK template)") }
                            Text(a.answer).font(.subheadline).foregroundStyle(a.scope == .unsupported ? colors.cautionText : colors.textBody)
                            ForEach(a.points, id: \.self) { Text("• \($0)").font(.subheadline).foregroundStyle(colors.textBody) }
                            if a.scope == .insufficientData { Caption(text: "The verified data can't answer this fully.") }
                            if !a.citations.isEmpty { Caption(text: "Source: " + a.citations.map { $0.label }.joined(separator: "; ")) }
                        }
                    }
                    .accessibilityElement(children: .combine)
                }
                if let note = state.contextNote { Caption(text: note, caution: true) }
                TextField("e.g. Why can a stock fall after an EPS beat?", text: $text, axis: .vertical)
                    .textFieldStyle(.roundedBorder).lineLimit(1...4)
                    .onChange(of: text) { _, v in if v.count > 300 { text = String(v.prefix(300)) } }
                    .accessibilityLabel("Your question")
                Caption(text: "\(text.count)/300 · Educational answers only, never advice.")
                HStack {
                    Button(state.plus || !state.signedIn ? "Ask" : "Ask · StockSteps+") { onAction(.ask(text)); if state.plus { text = "" } }
                        .buttonStyle(.borderedProminent).frame(minHeight: 48)
                        .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty || state.asking)
                    if !state.conversation.isEmpty { Button("Start Over") { onAction(.resetConversation) }.frame(minHeight: 48) }
                }
                if state.plus, let usage = state.askUsage { Caption(text: usage) }
            }
        }
        .stockCard()
        .onChange(of: state.reportId) { _, _ in text = "" }
    }
}

private struct HistorySection: View {
    let state: EarningsPremiumState
    let onAction: (PremiumAction) -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            PremiumTitle(title: "Historical Earnings", subtitle: "Revenue and EPS over recent fiscal quarters, with plain-English observations.")
            if let h = state.history {
                if h.sampleData { Caption(text: "Sample earnings history for development, not real results.", caution: true) }
                Picker("Metric", selection: Binding(get: { state.metric.name }, set: { onAction(.metric($0)) })) {
                    Text("Revenue").tag("REVENUE"); Text("EPS").tag("EPS")
                }
                .pickerStyle(.segmented)
                if let chart = state.chart {
                    HistoryChart(chart: chart)
                    if let note = chart.emptyNote { Caption(text: note) }
                    Caption(text: "Drag across the chart to see each quarter's full value.")
                }
                if !h.deterministicObservations.isEmpty {
                    Text("What the history shows").font(.subheadline.weight(.semibold)).accessibilityAddTraits(.isHeader)
                    ForEach(h.deterministicObservations, id: \.text) { Text("• \($0.text)").font(.subheadline).foregroundStyle(colors.textBody) }
                }
                ForEach(state.historyRows, id: \.reportId) { row in
                    VStack(alignment: .leading, spacing: 2) {
                        Divider()
                        Text(row.label).font(.subheadline.weight(.semibold))
                        if row.missing { Caption(text: "No results for this quarter from the data source.") }
                        ForEach(row.lines, id: \.label) { l in
                            HStack { Text(l.label).font(.subheadline).foregroundStyle(colors.textSecondary); Spacer(); Text(l.value).font(.subheadline.weight(.medium)) }
                        }
                        ForEach(row.notes, id: \.self) { Caption(text: $0) }
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(row.accessibility)
                }
                ForEach(h.dataWarnings, id: \.self) { Caption(text: $0, caution: true) }
                if h.nextCursor != nil { Button("Show Earlier Quarters") { onAction(.loadHistory) }.frame(minHeight: 48) }
                Caption(text: h.disclaimer)
                ForEach(h.sources, id: \.self) { Caption(text: $0) }
            } else {
                if let n = state.overview?.historyQuarters, n > 0 { Caption(text: "\(n) reported quarter\(n == 1 ? "" : "s") available (up to 8 shown).") }
                Button { onAction(state.signedIn ? .loadHistory : .signIn) } label: {
                    Text(state.plus || !state.signedIn ? "Show Historical Earnings" : "Show Historical Earnings · StockSteps+").frame(maxWidth: .infinity, minHeight: 48)
                }
                .buttonStyle(.bordered).disabled(state.historyLoading)
                if state.historyLoading { ProgressView().accessibilityLabel("Loading historical earnings") }
                if let error = state.historyError { PremiumErrorView(error: error) { onAction(.loadHistory) } }
            }
        }
        .stockCard()
    }
}

/// Quarterly revenue or EPS: nil slots are gaps (never zero, never joined), a zero line for losses,
/// drag to read the full value; VoiceOver reads every quarter.
struct HistoryChart: View {
    let chart: HistoryChartView
    @Environment(\.colorScheme) private var scheme
    @State private var selected: Int?

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let values: [Double?] = chart.values.map { ($0 as? NSNumber)?.doubleValue }
        let valid = values.compactMap { $0 } + (chart.zeroLine ? [0] : [])
        let high = valid.max() ?? 1, low = valid.min() ?? 0
        let span = high - low > 0 ? high - low : 1
        let focus = selected ?? (values.lastIndex { $0 != nil } ?? 0)
        VStack(alignment: .leading, spacing: 4) {
            Text(chart.details.indices.contains(focus) ? chart.details[focus] : "").font(.subheadline.weight(.semibold))
            if chart.zeroLine { Caption(text: "- - Zero (a loss is below this line)") }
            GeometryReader { proxy in
                let size = proxy.size
                let step = values.count > 1 ? size.width / CGFloat(values.count - 1) : 0
                let y: (Double) -> CGFloat = { 6 + (size.height - 12) * CGFloat(1 - ($0 - low) / span) }
                ZStack(alignment: .topLeading) {
                    if chart.zeroLine {
                        Path { p in p.move(to: CGPoint(x: 0, y: y(0))); p.addLine(to: CGPoint(x: size.width, y: y(0))) }
                            .stroke(colors.textTertiary, style: StrokeStyle(lineWidth: 1.5, dash: [8, 6]))
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
                    ForEach(values.indices, id: \.self) { i in
                        if let v = values[i] { Circle().fill(colors.primary).frame(width: i == focus ? 8 : 5, height: i == focus ? 8 : 5).position(x: CGFloat(i) * step, y: y(v)) }
                    }
                }
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0).onChanged { g in
                    guard values.count > 1 else { return }
                    selected = min(max(Int((g.location.x / size.width * CGFloat(values.count - 1)).rounded()), 0), values.count - 1)
                })
            }
            .frame(height: 144)
            HStack {
                Text(chart.xLabels.first ?? "").font(.caption2).foregroundStyle(colors.textTertiary)
                Spacer()
                Text(chart.xLabels.last ?? "").font(.caption2).foregroundStyle(colors.textTertiary)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(chart.description_)
    }
}

// MARK: - Personalized digest

@MainActor @Observable
final class EarningsDigestModel {
    private(set) var state: EarningsDigestState?
    @ObservationIgnored let presenter: EarningsDigestPresenter
    @ObservationIgnored let client: IosEarningsClient
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    /// Loads nothing until `activate()`: SwiftUI evaluates `navigationDestination` builders (and their `init`s) on every `AppScene` update even
    /// when the screen isn't shown, which sent up to 7 digest-preferences requests per launch (Phase 5C.1). The scenes activate from `.task`.
    init(client: IosEarningsClient, loadDigest: Bool) {
        self.client = client
        presenter = client.digest(loadDigest: loadDigest, start: false)
    }
    func activate() {
        guard subscription == nil else { return }
        subscription = client.observeDigest(presenter: presenter) { [weak self] in self?.state = $0 }
        presenter.start()
    }
    deinit {
        subscription?.cancel()
        client.release(presenter: presenter)
    }
}

struct EarningsDigestScene: View {
    @State private var model: EarningsDigestModel
    let onOpenResults: (String) -> Void
    let onOpenEvent: (String) -> Void
    let onSettings: () -> Void
    let onUpgrade: () -> Void
    let onSignIn: () -> Void
    let onRecentWatchlist: () -> Void

    init(client: IosEarningsClient, onOpenResults: @escaping (String) -> Void, onOpenEvent: @escaping (String) -> Void, onSettings: @escaping () -> Void,
         onUpgrade: @escaping () -> Void, onSignIn: @escaping () -> Void, onRecentWatchlist: @escaping () -> Void) {
        _model = State(initialValue: EarningsDigestModel(client: client, loadDigest: true))
        self.onOpenResults = onOpenResults; self.onOpenEvent = onOpenEvent; self.onSettings = onSettings
        self.onUpgrade = onUpgrade; self.onSignIn = onSignIn; self.onRecentWatchlist = onRecentWatchlist
    }

    var body: some View {
        EarningsDigestScreen(state: model.state, client: model.client, presenter: model.presenter, onOpenResults: onOpenResults, onOpenEvent: onOpenEvent,
                             onSettings: onSettings, onUpgrade: onUpgrade, onSignIn: onSignIn, onRecentWatchlist: onRecentWatchlist)
            .navigationTitle("Earnings digest")
            .navigationBarTitleDisplayMode(.inline)
            .task { model.activate() }
            .refreshable { model.presenter.refresh() }
            .onReceive(NotificationCenter.default.publisher(for: .stockStepsPlanMayHaveChanged)) { _ in model.presenter.refresh() }
            .premiumUpgradeAlert(model.state?.upgrade, benefits: model.client.premiumBenefits, fairUse: model.client.fairUse, onUpgrade: onUpgrade,
                                 onDismiss: { model.presenter.dismissUpgrade() })
    }
}

struct EarningsDigestScreen: View {
    let state: EarningsDigestState?
    let client: IosEarningsClient
    let presenter: EarningsDigestPresenter
    let onOpenResults: (String) -> Void
    let onOpenEvent: (String) -> Void
    let onSettings: () -> Void
    let onUpgrade: () -> Void
    let onSignIn: () -> Void
    let onRecentWatchlist: () -> Void
    @Environment(\.colorScheme) private var scheme
    @State private var lesson: DigestLesson?

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.md)) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack { Text("Your Earnings Digest").font(StockStepsTheme.font(type.screenTitle, relativeTo: .title1)).accessibilityAddTraits(.isHeader); Spacer(); PlusBadge() }
                    Text("What happened with the companies on your watchlists. Watching a company doesn't mean you own it.").font(.subheadline).foregroundStyle(colors.textSecondary)
                }
                if let state { content(state, colors) }
            }
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
        }
        .background(colors.appBackground)
        .alert(lesson?.title ?? "", isPresented: Binding(get: { lesson != nil }, set: { if !$0 { lesson = nil } })) {
            Button("Got it", role: .cancel) { lesson = nil }
        } message: { Text(lesson?.body ?? "") }
    }

    @ViewBuilder private func content(_ state: EarningsDigestState, _ colors: StockColors) -> some View {
        if !state.signedIn {
            StockSectionMessage(message: "Sign in to see your personalized earnings digest.", actionTitle: "Sign In", action: onSignIn)
        } else {
            if state.loading { ProgressView().accessibilityLabel("Loading your digest") }
            if let error = state.settingsError { StockSectionMessage(message: error.message, actionTitle: "Try again") { presenter.refresh() } }
            if let settings = state.settings, !settings.plus {
                VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                    Text("A weekly summary of your watchlist's earnings: what was reported, what's coming up and what to learn from it.").foregroundStyle(colors.textBody)
                    if let note = client.planStatus(status: settings.entitlementStatus) { Caption(text: note, caution: true) }
                    Button { onUpgrade() } label: { Text("See StockSteps+").frame(maxWidth: .infinity, minHeight: 48) }.buttonStyle(.borderedProminent)
                    Button("Recent Earnings From Your Watchlist", action: onRecentWatchlist).frame(minHeight: 48)
                    Caption(text: "Recent results and the Earnings Calendar stay free.")
                }
                .stockCard()
            }
            if let error = state.error { StockSectionMessage(message: error.message, actionTitle: error.retryable ? "Try again" : nil, action: error.retryable ? { presenter.refresh() } : nil) }
            if let d = state.digest { digest(d, state, colors) }
        }
    }

    @ViewBuilder private func digest(_ d: PersonalizedEarningsDigest, _ state: EarningsDigestState, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("\(client.date(date: d.periodStart)) – \(client.date(date: d.periodEnd))").font(.headline)
            Caption(text: "From \(d.watchlistCompanies) compan\(d.watchlistCompanies == 1 ? "y" : "ies") on your watchlists.")
            if d.sampleData {
                Caption(text: "Sample earnings data for development, not real results.", caution: true)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack { ForEach(client.digestScenarios, id: \.self) { s in StockChip(title: s[1], selected: (state.scenario ?? "") == s[0]) { client.setDigestScenario(presenter: presenter, id: s[0]) } } }
                }
            }
        }
        if d.empty { StockSectionMessage(message: d.emptyReason ?? "Nothing to report this week.", actionTitle: "Recent Earnings From Your Watchlist", action: onRecentWatchlist) }
        if !d.recentlyReported.isEmpty {
            Text("Recently Reported").font(.title3.weight(.semibold)).accessibilityAddTraits(.isHeader)
            ForEach(d.recentlyReported, id: \.reportId) { r in
                Button { onOpenResults(r.reportId) } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(r.companyName) (\(r.symbol))").font(.headline).foregroundStyle(colors.textPrimary)
                        Text(r.headline).font(.subheadline).foregroundStyle(colors.textBody)
                        Caption(text: "Reported \(client.date(date: r.reportDate))")
                        if let t = r.reactionText { Caption(text: t) }
                        if let n = r.dataNote { Caption(text: n, caution: true) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .stockCard()
                }
                .buttonStyle(.plain)
                .accessibilityHint("Opens \(r.companyName) results")
            }
        }
        if !d.upcomingEvents.isEmpty {
            Text("Coming Up").font(.title3.weight(.semibold)).accessibilityAddTraits(.isHeader)
            ForEach(d.upcomingEvents, id: \.eventId) { u in
                Button { onOpenEvent(u.eventId) } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(u.companyName) (\(u.symbol))").font(.headline).foregroundStyle(colors.textPrimary)
                        Caption(text: u.whenText)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .stockCard()
                }
                .buttonStyle(.plain)
            }
        }
        if !d.educationalHighlights.isEmpty {
            Text("What to Learn").font(.title3.weight(.semibold)).accessibilityAddTraits(.isHeader)
            ForEach(d.educationalHighlights, id: \.key) { l in
                VStack(alignment: .leading, spacing: 0) {
                    Button(l.title, systemImage: "lightbulb") { lesson = l }.frame(minHeight: 48)
                    Caption(text: l.reason)
                }
            }
        }
        if !d.empty {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                PremiumTitle(title: "Summarize With AI", subtitle: "A short educational summary of this digest, built only from the items above.")
                if let ai = d.aiSummary {
                    if ai.sample { Caption(text: "Sample summary (MOCK template)") }
                    Text(ai.text).font(.subheadline).foregroundStyle(colors.textBody)
                    ForEach(ai.points, id: \.self) { Text("• \($0)").font(.subheadline).foregroundStyle(colors.textBody) }
                    Caption(text: "Education, not investment advice.")
                } else {
                    Button { presenter.explain() } label: { Label("Summarize With AI", systemImage: "lightbulb").frame(minHeight: 48) }
                        .buttonStyle(.bordered).disabled(state.aiLoading)
                    if state.aiLoading { ProgressView() }
                }
                if let error = state.aiError { PremiumErrorView(error: error) { presenter.explain() } }
                if let q = client.digestQuota(digest: d) { Caption(text: q) }
            }
            .stockCard()
        }
        ForEach(d.dataNotes, id: \.self) { Caption(text: $0) }
        Button { onSettings() } label: { Label("Digest Settings", systemImage: "bell").frame(maxWidth: .infinity, minHeight: 48) }.buttonStyle(.bordered)
        Button("Recent Earnings From Your Watchlist", action: onRecentWatchlist).frame(minHeight: 48)
        if let h = state.history {
            Text("Earlier Digests").font(.subheadline.weight(.semibold)).accessibilityAddTraits(.isHeader)
            if h.entries.isEmpty { Caption(text: "No earlier digests yet.") }
            ForEach(h.entries, id: \.digestId) { e in
                Caption(text: "\(client.date(date: e.periodStart)) – \(client.date(date: e.periodEnd)): \(e.reportedCount) reported, \(e.upcomingCount) coming up" + (e.notified ? " · notification sent" : ""))
            }
        } else {
            Button("Earlier Digests") { presenter.loadHistory() }.frame(minHeight: 48)
        }
    }
}

/// "Earnings Digest & AI": weekly digest (opt-in), learning interests and AI usage.
struct EarningsDigestSettingsScene: View {
    @State private var model: EarningsDigestModel
    let permissionDenied: Bool
    let onUpgrade: () -> Void
    let onSignIn: () -> Void
    let onReminders: (() -> Void)?
    @Environment(\.colorScheme) private var scheme

    init(client: IosEarningsClient, permissionDenied: Bool, onUpgrade: @escaping () -> Void, onSignIn: @escaping () -> Void, onReminders: (() -> Void)? = nil) {
        _model = State(initialValue: EarningsDigestModel(client: client, loadDigest: false))
        self.permissionDenied = permissionDenied; self.onUpgrade = onUpgrade; self.onSignIn = onSignIn; self.onReminders = onReminders
    }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            VStack(alignment: .leading, spacing: CGFloat(space.md)) {
                Text("A weekly summary of your watchlist's earnings, and your AI allowance.").font(.subheadline).foregroundStyle(colors.textSecondary)
                if let state = model.state { content(state, colors) }
            }
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
        }
        .background(colors.appBackground)
        .navigationTitle("Earnings Digest & AI")
        .navigationBarTitleDisplayMode(.inline)
        .task { model.activate() }
        .onReceive(NotificationCenter.default.publisher(for: .stockStepsPlanMayHaveChanged)) { _ in model.presenter.refresh() }
        .premiumUpgradeAlert(model.state?.upgrade, benefits: model.client.premiumBenefits, fairUse: model.client.fairUse, onUpgrade: onUpgrade,
                             onDismiss: { model.presenter.dismissUpgrade() })
    }

    @ViewBuilder private func content(_ state: EarningsDigestState, _ colors: StockColors) -> some View {
        let client = model.client
        let presenter = model.presenter
        if !state.signedIn {
            StockSectionMessage(message: "Sign in to manage your earnings digest.", actionTitle: "Sign In", action: onSignIn)
        } else if let s = state.settings {
            let p = s.preferences
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                PremiumTitle(title: "Personalized Earnings Digest", subtitle: "Off unless you turn it on. Nothing is sent in a week with nothing to report.")
                Picker("Digest", selection: Binding(get: { p.cadence == .weekly }, set: { client.setDigestWeekly(presenter: presenter, weekly: $0) })) {
                    Text("No digest").tag(false); Text("Weekly").tag(true)
                }
                .pickerStyle(.segmented)
                Text(s.statusMessage).font(.subheadline).foregroundStyle(s.deliveryActive ? colors.textBody : colors.cautionText)
                if let next = s.nextDeliveryText { Caption(text: "Next: \(next)") }
                if permissionDenied && p.cadence == .weekly { Caption(text: "Notifications are off for StockSteps on this device, so the digest won't appear until you allow them.", caution: true) }
                if state.saving { ProgressView() }
                Text("Day").font(.subheadline.weight(.semibold))
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach(client.digestDays, id: \.self) { day in
                            StockChip(title: String(client.dayLabel(day: day).prefix(3)), selected: p.dayOfWeek == day) { client.setDigestDay(presenter: presenter, day: day) }
                                .accessibilityLabel(client.dayLabel(day: day))
                        }
                    }
                }
                Toggle("Include upcoming earnings", isOn: Binding(get: { p.includeUpcoming }, set: { client.setDigestUpcoming(presenter: presenter, include: $0) })).frame(minHeight: 48)
                Caption(text: "Delivered around \(s.deliveryTime) (\(s.timeZone))" + (s.quietHours.map { ", outside quiet hours \($0)" } ?? "") + ". Time and quiet hours are set in Earnings Reminders.")
                if let onReminders { Button("Earnings Reminders Settings", systemImage: "bell", action: onReminders).frame(minHeight: 48) }
                if !s.plus { Button { onUpgrade() } label: { Text("See StockSteps+").frame(minHeight: 48) }.buttonStyle(.bordered) }
                if let error = state.settingsError { Caption(text: error.message, caution: true) }
            }
            .stockCard()
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                PremiumTitle(title: "Learning Interests", subtitle: "Used only to choose lessons in your digest.", plus: false)
                ForEach(client.digestInterests, id: \.self) { pair in
                    Toggle(pair[1], isOn: Binding(get: { p.interests.contains(pair[0]) }, set: { _ in client.toggleDigestInterest(presenter: presenter, key: pair[0]) })).frame(minHeight: 48)
                }
            }
            .stockCard()
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                PremiumTitle(title: "AI Usage", subtitle: client.fairUse)
                if let usage = s.usage, usage.plus {
                    ForEach(usage.quotas, id: \.category.name) { q in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(q.category.label).font(.subheadline.weight(.semibold))
                            ProgressView(value: q.limit == 0 ? 0 : Double(q.remaining) / Double(q.limit))
                            if let t = client.quotaText(quota: q) { Caption(text: t) }
                        }
                        .accessibilityElement(children: .combine)
                    }
                } else {
                    Caption(text: "AI earnings features are part of StockSteps+.")
                }
                if let note = s.usage?.note { Caption(text: note, caution: true) }
                if let note = client.planStatus(status: s.entitlementStatus) { Caption(text: note, caution: true) }
                if s.sampleData { Caption(text: "MOCK: limits and AI output are simulated; no AI service or billing is used.") }
            }
            .stockCard()
        } else if let error = state.settingsError {
            StockSectionMessage(message: error.message, actionTitle: "Try again") { presenter.refresh() }
        } else {
            ProgressView()
        }
    }
}
