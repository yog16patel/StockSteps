import Shared
import SwiftUI

/// The one Company Details destination on iOS; Home, Watchlist and Search all push it by symbol.
struct CompanyDetailsScene: View {
    let symbol: String
    let accounts: AccountViewModel
    @State private var model: CompanyDetailsModel
    @State private var showFinancials = false
    @State private var showNews = false
    @Environment(\.openURL) private var openURL

    init(symbol: String, accounts: AccountViewModel, baseURL: @escaping () -> String = { BackendSettings.currentURL }) {
        self.symbol = symbol
        self.accounts = accounts
        _model = State(initialValue: CompanyDetailsModel(symbol: symbol, baseURL: baseURL))
    }

    var body: some View {
        CompanyDetailsScreen(
            model: model,
            watched: accounts.state.items.contains { $0.symbol == symbol },
            watchlistEnabled: !accounts.state.initializing,
            onToggleWatchlist: {
                // The same watchlist used by the Watchlist tab.
                let name = model.overview.value?.name ?? symbol
                Task { await accounts.toggle(stock: StockSearchResult(symbol: symbol, name: name, currency: nil, exchange: nil, exchangeFullName: nil)) }
            },
            onOpenURL: { openURL($0) },
            onOpenFinancials: { showFinancials = true },
            onOpenNews: { showNews = true }
        )
        // Deeper destinations stay inside the same navigation stack, so Back returns here.
        .navigationDestination(isPresented: $showFinancials) { CompanyFinancialsScene(symbol: symbol, client: model.client) }
        .navigationDestination(isPresented: $showNews) { CompanyNewsScene(symbol: symbol, client: model.client) }
        .task { if model.overview.value == nil { model.load() } }
    }
}
