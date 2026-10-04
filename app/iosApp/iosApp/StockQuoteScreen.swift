import Shared
import SwiftUI

struct StockQuoteScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    let state: StockSearchUiState
    let stock: StockSearchResult
    let onRetryQuote: () -> Void
    let onRetryProfile: () -> Void
    let saved: Bool
    let watchlistEnabled: Bool
    let watchlistError: String?
    let onToggleWatchlist: () -> Void
    private var palette: ThemePalette { StockStepsTheme.palette(colorScheme) }

    var body: some View {
        List {
            Section {
                Text(stock.name).font(StockStepsTheme.font(StockStepsTheme.typography.title, relativeTo: .title2))
                Text([stock.symbol, stock.exchange, stock.currency]
                    .compactMap { $0 }.joined(separator: " · ")).foregroundStyle(.secondary)
            }
            if state.isLoadingQuote {
                ProgressView("Loading quote…")
            } else if let error = state.quoteError {
                Section {
                    Text(error)
                    Button("Retry quote", action: onRetryQuote).stockStepsGlassButton()
                }
            } else if let quote = state.quote {
                Section("Quote") {
                    LabeledContent("Price", value: priceText(quote.price, currency: stock.currency))
                    LabeledContent("Change", value: numberText(quote.change))
                    LabeledContent("Change (%)", value: numberText(quote.changePercent))
                }
            }
            Section {
                Button(saved ? "Remove from WatchList" : "Add to WatchList", action: onToggleWatchlist)
                    .disabled(!watchlistEnabled)
                if let error = watchlistError { Text(error).foregroundStyle(.red) }
            }
            CompanyProfileView(state: state, onRetry: onRetryProfile)
        }
        .scrollContentBackground(.hidden)
        .background(StockStepsTheme.color(palette.background))
        .navigationTitle(stock.symbol)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func numberText(_ value: KotlinDouble?) -> String {
        guard let value else { return "Unavailable" }
        return value.doubleValue.formatted(.number.precision(.fractionLength(2)))
    }

    private func priceText(_ value: KotlinDouble?, currency: String?) -> String {
        guard let value else { return "Unavailable" }
        guard let currency else { return numberText(value) }
        return value.doubleValue.formatted(.currency(code: currency))
    }
}
