import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions
private let L = LearnDisplay.shared

/// SF Symbol per topic key (`LearnDisplay.topicIcon`); neutral, never a direction cue.
func learnTopicSymbol(_ key: String) -> String {
    switch key {
    case "business": "briefcase"
    case "growth": "chart.line.uptrend.xyaxis"
    case "profit": "dollarsign.circle"
    case "health": "shield"
    case "valuation": "tag"
    case "income": "banknote"
    case "earnings": "chart.bar"
    case "portfolio": "chart.pie"
    default: "graduationcap"
    }
}

/// Filled symbol for a topic tile (reference style); falls back to the outline symbol.
func learnTopicFilledSymbol(_ key: String) -> String {
    switch key {
    case "business": "briefcase.fill"
    case "profit": "dollarsign.circle.fill"
    case "health": "shield.fill"
    case "valuation": "tag.fill"
    case "income": "banknote.fill"
    case "earnings": "chart.bar.fill"
    case "portfolio": "chart.pie.fill"
    default: learnTopicSymbol(key)
    }
}

/// Topic icon colour from `LearnDisplay.topicTone`, using existing theme colours only.
func learnTopicTint(_ tone: String, _ colors: StockColors) -> Color {
    switch tone {
    case "green": colors.positiveText
    case "indigo": colors.learnAccent
    case "orange": colors.cautionText
    default: colors.primaryText
    }
}

/// A Learn destination pushed from the Learn tab (Phase 5D.5.2).
enum LearnTarget: Hashable, Identifiable {
    case topics(topic: String?, search: Bool)
    case term(String)
    var id: String {
        switch self {
        case let .topics(topic, search): "topics-\(topic ?? "all")-\(search)"
        case let .term(id): "term-\(id)"
        }
    }
}

// MARK: - Learn home

/// Learn home (Phase 5D.5.2, as Compose `LearnScreen`): one clear start, catalogue topics, today's concept, real progress only, more ways to learn.
struct LearnScreen: View {
    let learning: LearningModel
    let onResearch: (ResearchTarget) -> Void
    let onSearch: () -> Void
    var onPractice: () -> Void = {}
    var onLearnTarget: (LearnTarget) -> Void = { _ in }
    /// 5D.5.6B landing bar: the all-alerts history and Settings.
    var onNotifications: () -> Void = {}
    var onSettings: () -> Void = {}
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var width: CGFloat = 0

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let progress = learning.progress
        let inProgress = progress?.inProgress ?? []
        let completed = progress?.completed ?? []
        let concept = L.todaysConcept(dayOfYear: L.dayOfYear(epochMillis: Int64(Date().timeIntervalSince1970 * 1000)))
        ScrollView {
            VStack(alignment: .leading, spacing: CGFloat(space.xl)) {
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                    // 5D.5.6B: the shared landing bar ("Learn", term search, Notifications, Settings).
                    StockLandingTopBar(tab: .learn, actionCount: 1, onNotifications: onNotifications, onSettings: onSettings) {
                        StockLandingIconButton(systemImage: "magnifyingglass", label: L.SEARCH_ACTION) { onLearnTarget(.topics(topic: nil, search: true)) }
                    }
                    Text(L.INTRO).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody).fixedSize(horizontal: false, vertical: true)
                    if progress?.syncFailed == true {
                        Text(L.SYNC_NOTE).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
                    }
                }
                startCard(colors, action: inProgress.isEmpty ? L.START_ACTION : L.CONTINUE_ACTION, inProgress: inProgress)
                VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                    StockSectionHeader(title: L.EXPLORE, actionTitle: L.SEE_ALL, action: { onLearnTarget(.topics(topic: nil, search: false)) }) { EmptyView() }
                    topicGrid(L.featuredTopics(), colors)
                }
                VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                    StockSectionHeader(title: L.TODAY) { EmptyView() }
                    Button { onLearnTarget(.term(concept.id)) } label: {
                        HStack(spacing: CGFloat(space.md)) {
                            Image(systemName: "lightbulb.fill").foregroundStyle(colors.cautionText).accessibilityHidden(true)
                            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                                Text(L.unbroken(text: concept.title)).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle)
                                Text(L.preview(entry: concept)).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                            }
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            Image(systemName: "chevron.right").foregroundStyle(colors.textTertiary).accessibilityHidden(true)
                        }
                        .stockCard(bordered: false)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityElement(children: .combine)
                }
                if !inProgress.isEmpty {
                    VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                        StockSectionHeader(title: L.CONTINUE) { EmptyView() }
                        ForEach(inProgress, id: \.symbol) { journeyCard($0, action: "Continue", colors) }
                    }
                }
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    StockSectionHeader(title: L.MORE_WAYS) { EmptyView() }
                    StockNavigationRow(title: L.RESEARCH_ANY, detail: L.RESEARCH_ANY_DETAIL, icon: "magnifyingglass", action: onSearch)
                    StockDivider()
                    StockNavigationRow(title: L.PRACTICE, detail: L.PRACTICE_DETAIL, icon: "chart.line.uptrend.xyaxis", action: onPractice)
                }
                if !completed.isEmpty {
                    VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                        StockSectionHeader(title: L.COMPLETED) { EmptyView() }
                        ForEach(completed, id: \.symbol) { journeyCard($0, action: "Review", colors) }
                    }
                }
            }
            .frame(maxWidth: CGFloat(dims.contentMaxWidth))
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
            .frame(maxWidth: .infinity)
        }
        .scrollsToTopOnReselect(.learn) // 5D.5.6C2 (D12)
        .background(colors.appBackground.ignoresSafeArea())
    }

    /// D-L2: resume the latest research, else the first suggested company not started, else search.
    private func start(_ inProgress: [ResearchProgress]) {
        if let j = inProgress.first { onResearch(ResearchTarget(symbol: j.symbol, name: j.name)); return }
        let client = learning.client
        if let pick = zip(client.quickPickSymbols, client.quickPickNames).first(where: { learning.completed($0.0) == nil }) {
            onResearch(ResearchTarget(symbol: pick.0, name: pick.1))
        } else { onSearch() }
    }

    private func startCard(_ colors: StockColors, action: String, inProgress: [ResearchProgress]) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            HStack(alignment: .center, spacing: CGFloat(space.md)) {
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(L.START_HERE.uppercased()).font(StockStepsTheme.font(type.tiny, relativeTo: .caption2)).foregroundStyle(colors.textSupporting)
                        .accessibilityLabel(L.START_HERE)
                    Text(L.START_TITLE).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline).weight(.bold)).foregroundStyle(colors.textTitle)
                        .accessibilityAddTraits(.isHeader)
                    if !typeSize.isAccessibilitySize {
                        Text(L.START_BODY).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody).fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if !typeSize.isAccessibilitySize {
                    Image("LearnBasics").resizable().scaledToFit().frame(width: CGFloat(dims.learnIllustration), height: CGFloat(dims.learnIllustration))
                        .accessibilityHidden(true)
                }
            }
            // Accessibility sizes: the long sentence folds away (same text, one tap) so the action stays near the title.
            if typeSize.isAccessibilitySize {
                StockDisclosure(title: L.START_DETAILS) {
                    Text(L.START_BODY).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody).fixedSize(horizontal: false, vertical: true)
                }
            }
            Button(action) { start(inProgress) }.buttonStyle(.stockPrimary)
        }
        .padding(CGFloat(space.cardPadding))
        .frame(maxWidth: .infinity, alignment: .leading)
        // Reference: a light-blue "Start here" card (existing primaryContainer token).
        .background(colors.primaryContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
    }

    /// Two columns while every topic word fits (shared `StockCardGridLayout`, same rule as Android), else one.
    private func topicGrid(_ topics: [LearnTopic], _ colors: StockColors) -> some View {
        let font = StockStepsTheme.uiFont(type.bodySemiBold, typeSize: typeSize)
        let widest = topics.flatMap { StockCardGridLayout.shared.words(title: $0.name) }.map { ($0 as NSString).size(withAttributes: [.font: font]).width }.max() ?? 0
        // Reference: at accessibility sizes a compact tile (icon above, name only) that stays in two columns while every topic word fits.
        let inset = typeSize.isAccessibilitySize ? Float(space.md) : Float(space.md) + (Float(dims.icon) + Float(space.sm)) / 2
        // 5D.5.2 polish: at AX4 and up one column of compact rows instead of tall centred tiles.
        let columns = max(1, Int(StockCardGridLayout.shared.columns(widestTitleWord: Float(widest), availableWidth: Float(width), gap: Float(space.sm),
                                                                   cardPadding: inset, accessibilityText: typeSize >= .accessibility4)))
        let rows = stride(from: 0, to: topics.count, by: columns).map { Array(topics[$0..<min($0 + columns, topics.count)]) }
        return VStack(spacing: CGFloat(space.sm)) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(alignment: .top, spacing: CGFloat(space.sm)) {
                    ForEach(row, id: \.name) { topic in topicTile(topic, colors, singleColumn: columns == 1) }
                    if row.count < columns { Color.clear.frame(maxWidth: .infinity) }
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
    }

    /// `singleColumn` at accessibility sizes: a compact row (icon beside the name) instead of a tall centred tile.
    private func topicTile(_ topic: LearnTopic, _ colors: StockColors, singleColumn: Bool) -> some View {
        Button { onLearnTarget(.topics(topic: topic.name, search: false)) } label: {
            let icon = Image(systemName: learnTopicFilledSymbol(L.topicIcon(name: topic.name))).font(StockStepsTheme.font(type.cardTitle, relativeTo: .title3))
                .foregroundStyle(learnTopicTint(L.topicTone(name: topic.name), colors)).accessibilityHidden(true)
            Group {
                if typeSize.isAccessibilitySize && singleColumn {
                    HStack(spacing: CGFloat(space.md)) {
                        icon
                        Text(topic.name).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle).multilineTextAlignment(.leading)
                        Spacer(minLength: 0)
                    }
                } else if typeSize.isAccessibilitySize {
                    VStack(spacing: CGFloat(space.xs)) {
                        icon
                        Text(topic.name).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle).multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                } else {
                    // Reference: a coloured icon beside the topic name and what it covers (the topic's own term names).
                    HStack(alignment: .top, spacing: CGFloat(space.sm)) {
                        icon.frame(width: CGFloat(dims.icon))
                        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                            Text(topic.name).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle)
                            Text(L.topicSummary(topic: topic)).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .multilineTextAlignment(.leading)
                        Spacer(minLength: 0)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .stockCard(padding: CGFloat(space.md), bordered: false)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint("Opens \(topic.name)")
    }

    private func journeyCard(_ journey: ResearchProgress, action: String, _ colors: StockColors) -> some View {
        let label = L.journeyDetail(completed: journey.completedCount)
        return Button { onResearch(ResearchTarget(symbol: journey.symbol, name: journey.name)) } label: {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                HStack(spacing: CGFloat(space.sm)) {
                    StockTickerAvatar(symbol: journey.symbol, size: CGFloat(dims.logoCompact))
                    VStack(alignment: .leading) {
                        Text(journey.name).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary)
                        Text(label).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
                    }
                    Spacer()
                    Text(action).font(StockStepsTheme.font(type.label)).foregroundStyle(colors.primaryText)
                }
                ProgressView(value: Double(journey.completedCount), total: Double(L.stepCount())).tint(colors.primary)
            }
            .stockCard(bordered: false)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(journey.name), \(label)")
        .accessibilityHint("\(action) research")
    }
}

// MARK: - Topics

/// Topics (Phase 5D.5.2, as Compose `LearnTopicsScreen`): all topics with a term filter, or one topic's terms. Pushes its own topic and term pages.
struct LearnTopicsView: View {
    let topic: String?
    var focusSearch = false
    var onSearchCompanies: () -> Void = {}
    @State private var query = ""
    @Environment(TabRouter.self) private var router: TabRouter?
    @FocusState private var searchFocused: Bool
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let _ = typeSize
        let colors = StockStepsTheme.colors(scheme)
        let selected = topic.flatMap { L.topic(name: $0) }
        let results = L.search(query: query)
        ScrollView {
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                    Text(selected?.name ?? L.TOPICS_TITLE).font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).foregroundStyle(colors.textTitle)
                        .accessibilityAddTraits(.isHeader)
                    Text(selected.map { L.termCount(topic: $0) } ?? L.TOPICS_INTRO).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
                        .fixedSize(horizontal: false, vertical: true)
                    // Reference: a clean list; the filter appears when Learn's search opened this screen (or while it holds a query).
                    if selected == nil && (focusSearch || !query.isEmpty) {
                        TextField(L.SEARCH_LABEL, text: $query).textFieldStyle(.roundedBorder).font(StockStepsTheme.font(type.body))
                            .focused($searchFocused).submitLabel(.search).autocorrectionDisabled()
                            .onChange(of: query) { _, v in if v.count > 60 { query = String(v.prefix(60)) } }
                    }
                }
                .padding(.bottom, CGFloat(space.md))
                if let selected {
                    termRows(selected.terms)
                } else if !query.trimmingCharacters(in: .whitespaces).isEmpty {
                    Text(results.isEmpty ? L.searchEmpty(query: query) : L.resultCount(count: Int32(results.count)))
                        .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
                    termRows(results)
                } else {
                    ForEach(L.topics(), id: \.name) { t in
                        LearnCardRow(title: t.name, detail: L.topicSummary(topic: t), hint: "Open \(t.name)",
                                     symbol: learnTopicFilledSymbol(L.topicIcon(name: t.name)), tint: learnTopicTint(L.topicTone(name: t.name), colors)) { router?.open(.learn(.topics(topic: t.name, search: false))) }
                    }
                }
            }
            .frame(maxWidth: CGFloat(dims.contentMaxWidth))
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
            .frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .navigationTitle(L.TITLE) // names the next page's back button "‹ Learn" …
        .toolbar { ToolbarItem(placement: .principal) { Text(verbatim: " ").accessibilityHidden(true) } } // … while the bar itself shows no repeated title (reference)
        .navigationBarTitleDisplayMode(.inline)
        .task { if focusSearch && topic == nil { searchFocused = true } }
    }

    @ViewBuilder private func termRows(_ terms: [EducationEntry]) -> some View {
        ForEach(terms, id: \.id) { e in
            LearnCardRow(title: e.title, detail: L.preview(entry: e), hint: "Read \(e.title)") { router?.open(.learn(.term(e.id))) }
        }
    }
}

// MARK: - Term page

/// A term's reading page (Phase 5D.5.2, as Compose `LearnTermScreen`): topic, title, the "what it is" sentence, an outline, each section, next term.
/// The illustration band is decorative and hidden at accessibility sizes.
struct LearnTermView: View {
    let termId: String
    var onSearchCompanies: () -> Void = {}
    @Environment(TabRouter.self) private var router: TabRouter?
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        if let entry = BeginnerEducation.shared.entry(id: termId) {
            let next = L.next(entry: entry)
            ScrollView {
                VStack(alignment: .leading, spacing: CGFloat(space.lg)) {
                    if !typeSize.isAccessibilitySize {
                        Image("LearnBasics").resizable().scaledToFit().frame(width: CGFloat(dims.learnIllustration) * 1.5, height: CGFloat(dims.learnIllustration) * 1.5)
                            .frame(maxWidth: .infinity).frame(height: CGFloat(dims.learnIllustration) * 2)
                            .background(colors.primaryContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                            .accessibilityHidden(true)
                    }
                    VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                        Text(L.unbroken(text: entry.title)).font(StockStepsTheme.font(type.screenTitle, relativeTo: .largeTitle)).foregroundStyle(colors.textTitle)
                            .accessibilityAddTraits(.isHeader).fixedSize(horizontal: false, vertical: true)
                        Text(entry.short_).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody).fixedSize(horizontal: false, vertical: true)
                    }
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        Text(L.IN_THIS_LESSON).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle).accessibilityAddTraits(.isHeader)
                        ForEach(L.outline(entry: entry), id: \.self) { Text("• \($0)").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody) }
                    }
                    .padding(CGFloat(space.cardPadding))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(colors.primaryContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
                    ForEach(L.sections(entry: entry), id: \.title) { section in
                        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                            Text(section.title).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textTitle)
                                .accessibilityAddTraits(.isHeader)
                            Text(section.body).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody).fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    VStack(spacing: 0) {
                        if let next {
                            StockNavigationRow(title: L.nextLabel(next: next), detail: next.topic, icon: "arrow.right") { router?.open(.learn(.term(next.id))) }
                            StockDivider()
                        }
                        StockNavigationRow(title: L.ALL_TOPICS, icon: "graduationcap") { router?.open(.learn(.topics(topic: nil, search: false))) }
                        StockDivider()
                        StockNavigationRow(title: L.RESEARCH_ANY, detail: L.RESEARCH_ANY_DETAIL, icon: "magnifyingglass", action: onSearchCompanies)
                    }
                }
                .frame(maxWidth: CGFloat(dims.contentMaxWidth))
                .padding(.horizontal, CGFloat(space.screen))
                .padding(.vertical, CGFloat(space.md))
                .frame(maxWidth: .infinity)
            }
            .background(colors.appBackground.ignoresSafeArea())
            .navigationTitle(L.TITLE) // names the next page's back button "‹ Learn" …
        .toolbar { ToolbarItem(placement: .principal) { Text(verbatim: " ").accessibilityHidden(true) } } // … while the bar itself shows no repeated title (reference)
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

/// Reference list style (as Compose `LearnCardRow`): each entry is its own borderless card — optional coloured icon, bold title, one supporting
/// line, chevron; 48 pt minimum.
struct LearnCardRow: View {
    let title: String
    let detail: String
    let hint: String
    var symbol: String? = nil
    var tint: Color? = nil
    let action: () -> Void
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    var body: some View {
        let _ = typeSize
        let colors = StockStepsTheme.colors(scheme)
        Button(action: action) {
            HStack(spacing: CGFloat(space.md)) {
                if let symbol {
                    Image(systemName: symbol).font(StockStepsTheme.font(type.cardTitle, relativeTo: .title3)).foregroundStyle(tint ?? colors.primaryText)
                        .frame(width: CGFloat(dims.icon)).accessibilityHidden(true)
                }
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(L.unbroken(text: title)).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle)
                    Text(detail).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting).fixedSize(horizontal: false, vertical: true)
                }
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right").foregroundStyle(colors.textTertiary).accessibilityHidden(true)
            }
            .frame(minHeight: CGFloat(dims.touchTarget))
            .stockCard(padding: CGFloat(space.md), bordered: false)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint(hint)
    }
}

// MARK: - Contextual learning (Phase 5D.5.3)

extension MetricExplanation: @retroactive Identifiable {
    public var id: String { metricId }
}

/// "Learn more about P/E ratio" (primary, full width) when the metric has a Learn term; nothing otherwise.
struct MetricLearnMoreButton: View {
    let term: EducationEntry?
    let onLearn: (String) -> Void
    var body: some View {
        if let term {
            Button(MetricLearnTerms.shared.learnMoreLabel(entry: term)) { onLearn(term.id) }
                .buttonStyle(.stockPrimary)
                .frame(maxWidth: .infinity)
        }
    }
}

/// A tapped metric's explanation (as Compose `MetricExplanationSheet`): the screen's label, the existing metric explanation and — only when the
/// metric has a Learn term — "Learn more about …", which closes the sheet and opens the term. No new educational text.
struct MetricExplanationSheet: View {
    let explanation: MetricExplanation
    let onLearn: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    var body: some View {
        let _ = typeSize
        let colors = StockStepsTheme.colors(scheme)
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: CGFloat(space.md)) {
                    Text(explanation.explanation).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody).fixedSize(horizontal: false, vertical: true)
                    MetricLearnMoreButton(term: explanation.term) { id in dismiss(); onLearn(id) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
            }
            .navigationTitle(explanation.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}

/// Spoken hint and ⓘ rule for a tappable row (as Compose `explainLabel` / `hasTerm`).
func metricHasTerm(_ metricId: String?) -> Bool { metricId.map { MetricLearnTerms.shared.hasTerm(metricId: $0) } ?? false }
func metricExplanation(_ row: InfoRow) -> MetricExplanation? { row.metricId.flatMap { MetricLearnTerms.shared.explain(metricId: $0, title: row.label) } }

/// A financial row that opens its explanation when the metric has one (as Compose `StockInfoRow(onClick = …)`): the whole row is a 48 pt button;
/// the ⓘ appears only for metrics with a Learn term (D-C2). Rows without an explanation render unchanged.
struct ExplainableInfoLine: View {
    let label: String
    let value: String
    var helper: String? = nil
    var change: String? = nil
    var direction: PriceDirection? = nil
    var compact = false
    /// See `StockInfoLine.stacked` (5D.5.4C C3); default nil keeps the row.
    var stacked: Bool? = nil
    let explanation: MetricExplanation?
    let onExplain: (MetricExplanation) -> Void
    var body: some View {
        if let explanation {
            Button { onExplain(explanation) } label: {
                StockInfoLine(label: label, value: value, helper: helper, change: change, direction: direction, compact: compact,
                              info: explanation.term != nil, stacked: stacked)
                    .frame(minHeight: CGFloat(dims.touchTarget)).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint(MetricLearnTerms.shared.explainLabel(metricLabel: label))
        } else {
            StockInfoLine(label: label, value: value, helper: helper, change: change, direction: direction, compact: compact, stacked: stacked)
        }
    }
}
