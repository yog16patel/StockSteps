import Shared
import SwiftUI

struct StockQuoteView: View {
    @Environment(\.colorScheme) private var colorScheme
    let model: StockSearchViewModel
    let stock: StockSearchResult
    private var palette: ThemePalette { StockStepsTheme.palette(colorScheme) }

    var body: some View {
        List {
            Section {
                Text(stock.name).font(StockStepsTheme.font(StockStepsTheme.typography.title, relativeTo: .title2))
                Text([stock.symbol, stock.exchange, stock.currency]
                    .compactMap { $0 }.joined(separator: " · ")).foregroundStyle(.secondary)
            }
            if model.isLoadingQuote {
                ProgressView("Loading quote…")
            } else if let error = model.quoteError {
                Section {
                    Text(error)
                    Button("Retry quote") { model.loadQuote(for: stock) }.stockStepsGlassButton()
                }
            } else if let quote = model.quote {
                Section("Quote") {
                    LabeledContent("Price", value: priceText(quote.price, currency: stock.currency))
                    LabeledContent("Change", value: numberText(quote.change))
                    LabeledContent("Change (%)", value: numberText(quote.changePercent))
                }
            }
            CompanyProfileView(model: model, stock: stock)
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
