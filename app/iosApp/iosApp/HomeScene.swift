import SwiftUI

struct HomeScene: View {
    let model: HomeViewModel
    let accounts: AccountViewModel
    let onLearn: () -> Void
    let onExplore: (String) -> Void
    @Environment(\.openURL) private var openURL
    private var symbols: [String] { accounts.state.items.map(\.symbol) }
    private var ownerKey: String { (accounts.state.user?.id ?? "guest") + "|" + symbols.joined(separator: "|") }
    var body: some View {
        HomeScreen(
            signedIn: accounts.state.user != nil,
            market: model.market,
            movers: model.movers,
            newsStatus: model.newsStatus,
            news: model.newsCards,
            onSelectMovers: model.selectMovers,
            onOpenStock: onExplore,
            onLearn: onLearn,
            onOpenArticle: { openURL($0) },
            onRetryMarket: { model.refreshIndices(); model.loadWatchlist(accounts.state.items) },
            onRetryNews: model.refreshNews
        )
        .task { model.loadIfNeeded() }
        .task(id: ownerKey) { model.loadWatchlist(accounts.state.items) }
    }
}
