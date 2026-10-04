import Shared
import SwiftUI

struct DiscoveryScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    let state: DiscoveryUiState
    let onRefresh: () -> Void
    let onRetryGainers: () -> Void
    let onRetryLosers: () -> Void
    let onRetryNews: () -> Void
    let onSearch: () -> Void
    let onExplore: (String) -> Void
    private var palette: ThemePalette { StockStepsTheme.palette(colorScheme) }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("Explore market movers and the latest headlines.")
                    Button("Search stocks", action: onSearch)
                }
                MarketMoversView(title: "Top gainers", feed: state.gainers, onRetry: onRetryGainers, onExplore: onExplore)
                MarketMoversView(title: "Top losers", feed: state.losers, onRetry: onRetryLosers, onExplore: onExplore)
                Section("Market news") {
                    DiscoveryFeedStatus(feed: state.news, onRetry: onRetryNews)
                    ForEach(Array(state.news.items.enumerated()), id: \.offset) { _, article in
                        NewsCard(article: article)
                    }
                }
            }
            .navigationTitle("Discover")
            .scrollContentBackground(.hidden)
            .background(StockStepsTheme.color(palette.background))
             .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Refresh", action: onRefresh).stockStepsGlassButton()
                }
            }
        }
    }
}
