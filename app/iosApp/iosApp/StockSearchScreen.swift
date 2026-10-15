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
                    description: Text("Search for a company or ticker listed on a US exchange."))
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
                // 5D.5.5B: server-ranked results in "Best match" / "Other results" sections (or one "Results" section) with the shared
                // borderless row; order and contents are the server's.
                ForEach(SearchDisplay.shared.sections(query: state.query, results: state.results), id: \.title) { section in
                    Section {
                        ForEach(section.results, id: \.symbol) { stock in
                            SearchResultRow(result: stock, best: section.best) { onSelect(stock) }
                                .listRowBackground(Color.clear)
                                .listRowSeparatorTint(StockStepsTheme.color(palette.borderSubtle))
                                .alignmentGuide(.listRowSeparatorLeading) { _ in CGFloat(StockStepsTheme.dimensions.logo + StockStepsTheme.spacing.md) }
                        }
                    } header: {
                        Text(verbatim: section.title).font(StockStepsTheme.font(StockStepsTheme.typography.cardTitle, relativeTo: .headline))
                            .foregroundStyle(StockStepsTheme.color(palette.textPrimary)).textCase(nil)
                            .accessibilityAddTraits(.isHeader)
                    }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(StockStepsTheme.color(palette.background))
        .tint(StockStepsTheme.color(palette.primary))
        .searchable(text: $query, isPresented: $searchActive, prompt: "Company or ticker")
        .task { if query.isEmpty { searchActive = true } }
        // D1 (5D.5.5): search covers USD listings on US exchanges, so the former "USD · CAD" chip is gone.
    }
}
