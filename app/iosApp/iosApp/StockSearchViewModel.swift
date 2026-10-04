import Shared
import SwiftUI
import Observation
import Combine

@MainActor
@Observable
final class StockSearchViewModel {
    var query = ""
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
