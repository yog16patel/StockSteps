import SwiftUI

struct DiscoveryScene: View {
    let model: DiscoveryViewModel
    let onSearch: () -> Void
    let onExplore: (String) -> Void

    var body: some View {
        DiscoveryScreen(
            state: DiscoveryUiState(gainers: model.gainers, losers: model.losers, news: model.news),
            onRefresh: model.refresh,
            onRetryGainers: model.loadGainers,
            onRetryLosers: model.loadLosers,
            onRetryNews: model.loadNews,
            onSearch: onSearch,
            onExplore: onExplore
        )
        .task { model.loadIfNeeded() }
    }
}
