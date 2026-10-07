import Observation
import Shared

enum SectionState<Value> {
    case loading
    case content(Value)
    case unavailable

    var value: Value? { if case .content(let value) = self { value } else { nil } }
}

/// Company Details state: one aggregated core request plus independent chart, why-moving and
/// news requests. Charts are cached per range. Mock/Real is decided by the client's base URL.
@MainActor
@Observable
final class CompanyDetailsModel {
    let symbol: String
    private(set) var overview: SectionState<CompanyOverview> = .loading
    private(set) var range: ChartRange = .oneMonth
    private(set) var chart: SectionState<PriceChart> = .loading
    private(set) var whyMoving: SectionState<WhyMoving> = .loading
    private(set) var news: SectionState<[NewsUiModel]> = .loading
    @ObservationIgnored private let client: IosCompanyDetailsClient
    @ObservationIgnored private var charts: [ChartRange: PriceChart] = [:]
    @ObservationIgnored private var chartTask: Task<Void, Never>?

    init(symbol: String, baseURL: @escaping () -> String) {
        self.symbol = symbol
        client = IosCompanyDetailsClient(baseUrl: baseURL)
    }

    deinit { client.close() }

    func load() {
        loadCore()
        loadChart(range)
        loadWhyMoving()
        loadNews()
    }

    func loadCore() {
        overview = .loading
        Task { overview = (try? await client.getOverview(symbol: symbol)).map(SectionState.content) ?? .unavailable }
    }

    func select(range newRange: ChartRange) {
        range = newRange
        loadChart(newRange)
    }

    func retryChart() { charts[range] = nil; loadChart(range) }

    private func loadChart(_ requested: ChartRange) {
        if let cached = charts[requested] { chart = .content(cached); return }
        chartTask?.cancel()
        chart = .loading
        chartTask = Task {
            let result = try? await client.getChart(symbol: symbol, range: requested)
            guard !Task.isCancelled else { return }
            if let result { charts[requested] = result }
            chart = result.map(SectionState.content) ?? .unavailable
        }
    }

    func loadWhyMoving() {
        whyMoving = .loading
        Task { whyMoving = (try? await client.getWhyMoving(symbol: symbol)).map(SectionState.content) ?? .unavailable }
    }

    func loadNews() {
        news = .loading
        Task { news = (try? await client.getNews(symbol: symbol)).map(SectionState.content) ?? .unavailable }
    }
}
