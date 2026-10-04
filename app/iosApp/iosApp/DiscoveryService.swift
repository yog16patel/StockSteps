import Shared

@MainActor
protocol DiscoveryServing {
    func getGainers() async throws -> [MarketMover]
    func getLosers() async throws -> [MarketMover]
    func getNews() async throws -> [NewsArticle]
}

@MainActor
final class DiscoveryService: DiscoveryServing {
    private let client: IosMarketClient
    init(baseURL: String) { client = IosMarketClient(baseUrl: baseURL) }
    deinit { client.close() }
    func getGainers() async throws -> [MarketMover] { try await client.getGainers() }
    func getLosers() async throws -> [MarketMover] { try await client.getLosers() }
    func getNews() async throws -> [NewsArticle] { try await client.getNews() }
}
