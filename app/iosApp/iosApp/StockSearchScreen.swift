import Shared
import SwiftUI

struct StockSearchScreen: View {
    @Environment(\.colorScheme) private var colorScheme
    let state: StockSearchUiState
    @Binding var query: String
    let onRetry: () -> Void
    let onSelect: (StockSearchResult) -> Void
    /// The sheet opens with the search field active (keyboard up), so the first tap isn't lost to the presentation animation (Phase 5C.1).
    @State private var searchActive = false
    private var palette: ThemePalette { StockStepsTheme.palette(colorScheme) }

    var body: some View {
        List {
            if state.query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                ContentUnavailableView("Find your first stock", systemImage: "magnifyingglass",
                    description: Text("Search for a company or ticker in USD or CAD."))
            } else if state.isSearching {
                HStack { Spacer(); ProgressView("Searching…"); Spacer() }
            } else if let error = state.searchError {
                VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.medium)) {
                    Label(error, systemImage: "wifi.exclamationmark")
                    Button("Retry", action: onRetry).stockStepsGlassButton()
                }
            } else if state.results.isEmpty {
                ContentUnavailableView.search(text: state.query)
            } else {
                Section("Stocks") {
                    ForEach(state.results, id: \.symbol) { stock in
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
        .searchable(text: $query, isPresented: $searchActive, prompt: "Company or ticker")
        .task { if query.isEmpty { searchActive = true } }
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
