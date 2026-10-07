import Shared
import Observation

struct HomeStock: Identifiable {
    let symbol: String
    var name: String?
    var change: Double?
    var loading = false
    var error = false
    var price: Double?
    var currency: String?
    var id: String { symbol }
}

@MainActor
protocol HomeQuoteServing {
    func news() async throws -> [NewsArticle]
    func snapshot() async throws -> MarketSnapshot
    func sparkline(symbol: String) async throws -> Sparkline
    func profile(symbol: String) async throws -> CompanyProfile
    func quote(symbol: String) async throws -> StockQuote
}

@MainActor
final class HomeQuoteService: HomeQuoteServing {
    private let client: IosHomeClient
    init(baseURL: String) { client = IosHomeClient(baseUrl: baseURL) }
    deinit { client.close() }
    func news() async throws -> [NewsArticle] { try await client.getNews() }
    func snapshot() async throws -> MarketSnapshot { try await client.getSnapshot() }
    func sparkline(symbol: String) async throws -> Sparkline { try await client.getSparkline(symbol: symbol) }
    func profile(symbol: String) async throws -> CompanyProfile { try await client.getProfile(symbol: symbol) }
    func quote(symbol: String) async throws -> StockQuote { try await client.getQuote(symbol: symbol) }
}

@MainActor
@Observable
final class HomeViewModel {
    private(set) var news: [NewsArticle] = []
    private(set) var newsLoading = false
    private(set) var newsError: String?
    @ObservationIgnored private var newsTask: Task<Void, Never>?
    private(set) var snapshot: MarketSnapshot?
    private(set) var snapshotLoading = false
    private(set) var snapshotError: String?
    var indices: [HomeStock] {
        if let snapshot, !snapshot.indices.isEmpty {
            return snapshot.indices.map {
                HomeStock(symbol: $0.symbol, name: $0.name, change: $0.changePercent?.doubleValue, error: $0.error != nil || $0.price == nil, price: $0.price?.doubleValue)
            }
        }
        return Self.indexCards().map {
            var row = $0
            row.loading = snapshotLoading
            row.error = snapshotError != nil || snapshot?.errors.contains(where: { $0.section == "indices" }) == true
            return row
        }
    }
    private(set) var stocks: [HomeStock] = []
    /// All movers arrive in one snapshot, so switching chips is local and makes no request.
    private(set) var selectedMovers: MoverCategory = .gainers
    private(set) var sparklines: [String: [KotlinDouble]] = [:]
    @ObservationIgnored private var requestedSparklines = Set<String>()
    private(set) var logos: [String: String] = [:]
    @ObservationIgnored private var requestedLogos = Set<String>()
    var market: MarketSnapshotUiModel {
        HomePresentation.shared.market(snapshot: snapshot, loading: snapshotLoading, failed: snapshotError != nil, maxIndices: HomePresentation.shared.HOME_MARKET_CARDS)
    }
    var movers: MoversUiModel {
        HomePresentation.shared.movers(snapshot: snapshot, loading: snapshotLoading, failed: snapshotError != nil, category: selectedMovers, sparklines: sparklines, logos: logos)
    }
    var newsStatus: SectionStatus {
        HomePresentation.shared.newsStatus(count: Int32(news.count), loading: newsLoading, failed: newsError != nil)
    }
    var newsCards: [NewsUiModel] {
        var seen = Set<String>()
        return news.map { NewsPresentation.shared.model(article: $0) }
            .filter { seen.insert($0.id).inserted }
            .prefix(Int(HomePresentation.shared.MAX_NEWS))
            .map { $0 }
    }
    @ObservationIgnored private let service: any HomeQuoteServing
    @ObservationIgnored private var indexTask: Task<Void, Never>?
    @ObservationIgnored private var stockTask: Task<Void, Never>?
    @ObservationIgnored private var hasLoaded = false
    init(service: any HomeQuoteServing) { self.service = service }
    private static func indexCards() -> [HomeStock] {
        [HomeStock(symbol: "SPY", name: "S&P 500"), HomeStock(symbol: "QQQ", name: "Nasdaq-100"), HomeStock(symbol: "DIA", name: "Dow 30")]
    }
    func loadIfNeeded() { if !hasLoaded { refreshIndices(); refreshNews() } }
    func selectMovers(_ category: MoverCategory) {
        selectedMovers = category
        loadMoverExtras()
    }

    private func loadMoverExtras() {
        loadSparklines()
        loadLogos()
    }

    /// Profile logos fill in for snapshot movers without one; requested once per symbol.
    private func loadLogos() {
        for symbol in HomePresentation.shared.moversMissingLogos(snapshot: snapshot, category: selectedMovers)
        where requestedLogos.insert(symbol).inserted {
            Task { [weak self] in
                guard let self, let logo = try? await service.profile(symbol: symbol).logoUrl else { return }
                logos[symbol] = logo
            }
        }
    }

    /// Requested once per symbol until the next refresh; failures leave the row without a line.
    private func loadSparklines() {
        let symbols = HomePresentation.shared.visibleMoverSymbols(snapshot: snapshot, category: selectedMovers)
        for symbol in symbols where requestedSparklines.insert(symbol).inserted {
            Task { [weak self] in
                guard let self, let line = try? await service.sparkline(symbol: symbol) else { return }
                sparklines[symbol] = line.closes
            }
        }
    }

    func refreshIndices() {
        hasLoaded = true
        requestedSparklines.removeAll()
        indexTask?.cancel()
        snapshotLoading = true
        snapshotError = nil
        indexTask = Task { [weak self] in
            guard let self else { return }
            defer { if !Task.isCancelled { snapshotLoading = false } }
            do {
                let result = try await service.snapshot()
                guard !Task.isCancelled else { return }
                snapshot = result
                loadMoverExtras()
            } catch {
                guard !Task.isCancelled else { return }
                snapshotError = "Could not load market snapshot. Check your connection and try again."
            }
        }
    }

    func refreshNews() {
        newsTask?.cancel()
        newsLoading = true
        newsError = nil
        newsTask = Task { [weak self] in
            guard let self else { return }
            defer { if !Task.isCancelled { newsLoading = false } }
            do {
                let articles = try await service.news()
                guard !Task.isCancelled else { return }
                news = articles
            } catch {
                guard !Task.isCancelled else { return }
                newsError = "Could not load news. Try again."
            }
        }
    }

    func loadWatchlist(_ items: [WatchlistItem]) {
        stockTask?.cancel()
        stocks = items.map { HomeStock(symbol: $0.symbol, name: $0.name, loading: true, currency: $0.currency) }
        stockTask = Task { [weak self] in
            guard let self else { return }
            for item in items {
                let row = await fetch(HomeStock(symbol: item.symbol, name: item.name, currency: item.currency))
                guard !Task.isCancelled else { return }
                stocks = stocks.map { $0.symbol == row.symbol ? row : $0 }
            }
        }
    }
    private func fetch(_ original: HomeStock) async -> HomeStock {
        var row = original
        do {
            let result = try await service.quote(symbol: row.symbol)
            row.name = row.name ?? result.companyName
            row.change = result.changePercent?.doubleValue
            row.price = result.price?.doubleValue
            row.error = row.change == nil
        } catch { row.error = true }
        row.loading = false
        return row
    }
}
