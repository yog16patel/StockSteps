import Observation
import Shared
import SwiftUI

/// Full Financials + Valuation for one company (Annual/Quarterly), mirroring Android's
/// CompanyFinancialsScene. Reached from Company Details "See Financials" / "See Details".
@MainActor
@Observable
final class CompanyFinancialsModel {
    let symbol: String
    private(set) var period: FinancialPeriod = .annual
    private(set) var fundamentals: CompanyFundamentals?
    private(set) var loading = true
    private(set) var failed = false
    @ObservationIgnored private let client: IosCompanyDetailsClient
    @ObservationIgnored private var task: Task<Void, Never>?

    init(symbol: String, client: IosCompanyDetailsClient) {
        self.symbol = symbol
        self.client = client
    }

    var financials: FinancialsUiState { FinancialsPresenter.shared.build(fundamentals: fundamentals, loading: loading, failed: failed) }
    var valuation: [FinancialMetric] { fundamentals.map { client.valuation(symbol: symbol, fundamentals: $0) } ?? [] }

    func select(_ newPeriod: FinancialPeriod) {
        guard newPeriod != period else { return }
        // A new period clears old facts so annual values never appear under Quarterly.
        period = newPeriod
        fundamentals = nil
        load()
    }

    func load() {
        task?.cancel()
        loading = true
        failed = false
        task = Task {
            do {
                let result = try await client.getFundamentals(symbol: symbol, period: period)
                guard !Task.isCancelled else { return }
                fundamentals = result
            } catch {
                guard !Task.isCancelled else { return }
                failed = true
            }
            loading = false
        }
    }
}

struct CompanyFinancialsScene: View {
    @Environment(\.colorScheme) private var scheme
    @State private var model: CompanyFinancialsModel
    @State private var info: String?

    init(symbol: String, client: IosCompanyDetailsClient) {
        _model = State(initialValue: CompanyFinancialsModel(symbol: symbol, client: client))
    }

    var body: some View {
        let space = StockStepsTheme.spacing
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(space.lg)) {
                HStack(spacing: CGFloat(space.sm)) {
                    ForEach(FinancialPeriod.entries, id: \.self) { period in
                        StockChip(title: period.label, selected: period == model.period) { model.select(period) }
                    }
                }
                ForEach(model.financials.sections, id: \.title) { section in
                    CompanyFinancialsSectionView(section: section, onRetry: model.load, onInfo: { info = $0 })
                }
                ForEach(model.valuation, id: \.id) { metric in
                    CompanyDetailCard(title: metric.label) {
                        Text(CompanyDetailPresenter.shared.metricValue(metric: metric)).font(.title3.monospacedDigit())
                        Text("\(metric.historicalLabel): \(CompanyDetailPresenter.shared.number(value: metric.historicalAverage))")
                        if let range = metric.historicalRange { Text(range).font(.footnote) }
                        if let context = CompanyDetailPresenter.shared.historicalContext(metric: metric) { Text(context) }
                    }
                    .onTapGesture { info = metric.id }
                }
            }
            .frame(maxWidth: CGFloat(StockStepsTheme.dimensions.contentMaxWidth), alignment: .leading)
            .padding(CGFloat(space.screen))
            .frame(maxWidth: .infinity)
        }
        .background(StockStepsTheme.colors(scheme).appBackground.ignoresSafeArea())
        .navigationTitle("Financials")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: Binding(get: { info != nil }, set: { if !$0 { info = nil } })) {
            if let id = info {
                MetricInfoSheet(id: id, metrics: model.valuation, onClose: { info = nil })
                    .presentationDetents([.medium, .large])
            }
        }
        .task { model.load() }
    }
}

/// Every recent story for one company, reached from Company Details "View All".
struct CompanyNewsScene: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.openURL) private var openURL
    let symbol: String
    let client: IosCompanyDetailsClient
    @State private var news: SectionState<[NewsUiModel]> = .loading

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        ScrollView {
            VStack(spacing: 0) {
                switch news {
                case .loading: ForEach(0..<3, id: \.self) { _ in StockNewsCardSkeleton() }
                case .unavailable: StockSectionMessage(message: "News is temporarily unavailable.", actionTitle: "Try again") { Task { await load() } }
                case .content(let articles):
                    if articles.isEmpty { StockSectionMessage(message: "No recent company news.") }
                    ForEach(Array(articles.enumerated()), id: \.element.id) { index, article in
                        if index > 0 { StockDivider() }
                        StockNewsCard(model: article, onOpen: { openURL($0) })
                    }
                }
            }
            .padding(.horizontal, CGFloat(StockStepsTheme.spacing.md))
            .stockCard(padding: 0, bordered: false)
            .frame(maxWidth: CGFloat(StockStepsTheme.dimensions.contentMaxWidth))
            .padding(CGFloat(StockStepsTheme.spacing.screen))
            .frame(maxWidth: .infinity)
        }
        .background(colors.appBackground.ignoresSafeArea())
        .navigationTitle("Company news")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func load() async {
        news = .loading
        news = (try? await client.getAllNews(symbol: symbol)).map(SectionState.content) ?? .unavailable
    }
}
