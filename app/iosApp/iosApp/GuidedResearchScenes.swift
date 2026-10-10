import Observation
import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

/// Saved research progress (device for guests, synced for accounts) shared by Home, Learn and Company Details.
@MainActor @Observable
final class LearningModel {
    private(set) var progress: LearningProgressRepository.State?
    @ObservationIgnored let client: IosLearningClient
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    /// `start: false` defers observing progress to `activate()` (see ScreenerModel).
    init(accounts: AccountViewModel, baseURL: @escaping () -> String = { BackendSettings.currentURL }, start: Bool = true) {
        client = IosLearningClient(baseUrl: baseURL, account: accounts.client)
        if start { activate() }
    }
    func activate() {
        guard subscription == nil else { return }
        subscription = client.observeProgress { [weak self] in self?.progress = $0 }
    }
    deinit {
        subscription?.cancel()
        client.close()
    }

    /// Steps completed for a company; nil when it was never started (no invented progress).
    func completed(_ symbol: String) -> Int? {
        guard let progress, let journey = client.journey(state: progress, symbol: symbol) else { return nil }
        return Int(journey.completedCount)
    }
    var latestInProgress: ResearchProgress? { progress?.inProgress.first }
}

/// One company's guide (shared presenter: same steps, quizzes and progress rules as Android).
@MainActor @Observable
final class ResearchModel {
    private(set) var state: GuidedResearchState?
    @ObservationIgnored let presenter: GuidedResearchPresenter
    @ObservationIgnored private var subscription: (any AccountSubscription)?

    init(symbol: String, name: String?, client: IosLearningClient) {
        presenter = client.research(symbol: symbol, name: name, step: 0)
        subscription = client.observeResearch(presenter: presenter) { [weak self] in self?.state = $0 }
    }
    deinit {
        subscription?.cancel()
        presenter.close()
    }
}

/// Identifies a research destination pushed by symbol.
struct ResearchTarget: Hashable, Identifiable {
    let symbol: String
    let name: String?
    var id: String { symbol }
}

struct IdentifiedEntry: Identifiable {
    let entry: EducationEntry
    var id: String { entry.id }
}

// MARK: - Scene

/// Owns the research model and wires navigation; GuidedResearchScreen only renders.
struct GuidedResearchScene: View {
    let target: ResearchTarget
    let learning: LearningModel
    let accounts: AccountViewModel
    var onCompany: (String) -> Void = { _ in }
    var onResearchAnother: () -> Void = {}
    var onContinueLearning: () -> Void = {}
    var onUpgrade: () -> Void = {}
    @State private var model: ResearchModel

    init(target: ResearchTarget, learning: LearningModel, accounts: AccountViewModel, onCompany: @escaping (String) -> Void = { _ in },
         onResearchAnother: @escaping () -> Void = {}, onContinueLearning: @escaping () -> Void = {}, onUpgrade: @escaping () -> Void = {}) {
        self.target = target
        self.learning = learning
        self.accounts = accounts
        self.onCompany = onCompany
        self.onResearchAnother = onResearchAnother
        self.onContinueLearning = onContinueLearning
        self.onUpgrade = onUpgrade
        _model = State(initialValue: ResearchModel(symbol: target.symbol, name: target.name, client: learning.client))
    }

    var body: some View {
        let watched = accounts.state.items.contains { $0.symbol == target.symbol.uppercased() }
        GuidedResearchScreen(state: model.state, client: learning.client, presenter: model.presenter,
                             watched: watched, watchlistEnabled: !accounts.state.initializing,
                             onToggleWatchlist: {
                                 let name = model.state?.name ?? target.symbol
                                 Task { await accounts.toggle(stock: StockSearchResult(symbol: target.symbol.uppercased(), name: name, currency: nil, exchange: nil, exchangeFullName: nil)) }
                             },
                             onCompany: { onCompany(target.symbol.uppercased()) },
                             onResearchAnother: onResearchAnother, onContinueLearning: onContinueLearning,
                             onUpgrade: { model.presenter.dismissUpgrade(); onUpgrade() })
            .navigationTitle("Research")
            .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Screen

struct GuidedResearchScreen: View {
    let state: GuidedResearchState?
    let client: IosLearningClient
    let presenter: GuidedResearchPresenter
    let watched: Bool
    let watchlistEnabled: Bool
    let onToggleWatchlist: () -> Void
    let onCompany: () -> Void
    let onResearchAnother: () -> Void
    let onContinueLearning: () -> Void
    let onUpgrade: () -> Void
    @Environment(\.colorScheme) private var scheme
    @State private var education: EducationEntry?

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: CGFloat(space.md)) {
                    Color.clear.frame(height: 0).id("top")
                    if let state {
                        if let snapshot = state.snapshot {
                            switch state.screen {
                            case 0: overview(state, snapshot, colors)
                            case 6: summary(state, snapshot, colors)
                            default: if let step = state.step { stepContent(state, step, colors) }
                            }
                        } else if state.loading {
                            Text(state.name).font(StockStepsTheme.font(type.screenTitle, relativeTo: .title1))
                            ProgressView().accessibilityLabel("Loading company information")
                        } else {
                            StockSectionMessage(message: state.error ?? "This company's information couldn't be loaded.", actionTitle: "Try again", action: { presenter.retry() })
                        }
                    }
                }
                .frame(maxWidth: CGFloat(dims.contentMaxWidth))
                .padding(.horizontal, CGFloat(space.screen))
                .padding(.vertical, CGFloat(space.md))
                .frame(maxWidth: .infinity)
            }
            .onChange(of: state?.screen) { _, _ in proxy.scrollTo("top", anchor: .top) }
        }
        .background(colors.appBackground.ignoresSafeArea())
        .sheet(item: Binding(get: { education.map { IdentifiedEntry(entry: $0) } }, set: { education = $0?.entry })) { item in
            BeginnerExplanationSheet(entry: item.entry)
        }
        .alert("StockSteps+", isPresented: Binding(get: { state?.upgradeRequired == true }, set: { if !$0 { presenter.dismissUpgrade() } })) {
            Button("See StockSteps+", action: onUpgrade)
            Button("Not now", role: .cancel) { presenter.dismissUpgrade() }
        } message: { Text("Ask StockSteps AI is part of StockSteps+. Every lesson, step and quiz in this guide stays free.") }
        .alert(state?.message ?? "", isPresented: Binding(get: { state?.message != nil }, set: { if !$0 { presenter.dismissMessage() } })) {
            Button("OK") { presenter.dismissMessage() }
        }
    }

    // MARK: Overview

    @ViewBuilder
    private func overview(_ state: GuidedResearchState, _ snapshot: ResearchSnapshot, _ colors: StockColors) -> some View {
        companyHeader(snapshot, colors)
        Text("Understand This Stock").font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title2)).accessibilityAddTraits(.isHeader)
        Text("Learn how to research this company in five simple steps. Each step explains one question using the company's reported numbers.")
            .font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
        progressBlock(Int(state.completedCount), colors)
        ForEach(Array(snapshot.notes.dropLast()), id: \.self) { note($0, colors) }
        if client.isFund(snapshot: snapshot) { note("This is a fund (ETF). The guide still works, but several steps explain why company figures don't apply to funds.", colors) }
        if client.isUnsupported(snapshot: snapshot) { note("Reported financial statements aren't available for this company, so most steps can only explain the concepts.", colors) }
        if state.syncFailed { note("Your progress is saved on this device. It will sync with your account when the connection is back.", colors) }
        ForEach(snapshot.steps, id: \.step.number) { step in
            let number = step.step.number
            let done = client.completed(state: state, step: number)
            let current = !done && state.progress?.currentStep == number
            Button { presenter.open(screen: number) } label: {
                HStack(spacing: CGFloat(space.sm)) {
                    stepNumber(Int(number), done: done, colors)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(step.question).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary)
                        Text(done ? "Completed" : current ? "In progress" : step.description_)
                            .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(done ? colors.positiveText : colors.textSecondary).lineLimit(2)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right").foregroundStyle(colors.iconSecondary).accessibilityHidden(true)
                }
                .frame(minHeight: CGFloat(dims.touchTarget))
                .stockCard()
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(client.stepAccessibilityLabel(number: Int32(number), question: step.question, status: done ? "Completed" : current ? "In progress" : "Not started"))
            .accessibilityAddTraits(.isButton)
        }
        Button(state.primaryLabel) { presenter.startOrContinue() }.buttonStyle(.stockPrimary).controlSize(.large).frame(maxWidth: .infinity)
        if state.started { Button("Start over") { presenter.restart() }.frame(maxWidth: .infinity, minHeight: CGFloat(dims.touchTarget)) }
        if let last = snapshot.notes.last { Text(last).font(.caption).foregroundStyle(colors.textSecondary) }
    }

    private func companyHeader(_ snapshot: ResearchSnapshot, _ colors: StockColors) -> some View {
        HStack(spacing: CGFloat(space.sm)) {
            StockTickerAvatar(symbol: snapshot.symbol, logoUrl: snapshot.logoUrl)
            VStack(alignment: .leading) {
                Text(snapshot.name).font(StockStepsTheme.font(type.screenTitle, relativeTo: .title1)).accessibilityAddTraits(.isHeader)
                Text([snapshot.symbol, snapshot.sector].compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(colors.textSecondary)
            }
        }
    }

    private func progressBlock(_ completed: Int, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("\(completed) of \(client.stepCount) steps completed").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textPrimary)
            ProgressView(value: Double(completed), total: Double(client.stepCount)).tint(colors.primary).accessibilityHidden(true)
        }
        .accessibilityElement(children: .combine)
    }

    private func stepNumber(_ number: Int, done: Bool, _ colors: StockColors) -> some View {
        Text(done ? "✓" : "\(number)")
            .font(StockStepsTheme.font(type.label))
            .foregroundStyle(done ? colors.positiveText : colors.primaryText)
            .frame(width: 36, height: 36)
            .background(done ? colors.positiveContainer : colors.primaryContainer, in: Circle())
            .accessibilityHidden(true)
    }

    private func note(_ text: String, _ colors: StockColors) -> some View {
        Text(text).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textPrimary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(CGFloat(space.sm))
            .background(colors.warningContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
    }

    // MARK: Step

    @ViewBuilder
    private func stepContent(_ state: GuidedResearchState, _ step: StepView, _ colors: StockColors) -> some View {
        let number = Int(step.step.number)
        VStack(alignment: .leading, spacing: 4) {
            Text("Step \(number) of \(client.stepCount)").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.primaryText)
            ProgressView(value: Double(number), total: Double(client.stepCount)).tint(colors.primary).accessibilityHidden(true)
        }
        Text(step.question).font(StockStepsTheme.font(type.screenTitle, relativeTo: .title1)).accessibilityAddTraits(.isHeader)
        ForEach(step.intro, id: \.self) { Text($0).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody) }
        if !step.figures.isEmpty || step.chart != nil {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                Text("The numbers").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).accessibilityAddTraits(.isHeader)
                ForEach(step.figures, id: \.label) { figure in
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(figure.label).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
                            if let detail = figure.detail { Text(detail).font(.caption).foregroundStyle(colors.textTertiary) }
                        }
                        Spacer()
                        Text(figure.value).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary)
                    }
                    .accessibilityElement(children: .combine)
                }
                if let chart = step.chart { bars(chart, colors) }
                if let period = step.period { Text(period).font(.caption).foregroundStyle(colors.textSecondary) }
            }
            .stockCard()
        }
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text("What this means").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).accessibilityAddTraits(.isHeader)
            Text(step.meaning ?? step.unavailableReason ?? "This information isn't available for this company.").font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
            if step.meaning != nil, let reason = step.unavailableReason { Text(reason).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary) }
        }
        .padding(CGFloat(space.cardPadding))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.educationContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
        VStack(alignment: .leading, spacing: 4) {
            Text("Keep in mind").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSecondary).accessibilityAddTraits(.isHeader)
            Text(step.limitation).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
        }
        VStack(alignment: .leading, spacing: 4) {
            Text("Key takeaway").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.primaryText).accessibilityAddTraits(.isHeader)
            Text(step.takeaway).font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(colors.textPrimary)
        }
        .padding(CGFloat(space.sm))
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)).stroke(colors.primary, lineWidth: CGFloat(dims.border)))
        let related = step.learnMore.compactMap { client.term(id: $0) }
        if !related.isEmpty {
            Text("Learn the terms").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSecondary).accessibilityAddTraits(.isHeader)
            ForEach(related, id: \.id) { entry in
                Button { education = entry } label: {
                    HStack {
                        Text(entry.title).foregroundStyle(colors.primaryText)
                        Spacer()
                        Image(systemName: "info.circle").foregroundStyle(colors.primary).accessibilityHidden(true)
                    }
                    .frame(minHeight: CGFloat(dims.touchTarget))
                }
                .accessibilityHint("Opens an explanation")
            }
        }
        quizCard(state, step.quiz, colors)
        AskResearchCard(state: state, presenter: presenter)
        HStack(spacing: CGFloat(space.sm)) {
            Button(number == 1 ? "Overview" : "Back") { presenter.back() }.buttonStyle(.bordered).controlSize(.large).frame(maxWidth: .infinity)
            Button(number == Int(client.stepCount) ? "Finish" : "Continue") { presenter.next() }.buttonStyle(.stockPrimary).controlSize(.large).frame(maxWidth: .infinity)
        }
    }

    /// Horizontal bars with the exact value written beside each one (never color or length alone).
    private func bars(_ chart: SimpleChart, _ colors: StockColors) -> some View {
        let maxValue = max(chart.bars.map { $0.value }.max() ?? 1, 1e-9)
        return VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text(chart.title).font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSecondary)
            ForEach(Array(chart.bars.enumerated()), id: \.offset) { index, bar in
                HStack(spacing: CGFloat(space.xs)) {
                    Text(bar.label).font(.caption).foregroundStyle(colors.textSecondary).frame(minWidth: 72, alignment: .leading)
                    GeometryReader { geo in
                        RoundedRectangle(cornerRadius: 4)
                            .fill(index == chart.bars.count - 1 ? colors.primary : colors.primary.opacity(0.45))
                            .frame(width: max(geo.size.width * CGFloat(bar.value / maxValue), 4))
                    }
                    .frame(height: 14)
                    Text(bar.display).font(.caption).foregroundStyle(colors.textPrimary)
                }
            }
            if let note = chart.note { Text(note).font(.caption).foregroundStyle(colors.textSecondary) }
        }
        .padding(.top, CGFloat(space.xs))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(chart.description_)
    }

    private func quizCard(_ state: GuidedResearchState, _ quiz: Quiz, _ colors: StockColors) -> some View {
        let result = client.answer(state: state, quiz: quiz)
        let saved = client.quizRecord(state: state, quiz: quiz)
        return VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text("Quick check (optional)").font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSecondary).accessibilityAddTraits(.isHeader)
            Text(quiz.question).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary)
            ForEach(quiz.options, id: \.id) { option in
                let chosen = result?.selectedOptionId == option.id
                let correct = result != nil && option.id == quiz.correctOptionId
                Button { presenter.answer(optionId: option.id) } label: {
                    HStack {
                        Text(option.text).foregroundStyle(colors.textPrimary).multilineTextAlignment(.leading)
                        Spacer()
                        if correct { Text("Correct").font(.caption).foregroundStyle(colors.positiveText) }
                        else if chosen { Text("Your answer").font(.caption).foregroundStyle(colors.negativeText) }
                    }
                    .frame(minHeight: CGFloat(dims.touchTarget))
                    .padding(.horizontal, CGFloat(space.sm))
                    .background(correct ? colors.positiveContainer : chosen ? colors.negativeContainer : colors.surface,
                                in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                    .overlay(RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card))
                        .stroke(correct ? colors.positive : chosen ? colors.negativeBorder : colors.border, lineWidth: CGFloat(dims.border)))
                }
                .buttonStyle(.plain)
                .disabled(result != nil)
                .accessibilityValue(correct ? "Correct answer" : chosen ? "Your answer, not correct" : "")
            }
            if let result {
                Text(result.feedback).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(result.correct ? colors.positiveText : colors.negativeText)
                Text(result.explanation).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                Button("Try again") { presenter.retryQuiz() }.frame(minHeight: CGFloat(dims.touchTarget))
            } else if let saved {
                Text(saved.correct ? "You answered this correctly before." : "You tried this before. Give it another go.").font(.caption).foregroundStyle(colors.textSecondary)
            }
        }
        .stockCard()
    }

    // MARK: Summary

    @ViewBuilder
    private func summary(_ state: GuidedResearchState, _ snapshot: ResearchSnapshot, _ colors: StockColors) -> some View {
        companyHeader(snapshot, colors)
        Text(state.progress?.finished == true ? "You've researched \(client.shortName(name: snapshot.name))" : "Your research so far")
            .font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title2)).accessibilityAddTraits(.isHeader)
        progressBlock(Int(state.completedCount), colors)
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text("What you learned").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).accessibilityAddTraits(.isHeader)
            ForEach(snapshot.steps, id: \.step.number) { step in
                VStack(alignment: .leading, spacing: 2) {
                    Text(step.question).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
                    Text(step.summary).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textPrimary)
                }
                .accessibilityElement(children: .combine)
            }
        }
        .stockCard()
        Text("This summary explains reported figures. It isn't a recommendation to buy, sell or hold, and it doesn't predict the share price.")
            .font(.caption).foregroundStyle(colors.textSecondary)
        Group {
            Button("Review steps") { presenter.open(screen: 1) }.buttonStyle(.bordered)
            if watchlistEnabled { Button(watched ? "Saved to watchlist" : "Add to Watchlist", action: onToggleWatchlist).buttonStyle(.bordered) }
            Button("Company details", action: onCompany).buttonStyle(.bordered)
            Button("Research another company", action: onResearchAnother).buttonStyle(.bordered)
            Button("Continue learning", action: onContinueLearning).buttonStyle(.stockPrimary)
        }
        .controlSize(.large)
        .frame(maxWidth: .infinity)
    }
}

/// Plus-only questions; free and signed-out users see the upgrade or sign-in message instead.
private struct AskResearchCard: View {
    let state: GuidedResearchState
    let presenter: GuidedResearchPresenter
    @State private var open = false
    @State private var question = ""
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        if !open && state.answer == nil {
            Button(state.plus ? "Ask StockSteps AI" : "Ask StockSteps AI · StockSteps+") { if presenter.requestAi() { open = true } }
                .frame(maxWidth: .infinity, minHeight: CGFloat(dims.touchTarget))
        } else {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                Text("Ask StockSteps AI").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).accessibilityAddTraits(.isHeader)
                Text("Answers use this company's public figures only. They don't predict prices or recommend buying or selling.").font(.caption).foregroundStyle(colors.textSecondary)
                TextField("e.g. What does this mean for a beginner?", text: $question, axis: .vertical).textFieldStyle(.roundedBorder)
                    .onChange(of: question) { _, value in if value.count > 300 { question = String(value.prefix(300)) } }
                HStack {
                    Button("Ask") { presenter.ask(question: question) }.buttonStyle(.stockPrimary)
                        .disabled(question.trimmingCharacters(in: .whitespaces).count < 3 || state.asking)
                    Button("Close") { open = false }
                }
                if state.asking { ProgressView().accessibilityLabel("Asking StockSteps AI") }
                if let answer = state.answer {
                    Text(answer.answer).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
                    if !answer.sources.isEmpty { Text("Sources: " + answer.sources.joined(separator: ", ")).font(.caption).foregroundStyle(colors.textSecondary) }
                    if let remaining = answer.remainingToday { Text("\(remaining.intValue) questions left today").font(.caption).foregroundStyle(colors.textSecondary) }
                }
            }
            .stockCard()
        }
    }
}

// MARK: - Shared education UI

/// The reusable explanation sheet: what it is, why it matters, how to read it, and its limits.
struct BeginnerExplanationSheet: View {
    let entry: EducationEntry
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: CGFloat(space.md)) {
                    section("What it is", entry.short_, colors)
                    section("Why it matters", entry.why, colors)
                    section("How to read it", entry.interpret, colors)
                    if let example = entry.example { section("Example", example, colors) }
                    if let detailed = entry.detailed { section("More detail", detailed, colors) }
                    section("Keep in mind", entry.limitations, colors)
                }
                .padding(CGFloat(space.screen))
            }
            .navigationTitle(entry.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Got it") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }

    private func section(_ title: String, _ body: String, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSecondary).accessibilityAddTraits(.isHeader)
            Text(body).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
        }
    }
}

/// Company Details entry: "Understand This Stock" with real saved progress only.
struct UnderstandStockCard: View {
    let completed: Int?
    let action: () -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text("Understand This Stock").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).accessibilityAddTraits(.isHeader)
            Text("Learn how to research this company in five simple steps.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
            if let completed { Text("\(completed) of 5 steps completed").font(.caption).foregroundStyle(colors.primaryText) }
            Button(completed == nil ? "Start learning" : completed! >= 5 ? "Review research" : "Continue learning", action: action)
                .buttonStyle(.stockPrimary).controlSize(.large).frame(maxWidth: .infinity)
        }
        .padding(CGFloat(space.cardPadding))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.educationContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
    }
}

// MARK: - Learn hub

/// Learn hub: start, continue and review company research, plus the financial terms glossary. All free.
struct LearnScreen: View {
    let learning: LearningModel
    let onResearch: (ResearchTarget) -> Void
    let onSearch: () -> Void
    var onPractice: () -> Void = {}
    @Environment(\.colorScheme) private var scheme
    @State private var education: EducationEntry?

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let client = learning.client
        let progress = learning.progress
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                Text("Research a company one simple question at a time. Every lesson is free.").font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
                if progress?.syncFailed == true {
                    Text("Your progress is saved on this device and will sync with your account when the connection is back.").font(.caption).foregroundStyle(colors.textSecondary)
                }
                if let inProgress = progress?.inProgress, !inProgress.isEmpty {
                    title("Continue learning")
                    ForEach(inProgress, id: \.symbol) { journey in journeyCard(journey, action: "Continue", colors) }
                }
                title("Start learning")
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                    Text("Understand a stock in five steps").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline))
                    Text("What it does, whether it's growing, whether it makes money, how much debt it has, and whether the stock looks expensive.")
                        .font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                    ForEach(Array(zip(client.quickPickSymbols, client.quickPickNames)), id: \.0) { symbol, name in
                        if learning.completed(symbol) == nil {
                            Button { onResearch(ResearchTarget(symbol: symbol, name: name)) } label: {
                                HStack(spacing: CGFloat(space.sm)) {
                                    StockTickerAvatar(symbol: symbol, size: CGFloat(dims.logoCompact))
                                    Text("Research \(name)").foregroundStyle(colors.primaryText)
                                    Spacer()
                                    Text(symbol).font(.caption).foregroundStyle(colors.textSecondary)
                                }
                                .frame(minHeight: CGFloat(dims.touchTarget))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .stockCard()
                title("Research a company")
                Text("Search for any company, then choose \"Understand This Stock\" on its page.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                Button("Search companies", action: onSearch).buttonStyle(.bordered).controlSize(.large).frame(maxWidth: .infinity)
                if let completed = progress?.completed, !completed.isEmpty {
                    title("Completed research")
                    ForEach(completed, id: \.symbol) { journey in journeyCard(journey, action: "Review", colors) }
                }
                title("Practice what you learn")
                PracticeEntryCard(message: "Try simulated investments with $10,000 of virtual money and guided challenges.", action: onPractice)
                title("Financial terms")
                ForEach(client.terms, id: \.id) { entry in
                    Button { education = entry } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(entry.title).foregroundStyle(colors.textPrimary)
                                Text(entry.topic).font(.caption).foregroundStyle(colors.textSecondary)
                            }
                            Spacer()
                            Image(systemName: "info.circle").foregroundStyle(colors.primary).accessibilityHidden(true)
                        }
                        .frame(minHeight: CGFloat(dims.touchTarget))
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint("Opens an explanation")
                }
            }
            .frame(maxWidth: CGFloat(dims.contentMaxWidth))
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
            .frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .sheet(item: Binding(get: { education.map { IdentifiedEntry(entry: $0) } }, set: { education = $0?.entry })) { item in
            BeginnerExplanationSheet(entry: item.entry)
        }
    }

    private func title(_ text: String) -> some View {
        Text(text).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title2)).padding(.top, CGFloat(space.md)).accessibilityAddTraits(.isHeader)
    }

    private func journeyCard(_ journey: ResearchProgress, action: String, _ colors: StockColors) -> some View {
        let label = "\(journey.completedCount) of 5 steps completed"
        return Button { onResearch(ResearchTarget(symbol: journey.symbol, name: journey.name)) } label: {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                HStack(spacing: CGFloat(space.sm)) {
                    StockTickerAvatar(symbol: journey.symbol, size: CGFloat(dims.logoCompact))
                    VStack(alignment: .leading) {
                        Text(journey.name).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary)
                        Text(label).font(.caption).foregroundStyle(colors.textSecondary)
                    }
                    Spacer()
                    Text(action).font(StockStepsTheme.font(type.label)).foregroundStyle(colors.primaryText)
                }
                ProgressView(value: Double(journey.completedCount), total: 5).tint(colors.primary)
            }
            .stockCard()
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(journey.name), \(label)")
        .accessibilityHint("\(action) research")
    }
}
