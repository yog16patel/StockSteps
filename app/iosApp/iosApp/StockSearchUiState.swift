import Shared

struct StockSearchUiState {
    let query: String
    let results: [StockSearchResult]
    let isSearching: Bool
    let searchError: String?
    let quote: StockQuote?
    let isLoadingQuote: Bool
    let quoteError: String?
    let profile: CompanyProfile?
    let isLoadingProfile: Bool
    let profileError: String?
}

extension StockSearchViewModel {
    var uiState: StockSearchUiState {
        StockSearchUiState(
            query: query, results: results, isSearching: isSearching, searchError: searchError,
            quote: quote, isLoadingQuote: isLoadingQuote, quoteError: quoteError,
            profile: profile, isLoadingProfile: isLoadingProfile, profileError: profileError
        )
    }
}
