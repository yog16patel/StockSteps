import Shared
import SwiftUI

struct StockSearchView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Bindable var model: StockSearchViewModel
    let onSelect: (StockSearchResult) -> Void
    private var palette: ThemePalette { StockStepsTheme.palette(colorScheme) }

    var body: some View {
        List {
            if model.query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                ContentUnavailableView("Find your first stock", systemImage: "magnifyingglass",
                    description: Text("Search for a company or ticker in USD or CAD."))
            } else if model.isSearching {
                HStack { Spacer(); ProgressView("Searching…"); Spacer() }
            } else if let error = model.searchError {
                VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.medium)) {
                    Label(error, systemImage: "wifi.exclamationmark")
                    Button("Retry", action: model.search).stockStepsGlassButton()
                }
            } else if model.results.isEmpty {
                ContentUnavailableView.search(text: model.query)
            } else {
                Section("Stocks") {
                    ForEach(model.results, id: \.symbol) { stock in
                        Button {
                            onSelect(stock)
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.tiny)) {
                                    Text(stock.name).font(StockStepsTheme.font(StockStepsTheme.typography.subtitle, relativeTo: .headline)).foregroundStyle(StockStepsTheme.color(palette.textPrimary))
                                    Text([stock.symbol, stock.exchange, stock.currency]
                                        .compactMap { $0 }.joined(separator: " · "))
                                        .font(StockStepsTheme.font(StockStepsTheme.typography.body, relativeTo: .body)).foregroundStyle(StockStepsTheme.color(palette.textSecondary))
                                }
                                Spacer()
                                Image(systemName: "chevron.right").foregroundStyle(.secondary)
                            }.padding(.vertical, CGFloat(StockStepsTheme.spacing.tiny))
                        }.buttonStyle(.plain)
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(StockStepsTheme.color(palette.background))
        .tint(StockStepsTheme.color(palette.primary))
        .navigationTitle("StockSteps")
        .searchable(text: $model.query, prompt: "Company or ticker")
        .onChange(of: model.query) { _, _ in model.scheduleSearch() }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Text("USD · CAD").font(StockStepsTheme.font(StockStepsTheme.typography.caption, relativeTo: .caption1))
                    .padding(.horizontal, CGFloat(StockStepsTheme.spacing.medium)).padding(.vertical, CGFloat(StockStepsTheme.spacing.small))
                    .stockStepsGlassSurface()
                    .accessibilityLabel("Search results in US and Canadian dollars")
            }
        }
    }
}
