import Shared
import Observation

struct HomeStock: Identifiable {
    let symbol: String
    var name: String?
    var change: Double?
    var loading = false
    var error = false
    var price: Double?
    var id: String { symbol }
}

@MainActor
protocol HomeQuoteServing {
    func news() async throws -> [NewsArticle]
    func snapshot() async throws -> MarketSnapshot
    func quote(symbol: String) async throws -> StockQuote
}

@MainActor
final class HomeQuoteService: HomeQuoteServing {
    private let client: IosHomeClient
    init(baseURL: String) { client = IosHomeClient(baseUrl: baseURL) }
    deinit { client.close() }
    func news() async throws -> [NewsArticle] { try await client.getNews() }
    func snapshot() async throws -> MarketSnapshot { try await client.getSnapshot() }
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
    @ObservationIgnored private let service: any HomeQuoteServing
    @ObservationIgnored private var indexTask: Task<Void, Never>?
    @ObservationIgnored private var stockTask: Task<Void, Never>?
    @ObservationIgnored private var hasLoaded = false
    init(service: any HomeQuoteServing) { self.service = service }
    private static func indexCards() -> [HomeStock] {
        [HomeStock(symbol: "SPY", name: "S&P 500"), HomeStock(symbol: "QQQ", name: "Nasdaq-100"), HomeStock(symbol: "DIA", name: "Dow 30")]
    }
    func loadIfNeeded() { if !hasLoaded { refreshIndices(); refreshNews() } }
    func refreshIndices() {
        hasLoaded = true
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

    func loadWatchlist(_ symbols: [String]) {
        stockTask?.cancel()
        stocks = symbols.map { HomeStock(symbol: $0, loading: true) }
        stockTask = Task { [weak self] in
            guard let self else { return }
            for symbol in symbols {
                let row = await fetch(HomeStock(symbol: symbol))
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
