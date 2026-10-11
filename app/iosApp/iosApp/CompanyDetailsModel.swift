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
    /// Why the chart can't be drawn (connection / temporary / no history); nil while loading or when it draws (Phase 4C.2).
    private(set) var chartIssue: ChartIssue?
    /// `.content(nil)`: no source-backed explanation exists, so the section is hidden.
    private(set) var whyMoving: SectionState<WhyMoving?> = .loading
    private(set) var news: SectionState<[NewsUiModel]> = .loading
    @ObservationIgnored let client: IosCompanyDetailsClient
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
        if let cached = charts[requested] {
            chart = .content(cached); chartIssue = ChartPresentation.shared.issue(chart: cached); return
        }
        chartTask?.cancel()
        chart = .loading
        chartIssue = nil
        chartTask = Task {
            guard let result = try? await client.loadChart(symbol: symbol, range: requested), !Task.isCancelled else { return }
            if let loaded = result.chart { charts[requested] = loaded }
            chart = result.chart.map(SectionState.content) ?? .unavailable
            chartIssue = result.issue
        }
    }

    func loadWhyMoving() {
        whyMoving = .loading
        Task {
            do { whyMoving = .content(try await client.getWhyMoving(symbol: symbol)) } catch { whyMoving = .unavailable }
        }
    }

    func loadNews() {
        news = .loading
        Task { news = (try? await client.getNews(symbol: symbol)).map(SectionState.content) ?? .unavailable }
    }
}
