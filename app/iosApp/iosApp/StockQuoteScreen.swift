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
    let onDetailAction: (NativeCompanyDetailAction) -> Void
    let onToggleWatchlist: () -> Void

    var body: some View {
        CompanyDetailView(
            state: state, saved: saved, watchlistEnabled: watchlistEnabled,
            watchlistError: watchlistError, onToggleWatchlist: onToggleWatchlist,
            onRetryQuote: onRetryQuote, onRetryProfile: onRetryProfile,
            onAction: onDetailAction
        )
        .background(StockStepsTheme.color(StockStepsTheme.palette(colorScheme).background))
    }
}
