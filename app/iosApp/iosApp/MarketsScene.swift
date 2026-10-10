import Observation
import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

/// Mirrors Android's MarketsViewModel: one overview request per load (the backend aggregates and
/// caches every section); tabs and "Show all" only re-slice it through the shared presenter.
@MainActor
@Observable
final class MarketsModel {
    private(set) var overview: MarketsOverview?
    private(set) var loading = true
    private(set) var error: String?
    private(set) var tab: MoversTab = .gainers
    private(set) var expanded = false
    @ObservationIgnored let client: IosMarketsClient
    @ObservationIgnored let detailsClient: IosCompanyDetailsClient
    @ObservationIgnored private var started = false
    @ObservationIgnored private var task: Task<Void, Never>?

    init(baseURL: @escaping () -> String = { BackendSettings.currentURL }) {
        client = IosMarketsClient(baseUrl: baseURL)
        detailsClient = IosCompanyDetailsClient(baseUrl: baseURL)
    }

    var model: MarketsUiModel? { overview.map { client.model(overview: $0, tab: tab, expanded: expanded) } }

    func start() {
        guard !started else { return }
        started = true
        load()
    }

    func select(_ tab: MoversTab) {
        guard tab != self.tab else { return }
        self.tab = tab
        expanded = false
    }

    func toggleExpanded() { expanded.toggle() }

    func load() {
        task?.cancel()
        loading = overview == nil
        error = nil
        task = Task { await fetch() }
    }

    /// Pull-to-refresh: keeps current content on screen until the new response arrives.
    func refresh() async {
        task?.cancel()
        await fetch()
    }

    /// The Mock/Real setting changed: drop the other backend's data.
    func reset() {
        overview = nil
        load()
    }

    private func fetch() async {
        do {
            let result = try await client.getOverview()
            if Task.isCancelled { return }
            overview = result
            error = nil
        } catch {
            if Task.isCancelled { return }
            self.error = "Market data isn't available right now."
        }
        loading = false
    }
}

/// Markets dashboard: session, indices, top movers, sector performance (ETF proxies), market news and a daily lesson.
struct MarketsScene: View {
    @Environment(\.colorScheme) private var scheme
    /// Not `@Environment(\.openURL)`: see `ExternalURLOpener` (that environment value re-rendered this scene in a loop once a destination was pushed).
    private let openURL = ExternalURLOpener()
    let model: MarketsModel
    let onOpenStock: (String) -> Void
    let onSearch: () -> Void
    var onDiscover: () -> Void = {}
    var onCompare: () -> Void = {}
    var onEarnings: () -> Void = {}
    /// Earnings Center counts (calendar data only; nil hides them).
    var earnings: EarningsModel? = nil
    var brief: BriefModel? = nil
    var onDailyBrief: () -> Void = {}
    var onBriefHistory: () -> Void = {}
    @State private var lesson: MarketLesson?
    /// Width of the indices card; the trend sparkline needs `TREND_MIN_ROW_WIDTH` so names keep their room.
    @State private var indexCardWidth: CGFloat = 0
    @State private var movementSymbol: String?

    private let present = MarketsScreenPresentation.shared
    @Environment(\.dynamicTypeSize) private var typeSize

    /// Data first (Phase 4B): title → market status → major indices → brief entry → movers → sectors → research tools → news → lesson.
    /// The brief and research tools stay available when the market overview itself fails.
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                title(colors).padding(.top, CGFloat(space.md))
                if let content = model.model {
                    header(content.header, session: model.overview?.session, colors).padding(.top, CGFloat(space.md))
                    indices(content, colors).padding(.top, CGFloat(space.sectionGap))
                } else if model.loading {
                    VStack(spacing: CGFloat(space.sm)) {
                        StockSkeleton(width: 1, height: CGFloat(dims.touchTarget) * 2).frame(maxWidth: .infinity)
                        ForEach(0..<5, id: \.self) { _ in StockRowSkeleton() }
                    }
                    .padding(.top, CGFloat(space.md)).accessibilityLabel("Loading")
                } else {
                    StockSectionMessage(message: model.error ?? "Market data isn't available right now.", actionTitle: "Try again", action: model.load)
                        .stockCard().padding(.top, CGFloat(space.md))
                }
                if let brief { briefEntry(brief, colors).padding(.top, CGFloat(space.sectionGap)) }
                if let content = model.model {
                    movers(content.movers, colors).padding(.top, CGFloat(space.sectionGap))
                    sectors(content.sectors, colors).padding(.top, CGFloat(space.sectionGap))
                }
                researchTools(colors).padding(.top, CGFloat(space.sectionGap))
                if let content = model.model {
                    news(content, colors).padding(.top, CGFloat(space.sectionGap))
                    StockInsightCard(title: content.lesson.title, message: content.lesson.body, tone: .education, actionTitle: "Learn more") {
                        lesson = content.lesson
                    }
                    .padding(.top, CGFloat(space.sectionGap))
                    Text("Educational information, not a recommendation to buy or sell.")
                        .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary).padding(.top, CGFloat(space.lg))
                }
            }
            .frame(maxWidth: CGFloat(dims.contentMaxWidth), alignment: .leading)
            .padding(.horizontal, CGFloat(space.screen))
            .padding(.bottom, CGFloat(space.xl))
            .frame(maxWidth: .infinity)
        }
        .refreshable { await model.refresh() }
        .background(colors.appBackground.ignoresSafeArea())
        .navigationDestination(item: $movementSymbol) { symbol in StockMovementScene(symbol: symbol, client: model.detailsClient) }
        .sheet(item: Binding(get: { lesson.map(LessonItem.init) }, set: { lesson = $0?.lesson })) { item in
            LessonSheet(lesson: item.lesson).presentationDetents([.medium, .large])
        }
        .task { model.start() }
    }

    private func title(_ colors: StockColors) -> some View {
        HStack(alignment: .center) {
            Text(present.TITLE).font(StockStepsTheme.font(type.screenTitle, relativeTo: .title2)).foregroundStyle(colors.textTitle)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: CGFloat(space.sm))
            Button(action: onSearch) {
                Image(systemName: "magnifyingglass").font(.title3).foregroundStyle(colors.textPrimary)
                    .frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
            }
            .accessibilityLabel("Search stocks")
        }
    }

    /// Compact US Market status card (Phase 4B.1): "US Market" + Sample badge, dot + short status in words (closed is neutral, not red),
    /// the calendar's next open/close line and one short footer; the full data notice is the footer's spoken label.
    private func header(_ header: MarketHeaderModel, session: MarketSession?, _ colors: StockColors) -> some View {
        let dot: Color = switch present.statusDot(tone: header.tone) {
        case .open: colors.positive
        case .extended: colors.warning
        default: colors.textDisabled
        }
        let indent = CGFloat(space.sm + space.sm)
        return VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            StockSectionHeader(title: present.STATUS_TITLE) {
                if header.sampleData { StockStatusBadge(text: "Sample", kind: .sample, size: .compact) }
            }
            HStack(spacing: CGFloat(space.sm)) {
                Circle().fill(dot).frame(width: CGFloat(space.sm), height: CGFloat(space.sm)).accessibilityHidden(true)
                Text(session.map { present.statusLabel(session: $0) } ?? header.statusLabel)
                    .font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle)
            }
            if let line = session.flatMap({ present.sessionLine(session: $0) }) ?? header.detail {
                Text(line).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textSupporting)
                    .padding(.leading, indent)
            }
            if let footer = present.statusFooter(header: header) {
                Text(footer).font(StockStepsTheme.font(type.caption, relativeTo: .caption1))
                    .foregroundStyle(header.sampleData ? colors.cautionText : colors.textMeta)
                    .padding(.leading, indent)
                    .accessibilityLabel(present.statusFooterDescription(header: header))
            }
        }
        .stockCard()
    }

    /// Major indices as one grouped card of rows: name and source quote time left, value and signed change right; stacked at large text.
    @ViewBuilder
    private func indices(_ content: MarketsUiModel, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: "Major indices")
            let indices = present.usIndices(cards: content.indices, quotes: model.overview?.indices ?? [])
            if content.indicesFailed || indices.isEmpty {
                StockSectionMessage(message: "Market indices aren't available right now.", actionTitle: "Try again", action: model.load).stockCard()
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(indices.enumerated()), id: \.element.id) { n, card in
                        if n > 0 { StockDivider().padding(.leading, CGFloat(space.cardPadding)) }
                        Button { lesson = model.client.indexLesson(id: card.id) } label: { indexRow(card, colors) }
                            .buttonStyle(.plain)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel(card.accessibilityLabel)
                            .accessibilityHint(present.INDEX_HINT)
                    }
                }
                .padding(.vertical, CGFloat(space.xxs))
                .stockCard(padding: 0)
                .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { indexCardWidth = $0 }
            }
        }
    }

    private func indexRow(_ card: IndexCardModel, _ colors: StockColors) -> some View {
        let stacked = typeSize >= .xxxLarge
        let name = VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            Text(card.name).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle).multilineTextAlignment(.leading)
            if let meta = present.indexMeta(card: card) {
                Text(meta).font(StockStepsTheme.font(type.caption, relativeTo: .caption1))
                    .foregroundStyle(card.proxyLabel != nil ? colors.cautionText : colors.textMeta)
            }
        }
        let values = VStack(alignment: stacked ? .leading : .trailing, spacing: CGFloat(space.xxs)) {
            Text(card.value).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .subheadline)).foregroundStyle(colors.textValue).monospacedDigit()
            if card.available {
                StockPriceChange(percentage: present.indexChange(card: card), direction: card.direction, lineLimit: nil)
            } else {
                Text(present.INDEX_UNAVAILABLE).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
            }
        }
        let trend = Group {
            if present.showTrend(cardWidth: Float(indexCardWidth), card: card) {
                StockSparkline(closes: card.trend.map { $0.doubleValue }, direction: card.direction)
                    .frame(width: CGFloat(dims.sparklineWidth), height: CGFloat(dims.sparklineHeight))
            }
        }
        return Group {
            if stacked {
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) { name; values; trend }
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                HStack(spacing: CGFloat(space.md)) {
                    name.frame(maxWidth: .infinity, alignment: .leading)
                    trend
                    values.fixedSize(horizontal: true, vertical: false)
                }
            }
        }
        .padding(.horizontal, CGFloat(space.cardPadding))
        .padding(.vertical, CGFloat(space.sm))
        .frame(minHeight: CGFloat(dims.rowCompactMinHeight))
        .contentShape(Rectangle())
    }

    /// The Daily Market Brief as one entry row (no summary sentence: the index rows already show those moves) + Previous briefs.
    private func briefEntry(_ brief: BriefModel, _ colors: StockColors) -> some View {
        let state = brief.state
        let home = HomeDashboardPresentation.shared
        return VStack(alignment: .leading, spacing: 0) {
            if let latest = state?.latest {
                let now = brief.client.now()
                let caution = brief.client.isStale(brief: latest) || state?.offline == true
                StockNavigationRow(title: "Your Daily Market Brief",
                                   detail: present.briefDetail(brief: latest, nowMillis: now),
                                   icon: "newspaper", detailColor: caution ? colors.cautionText : colors.textMeta,
                                   secondaryDetail: present.briefLabels(brief: latest, offline: state?.offline == true),
                                   hint: home.briefAction(brief: latest, nowMillis: now), action: onDailyBrief)
                StockDivider()
                Button("Previous briefs", action: onBriefHistory)
                    .font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.primaryText)
                    .frame(maxWidth: .infinity, minHeight: CGFloat(dims.touchTarget), alignment: .leading)
            } else if state?.loading != false {
                ProgressView().frame(maxWidth: .infinity).padding(.vertical, CGFloat(space.md)).accessibilityLabel("Loading the Daily Market Brief")
            } else {
                Text(state?.error ?? "The brief isn't available right now.").font(StockStepsTheme.font(type.small))
                    .foregroundStyle(colors.textSupporting).padding(.vertical, CGFloat(space.md))
            }
        }
        .padding(.horizontal, CGFloat(space.cardPadding)).padding(.vertical, CGFloat(space.xxs))
        .stockCard(padding: 0)
    }

    /// Research tools as one grouped card of navigation rows; the calendar row shows real counts only.
    private func researchTools(_ colors: StockColors) -> some View {
        let summary = earnings?.summary
        return VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: "Research tools")
            VStack(spacing: 0) {
                StockNavigationRow(title: present.DISCOVER.title, detail: present.DISCOVER.detail, icon: "magnifyingglass",
                                   hint: "Opens \(present.DISCOVER.title)", action: onDiscover)
                StockDivider()
                StockNavigationRow(title: present.COMPARE.title, detail: present.COMPARE.detail, icon: "square.split.2x1",
                                   hint: "Opens \(present.COMPARE.title)", action: onCompare)
                StockDivider()
                StockNavigationRow(title: present.EARNINGS_TITLE, detail: present.earningsDetail(summary: summary), icon: "calendar",
                                   secondaryDetail: present.earningsSecondary(summary: summary), hint: "Opens the Earnings Calendar", action: onEarnings)
            }
            .padding(.horizontal, CGFloat(space.cardPadding)).padding(.vertical, CGFloat(space.xxs))
            .stockCard(padding: 0)
        }
        .task { earnings?.startSummary() }
    }

    private func movers(_ movers: MoversModel, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: "Top Movers")
            StockPillSelector(options: MoversTab.entries, selected: movers.tab, label: { $0.label }) { model.select($0) }
            VStack(spacing: 0) {
                if movers.failed {
                    StockSectionMessage(message: "This section couldn't be loaded.", actionTitle: "Try again", action: model.load).padding(CGFloat(space.md))
                } else if let empty = movers.emptyMessage {
                    StockSectionMessage(message: empty).padding(CGFloat(space.md))
                } else {
                    ForEach(Array(movers.rows.enumerated()), id: \.element.row.symbol) { index, mover in
                        if index > 0 { StockDivider().padding(.leading, CGFloat(space.cardPadding)) }
                        moverRow(mover, tab: movers.tab, colors)
                    }
                }
            }
            .padding(.vertical, CGFloat(space.xxs))
            .stockCard(padding: 0)
            Text("\(movers.rankedBy). \(movers.universe).").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
            if !movers.failed && movers.total > MarketsPresenter.shared.PREVIEW_ROWS {
                Button(movers.expanded ? "Show fewer" : "Show all \(movers.total)", action: model.toggleExpanded)
                    .font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.primaryText)
                    .frame(minHeight: CGFloat(dims.touchTarget))
            }
        }
    }

    private func moverRow(_ mover: MoverRowModel, tab: MoversTab, _ colors: StockColors) -> some View {
        let row = mover.row
        return HStack(spacing: 0) {
            StockRow(symbol: row.symbol, name: row.name, logoUrl: row.logoUrl, action: { onOpenStock(row.symbol) }) {
                if let price = row.price {
                    Text(price).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .subheadline)).foregroundStyle(colors.textValue).lineLimit(1)
                }
                StockPriceChange(percentage: row.change, direction: row.direction)
                if tab == .mostActive, let volume = mover.volume {
                    Text(volume).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSupporting)
                }
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel(mover.accessibilityLabel)
            if tab != .mostActive && row.direction != .unavailable {
                Button { movementSymbol = row.symbol } label: {
                    Image(systemName: "questionmark.circle").foregroundStyle(colors.iconSecondary)
                        .frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
                }
                .accessibilityLabel("Why did \(row.symbol) move?")
            }
        }
    }

    private func sectors(_ sectors: SectorsModel, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: "Sector Performance") {
                Button { lesson = present.methodologyLesson(sectors: sectors) } label: {
                    Image(systemName: "info.circle").foregroundStyle(colors.iconSecondary)
                        .frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
                }
                .accessibilityLabel(present.SECTORS_INFO)
            }
            VStack(spacing: 0) {
                if sectors.failed {
                    StockSectionMessage(message: "This section couldn't be loaded.", actionTitle: "Try again", action: model.load).padding(.vertical, CGFloat(space.sm))
                } else if sectors.rows.isEmpty {
                    StockSectionMessage(message: "Sector data isn't available right now.").padding(.vertical, CGFloat(space.sm))
                } else {
                    ForEach(sectors.rows, id: \.symbol) { row in
                        Button { lesson = model.client.sectorLesson(sector: row.sector, symbol: row.symbol, methodology: sectors.methodology) } label: {
                            sectorRow(row, colors)
                        }
                        .buttonStyle(.plain)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(row.accessibilityLabel)
                        .accessibilityHint("Explains this sector")
                    }
                }
            }
            .padding(.horizontal, CGFloat(space.cardPadding)).padding(.vertical, CGFloat(space.xs))
            .stockCard(padding: 0)
            Text(present.sectorsCaption(sectors: sectors)).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textMeta)
        }
    }

    /// Sector name (wraps) — bar — signed change. At large text the bar and change move under the name so neither is squeezed.
    private func sectorRow(_ row: SectorRowModel, _ colors: StockColors) -> some View {
        let fill: Color = row.direction == .up ? colors.positive : row.direction == .down ? colors.negative : colors.textDisabled
        let bar = GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(colors.surfaceSecondary)
                Capsule().fill(fill).frame(width: proxy.size.width * CGFloat(row.fraction))
            }
        }
        .frame(height: CGFloat(space.sm))
        let name = Text(row.sector).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
        return Group {
            if typeSize >= .xxxLarge {
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                    name
                    HStack(spacing: CGFloat(space.sm)) { bar; StockPriceChange(percentage: row.change, direction: row.direction).fixedSize() }
                }
            } else {
                HStack(spacing: CGFloat(space.sm)) {
                    name.frame(maxWidth: .infinity, alignment: .leading)
                    bar.frame(maxWidth: .infinity)
                    StockPriceChange(percentage: row.change, direction: row.direction).fixedSize()
                }
            }
        }
        .padding(.vertical, typeSize >= .xxxLarge ? CGFloat(space.xs) : 0)
        .frame(minHeight: CGFloat(dims.touchTarget))
        .contentShape(Rectangle())
    }

    private func news(_ content: MarketsUiModel, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            StockSectionHeader(title: "Market News")
            VStack(spacing: 0) {
                if content.newsFailed {
                    StockSectionMessage(message: "News isn't available right now.", actionTitle: "Try again", action: model.load).padding(.vertical, CGFloat(space.sm))
                } else if content.news.isEmpty {
                    StockSectionMessage(message: "No market news right now.").padding(.vertical, CGFloat(space.sm))
                } else {
                    ForEach(Array(content.news.enumerated()), id: \.element.id) { index, article in
                        if index > 0 { StockDivider() }
                        StockNewsCard(model: article, onOpen: { openURL($0) })
                    }
                }
            }
            .padding(.horizontal, CGFloat(space.md))
            .stockCard(padding: 0)
        }
    }
}

private struct LessonItem: Identifiable {
    let lesson: MarketLesson
    var id: String { lesson.id }
}

private struct LessonSheet: View {
    @Environment(\.colorScheme) private var scheme
    let lesson: MarketLesson
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
                Text(lesson.title).font(StockStepsTheme.font(type.sectionTitle, relativeTo: .title3)).foregroundStyle(colors.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                Text(lesson.body).font(StockStepsTheme.font(type.body)).foregroundStyle(colors.textBody)
                ForEach(lesson.more, id: \.self) { point in
                    Text("• \(point)").font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
                }
            }
            .padding(CGFloat(space.screen))
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(colors.surface.ignoresSafeArea())
    }
}
