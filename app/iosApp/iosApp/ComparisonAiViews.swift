import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography

/// Compare-screen entry point for Company Comparison Phase 5 (one card; it says it's StockSteps+).
struct ComparisonAiEntryCard: View {
    let onOpen: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            HStack {
                Text("Ask StockSteps AI").font(.headline).accessibilityAddTraits(.isHeader)
                Spacer()
                Text("StockSteps+").font(.caption2.weight(.semibold)).padding(.horizontal, 8).padding(.vertical, 2).background(Capsule().fill(Color.secondary.opacity(0.15)))
            }
            Text("Understand the financial differences between these companies. Answers use only the verified figures on this screen, with every number linked to its data.")
                .font(.subheadline)
            Button("Ask StockSteps AI", action: onOpen).buttonStyle(.bordered).frame(maxWidth: .infinity, minHeight: 48)
        }
        .stockCard()
    }
}

/// Company Comparison Phase 5: AI Comparison Assistant (mirrors Android's `ComparisonAiScreen`). Focused on the
/// selected companies: suggested questions, a bounded conversation, cited evidence that opens the source metric,
/// data freshness and limits, and the remaining allowance. The server decides StockSteps+ and checks every answer.
struct ComparisonAiView: View {
    let model: ScreenerModel
    var researchQuestionId: String? = nil
    var researchSessionId: String? = nil
    var researchHasNote: Bool = false
    let onUpgrade: () -> Void
    let onSignIn: () -> Void
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dismiss) private var dismiss
    @State private var startedResearch = false
    @State private var copied: String?
    @FocusState private var inputFocused: Bool

    private var client: IosScreenerClient { model.client }
    private var presenter: ComparisonAiPresenter? { model.client.ai }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let state = model.ai
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                    header(state, colors)
                    if let state { content(state, colors) }
                    Color.clear.frame(height: 1).id("bottom")
                }
                .padding(.horizontal, CGFloat(space.screen))
                .padding(.vertical, CGFloat(space.md))
            }
            .onChange(of: state?.turns.count) { _, _ in withAnimation { proxy.scrollTo("bottom") } }
        }
        .background(colors.appBackground)
        .navigationTitle("Ask AI")
        .navigationBarTitleDisplayMode(.inline)
        .task { presenter?.refreshUsage() }
        .onChange(of: state?.usage != nil) { _, loaded in
            // Opened from a research question: ask once (a saved note is only included after the user agrees).
            if loaded, !startedResearch, let id = researchQuestionId {
                startedResearch = true
                presenter?.research(questionId: id, sessionId: researchSessionId, hasNote: researchHasNote)
            }
        }
        .confirmationDialog("Include your note?", isPresented: Binding(get: { model.ai?.pendingResearch != nil }, set: { if !$0, model.ai?.pendingResearch != nil { presenter?.cancelResearch() } }), titleVisibility: .visible) {
            Button("Include my note") { presenter?.confirmResearch(includeNote: true) }
            Button("Ask without it") { presenter?.confirmResearch(includeNote: false) }
            Button("Cancel", role: .cancel) { presenter?.cancelResearch() }
        } message: {
            Text("“\(model.ai?.pendingResearch?.questionText ?? "")”\n\nIf you include it, your saved note for this question (only this one) is sent to StockSteps' AI service to tailor the answer. It isn't shared with other users, isn't changed, and isn't saved with the answer.")
        }
        .alert(state?.upsell?.title ?? "", isPresented: Binding(get: { model.ai?.upsell != nil }, set: { if !$0 { presenter?.dismissUpsell() } })) {
            let signIn = state?.upsell?.signIn == true
            Button(signIn ? "Sign In" : "See StockSteps+") { presenter?.dismissUpsell(); if signIn { onSignIn() } else { onUpgrade() } }
            Button("Not now", role: .cancel) { presenter?.dismissUpsell() }
        } message: {
            if let upsell = state?.upsell { Text(([upsell.body] + upsell.benefits.map { "• \($0)" } + [upsell.footnote]).joined(separator: "\n")) }
        }
        .alert("", isPresented: Binding(get: { model.ai?.message != nil || copied != nil }, set: { if !$0 { presenter?.dismissMessage(); copied = nil } })) {
            Button("OK") { presenter?.dismissMessage(); copied = nil }
        } message: { Text(copied ?? model.ai?.message ?? "") }
    }

    private func header(_ state: ComparisonAiUiState?, _ colors: StockColors) -> some View {
        let names = Dictionary((model.comparison?.columns ?? []).map { ($0.symbol, $0.name) }, uniquingKeysWith: { a, _ in a })
        let symbols = state?.symbols ?? []
        return VStack(alignment: .leading, spacing: 4) {
            Text("Ask StockSteps AI").font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).accessibilityAddTraits(.isHeader)
            Text("Understand the financial differences between these companies.").font(.subheadline).foregroundStyle(colors.textSecondary)
            Text(symbols.map { s in names[s].map { "\($0) (\(s))" } ?? s }.joined(separator: " · "))
                .font(.subheadline.weight(.medium))
                .accessibilityLabel("Comparing \(symbols.joined(separator: ", "))")
        }
    }

    @ViewBuilder private func content(_ state: ComparisonAiUiState, _ colors: StockColors) -> some View {
        if let note = state.resetNote {
            VStack(alignment: .leading, spacing: 4) {
                Text(note).font(.subheadline)
                Button("OK") { presenter?.dismissResetNote() }.frame(minHeight: 44)
            }
            .stockCard()
        }
        if !state.signedIn || (state.usage != nil && !state.plus) {
            gate(signIn: !state.signedIn)
        } else if state.symbols.count < 2 {
            Text("Choose at least two companies on Compare first.").font(.subheadline).stockCard()
        } else {
            usage(state, colors)
            if state.turns.isEmpty {
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                    Text("Start with an overview").font(.headline).accessibilityAddTraits(.isHeader)
                    Text("A short, beginner-friendly explanation of the biggest financial differences, with the data behind each point.").font(.subheadline)
                    Button("Explain the biggest differences") { _ = client.aiSummary(type: "OVERVIEW") }
                        .buttonStyle(.borderedProminent).frame(maxWidth: .infinity, minHeight: 48).disabled(!state.canAsk)
                }
                .stockCard()
            }
            ForEach(state.turns, id: \.id) { turn in turnView(turn, colors) }
            suggestions(state, colors)
            input(state, colors)
            Text("Education, not investment advice. StockSteps AI explains the data; it doesn't recommend stocks or predict prices, and it can make mistakes, so check the linked figures.")
                .font(.caption2).foregroundStyle(colors.textTertiary)
        }
    }

    private func gate(signIn: Bool) -> some View {
        let upsell = AiUpsell(signIn: signIn)
        return VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text(upsell.title).font(.headline).accessibilityAddTraits(.isHeader)
            Text(upsell.body).font(.subheadline)
            ForEach(upsell.benefits, id: \.self) { Text("• \($0)").font(.subheadline) }
            Text(upsell.footnote).font(.caption).foregroundStyle(.secondary)
            Button(signIn ? "Sign In" : "See StockSteps+", action: signIn ? onSignIn : onUpgrade).buttonStyle(.borderedProminent).frame(maxWidth: .infinity, minHeight: 48)
        }
        .stockCard()
    }

    @ViewBuilder private func usage(_ state: ComparisonAiUiState, _ colors: StockColors) -> some View {
        if let usage = state.usage {
            VStack(alignment: .leading, spacing: 2) {
                Text(usage.label).font(.caption).foregroundStyle(usage.exhausted ? colors.cautionText : colors.textSecondary)
                if usage.exhausted {
                    Text("Your allowance is used up for now\(usage.dailyResetAt.map { " (daily requests reset \(String($0.prefix(10))) 00:00 UTC)" } ?? ""). The comparison and guided explanations stay available.")
                        .font(.caption).foregroundStyle(colors.cautionText)
                }
                if let note = usage.note { Text(note).font(.caption).foregroundStyle(colors.textSecondary) }
                if usage.sample { Text("Sample AI for development: deterministic templates, not an AI service.").font(.caption).foregroundStyle(colors.cautionText) }
            }
            .accessibilityElement(children: .combine)
        }
    }

    private func suggestions(_ state: ComparisonAiUiState, _ colors: StockColors) -> some View {
        let items = client.aiSuggestions(comparison: model.comparison).filter { !(state.turns.isEmpty && $0.type.name == "OVERVIEW") }
        return VStack(alignment: .leading, spacing: 4) {
            Text(state.turns.isEmpty ? "Or ask about" : "Ask a follow-up").font(.caption.weight(.semibold)).foregroundStyle(colors.textSecondary).accessibilityAddTraits(.isHeader)
            ForEach(items, id: \.text) { s in
                Button { presenter?.suggest(suggestion: s) } label: {
                    Text(s.text).font(.subheadline).frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                }
                .buttonStyle(.bordered)
                .accessibilityLabel("Ask: \(s.text)")
                .disabled(state.busy)
            }
        }
    }

    private func input(_ state: ComparisonAiUiState, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            TextField("For example: how do their margins compare?", text: Binding(get: { model.ai?.input ?? "" }, set: { presenter?.editInput(text: $0) }), axis: .vertical)
                .lineLimit(2...4)
                .textFieldStyle(.roundedBorder)
                .focused($inputFocused)
                .accessibilityLabel("Your question")
            Text("\(state.remaining) characters left · about these companies only").font(.caption2).foregroundStyle(colors.textTertiary)
            HStack {
                Button(state.busy ? "Thinking…" : "Ask") { inputFocused = false; presenter?.ask(question: state.input) }
                    .buttonStyle(.borderedProminent).disabled(!(state.canAsk && state.inputValid))
                if !state.turns.isEmpty { Button("New conversation") { presenter?.startOver() }.disabled(state.busy) }
            }
            .frame(minHeight: 48)
        }
    }

    @ViewBuilder private func turnView(_ turn: AiTurn, _ colors: StockColors) -> some View {
        HStack {
            Spacer(minLength: 40)
            Text(turn.title).font(.subheadline).padding(.horizontal, 12).padding(.vertical, 8)
                .background(RoundedRectangle(cornerRadius: 12).fill(colors.primary.opacity(0.15)))
                .accessibilityLabel("You asked: \(turn.title)")
        }
        if turn.loading {
            ProgressView().frame(maxWidth: .infinity).accessibilityLabel("StockSteps AI is preparing an answer").stockCard()
        } else if let error = turn.error {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                Text(error).font(.subheadline)
                HStack {
                    if turn.retryable { Button("Try again") { presenter?.retry(turnId: turn.id) }.buttonStyle(.bordered) }
                    if turn.errorCode == "PLUS_REQUIRED" { Button("Learn about StockSteps+") { presenter?.showUpsell() } }
                    Button("Dismiss") { presenter?.remove(turnId: turn.id) }
                }
                .frame(minHeight: 48)
            }
            .stockCard()
        } else if let r = turn.response {
            answer(turn, r, colors)
        }
    }

    private func answer(_ turn: AiTurn, _ r: ComparisonAiResponse, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            HStack {
                Text(r.scope.name == "DECLINED" ? "StockSteps" : r.scope.name == "FALLBACK" ? "StockSteps (AI answer unavailable)" : "StockSteps AI")
                    .font(.caption.weight(.semibold)).foregroundStyle(colors.textSecondary).accessibilityAddTraits(.isHeader)
                Spacer()
                if r.sample { tag("Sample") }
                if r.cached { tag("Saved answer") }
            }
            Text(r.summary).font(.body)
            ForEach(Array(r.observations.enumerated()), id: \.offset) { _, o in
                VStack(alignment: .leading, spacing: 4) {
                    Text(o.category).font(.caption2).foregroundStyle(colors.textTertiary)
                    Text(o.text).font(.subheadline.weight(.semibold))
                    if let why = o.whyItMatters { Text("Why it matters: \(why)").font(.subheadline) }
                    ForEach(client.aiEvidence(response: r, ids: o.evidenceIds), id: \.id) { e in
                        Button {
                            client.openEvidence(evidence: e)
                            dismiss()
                        } label: {
                            Text("\(e.chip): \(e.value)").font(.caption).lineLimit(1).padding(.horizontal, 10).padding(.vertical, 4)
                                .background(Capsule().fill(colors.surfaceSecondary))
                        }
                        .frame(minHeight: 44, alignment: .leading)
                        .accessibilityLabel("Source. \(e.accessibility). Opens the data on Compare.")
                    }
                }
            }
            if !r.caveats.isEmpty {
                Text("Important caveats").font(.subheadline.weight(.semibold)).accessibilityAddTraits(.isHeader)
                ForEach(r.caveats, id: \.self) { Text("• \($0)").font(.subheadline) }
            }
            if !r.missingData.isEmpty {
                Text("Not in the data").font(.subheadline.weight(.semibold)).accessibilityAddTraits(.isHeader)
                ForEach(Array(r.missingData.prefix(4)), id: \.self) { Text("• \($0)").font(.caption).foregroundStyle(colors.textSecondary) }
            }
            if !r.researchQuestions.isEmpty {
                Text("Questions to research").font(.subheadline.weight(.semibold)).accessibilityAddTraits(.isHeader)
                ForEach(r.researchQuestions, id: \.self) { q in
                    Button("• \(q)") { presenter?.ask(question: q) }.font(.subheadline).frame(minHeight: 44, alignment: .leading).accessibilityHint("Ask this")
                }
            }
            if let note = r.note { Text(note).font(.caption).foregroundStyle(colors.textSecondary) }
            Text([r.dataAsOf.map { "Data as of \(String($0.prefix(10)))" }, "answered \(String(r.generatedAt.prefix(10))) \(String(r.generatedAt.dropFirst(11).prefix(5))) UTC", r.usedNote ? "used your note" : nil]
                .compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundStyle(colors.textTertiary)
            if let warning = r.warnings.first { Text(warning).font(.caption).foregroundStyle(colors.textSecondary).lineLimit(3) }
            if client.canAddAiAnswerToNote(turn: turn) {
                Button("Copy to my note draft") {
                    copied = client.addAiAnswerToNote(turn: turn)
                        ? "Added to your note draft for this question. Open the research checklist to review it and tap Save note to keep it."
                        : "Open the matching research session first, then try again."
                }
                .frame(minHeight: 44)
            }
            Text(r.disclaimer).font(.caption2).foregroundStyle(colors.textTertiary)
        }
        .stockCard()
    }

    private func tag(_ text: String) -> some View {
        Text(text).font(.caption2.weight(.semibold)).padding(.horizontal, 8).padding(.vertical, 2).background(Capsule().fill(Color.secondary.opacity(0.15)))
    }
}
