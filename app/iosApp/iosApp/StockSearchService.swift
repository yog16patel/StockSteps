import Shared

@MainActor
protocol StockSearchServing {
    func searchStocks(query: String) async throws -> [StockSearchResult]
    func getQuote(symbol: String) async throws -> StockQuote
    func getProfile(symbol: String) async throws -> CompanyProfile
    func cancelProfile()
    func cancelSearch()
    func cancelQuote()
}

/// Native adapter to the shared domain use cases. Owns the Kotlin bridge.
@MainActor
final class StockSearchService: StockSearchServing {
    private let client: IosStockStepsClient

    init(baseURL: String) { client = IosStockStepsClient(baseUrl: baseURL) }
    deinit { client.close() }

    func searchStocks(query: String) async throws -> [StockSearchResult] {
        try await client.searchStocks(query: query)
    }
    func getQuote(symbol: String) async throws -> StockQuote {
        try await client.getQuote(symbol: symbol)
    }
    func getProfile(symbol: String) async throws -> CompanyProfile {
        try await client.getProfile(symbol: symbol)
    }
    func cancelProfile() { client.cancelProfile() }
    func cancelSearch() { client.cancelSearch() }
    func cancelQuote() { client.cancelQuote() }
}
