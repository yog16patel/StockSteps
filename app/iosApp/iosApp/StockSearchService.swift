import Shared

@MainActor
protocol StockSearchServing {
    func searchStocks(query: String) async throws -> [StockSearchResult]
    func getQuote(symbol: String) async throws -> StockQuote
    func getProfile(symbol: String) async throws -> CompanyProfile
    func getCompanyNews(symbol: String) async throws -> [NewsArticle]
    func getFundamentals(symbol: String, period: String) async throws -> CompanyFundamentals
    func cancelFundamentals()
    func cancelNews()
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
    func getCompanyNews(symbol: String) async throws -> [NewsArticle] { try await client.getCompanyNews(symbol: symbol) }
    func getFundamentals(symbol: String, period: String) async throws -> CompanyFundamentals {
        try await client.getFundamentals(symbol: symbol, period: period)
    }
    func cancelFundamentals() { client.cancelFundamentals() }
    func cancelNews() { client.cancelNews() }
    func cancelProfile() { client.cancelProfile() }
    func cancelSearch() { client.cancelSearch() }
    func cancelQuote() { client.cancelQuote() }
}
