import Shared

struct DiscoveryUiState {
    let gainers: DiscoveryFeed<MarketMover>
    let losers: DiscoveryFeed<MarketMover>
    let news: DiscoveryFeed<NewsArticle>
}
