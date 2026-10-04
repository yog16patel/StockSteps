import SwiftUI

struct DiscoveryScene: View {
    let model: DiscoveryViewModel
    let onExplore: (String) -> Void

    var body: some View {
        DiscoveryScreen(
            state: DiscoveryUiState(gainers: model.gainers, losers: model.losers, news: model.news),
            onRefresh: model.refresh,
            onRetryGainers: model.loadGainers,
            onRetryLosers: model.loadLosers,
            onRetryNews: model.loadNews,
            onExplore: onExplore
        )
        .task { model.loadIfNeeded() }
    }
}
