import Shared
import SwiftUI

struct StockSearchScene: View {
    @Environment(\.colorScheme) private var colorScheme
    let model: StockSearchViewModel
    let accounts: AccountViewModel
    var onClose: () -> Void = {}
    @State private var selectedStock: StockSearchResult?
    @State private var compactColumn: NavigationSplitViewColumn = .sidebar

    private var palette: ThemePalette { StockStepsTheme.palette(colorScheme) }

    var body: some View {
        @Bindable var model = model
        NavigationSplitView(preferredCompactColumn: $compactColumn) {
            StockSearchScreen(
                state: model.uiState,
                query: $model.query,
                onQueryChanged: model.scheduleSearch,
                onRetry: model.search
            ) { stock in
                selectedStock = stock
                compactColumn = .detail
                model.selectStock(stock)
            }
            .stockStepsTopBar(
                .screen("Search stocks", actions: [AppBarAction(id: "close", label: "Done", enabled: true)]),
                onAction: { _ in onClose() }
            )
            .navigationSplitViewColumnWidth(min: 280, ideal: 360, max: 440)
        } detail: {
            if let stock = selectedStock {
                StockQuoteScreen(
                    state: model.uiState,
                    stock: stock,
                    onRetryQuote: { model.loadQuote(for: stock) },
                    onRetryProfile: { model.loadProfile(for: stock) },
                    saved: accounts.state.items.contains { $0.symbol == stock.symbol },
                    watchlistEnabled: !accounts.state.initializing,
                    watchlistError: accounts.state.error,
                    onToggleWatchlist: { Task { await accounts.toggle(symbol: stock.symbol) } }
                )
                .stockStepsTopBar(
                    .screen(stock.symbol, actions: [AppBarAction(id: "close", label: "Done", enabled: true)]),
                    onAction: { _ in onClose() }
                )
            } else {
                ContentUnavailableView("Select a stock", systemImage: "chart.line.uptrend.xyaxis",
                    description: Text("Choose a search result to view its quote."))
                    .stockStepsTopBar(
                        .screen("Stock details", actions: [AppBarAction(id: "close", label: "Done", enabled: true)]),
                        onAction: { _ in onClose() }
                    )
            }
        }
        .navigationSplitViewStyle(.balanced)
        .tint(StockStepsTheme.color(palette.primary))
    }

}
