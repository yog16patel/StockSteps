import Shared
import Observation
import SwiftUI

struct DiscoveryFeed<Item> {
    var items: [Item] = []
    var isLoading = false
    var error: String?
}

@MainActor
@Observable
final class DiscoveryViewModel {
    private(set) var gainers = DiscoveryFeed<MarketMover>()
    private(set) var losers = DiscoveryFeed<MarketMover>()
    private(set) var news = DiscoveryFeed<NewsArticle>()
    @ObservationIgnored private let service: any DiscoveryServing
    @ObservationIgnored private var gainersTask: Task<Void, Never>?
    @ObservationIgnored private var losersTask: Task<Void, Never>?
    @ObservationIgnored private var newsTask: Task<Void, Never>?
    @ObservationIgnored private var hasLoaded = false

    init(service: any DiscoveryServing) { self.service = service }
    func loadIfNeeded() { if !hasLoaded { refresh() } }
    func refresh() {
        hasLoaded = true
        loadGainers()
        loadLosers()
        loadNews()
    }

    func loadGainers() {
        gainersTask?.cancel()
        gainers.isLoading = true
        gainers.error = nil
        gainersTask = Task { [weak self] in
            guard let self else { return }
            do {
                let items = try await service.getGainers()
                guard !Task.isCancelled else { return }
                gainers = DiscoveryFeed(items: items)
            } catch {
                guard !Task.isCancelled else { return }
                gainers.isLoading = false
                gainers.error = "Could not load gainers. Try again."
            }
        }
    }

    func loadLosers() {
        losersTask?.cancel()
        losers.isLoading = true
        losers.error = nil
        losersTask = Task { [weak self] in
            guard let self else { return }
            do {
                let items = try await service.getLosers()
                guard !Task.isCancelled else { return }
                losers = DiscoveryFeed(items: items)
            } catch {
                guard !Task.isCancelled else { return }
                losers.isLoading = false
                losers.error = "Could not load losers. Try again."
            }
        }
    }

    func loadNews() {
        newsTask?.cancel()
        news.isLoading = true
        news.error = nil
        newsTask = Task { [weak self] in
            guard let self else { return }
            do {
                let items = try await service.getNews()
                guard !Task.isCancelled else { return }
                news = DiscoveryFeed(items: items)
            } catch {
                guard !Task.isCancelled else { return }
                news.isLoading = false
                news.error = "Could not load news. Try again."
            }
        }
    }
}
