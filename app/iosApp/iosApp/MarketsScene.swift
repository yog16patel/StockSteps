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
    @Environment(\.openURL) private var openURL
    let model: MarketsModel
    let onOpenStock: (String) -> Void
    let onSearch: () -> Void
    var onDiscover: () -> Void = {}
    var onCompare: () -> Void = {}
    var onEarnings: () -> Void = {}
    @State private var lesson: MarketLesson?
    @State private var movementSymbol: String?

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                title(colors).padding(.top, CGFloat(space.lg))
                researchTools(colors).padding(.top, CGFloat(space.md))
                if let content = model.model {
                    header(content.header, colors).padding(.top, CGFloat(space.sm))
                    indices(content, colors).padding(.top, CGFloat(space.lg))
                    movers(content.movers, colors).padding(.top, CGFloat(space.xl))
                    sectors(content.sectors, colors).padding(.top, CGFloat(space.xl))
                    news(content, colors).padding(.top, CGFloat(space.xl))
                    StockInsightCard(title: content.lesson.title, message: content.lesson.body, tone: .education, actionTitle: "Learn more") {
                        lesson = content.lesson
                    }
                    .padding(.top, CGFloat(space.xl))
                    Text("Educational information, not a recommendation to buy or sell.")
                        .font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary).padding(.top, CGFloat(space.lg))
                } else if model.loading {
                    VStack(spacing: CGFloat(space.sm)) {
                        HStack(spacing: CGFloat(space.sm)) {
                            ForEach(0..<2, id: \.self) { _ in StockSkeleton(width: 1, height: CGFloat(dims.touchTarget) * 2).frame(maxWidth: .infinity) }
                        }
                        ForEach(0..<5, id: \.self) { _ in StockRowSkeleton() }
                    }
                    .padding(.top, CGFloat(space.lg)).accessibilityLabel("Loading")
                } else {
                    StockSectionMessage(message: model.error ?? "Market data isn't available right now.", actionTitle: "Try again", action: model.load)
                        .stockCard(padding: CGFloat(space.md), bordered: false).padding(.top, CGFloat(space.lg))
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

    /// Market Overview (this screen) plus entry points to Discover Stocks and Compare Stocks.
    private func researchTools(_ colors: StockColors) -> some View {
        VStack(spacing: CGFloat(space.sm)) {
            HStack(spacing: CGFloat(space.sm)) {
                tool("Discover Stocks", "Find companies by growth, dividends, strength or valuation", "magnifyingglass", colors, onDiscover)
                tool("Compare Stocks", "See 2–4 companies side by side", "square.split.2x1", colors, onCompare)
            }
            tool("Earnings Center", "Upcoming dates, recent results and the companies you follow", "calendar", colors, onEarnings)
        }
    }

    private func tool(_ title: String, _ subtitle: String, _ icon: String, _ colors: StockColors, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                Image(systemName: icon).foregroundStyle(colors.primary)
                Text(title).font(.headline).foregroundStyle(colors.textPrimary)
                Text(subtitle).font(.caption).foregroundStyle(colors.textSecondary).multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, minHeight: 88, alignment: .topLeading)
            .stockCard()
        }
        .buttonStyle(.plain)
        .accessibilityHint("Opens \(title)")
    }

    private func title(_ colors: StockColors) -> some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                Text("Explore Markets").font(StockStepsTheme.font(type.screenTitle, relativeTo: .title2)).foregroundStyle(colors.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                Text("Discover what's happening in the market.").font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textSecondary)
            }
            Spacer(minLength: CGFloat(space.sm))
            Button(action: onSearch) {
                Image(systemName: "magnifyingglass").font(.title3).foregroundStyle(colors.textPrimary)
                    .frame(width: CGFloat(dims.touchTarget), height: CGFloat(dims.touchTarget))
            }
            .accessibilityLabel("Search stocks")
        }
    }

    private func header(_ header: MarketHeaderModel, _ colors: StockColors) -> some View {
        let dot: Color = switch header.tone {
        case .open: colors.positive
        case .extended: colors.warning
        case .closed: colors.negative
        default: colors.textDisabled
        }
        return VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            HStack(spacing: CGFloat(space.sm)) {
                Circle().fill(dot).frame(width: CGFloat(space.sm), height: CGFloat(space.sm)).accessibilityHidden(true)
                Text(header.statusLabel).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary)
                if let detail = header.detail {
                    Text("· \(detail)").font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textSecondary).lineLimit(1)
                }
            }
            .accessibilityElement(children: .combine)
            Text([header.updated, header.notice].compactMap { $0 }.joined(separator: " · "))
                .font(StockStepsTheme.font(type.caption, relativeTo: .caption1))
                .foregroundStyle(header.sampleData ? colors.cautionText : colors.textSecondary)
        }
    }

    @ViewBuilder
    private func indices(_ content: MarketsUiModel, _ colors: StockColors) -> some View {
        if content.indicesFailed || content.indices.isEmpty {
            StockSectionMessage(message: "Market indices aren't available right now.", actionTitle: "Try again", action: model.load)
                .stockCard(padding: CGFloat(space.md), bordered: false)
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: CGFloat(space.sm)) {
                    ForEach(content.indices, id: \.id) { card in
                        Button { lesson = model.client.indexLesson(id: card.id) } label: { indexCard(card, colors) }
                            .buttonStyle(.plain)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel(card.accessibilityLabel)
                            .accessibilityHint("Explains this index")
                    }
                }
            }
        }
    }

    private func indexCard(_ card: IndexCardModel, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            Text(card.name).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSecondary).lineLimit(1)
            Text(card.value).font(StockStepsTheme.font(type.numberEmphasis, relativeTo: .headline)).foregroundStyle(colors.textPrimary).lineLimit(1)
            if card.available {
                StockPriceChange(percentage: [card.change, card.percent].compactMap { $0 }.joined(separator: " "), direction: card.direction)
                if card.trend.count >= 2 {
                    StockSparkline(closes: card.trend.map { $0.doubleValue }, direction: card.direction)
                        .frame(height: CGFloat(dims.sparklineHeight)).padding(.top, CGFloat(space.xs))
                }
            } else {
                Text("Not available right now").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
            }
            if let proxy = card.proxyLabel {
                Text(proxy).font(StockStepsTheme.font(type.tiny, relativeTo: .caption2)).foregroundStyle(colors.cautionText).lineLimit(1)
            }
            if let updated = card.updated {
                Text(updated).font(StockStepsTheme.font(type.tiny, relativeTo: .caption2)).foregroundStyle(colors.textTertiary).lineLimit(1)
            }
        }
        .frame(width: CGFloat(dims.indexCardWidth), alignment: .leading)
        .stockCard(padding: CGFloat(space.md), bordered: false)
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
                        if index > 0 { StockDivider() }
                        moverRow(mover, tab: movers.tab, colors)
                    }
                }
            }
            .stockCard(padding: 0, bordered: false)
            Text("\(movers.rankedBy). \(movers.universe).").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
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
                    Text(price).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .subheadline)).foregroundStyle(colors.textPrimary).lineLimit(1)
                }
                StockPriceChange(percentage: row.change, direction: row.direction)
                if tab == .mostActive, let volume = mover.volume {
                    Text(volume).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
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
            StockSectionHeader(title: "Sector Performance")
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
            .padding(.horizontal, CGFloat(space.md)).padding(.vertical, CGFloat(space.xs))
            .stockCard(padding: 0, bordered: false)
            Text("\(sectors.period). \(sectors.methodology)").font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textTertiary)
        }
    }

    private func sectorRow(_ row: SectorRowModel, _ colors: StockColors) -> some View {
        let fill: Color = row.direction == .up ? colors.positive : row.direction == .down ? colors.negative : colors.textDisabled
        return HStack(spacing: CGFloat(space.sm)) {
            Text(row.sector).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody).lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(colors.surfaceSecondary)
                    Capsule().fill(fill).frame(width: proxy.size.width * CGFloat(row.fraction))
                }
            }
            .frame(width: 96, height: CGFloat(space.sm))
            StockPriceChange(percentage: row.change, direction: row.direction).frame(width: 72, alignment: .trailing)
        }
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
            .stockCard(padding: 0, bordered: false)
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
