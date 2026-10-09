import Shared
import SwiftUI
import UniformTypeIdentifiers

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography

/// Company Comparison Phase 4: Guided Research Checklist (mirrors Android's `ComparisonResearchScreen`).
/// Free: basic checklist, notes, progress, three saved sessions and the basic summary. StockSteps+
/// (server-verified): advanced questions, snapshots, the detailed summary and a PDF report. Free accounts
/// see one upgrade entry point. "What the data shows" comes from the comparison already on screen.
struct ComparisonResearchView: View {
    let model: ScreenerModel
    let onUpgrade: () -> Void
    let onSignIn: () -> Void
    @Environment(\.colorScheme) private var scheme
    @State private var deleting: ResearchSessionInfo?

    private var client: IosScreenerClient { model.client }
    private var presenter: ComparisonResearchPresenter? { model.client.research }

    var body: some View {
        dialogs(exporter(page))
    }

    private var page: some View {
        let colors = StockStepsTheme.colors(scheme)
        return ScrollView {
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Research checklist").font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).accessibilityAddTraits(.isHeader)
                    Text("Work through the questions at your own pace and write what you notice. Your notes are private to your account. Education, not investment advice.")
                        .font(.subheadline).foregroundStyle(colors.textSecondary)
                }
                if let state = model.research { content(state, colors) }
            }
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
        }
        .background(colors.appBackground)
        .navigationTitle("Research")
        .navigationBarTitleDisplayMode(.inline)
        .task { presenter?.reload() }
        .onChange(of: model.research?.open?.session.id) { _, _ in
            if let symbols = model.research?.open?.session.symbols { client.selectResearchCompanies(symbols: symbols) }
        }
    }

    private func dialogs<V: View>(_ view: V) -> some View {
        let upsell = model.research?.upsell
        return view
            .confirmationDialog("Delete “\(deleting?.title ?? "")”?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
                Button("Delete", role: .destructive) { if let id = deleting?.id { _ = presenter?.delete(id: id) }; deleting = nil }
            } message: { Text("Its checklist answers, notes and snapshots will be deleted. This can't be undone.") }
            .alert(upsell?.title ?? "", isPresented: Binding(get: { model.research?.upsell != nil }, set: { if !$0 { presenter?.dismissUpsell() } })) {
                Button("See StockSteps+") { presenter?.dismissUpsell(); onUpgrade() }
                Button("Not now", role: .cancel) { presenter?.dismissUpsell() }
            } message: {
                if let upsell { Text(([upsell.body] + upsell.benefits.map { "• \($0)" } + [upsell.footnote]).joined(separator: "\n")) }
            }
            .alert("", isPresented: Binding(get: { model.research?.message != nil }, set: { if !$0 { presenter?.dismissMessage() } })) {
                Button("OK") { presenter?.dismissMessage() }
            } message: { Text(model.research?.message ?? "") }
    }

    private func exporter<V: View>(_ view: V) -> some View {
        let export = model.research?.export_
        let data: Data = export.map { client.exportData(export: $0) } ?? Data()
        return view.fileExporter(isPresented: Binding(get: { model.research?.export_ != nil }, set: { if !$0, model.research?.export_ != nil { presenter?.exportHandled(message: "The report wasn't saved.") } }),
                                 document: PDFReportFile(data: data), contentType: .pdf, defaultFilename: export?.fileName ?? "StockSteps-research.pdf") { result in
            switch result {
            case .success: presenter?.exportHandled(message: "Report saved.")
            case .failure: presenter?.exportHandled(message: "The report couldn't be saved. Try again.")
            }
        }
    }

    @ViewBuilder private func content(_ state: ResearchUiState, _ colors: StockColors) -> some View {
        if !state.signedIn {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                Text("Sign in to save your research").font(.headline).accessibilityAddTraits(.isHeader)
                Text("Research sessions, notes and progress are saved to your account so you can resume later. The checklist is free.").font(.subheadline)
                Button("Sign In", action: onSignIn).buttonStyle(.borderedProminent).frame(minHeight: 48)
            }
            .stockCard()
        } else {
            if state.loading || state.opening || state.busy { ProgressView().accessibilityLabel("Loading research") }
            if let error = state.error { StockSectionMessage(message: error, actionTitle: "Try again") { presenter?.reload() } }
            if let open = state.open {
                sessionHeader(open, colors)
                if let summary = state.summary { summaryCard(summary, colors) }
                if state.summaryLoading { ProgressView().accessibilityLabel("Building summary") }
                ForEach(client.researchCategories(state: state, comparison: model.comparison), id: \.category.id) { category in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(category.category.title + (category.category.tier == .plus ? " · StockSteps+" : "")).font(.title3.weight(.semibold)).accessibilityAddTraits(.isHeader)
                        Text(category.category.description_).font(.caption).foregroundStyle(colors.textSecondary)
                    }
                    .padding(.top, 8)
                    ForEach(category.questions, id: \.question.id) { question in questionCard(question, colors) }
                }
                if !state.advancedPreview.isEmpty {
                    VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                        Text("Advanced research · StockSteps+").font(.headline).accessibilityAddTraits(.isHeader)
                        ForEach(state.advancedPreview, id: \.id) { Text("• \($0.title): \($0.description_)").font(.subheadline) }
                        Text("StockSteps+ also adds research snapshots, a detailed summary and a PDF report.").font(.caption).foregroundStyle(colors.textSecondary)
                        Button("Learn about StockSteps+") { presenter?.showUpsell() }.buttonStyle(.bordered).frame(minHeight: 48)
                    }
                    .stockCard()
                }
                if !state.snapshots.isEmpty {
                    VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                        Text("Snapshots").font(.headline).accessibilityAddTraits(.isHeader)
                        Text("Saved copies of your progress and notes. Values in a snapshot are as of the date shown, not live.").font(.caption).foregroundStyle(colors.textSecondary)
                        ForEach(state.snapshots, id: \.id) { snap in
                            HStack {
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(snap.label).font(.subheadline.weight(.semibold))
                                    Text("\(snap.progress.reviewed) of \(snap.progress.total) reviewed · values as of \(snap.dataAsOf.map { String($0.prefix(10)) } ?? "not reported")")
                                        .font(.caption).foregroundStyle(colors.textSecondary)
                                }
                                Spacer()
                                Button("Delete") { _ = presenter?.deleteSnapshot(snapshotId: snap.id) }.frame(minHeight: 48).accessibilityLabel("Delete snapshot \(snap.label)")
                            }
                        }
                    }
                    .stockCard()
                }
            } else if let sessions = state.sessions {
                sessionsCard(sessions, colors)
            }
        }
    }

    private func sessionsCard(_ sessions: ResearchSessionsResponse, _ colors: StockColors) -> some View {
        let selected = model.comparison?.selected.map { $0.symbol } ?? []
        return VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text("Your research (\(sessions.sessions.count) of \(sessions.limit)\(sessions.plus ? ", fair use" : ""))").font(.headline).accessibilityAddTraits(.isHeader)
            if sessions.sessions.isEmpty { Text("No saved research yet. Start one for the companies you're comparing.").font(.subheadline) }
            ForEach(sessions.sessions, id: \.id) { s in
                HStack {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(s.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                        Text(s.symbols.joined(separator: " · ") + " · \(s.reviewed) reviewed" + (s.snapshotCount > 0 ? " · \(s.snapshotCount) snapshots" : ""))
                            .font(.caption).foregroundStyle(colors.textSecondary)
                    }
                    Spacer()
                    Button("Open") { _ = presenter?.open(id: s.id) }.frame(minHeight: 48).accessibilityLabel("Open \(s.title)")
                    Button("Delete") { deleting = s }.frame(minHeight: 48).accessibilityLabel("Delete \(s.title)")
                }
            }
            if selected.count >= 2 {
                Button("Start research: \(selected.joined(separator: " vs "))") { client.createResearchForSelection() }
                    .buttonStyle(.borderedProminent).frame(minHeight: 48).disabled(!sessions.canCreate)
            } else {
                Text("Choose at least two companies on Compare to start new research.").font(.caption).foregroundStyle(colors.textSecondary)
            }
            if let message = sessions.message { Text(message).font(.caption).foregroundStyle(colors.textSecondary) }
            if !sessions.canCreate && !sessions.plus { Button("Learn about StockSteps+") { presenter?.showUpsell() }.frame(minHeight: 48) }
        }
        .stockCard()
    }

    private func sessionHeader(_ open: ResearchSessionResponse, _ colors: StockColors) -> some View {
        let progress = open.progress
        return VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Button("‹ All research") { presenter?.close() }.frame(minHeight: 44)
            Text(open.session.title).font(.headline).accessibilityAddTraits(.isHeader)
            Text(open.session.symbols.joined(separator: " · ")).font(.caption).foregroundStyle(colors.textSecondary)
            ProgressView(value: Double(progress.percent), total: 100)
                .accessibilityLabel("\(progress.reviewed) of \(progress.total) questions reviewed")
            Text("\(progress.reviewed) of \(progress.total) reviewed · \(progress.needsMore) need more research · \(progress.notReviewed) not reviewed").font(.subheadline)
            HStack(spacing: 8) {
                Button("Summary") { _ = presenter?.loadSummary() }.buttonStyle(.bordered)
                if open.plus {
                    Button("Snapshot") { _ = presenter?.createSnapshot(label: nil) }.buttonStyle(.bordered)
                    Button("Export PDF") { _ = presenter?.export() }.buttonStyle(.bordered)
                }
            }
            .frame(minHeight: 48)
        }
        .stockCard()
    }

    private func questionCard(_ q: ResearchQuestionView, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text(q.question.text).font(.subheadline.weight(.semibold)).accessibilityAddTraits(.isHeader)
            Text(q.question.help).font(.caption).foregroundStyle(colors.textSecondary)
            if !q.context.isEmpty {
                Text("What the data shows").font(.caption2).foregroundStyle(colors.textTertiary)
                ForEach(q.context, id: \.self) { Text("• \($0)").font(.subheadline) }
            }
            if q.locked {
                Text("Your answer: \(q.status.label)" + (q.note.isEmpty ? "" : " — \(q.note)")).font(.subheadline)
                Text("Saved during StockSteps+. Kept and readable; editing advanced questions needs StockSteps+.").font(.caption).foregroundStyle(colors.textSecondary)
            } else {
                Picker("Status", selection: Binding(get: { q.status.name }, set: { name in
                    if let status = client.researchStatuses().first(where: { $0.name == name }) { _ = presenter?.setStatus(questionId: q.question.id, status: status) }
                })) {
                    ForEach(client.researchStatuses(), id: \.name) { Text($0.label).tag($0.name) }
                }
                .pickerStyle(.segmented)
                .accessibilityValue(q.status.label)
                TextField("What do you notice? What would you check next?", text: Binding(get: { q.draft }, set: { presenter?.editNote(questionId: q.question.id, text: $0) }), axis: .vertical)
                    .lineLimit(2...6)
                    .textFieldStyle(.roundedBorder)
                    .accessibilityLabel("Your note")
                Text("\(q.remaining) characters left · private to your account").font(.caption2).foregroundStyle(colors.textTertiary)
                if q.dirty {
                    HStack {
                        Button(q.saving ? "Saving…" : "Save note") { _ = presenter?.saveNote(questionId: q.question.id) }.buttonStyle(.borderedProminent).disabled(q.saving)
                        Button("Discard") { presenter?.discardNote(questionId: q.question.id) }
                    }
                    .frame(minHeight: 48)
                } else if q.saving { Text("Saving…").font(.caption).foregroundStyle(colors.textSecondary) }
            }
        }
        .stockCard()
    }

    private func summaryCard(_ summary: ResearchSummary, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text(summary.detailed ? "Detailed research summary" : "Research summary").font(.headline).accessibilityAddTraits(.isHeader)
            Text("Financial data as of \(summary.dataAsOf.map { String($0.prefix(10)) } ?? "not reported"). Labels show what each line is: reported data, a calculation, your note, education or a limitation.")
                .font(.caption).foregroundStyle(colors.textSecondary)
            if summary.sampleData { Text("Sample data for development, not real financial data.").font(.caption).foregroundStyle(colors.cautionText) }
            ForEach(summary.sections, id: \.title) { section in
                Text(section.title).font(.subheadline.weight(.semibold)).padding(.top, 4).accessibilityAddTraits(.isHeader)
                ForEach(Array(section.items.enumerated()), id: \.offset) { _, item in
                    VStack(alignment: .leading, spacing: 1) {
                        Text(item.kind.label + (item.attribution.map { " · \($0)" } ?? "")).font(.caption2).foregroundStyle(colors.textTertiary).lineLimit(2)
                        Text(item.text).font(.subheadline)
                    }
                    .accessibilityElement(children: .combine)
                }
            }
            Button("Close summary") { presenter?.closeSummary() }.frame(minHeight: 44)
        }
        .stockCard()
    }
}

/// The exported report handed to the system "Save to Files" sheet (never uploaded or stored by StockSteps).
struct PDFReportFile: FileDocument {
    static var readableContentTypes: [UTType] { [.pdf] }
    let data: Data
    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}
