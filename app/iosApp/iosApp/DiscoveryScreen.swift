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
                        VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.small)) {
                            Text(article.title).font(.headline)
                            Text([article.source, article.publishedAt].compactMap { $0 }.joined(separator: " · "))
                                .font(.caption).foregroundStyle(.secondary)
                            if let url = URL(string: article.url), ["http", "https"].contains(url.scheme?.lowercased() ?? "") {
                                Link("Read article", destination: url)
                            }
                        }
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
