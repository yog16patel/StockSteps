import Shared

struct StockSearchUiState {
    let financials: FinancialsUiState
    let isLoadingFundamentals: Bool
    let fundamentalsError: String?
    let financialPeriod: String
    let detail: CompanyDetailUiState
    let detailTab: CompanyDetailTab
    let metricInfo: String?
    let companyNews: [NewsArticle]
    let isLoadingNews: Bool
    let newsError: String?
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
            financials: FinancialsPresenter.shared.build(fundamentals: fundamentals, loading: isLoadingFundamentals, failed: fundamentalsError != nil),
            isLoadingFundamentals: isLoadingFundamentals, fundamentalsError: fundamentalsError, financialPeriod: financialPeriod,
            detail: companyDetail, detailTab: detailTab, metricInfo: metricInfo, companyNews: companyNews, isLoadingNews: isLoadingNews, newsError: newsError,
            query: query, results: results, isSearching: isSearching, searchError: searchError,
            quote: quote, isLoadingQuote: isLoadingQuote, quoteError: quoteError,
            profile: profile, isLoadingProfile: isLoadingProfile, profileError: profileError
        )
    }
}
