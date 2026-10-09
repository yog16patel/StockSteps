import Observation
import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

// MARK: - Company News

/// Mirrors Android's CompanyNewsViewModel: one feed request (filters re-slice it through the shared
/// presenter) and today's computed move in parallel. Explanations load only when opened.
@MainActor
@Observable
final class CompanyNewsViewModel {
    let symbol: String
    private(set) var feed: SectionState<[NewsArticle]> = .loading
    private(set) var preview: MovementPreview?
    var filter: NewsFilter = .all
    @ObservationIgnored let client: IosCompanyDetailsClient
    @ObservationIgnored private var started = false

    init(symbol: String, client: IosCompanyDetailsClient) {
        self.symbol = symbol
        self.client = client
    }

    var model: CompanyNewsFeedModel? { feed.value.map { client.newsFeedModel(articles: $0, filter: filter) } }

    func start() {
        guard !started else { return }
        started = true
        load()
    }

    func load() {
        feed = .loading
        Task { feed = (try? await client.getNewsFeed(symbol: symbol)).map(SectionState.content) ?? .unavailable }
        // Today's move is optional context: a failure simply hides the card.
        Task { preview = (try? await client.getMovementPreview(symbol: symbol)) ?? nil }
    }
}

struct CompanyNewsScene: View {
    @Environment(\.colorScheme) private var scheme
    /// Not `@Environment(\.openURL)`: see `ExternalURLOpener` (that environment value re-rendered this scene in a loop once a destination was pushed).
    private let openURL = ExternalURLOpener()
    @State private var model: CompanyNewsViewModel
    @State private var insightArticle: String?
    @State private var showMovement = false

    init(symbol: String, client: IosCompanyDetailsClient) {
        _model = State(initialValue: CompanyNewsViewModel(symbol: symbol, client: client))
    }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                if let preview = model.preview {
                    MovementPreviewCard(preview: preview) { showMovement = true }
                }
                switch model.feed {
                case .loading:
                    ForEach(0..<3, id: \.self) { _ in StockNewsCardSkeleton() }
                case .unavailable:
                    StockSectionMessage(message: "We couldn't load company news.", actionTitle: "Try again", action: model.load)
                        .stockCard(padding: CGFloat(space.md), bordered: false)
                case .content:
                    if let feed = model.model { feedContent(feed) }
                }
                Text("Educational information, not a recommendation to buy or sell.")
                    .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
                    .padding(.top, CGFloat(space.md))
            }
            .frame(maxWidth: CGFloat(dims.contentMaxWidth), alignment: .leading)
            .padding(CGFloat(space.screen))
            .frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .navigationTitle("Company news")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(item: $insightArticle) { id in NewsInsightScene(symbol: model.symbol, articleId: id, client: model.client) }
        .navigationDestination(isPresented: $showMovement) { StockMovementScene(symbol: model.symbol, client: model.client) }
        .task { model.start() }
    }

    @ViewBuilder
    private func feedContent(_ feed: CompanyNewsFeedModel) -> some View {
        if feed.filters.count > 1 {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: CGFloat(space.xs)) {
                    ForEach(feed.filters, id: \.filter) { option in
                        StockChip(title: "\(option.label) \(option.count)", selected: option.filter == feed.selected) { model.filter = option.filter }
                    }
                }
            }
        }
        if let empty = feed.emptyMessage {
            StockSectionMessage(message: empty).stockCard(padding: CGFloat(space.md), bordered: false)
        }
        let learnAt = min(3, feed.items.count)
        ForEach(Array(feed.items.enumerated()), id: \.element.news.id) { index, item in
            if index == learnAt && !feed.learnTerms.isEmpty { LearnTermsCard(terms: feed.learnTerms) }
            CompanyNewsCard(item: item, onOpen: { openURL($0) }, onExplain: { insightArticle = $0 })
        }
        if learnAt == feed.items.count && !feed.learnTerms.isEmpty && !feed.items.isEmpty { LearnTermsCard(terms: feed.learnTerms) }
    }
}

/// "Today's move" summary linking to the full Why Did It Move? screen.
struct MovementPreviewCard: View {
    @Environment(\.colorScheme) private var scheme
    let preview: MovementPreview
    let onOpen: () -> Void
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        Button(action: onOpen) {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                HStack {
                    Text("Today's move").font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary)
                        .accessibilityAddTraits(.isHeader)
                    Spacer(minLength: 0)
                    StockPriceChange(percentage: preview.change, direction: preview.direction)
                }
                if let session = preview.sessionLabel {
                    Text(session).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
                }
                if preview.noConfirmedCatalyst { StockBadge(text: MovementPresenter.shared.NO_CATALYST_TITLE, tone: .neutral) }
                Text(preview.summary).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
                    .lineLimit(3).multilineTextAlignment(.leading)
                Text("See why it moved →").font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.primaryText)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .stockCard(padding: CGFloat(space.md), bordered: false)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint("Opens why the stock moved")
    }
}

/// Category, source and time, headline, summary and thumbnail; "Explain this" opens the explanation.
private struct CompanyNewsCard: View {
    @Environment(\.colorScheme) private var scheme
    let item: CompanyNewsItem
    let onOpen: (URL) -> Void
    let onExplain: (String) -> Void
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let news = item.news
        let url = news.url.flatMap(URL.init(string:))
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Button { if let url { onOpen(url) } } label: {
                HStack(alignment: .top, spacing: CGFloat(space.md)) {
                    VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                        HStack(spacing: CGFloat(space.sm)) {
                            if let category = item.categoryLabel { StockBadge(text: category, tone: item.categoryTone) }
                            let meta = [news.source, news.publishedLabel].compactMap { $0 }.joined(separator: " · ")
                            if !meta.isEmpty {
                                Text(meta).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary).lineLimit(1)
                            }
                        }
                        Text(news.headline).font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(colors.textPrimary).multilineTextAlignment(.leading)
                        if let summary = news.summary {
                            Text(summary).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
                                .lineLimit(3).multilineTextAlignment(.leading)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    if let image = news.imageUrl.flatMap(URL.init(string:)) {
                        AsyncImage(url: image) { $0.resizable().scaledToFill() } placeholder: { colors.surfaceSecondary }
                            .frame(width: CGFloat(dims.newsThumbnail), height: CGFloat(dims.newsThumbnail))
                            .clipShape(RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.chip)))
                            .accessibilityHidden(true)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(url == nil)
            .accessibilityElement(children: .combine)
            .accessibilityHint(url == nil ? "" : "Opens the original article")
            if let id = item.articleId {
                Button { onExplain(id) } label: {
                    Label("Explain this", systemImage: "lightbulb.fill")
                        .font(StockStepsTheme.font(type.label, relativeTo: .footnote))
                        .foregroundStyle(colors.primaryText)
                        .frame(minHeight: CGFloat(dims.touchTarget))
                }
                .buttonStyle(.plain)
                .accessibilityHint("Opens a simple explanation of this article")
            }
        }
        .stockCard(padding: CGFloat(space.md), bordered: false)
    }
}

/// Deterministic glossary terms ("Learn as you read" / "Terms to know").
struct LearnTermsCard: View {
    @Environment(\.colorScheme) private var scheme
    let terms: [GlossaryTerm]
    var title = "Learn as you read"
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            Label(title, systemImage: "lightbulb.fill")
                .font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
                .accessibilityAddTraits(.isHeader)
            ForEach(terms, id: \.term) { term in
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(term.term).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.educationAccent)
                    Text(term.definition).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
                }
                .accessibilityElement(children: .combine)
            }
        }
        .padding(CGFloat(space.md))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.educationContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
    }
}

// MARK: - Article explanation

@MainActor
@Observable
final class NewsInsightViewModel {
    let symbol: String
    let articleId: String
    /// `.content(nil)` when the article left the company's recent feed.
    private(set) var insight: SectionState<ArticleInsightModel?> = .loading
    @ObservationIgnored private let client: IosCompanyDetailsClient
    @ObservationIgnored private var started = false

    init(symbol: String, articleId: String, client: IosCompanyDetailsClient) {
        self.symbol = symbol
        self.articleId = articleId
        self.client = client
    }

    func start() {
        guard !started else { return }
        started = true
        load()
    }

    func load() {
        insight = .loading
        Task {
            do { insight = .content(try await client.getArticleInsight(symbol: symbol, articleId: articleId)) } catch { insight = .unavailable }
        }
    }
}

/// "Explained simply": what happened, why it could matter, what to watch, terms, source and limits.
struct NewsInsightScene: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.openURL) private var openURL
    @State private var model: NewsInsightViewModel

    init(symbol: String, articleId: String, client: IosCompanyDetailsClient) {
        _model = State(initialValue: NewsInsightViewModel(symbol: symbol, articleId: articleId, client: client))
    }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                switch model.insight {
                case .loading:
                    VStack(spacing: CGFloat(space.sm)) {
                        ForEach(0..<3, id: \.self) { _ in StockSkeleton(width: 1, height: CGFloat(dims.touchTarget) * 2).frame(maxWidth: .infinity) }
                    }
                    .padding(.top, CGFloat(space.md)).accessibilityLabel("Loading")
                case .unavailable:
                    StockSectionMessage(message: "We couldn't load this explanation.", actionTitle: "Try again", action: model.load)
                        .stockCard(padding: CGFloat(space.md), bordered: false).padding(.top, CGFloat(space.md))
                case .content(let insight):
                    if let insight { content(insight, colors) } else {
                        StockSectionMessage(message: "This article is no longer in the company's recent news.")
                            .stockCard(padding: CGFloat(space.md), bordered: false).padding(.top, CGFloat(space.md))
                    }
                }
                Text("Educational information, not a recommendation to buy or sell.")
                    .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary).padding(.top, CGFloat(space.lg))
            }
            .frame(maxWidth: CGFloat(dims.contentMaxWidth), alignment: .leading)
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.bottom, CGFloat(space.xl))
            .frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .navigationTitle("Explained simply")
        .navigationBarTitleDisplayMode(.inline)
        .task { model.start() }
    }

    @ViewBuilder
    private func content(_ insight: ArticleInsightModel, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            if let meta = insight.sources.first?.meta {
                Text(meta).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
            }
            if let headline = insight.headline {
                Text(headline).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
                    .accessibilityAddTraits(.isHeader)
            }
        }
        .padding(.top, CGFloat(space.sm))
        if insight.available {
            ProvenanceNote(text: insight.provenance).padding(.top, CGFloat(space.sm))
            InsightSection(title: "What happened", lines: [insight.summary ?? ""], bullets: false)
            if !insight.whyItMatters.isEmpty { InsightSection(title: "Why it could matter", lines: insight.whyItMatters) }
            if !insight.watchNext.isEmpty { InsightSection(title: "What to watch next", lines: insight.watchNext) }
        } else {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                Text("Explanation not available").font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                Text(insight.unavailableMessage ?? "").font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .stockCard(padding: CGFloat(space.md), bordered: false)
            .padding(.top, CGFloat(space.lg))
        }
        if !insight.terms.isEmpty { LearnTermsCard(terms: insight.terms, title: "Terms to know").padding(.top, CGFloat(space.lg)) }
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: "Source")
            ForEach(Array(insight.sources.enumerated()), id: \.offset) { _, source in
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(source.title).font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(colors.textPrimary)
                    if let meta = source.meta {
                        Text(meta).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
                    }
                    if let url = source.url.flatMap(URL.init(string:)) {
                        Button("Read the original article") { openURL(url) }
                            .font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(colors.primaryText)
                            .frame(minHeight: CGFloat(dims.touchTarget))
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .stockCard(padding: CGFloat(space.md), bordered: false)
            }
        }
        .padding(.top, CGFloat(space.lg))
        if insight.available && !insight.limitations.isEmpty {
            InsightSection(title: "Limits of this explanation", lines: insight.limitations, subdued: true)
        }
    }
}

/// Where the text came from (AI or template) and what it was based on.
struct ProvenanceNote: View {
    @Environment(\.colorScheme) private var scheme
    let text: String
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        HStack(alignment: .top, spacing: CGFloat(space.sm)) {
            Image(systemName: "info.circle.fill").foregroundStyle(colors.primaryText).accessibilityHidden(true)
            Text(text).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
        }
        .padding(CGFloat(space.sm))
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(colors.primaryContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
        .accessibilityElement(children: .combine)
    }
}

/// Titled card with a paragraph or bullet list.
struct InsightSection: View {
    @Environment(\.colorScheme) private var scheme
    let title: String
    let lines: [String]
    var bullets = true
    var subdued = false
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: title)
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                    Text(bullets ? "• \(line)" : line)
                        .font(StockStepsTheme.font(subdued ? type.small : type.body, relativeTo: subdued ? .subheadline : .body))
                        .foregroundStyle(subdued ? colors.textSecondary : colors.textBody)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .stockCard(padding: CGFloat(space.md), bordered: false)
        }
        .padding(.top, CGFloat(space.lg))
    }
}

// MARK: - Why did it move?

@MainActor
@Observable
final class StockMovementViewModel {
    let symbol: String
    private(set) var period: MovementPeriod = .oneDay
    /// One result per period; switching back to a loaded period doesn't request it again.
    private(set) var results: [MovementPeriod: SectionState<MovementModel?>] = [:]
    @ObservationIgnored private let client: IosCompanyDetailsClient
    @ObservationIgnored private var started = false

    init(symbol: String, client: IosCompanyDetailsClient) {
        self.symbol = symbol
        self.client = client
    }

    var current: SectionState<MovementModel?> { results[period] ?? .loading }

    func start() {
        guard !started else { return }
        started = true
        load(.oneDay)
    }

    func select(_ period: MovementPeriod) {
        self.period = period
        if results[period]?.value == nil { load(period) }
    }

    func retry() { load(period) }

    private func load(_ period: MovementPeriod) {
        results[period] = .loading
        Task {
            do { results[period] = .content(try await client.getMovement(symbol: symbol, period: period)) } catch { results[period] = .unavailable }
        }
    }
}

/// Why did it move?: computed move and benchmarks, what we know, time-aligned news with evidence
/// labels, what we can't confirm, and the "No confirmed catalyst" state.
struct StockMovementScene: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.openURL) private var openURL
    @State private var model: StockMovementViewModel

    init(symbol: String, client: IosCompanyDetailsClient) {
        _model = State(initialValue: StockMovementViewModel(symbol: symbol, client: client))
    }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                StockPillSelector(options: MovementPeriod.entries, selected: model.period, label: { $0.label }) { model.select($0) }
                switch model.current {
                case .loading:
                    VStack(spacing: CGFloat(space.sm)) {
                        ForEach(0..<3, id: \.self) { _ in StockSkeleton(width: 1, height: CGFloat(dims.touchTarget) * 2).frame(maxWidth: .infinity) }
                    }
                    .padding(.top, CGFloat(space.md)).accessibilityLabel("Loading")
                case .unavailable:
                    StockSectionMessage(message: "We couldn't load this explanation.", actionTitle: "Try again", action: model.retry)
                        .stockCard(padding: CGFloat(space.md), bordered: false).padding(.top, CGFloat(space.md))
                case .content(let movement):
                    if let movement { content(movement, colors) } else {
                        StockSectionMessage(message: "Price data for this period isn't available yet.")
                            .stockCard(padding: CGFloat(space.md), bordered: false).padding(.top, CGFloat(space.md))
                    }
                }
                Text("Educational information, not a recommendation to buy or sell.")
                    .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary).padding(.top, CGFloat(space.lg))
            }
            .frame(maxWidth: CGFloat(dims.contentMaxWidth), alignment: .leading)
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.bottom, CGFloat(space.xl))
            .frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .navigationTitle("Why did \(model.symbol) move?")
        .navigationBarTitleDisplayMode(.inline)
        .task { model.start() }
    }

    @ViewBuilder
    private func content(_ movement: MovementModel, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            if let session = movement.sessionLabel {
                Text(session).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary)
            }
            StockPriceChange(percentage: [movement.amount, movement.change].compactMap { $0 }.joined(separator: " "), direction: movement.direction, style: type.largeNumber)
            if let prices = movement.priceLine {
                Text(prices).font(StockStepsTheme.font(type.numberLabel, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
            }
            if let relative = movement.relativeToMarket { StockBadge(text: relative, tone: .neutral) }
            StockDivider()
            Text(movement.summary).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
            ProvenanceNote(text: movement.provenance)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .stockCard(padding: CGFloat(space.lg), bordered: false)
        .padding(.top, CGFloat(space.md))
        if movement.noConfirmedCatalyst {
            HStack(alignment: .top, spacing: CGFloat(space.sm)) {
                Image(systemName: "questionmark.circle.fill").foregroundStyle(colors.educationAccent).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text(MovementPresenter.shared.NO_CATALYST_TITLE).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textPrimary)
                        .accessibilityAddTraits(.isHeader)
                    Text(MovementPresenter.shared.NO_CATALYST_BODY).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
                }
            }
            .padding(CGFloat(space.md))
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(colors.educationContainer, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
            .accessibilityElement(children: .combine)
            .padding(.top, CGFloat(space.lg))
        }
        InsightSection(title: "What we know", lines: movement.whatWeKnow)
        if !movement.benchmarks.isEmpty {
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                StockSectionHeader(title: "Compared with the market")
                VStack(spacing: 0) {
                    benchmarkRow(model.symbol, movement.change, movement.direction, colors, emphasized: true)
                    ForEach(Array(movement.benchmarks.enumerated()), id: \.offset) { _, row in
                        StockDivider()
                        benchmarkRow(row.name, row.change, row.direction, colors)
                    }
                }
                .padding(.horizontal, CGFloat(space.md)).stockCard(padding: 0, bordered: false)
            }
            .padding(.top, CGFloat(space.lg))
        }
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: "News in this period")
            if movement.events.isEmpty {
                StockSectionMessage(message: "No company news was found for this period.").stockCard(padding: CGFloat(space.md), bordered: false)
            } else {
                ForEach(movement.events, id: \.articleId) { event in eventCard(event, colors) }
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                    Text("What the labels mean").font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary)
                    ForEach(Array(movement.labelGuide.enumerated()), id: \.offset) { _, entry in
                        Text("\(entry.first ?? ""): \(entry.second ?? "")")
                            .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
                    }
                }
                .padding(.top, CGFloat(space.xs))
            }
        }
        .padding(.top, CGFloat(space.lg))
        InsightSection(title: "What we can't confirm", lines: movement.whatWeCannotConfirm)
        if !movement.limitations.isEmpty { InsightSection(title: "Limitations", lines: movement.limitations, subdued: true) }
    }

    private func benchmarkRow(_ name: String, _ change: String, _ direction: PriceDirection, _ colors: StockColors, emphasized: Bool = false) -> some View {
        HStack {
            Text(name).font(StockStepsTheme.font(emphasized ? type.bodyMedium : type.body)).foregroundStyle(emphasized ? colors.textPrimary : colors.textBody)
            Spacer(minLength: CGFloat(space.sm))
            StockPriceChange(percentage: change, direction: direction)
        }
        .padding(.vertical, CGFloat(space.sm))
        .frame(minHeight: CGFloat(dims.touchTarget))
        .accessibilityElement(children: .combine)
    }

    private func eventCard(_ event: MovementEventRow, _ colors: StockColors) -> some View {
        let url = event.url.flatMap(URL.init(string:))
        return Button { if let url { openURL(url) } } label: {
            VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                StockBadge(text: event.label, tone: event.tone)
                Text(event.title).font(StockStepsTheme.font(type.bodyMedium)).foregroundStyle(colors.textPrimary).multilineTextAlignment(.leading)
                if let meta = event.meta {
                    Text(meta).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .stockCard(padding: CGFloat(space.md), bordered: false)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(url == nil)
        .accessibilityElement(children: .combine)
        .accessibilityHint(url == nil ? "" : "Opens the original article")
    }
}
