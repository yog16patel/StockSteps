import Shared
import SwiftUI
import Observation
import Combine

@MainActor
@Observable
final class StockSearchViewModel {
    var query = ""
    private(set) var fundamentals: CompanyFundamentals?
    private(set) var isLoadingFundamentals = false
    private(set) var fundamentalsError: String?
    private(set) var financialPeriod = "annual"
    @ObservationIgnored private var fundamentalsTask: Task<Void, Never>?

    private func loadFundamentals(symbol: String, preserveValues: Bool = false) {
        fundamentalsTask?.cancel()
        service.cancelFundamentals()
        if !preserveValues { fundamentals = nil }
        isLoadingFundamentals = true
        fundamentalsError = nil
        let period = financialPeriod
        fundamentalsTask = Task { [weak self] in
            guard let self else { return }
            do {
                let value = try await service.getFundamentals(symbol: symbol, period: period)
                guard !Task.isCancelled else { return }
                fundamentals = value
                isLoadingFundamentals = false
            } catch {
                guard !Task.isCancelled else { return }
                fundamentalsError = "Financial data is temporarily unavailable. Try again."
                isLoadingFundamentals = false
            }
        }
    }
    private(set) var selectedStock: StockSearchResult?
    private(set) var detailTab: CompanyDetailTab = .overview
    private(set) var metricInfo: String?
    private(set) var companyNews: [NewsArticle] = []
    private(set) var isLoadingNews = false
    private(set) var newsError: String?
    @ObservationIgnored private var newsTask: Task<Void, Never>?

    var companyDetail: CompanyDetailUiState {
        guard let selectedStock else { return CompanyDetailPresenter.shared.build(stock: StockSearchResult(symbol: "", name: "", currency: nil, exchange: nil, exchangeFullName: nil), quote: nil, profile: nil, fundamentals: nil) }
        return CompanyDetailPresenter.shared.build(stock: selectedStock, quote: quote, profile: profile, fundamentals: fundamentals)
    }
    func detailAction(_ action: NativeCompanyDetailAction) {
        switch action {
        case .selectTab(let tab): detailTab = tab
        case .openMetric(let id): metricInfo = id
        case .closeMetric: metricInfo = nil
        case .refreshFundamentals: if let selectedStock { loadFundamentals(symbol: selectedStock.symbol, preserveValues: true) }
        case .selectFinancialPeriod(let period):
            financialPeriod = period
            if let selectedStock { loadFundamentals(symbol: selectedStock.symbol) }
        case .refreshNews: if let selectedStock { loadNews(symbol: selectedStock.symbol) }
        }
    }
    private func loadNews(symbol: String) {
        newsTask?.cancel()
        service.cancelNews()
        companyNews = []; newsError = nil; isLoadingNews = true
        newsTask = Task { [weak self] in
            guard let self else { return }
            do {
                let articles = try await service.getCompanyNews(symbol: symbol)
                guard !Task.isCancelled else { return }
                companyNews = articles; isLoadingNews = false
            } catch {
                guard !Task.isCancelled else { return }
                newsError = "Company news is temporarily unavailable."; isLoadingNews = false
            }
        }
    }
    private(set) var results: [StockSearchResult] = []
    private(set) var isSearching = false
    private(set) var searchError: String?
    private(set) var quote: StockQuote?
    private(set) var isLoadingQuote = false
    private(set) var quoteError: String?
    private(set) var profile: CompanyProfile?
    private(set) var isLoadingProfile = false
    private(set) var profileError: String?

    @ObservationIgnored private let queries = PassthroughSubject<String, Never>()
    @ObservationIgnored private var querySubscription: AnyCancellable?
    @ObservationIgnored private var lastQuery = ""
    @ObservationIgnored private let service: any StockSearchServing
    @ObservationIgnored private var searchTask: Task<Void, Never>?
    @ObservationIgnored private var profileTask: Task<Void, Never>?
    @ObservationIgnored private var quoteTask: Task<Void, Never>?

    init(service: any StockSearchServing) {
        self.service = service
        querySubscription = queries
            .removeDuplicates()
            .debounce(for: .milliseconds(300), scheduler: RunLoop.main)
            .sink { [weak self] text in
                guard let self, text == normalizedQuery else { return }
                performSearch(text)
            }
    }


    private var normalizedQuery: String { query.trimmingCharacters(in: .whitespacesAndNewlines) }

    func scheduleSearch() {
        let text = normalizedQuery
        guard text != lastQuery else { return }
        lastQuery = text
        prepareSearch(text)
        queries.send(text)
    }

    // Explicit retries execute immediately rather than waiting for typing to settle.
    func search() {
        let text = normalizedQuery
        prepareSearch(text)
        performSearch(text)
    }

    private func prepareSearch(_ text: String) {
        searchTask?.cancel()
        service.cancelSearch()
        results = []
        searchError = nil
        isSearching = !text.isEmpty
    }

    private func performSearch(_ text: String) {
        guard !text.isEmpty else { return }
        searchTask?.cancel()
        service.cancelSearch()
        searchTask = Task { [weak self] in
            do {
                guard let self, !Task.isCancelled else { return }
                let values = try await service.searchStocks(query: text)
                guard !Task.isCancelled else { return }
                results = values
                isSearching = false
            } catch {
                guard let self, !Task.isCancelled else { return }
                searchError = "Could not load stocks. Check your connection and try again."
                isSearching = false
            }
        }
    }

    func selectStock(_ stock: StockSearchResult) {
        selectedStock = stock; detailTab = .overview; metricInfo = nil
        loadFundamentals(symbol: stock.symbol)
        loadNews(symbol: stock.symbol)
        loadQuote(for: stock)
        loadProfile(for: stock)
    }

    func loadProfile(for stock: StockSearchResult) {
        profileTask?.cancel()
        service.cancelProfile()
        profile = nil
        profileError = nil
        isLoadingProfile = true
        profileTask = Task { [weak self] in
            guard let self else { return }
            do {
                let value = try await service.getProfile(symbol: stock.symbol)
                guard !Task.isCancelled else { return }
                profile = value
                isLoadingProfile = false
            } catch {
                guard !Task.isCancelled else { return }
                profileError = "Could not load the company profile. Try again."
                isLoadingProfile = false
            }
        }
    }

    func loadQuote(for stock: StockSearchResult) {
        quoteTask?.cancel()
        service.cancelQuote()
        quote = nil
        quoteError = nil
        isLoadingQuote = true
        quoteTask = Task { [weak self] in
            guard let self else { return }
            do {
                let value = try await service.getQuote(symbol: stock.symbol)
                guard !Task.isCancelled else { return }
                quote = value
                isLoadingQuote = false
            } catch {
                guard !Task.isCancelled else { return }
                quoteError = "Could not load this quote. Try again."
                isLoadingQuote = false
            }
        }
    }

    func cancelQuote() {
        quoteTask?.cancel()
        service.cancelQuote()
        isLoadingQuote = false
    }
}
