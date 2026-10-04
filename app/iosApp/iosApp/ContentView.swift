import Shared
import SwiftUI

struct ContentView: View {
    @Environment(\.colorScheme) private var colorScheme
    @State private var model: StockSearchViewModel
    @State private var selectedStock: StockSearchResult?
    @State private var compactColumn: NavigationSplitViewColumn = .sidebar

    init(baseURL: String = "http://localhost:8080") {
        _model = State(initialValue: StockSearchViewModel(service: StockSearchService(baseURL: baseURL)))
    }

    private var palette: ThemePalette { StockStepsTheme.palette(colorScheme) }

    var body: some View {
        NavigationSplitView(preferredCompactColumn: $compactColumn) {
            StockSearchView(model: model) { stock in
                selectedStock = stock
                compactColumn = .detail
                model.loadQuote(for: stock)
            }
            .navigationSplitViewColumnWidth(min: 280, ideal: 360, max: 440)
        } detail: {
            if let stock = selectedStock {
                StockQuoteView(model: model, stock: stock)
            } else {
                ContentUnavailableView("Select a stock", systemImage: "chart.line.uptrend.xyaxis",
                    description: Text("Choose a search result to view its quote."))
            }
        }
        .navigationSplitViewStyle(.balanced)
        .tint(StockStepsTheme.color(palette.primary))
    }

}
