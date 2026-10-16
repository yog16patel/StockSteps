import Observation
import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions
private let D = DailyBriefDisplay.shared

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

    /// `start: false` defers observing to `activate()` (see ScreenerModel).
    init(account: IosAccountClient, start: Bool = true) {
        client = IosBriefClient(account: account)
        if start { activate() }
    }
    func activate() {
        guard subscription == nil else { return }
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

/// A brief index as the shared `StockIndexRow` (Home "Markets today" and the reader, Phase 5D.5.1).
struct BriefIndexRow: View {
    let index: BriefIndex
    let client: IosBriefClient
    var body: some View {
        StockIndexRow(name: index.displayName, stateLabel: index.stateLabel, value: client.value(i: index), change: D.indexChange(index: index),
                      direction: D.indexDirection(index: index), accessibilityText: client.accessibility(i: index), caution: D.indexCaution(index: index),
                      note: index.proxyNote, changeUnavailable: D.CHANGE_UNAVAILABLE)
    }
}

/// The reader's indices (5D.5.1b, as Compose `BriefIndices`): compact tiles while every unbreakable piece fits a tile
/// (`StockIndexTilesLayout`, measured in the fonts used), otherwise full-width `BriefIndexRow`s. The shared state label appears once below the tiles.
struct BriefIndices: View {
    let brief: DailyBrief
    let client: IosBriefClient
    @State private var width: CGFloat = 0
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let shared = D.sharedStateLine(brief: brief)
        Group {
            if tiles {
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                    HStack(alignment: .top, spacing: CGFloat(space.sm)) {
                        ForEach(brief.marketSnapshot, id: \.indexId) { i in
                            StockIndexTile(name: i.displayName, percent: D.indexPercent(index: i), value: client.value(i: i), points: D.indexPoints(index: i),
                                           direction: D.indexDirection(index: i), accessibilityText: client.accessibility(i: i),
                                           stateLabel: shared == nil ? i.stateLabel : nil, caution: D.indexCaution(index: i), changeUnavailable: D.CHANGE_UNAVAILABLE)
                        }
                    }
                    .fixedSize(horizontal: false, vertical: true)
                    if let shared {
                        Text(shared).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
                    }
                }
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(brief.marketSnapshot.enumerated()), id: \.element.indexId) { n, index in
                        if n > 0 { StockDivider() }
                        BriefIndexRow(index: index, client: client)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
    }

    /// Shared `StockIndexTilesLayout` decision (same rule and fonts as Android).
    private var tiles: Bool {
        let label = StockStepsTheme.uiFont(type.label, typeSize: typeSize)
        let number = StockStepsTheme.uiFont(type.numberMedium, typeSize: typeSize)
        let strong = StockStepsTheme.uiFont(type.numberLabelStrong, typeSize: typeSize)
        func w(_ t: String, _ f: UIFont) -> CGFloat { (t as NSString).size(withAttributes: [.font: f]).width }
        let names = brief.marketSnapshot.flatMap { $0.displayName.split(separator: " ").map(String.init) }.map { w($0, label) }.max() ?? 0
        let arrow = number.pointSize * 1.2 + CGFloat(space.xxs)
        let percents = brief.marketSnapshot.compactMap { D.indexPercent(index: $0) }.map { w($0, number) + arrow }.max() ?? 0
        let others = D.tileTokens(brief: brief).map { w($0, strong) }.max() ?? 0
        return StockIndexTilesLayout.shared.tiles(widestToken: Float(max(names, percents, others)), count: Int32(brief.marketSnapshot.count),
                                                  availableWidth: Float(width), gap: Float(space.sm), tilePadding: Float(space.sm),
                                                  accessibilityText: typeSize.isAccessibilitySize)
    }
}

/// One upcoming report (5D.5.1b, as Compose `BriefEarningsRow`): company and ticker, "Expected Oct 30 · After market close" with the server's date
/// status as a badge (words, not colour alone), and the estimate/reason line.
struct BriefEarningsRow: View {
    let earnings: BriefEarnings
    let action: () -> Void
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    var body: some View {
        let _ = typeSize
        let colors = StockStepsTheme.colors(scheme)
        let e = earnings
        Button(action: action) {
            HStack(spacing: CGFloat(space.md)) {
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(D.earningsTitle(e: e)).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle).multilineTextAlignment(.leading)
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: CGFloat(space.sm)) { when(e, colors); badge(e) }
                        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) { when(e, colors); badge(e) }
                    }
                    Text(D.earningsNote(e: e)).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
                        .multilineTextAlignment(.leading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right").font(StockStepsTheme.font(type.caption, relativeTo: .caption1).weight(.semibold))
                    .foregroundStyle(colors.textTertiary).accessibilityHidden(true)
            }
            .padding(.vertical, CGFloat(space.sm))
            .frame(minHeight: CGFloat(StockStepsTheme.dimensions.rowCompactMinHeight))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(D.earningsSpoken(e: e))
        .accessibilityHint(D.earningsAction(e: e))
        .accessibilityAddTraits(.isButton)
    }

    private func when(_ e: BriefEarnings, _ colors: StockColors) -> some View {
        Text(D.earningsWhen(e: e)).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
            .multilineTextAlignment(.leading).fixedSize(horizontal: false, vertical: true)
    }

    private func badge(_ e: BriefEarnings) -> some View {
        StockStatusBadge(text: D.earningsStatus(e: e), kind: D.earningsConfirmed(e: e) ? .info : .neutral, size: .compact)
    }
}

/// Neutral SF Symbol per watchlist highlight kind (`DailyBriefDisplay.highlightIcon`); the sentence carries the direction in words.
func briefHighlightSymbol(_ key: String) -> String {
    switch key {
    case "chart": "chart.bar"
    case "news": "newspaper"
    case "calendar": "calendar"
    default: "doc.text"
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
    var onOpenDigest: (() -> Void)? = nil

    var body: some View {
        DailyBriefScreen(state: model.state, client: model.client, onOpenStock: onOpenStock, onOpenEarnings: onOpenEarnings, onOpenResults: onOpenResults, onHistory: onHistory, onWatchlist: onWatchlist,
                         onLearn: onLearn, onSignIn: onSignIn, onUpgrade: { model.presenter.dismissUpgrade(); onUpgrade() }, onOpenDigest: onOpenDigest)
            .navigationTitle(D.TITLE)
            .navigationBarTitleDisplayMode(.inline)
            .taskOnce(id: target.id) { model.presenter.open(id: target.briefId) } // 5D.5.6C3: not again on tab switches
            .refreshable { model.presenter.loadLatest() }
    }
}

/// Daily Brief reader (Global UI Refinement Phase 5D.5.1, as Compose `DailyBriefScreen`): answer first — one meta line, the server summary as the
/// headline, freshness and sessions, compact index rows, stories with the first sentence and "Show details", personal sections, the Concept of
/// the day and one folded "About this brief". Borderless sections; every value and sentence comes from the brief.
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
    /// Phase 5: the StockSteps+ personalized earnings digest.
    var onOpenDigest: (() -> Void)? = nil
    @Environment(\.colorScheme) private var scheme
    @Environment(\.openURL) private var openURL
    /// Read in body so a live Dynamic Type change re-renders (theme fonts resolve when the body runs).
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var showPreferences = false
    @State private var concept: EducationEntry?
    @State private var question = ""
    private var presenter: DailyBriefPresenter { client.presenter }

    var body: some View {
        let _ = typeSize
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.xl)) {
                if let state, let brief = state.current {
                    header(brief, state, colors)
                    markets(brief, colors)
                    stories(brief, state, colors)
                    watchlist(colors)
                    earnings(colors)
                    VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                        sectionTitle(D.LEARN)
                        conceptCard(brief, colors)
                    }
                    premium(colors)
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        StockDisclosure(title: D.ABOUT_BRIEF) { StockNotes(lines: D.aboutLines(brief: brief)) }
                        footerLink(D.HISTORY, systemImage: "calendar", colors, action: onHistory)
                    }
                } else if state?.loading != false {
                    skeleton
                } else {
                    StockSectionMessage(message: state?.error ?? D.UNAVAILABLE, actionTitle: "Try again", action: { presenter.loadLatest() })
                }
            }
            .frame(maxWidth: CGFloat(dims.contentMaxWidth))
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
            .frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .toolbar { ToolbarItem(placement: .topBarTrailing) { moreMenu } }
        .sheet(isPresented: $showPreferences) { preferences(colors).presentationDetents([.medium, .large]) }
        .sheet(item: Binding(get: { concept.map { IdentifiedEntry(entry: $0) } }, set: { concept = $0?.entry })) { BeginnerExplanationSheet(entry: $0.entry) }
        .alert(D.UPGRADE_TITLE, isPresented: Binding(get: { state?.upgrade == true }, set: { if !$0 { presenter.dismissUpgrade() } })) {
            Button(D.SEE_PLUS, action: onUpgrade)
            Button(D.NOT_NOW, role: .cancel) { presenter.dismissUpgrade() }
        } message: { Text(D.UPGRADE) }
        .alert(state?.message ?? "", isPresented: Binding(get: { state?.message != nil }, set: { if !$0 { presenter.dismissMessage() } })) { Button("OK") { presenter.dismissMessage() } }
    }

    /// ⋯ : Notify me, Previous briefs and (sample briefs only, MOCK) the scenario list.
    private var moreMenu: some View {
        Menu {
            Button(D.NOTIFY, systemImage: "bell") { presenter.loadPreferences(); showPreferences = true }
            Button(D.HISTORY, systemImage: "calendar", action: onHistory)
            if state?.current?.sampleData == true {
                Menu(D.SCENARIOS) {
                    Button(D.SCENARIO_NONE) { presenter.scenario(name: nil) }
                    ForEach(client.scenarios, id: \.self) { n in Button(n) { presenter.scenario(name: n) } }
                }
            }
        } label: {
            Image(systemName: "ellipsis.circle").accessibilityLabel(D.MORE_OPTIONS)
        }
    }

    private func sectionTitle(_ text: String) -> some View {
        StockSectionHeader(title: text) { EmptyView() }
    }

    private func caption(_ text: String, _ color: Color) -> some View {
        Text(text).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(color).fixedSize(horizontal: false, vertical: true)
    }

    /// 5D.5.1c (D-C4): "Previous briefs" in the same compact label style as the "About this brief" disclosure (icon, label, chevron; 48 pt).
    private func footerLink(_ title: String, systemImage: String, _ colors: StockColors, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: CGFloat(space.xs)) {
                Image(systemName: systemImage).accessibilityHidden(true)
                Text(title).multilineTextAlignment(.leading)
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").accessibilityHidden(true)
            }
            .font(StockStepsTheme.font(type.label, relativeTo: .subheadline))
            .foregroundStyle(colors.primaryText)
            .frame(minHeight: CGFloat(dims.touchTarget))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func header(_ b: DailyBrief, _ state: DailyBriefUiState, _ colors: StockColors) -> some View {
        let now = client.now()
        // 5D.5.1c: meta, takeaway and summary read as one block (xs); freshness and the status banner follow (sm).
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                StockTitleWithBadge {
                    caption(D.meta(brief: b), colors.textMeta)
                } badge: {
                    if b.sampleData { StockStatusBadge(text: D.SAMPLE, kind: .sample, size: .compact, accessibilityText: D.SAMPLE_DESCRIPTION) }
                }
                // D-B1: the takeaway derived from the index values leads; the server summary stays word for word below it (D-B2: folded at
                // accessibility sizes). Without a takeaway the summary itself is the headline, as in 5D.5.1.
                let takeaway = D.takeaway(brief: b)
                Text(takeaway ?? b.summaryLine).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3)).foregroundStyle(colors.textTitle)
                    .fixedSize(horizontal: false, vertical: true).accessibilityAddTraits(.isHeader)
                if takeaway != nil {
                    if typeSize.isAccessibilitySize {
                        StockDisclosure(title: D.FULL_SUMMARY, systemImage: "doc.text") {
                            Text(b.summaryLine).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody).fixedSize(horizontal: false, vertical: true)
                        }
                    } else {
                        Text(b.summaryLine).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody).fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            caption(D.freshness(brief: b, nowMillis: now), colors.textMeta)
            // One banner for stale and offline, one sentence each (U-05).
            if let status = D.status(brief: b, nowMillis: now, offline: state.offline) {
                StockBanner(kind: status.stale ? .warning : .info, message: status.message)
            }
        }
    }

    @ViewBuilder
    private func markets(_ brief: DailyBrief, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            sectionTitle(D.MARKETS)
            // 5D.5.1c (D-C1): market open/closed status sits with the indices it describes.
            ForEach(D.sessionLines(brief: brief), id: \.self) { caption($0, colors.textMeta) }
            if brief.marketSnapshot.isEmpty {
                Text(D.NO_INDICES).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
            } else {
                BriefIndices(brief: brief, client: client)
            }
            StockDisclosure(title: D.INDEX_EXPLAINER_TITLE, systemImage: "graduationcap") {
                Text(brief.indexExplainer).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody).fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    @ViewBuilder
    private func stories(_ brief: DailyBrief, _ state: DailyBriefUiState, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.md)) {
            sectionTitle(D.STORIES)
            if brief.stories.isEmpty {
                Text(D.NO_STORIES).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
            }
            if !brief.stories.isEmpty {
                // D-B7: the stories share one grouped card (rows divided inside; no card per story).
                VStack(alignment: .leading, spacing: CGFloat(space.md)) {
                    ForEach(Array(brief.stories.enumerated()), id: \.element.id) { n, story in
                        if n > 0 { StockDivider() }
                        storyRow(story, state, colors)
                    }
                }
                .stockCard(bordered: false) // 5D.5.1c (D-C2): the tiles' borderless surface; the default is unchanged
                StockDisclosure(title: D.ABOUT_STORIES) { StockNotes(lines: D.storyNotes(brief: brief)) }
            }
        }
    }

    private func storyRow(_ story: BriefStory, _ state: DailyBriefUiState, _ colors: StockColors) -> some View {
        let ai = client.ai(state: state, key: story.id)
        return VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            caption(D.storyMeta(story: story, nowMillis: client.now()), colors.textMeta)
            Text(story.headline).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle)
                .fixedSize(horizontal: false, vertical: true).accessibilityAddTraits(.isHeader)
            StockShowDetails(explanation: D.excerpt(story: story), hasMore: true, trailing: AnyView(
                linkButton(D.READ_ARTICLE, systemImage: "arrow.up.right", colors) { if let url = URL(string: story.sourceUrl) { openURL(url) } }
                    .accessibilityLabel(D.readArticleLabel(story: story))
            )) {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: CGFloat(space.sm)) { storyActions(story, state, ai, colors) }
                    VStack(alignment: .leading, spacing: CGFloat(space.xs)) { storyActions(story, state, ai, colors) }
                }
            }
            if let ai { aiAnswer(ai, colors) }
        }
    }

    @ViewBuilder
    private func storyActions(_ story: BriefStory, _ state: DailyBriefUiState, _ ai: BriefAiState?, _ colors: StockColors) -> some View {
        ForEach(story.relatedSymbols, id: \.self) { s in Button(D.companyAction(symbol: s)) { onOpenStock(s) }.buttonStyle(.stockSecondary) }
        linkButton(D.understandLabel(plus: client.isPlus(state: state)), systemImage: "lightbulb", colors) { presenter.explain(storyId: story.id) }
            .disabled(ai?.loading == true)
    }

    /// A text action in the app's link style (48 pt target).
    private func linkButton(_ title: String, systemImage: String, _ colors: StockColors, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: systemImage).font(StockStepsTheme.font(type.label, relativeTo: .subheadline)).foregroundStyle(colors.primaryText)
                .multilineTextAlignment(.leading).frame(minHeight: CGFloat(dims.touchTarget)).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func aiAnswer(_ ai: BriefAiState, _ colors: StockColors) -> some View {
        if ai.loading {
            ProgressView().accessibilityLabel(D.PREPARING)
        } else if let error = ai.error {
            Text(error).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.negativeText)
        } else if let a = ai.answer {
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                Text(D.aiLabel(answer: a)).font(StockStepsTheme.font(type.label)).foregroundStyle(colors.primaryText)
                Text(a.answer).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
                ForEach(a.points, id: \.self) { Text("• \($0)").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody) }
                if a.insufficientEvidence { caption(D.INSUFFICIENT, colors.cautionText) }
                ForEach(a.sources, id: \.id) { s in linkButton(D.aiSource(source: s), systemImage: "arrow.up.right", colors) { if let url = URL(string: s.url) { openURL(url) } } }
                if let left = a.remainingToday { caption(D.aiRemaining(left: left.int32Value), colors.textMeta) }
            }
        }
    }

    @ViewBuilder
    private func watchlist(_ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            sectionTitle(D.WATCHLIST)
            if state?.signedIn != true {
                StockSectionMessage(message: D.SIGN_IN_PROMPT, actionTitle: D.SIGN_IN, action: onSignIn)
            } else if let p = state?.personal {
                if p.watchlistCount == 0 {
                    // 5D.5.1c (D-C3): one tinted action aligned with the text.
                    VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                        Text(D.WATCHLIST_EMPTY_SHORT).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                        Button(D.ADD_COMPANIES, action: onWatchlist).buttonStyle(.stockSecondary)
                    }
                } else if p.watchlistHighlights.isEmpty {
                    Text(D.nothingNotable(count: p.watchlistCount)).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                } else {
                    VStack(spacing: 0) {
                        ForEach(Array(p.watchlistHighlights.enumerated()), id: \.offset) { n, h in
                            if n > 0 { StockDivider() }
                            StockNavigationRow(title: h.text, detail: [D.highlightTitle(h: h), h.publisher].compactMap { $0 }.joined(separator: " · "),
                                               icon: briefHighlightSymbol(D.highlightIcon(h: h)), hint: D.highlightAction(h: h)) {
                                if let u = h.url, let url = URL(string: u) { openURL(url) }
                                else if let id = h.reportId { onOpenResults(id) }
                                else { onOpenStock(h.symbol) }
                            }
                        }
                        if p.moreHighlights > 0 {
                            StockDivider()
                            StockNavigationRow(title: D.moreHighlights(count: p.moreHighlights), badge: "StockSteps+", action: onUpgrade)
                        }
                    }
                }
                // D-B6: the method note is one tap away, under a named disclosure.
                if let note = p.notes.first(where: { $0.hasPrefix("Watching") }) {
                    StockDisclosure(title: D.ABOUT_WATCHLIST) { StockNotes(lines: [note]) }
                }
                if let onOpenDigest, p.watchlistCount > 0 {
                    StockNavigationRow(title: D.EARNINGS_DIGEST, icon: "lightbulb", badge: "StockSteps+", action: onOpenDigest)
                }
            } else if state?.personalLoading == true {
                VStack(alignment: .leading, spacing: CGFloat(space.sm)) { StockSkeleton(width: 240); StockSkeleton(width: 180) }
                    .accessibilityElement(children: .ignore).accessibilityLabel("Loading")
            } else {
                Text(state?.personalError ?? D.WATCHLIST_UNAVAILABLE).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
            }
        }
    }

    @ViewBuilder
    private func earnings(_ colors: StockColors) -> some View {
        if let rows = state?.personal?.earnings, !rows.isEmpty {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                sectionTitle(D.EARNINGS)
                VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.offset) { n, e in
                        if n > 0 { StockDivider() }
                        BriefEarningsRow(earnings: e) { if let id = e.eventId { onOpenEarnings(id) } else { onOpenStock(e.symbol) } }
                    }
                }
                caption(D.EARNINGS_DATES_NOTE, colors.textMeta)
            }
        }
    }

    /// The one tinted card: the concept title, the first sentence, details behind "Show details"; the full explanation in the sheet.
    private func conceptCard(_ brief: DailyBrief, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text(D.CONCEPT).font(StockStepsTheme.font(type.label)).foregroundStyle(colors.primaryText)
            Text(brief.concept.title).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textTitle).accessibilityAddTraits(.isHeader)
            StockShowDetails(explanation: brief.concept.explanation, hasMore: true, trailing: AnyView(
                linkButton(D.UNDERSTAND_CONCEPT, systemImage: "graduationcap", colors) { concept = client.term(id: brief.concept.learnTermId) }
            )) {
                if let example = brief.concept.example {
                    Text(D.conceptExample(example: example)).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody).fixedSize(horizontal: false, vertical: true)
                }
                caption(brief.concept.whyToday, colors.textSupporting)
                linkButton(D.OPEN_LEARN, systemImage: "arrow.right", colors, action: onLearn)
            }
        }
        // 5D.5.1c: a small bottom padding — the 48 pt action row already gives the space below the links.
        .padding([.horizontal, .top], CGFloat(space.cardPadding))
        .padding(.bottom, CGFloat(space.xs))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.educationContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
    }

    @ViewBuilder
    private func premium(_ colors: StockColors) -> some View {
        if let p = state?.personal {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                sectionTitle(D.PLUS)
                if let insights = p.premiumInsights {
                    ForEach(insights, id: \.self) { Text("• \($0)").font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody) }
                    if !p.companyStories.isEmpty {
                        Text(D.PLUS_STORIES).font(StockStepsTheme.font(type.label)).foregroundStyle(colors.textSupporting)
                        ForEach(p.companyStories, id: \.id) { s in
                            StockNavigationRow(title: s.headline, detail: s.relatedSymbols.first, hint: D.readArticleLabel(story: s)) {
                                if let url = URL(string: s.sourceUrl) { openURL(url) }
                            }
                        }
                    }
                    TextField(D.ASK_LABEL, text: $question, axis: .vertical).textFieldStyle(.roundedBorder).font(StockStepsTheme.font(type.body))
                        .onChange(of: question) { _, v in if v.count > 300 { question = String(v.prefix(300)) } }
                    Button(D.ASK) { presenter.ask(question: question) }.buttonStyle(.stockSecondary)
                        .disabled(question.trimmingCharacters(in: .whitespaces).count < 3 || state.flatMap { client.ai(state: $0, key: "ask") }?.loading == true)
                    if let ai = state.flatMap({ client.ai(state: $0, key: "ask") }) { aiAnswer(ai, colors) }
                    caption(D.ASK_NOTE, colors.textMeta)
                } else {
                    Text(D.PLUS_PREVIEW).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody).fixedSize(horizontal: false, vertical: true)
                    linkButton(D.PLUS_PREVIEW_ACTION, systemImage: "arrow.right", colors, action: onUpgrade)
                }
            }
        }
    }

    /// Loading layout in the reader's shape (meta, two headline lines, three index rows, two story rows); read as one "Loading".
    private var skeleton: some View {
        VStack(alignment: .leading, spacing: CGFloat(space.md)) {
            StockSkeleton(width: 160)
            StockSkeleton(width: 240, height: CGFloat(space.xl))
            StockSkeleton(width: 220, height: CGFloat(space.xl))
            ForEach(0..<3, id: \.self) { _ in HStack { StockSkeleton(width: 140); Spacer(); StockSkeleton(width: 64) } }
            ForEach(0..<2, id: \.self) { _ in VStack(alignment: .leading, spacing: CGFloat(space.xs)) { StockSkeleton(width: 120); StockSkeleton(width: 240) } }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(D.LOADING)
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

/// Previous briefs as navigation rows (sample briefs badged); the StockSteps+ remainder as one row (as Compose `DailyBriefHistoryScreen`).
struct DailyBriefHistoryScene: View {
    let model: BriefModel
    let onOpenBrief: (String) -> Void
    var onUpgrade: () -> Void = {}
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                if let history = model.state?.history {
                    if history.items.isEmpty { StockSectionMessage(message: D.NO_HISTORY) }
                    ForEach(Array(history.items.enumerated()), id: \.element.id) { n, s in
                        if n > 0 { StockDivider() }
                        StockNavigationRow(title: D.historyTitle(s: s), detail: D.historyDetail(s: s), hint: "Read this brief",
                                           badge: s.sampleData ? D.SAMPLE : nil, badgeKind: .sample, summary: D.historySummary(s: s)) { onOpenBrief(s.id) }
                    }
                    if history.lockedCount > 0 {
                        StockDivider()
                        StockNavigationRow(title: D.lockedHistory(count: history.lockedCount), hint: D.SEE_PLUS, badge: "StockSteps+", action: onUpgrade)
                    }
                } else {
                    VStack(alignment: .leading, spacing: CGFloat(space.md)) { ForEach(0..<3, id: \.self) { _ in StockSkeleton(width: 260) } }
                        .accessibilityElement(children: .ignore).accessibilityLabel("Loading")
                }
            }
            .frame(maxWidth: CGFloat(dims.contentMaxWidth))
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.vertical, CGFloat(space.md))
            .frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .navigationTitle(D.HISTORY)
        .navigationBarTitleDisplayMode(.inline)
        .taskOnce { model.presenter.loadHistory() }
    }
}
