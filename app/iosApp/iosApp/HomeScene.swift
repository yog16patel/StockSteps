import SwiftUI

struct HomeScene: View {
    let model: HomeViewModel
    let accounts: AccountViewModel
    let onSearch: () -> Void
    let onExplore: (String) -> Void
    private var symbols: [String] { accounts.state.items.map(\.symbol) }
    private var ownerKey: String { (accounts.state.user?.id ?? "guest") + "|" + symbols.joined(separator: "|") }
    var body: some View {
        HomeScreen(
            news: model.news,
            newsLoading: model.newsLoading,
            newsError: model.newsError,
            onRefreshNews: model.refreshNews,
            indices: model.indices,
            snapshot: model.snapshot,
            loading: model.snapshotLoading,
            error: model.snapshotError,
            stocks: model.stocks.filter { symbols.contains($0.symbol) },
            onSearch: onSearch,
            onExplore: onExplore,
            onRefresh: { model.refreshIndices(); model.loadWatchlist(symbols) }
        )
        .task { model.loadIfNeeded() }
        .task(id: ownerKey) { model.loadWatchlist(symbols) }
    }
}
