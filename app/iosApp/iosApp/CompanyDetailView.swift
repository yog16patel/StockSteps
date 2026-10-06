import Shared
import SwiftUI

enum NativeCompanyDetailAction {
    case selectTab(CompanyDetailTab)
    case openMetric(String)
    case closeMetric
    case refreshFundamentals
    case selectFinancialPeriod(String)
    case refreshNews
}

struct CompanyDetailView: View {
    @Environment(\.colorScheme) private var scheme
    let state: StockSearchUiState
    let saved: Bool
    let watchlistEnabled: Bool
    let watchlistError: String?
    let onToggleWatchlist: () -> Void
    let onRetryQuote: () -> Void
    let onRetryProfile: () -> Void
    let onAction: (NativeCompanyDetailAction) -> Void
    private let tabs: [CompanyDetailTab] = [.overview, .financials, .valuation, .news]
    private var detail: CompanyDetailUiState { state.detail }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.large), pinnedViews: [.sectionHeaders]) {
                CompanyHeaderView(detail: detail, loading: state.isLoadingQuote, error: state.quoteError,
                    saved: saved, enabled: watchlistEnabled, onToggle: onToggleWatchlist, onRetry: onRetryQuote)
                if let error = watchlistError { Text(error).foregroundStyle(.red) }
                Section {
                    tabContent
                    Text("Educational information, not a stock recommendation.").font(.footnote).foregroundStyle(.secondary)
                } header: {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: CGFloat(StockStepsTheme.spacing.large)) {
                            ForEach(tabs, id: \.self) { tab in
                                Button { onAction(.selectTab(tab)) } label: {
                                    VStack(spacing: CGFloat(StockStepsTheme.spacing.small)) {
                                        Text(tab.title).font(.subheadline.weight(state.detailTab == tab ? .semibold : .regular)).fixedSize()
                                        Capsule().fill(state.detailTab == tab ? Color.blue : .clear).frame(height: 2)
                                    }
                                    .padding(.vertical, CGFloat(StockStepsTheme.spacing.small))
                                }
                                .buttonStyle(.plain)
                                .accessibilityAddTraits(state.detailTab == tab ? .isSelected : [])
                            }
                        }
                    }
                    .background(.background)
                }
            }
            .padding(CGFloat(StockStepsTheme.spacing.large))
            .frame(maxWidth: CompanyDetailLayout.readingWidth, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .background(StockStepsTheme.color(StockStepsTheme.palette(scheme).background))
        .sheet(isPresented: Binding(get: { state.metricInfo != nil }, set: { if !$0 { onAction(.closeMetric) } })) {
            if let id = state.metricInfo {
                MetricInfoSheet(id: id, metrics: detail.keyMetrics + detail.valuation + detail.financials.flatMap { $0.metrics }, onClose: { onAction(.closeMetric) })
                    .presentationDetents([.medium, .large])
            }
        }
    }

    @ViewBuilder private var tabContent: some View {
        switch state.detailTab {
        case .overview:
            CompanyDetailCard(title: "What does this company do?") {
                if state.isLoadingProfile { ProgressView("Loading company information…") }
                if let error = state.profileError { Text(error); Button("Retry company information", action: onRetryProfile) }
                Text(detail.description_?.isEmpty == false ? detail.description_! : "A company description is not available yet.")
                if let industry = detail.industry { Text(industry).font(.caption) }
            }
            CompanyDetailCard(title: "Stock price history") {
                HStack { ForEach(["1D", "1W", "1M", "3M", "1Y", "5Y"], id: \.self) { period in Text(period).font(.caption).foregroundStyle(.secondary) } }
                Text("Price history is not available from the current integration.")
            }
            CompanyDetailCard(title: "Company snapshot") {
                Text("Financial evidence").font(.headline)
                ForEach(detail.snapshot.scores, id: \.category) { score in Text("\(score.category): \(score.context ?? "Unavailable")") }
                Text("These are reported facts and calculations, not an investment score.")
                Button("How we calculate this →") { onAction(.openMetric("snapshot")) }
            }
            CompanyDetailCard(title: "Key metrics") {
                ForEach(detail.keyMetrics.filter { $0.value != nil }, id: \.id) { metric in metricRow(metric) }
                if detail.keyMetrics.allSatisfy({ $0.value == nil }) { Text("Key financial metrics are unavailable from the current integration.") }
                Button("View all metrics →") { onAction(.selectTab(.financials)) }
            }
            CompanyDetailCard(title: "Risks to understand") {
                if detail.risks.isEmpty { Text("No verified risk analysis is available. This does not mean the company has no risks.") }
                ForEach(detail.risks, id: \.title) { risk in Text("\(risk.title): \(risk.explanation)\nSource: \(risk.source)") }
            }
        case .financials:
            Picker("Financial period", selection: Binding(get: { state.financialPeriod }, set: { onAction(.selectFinancialPeriod($0)) })) {
                Text("Annual").tag("annual")
                Text("Quarterly").tag("quarter")
            }.pickerStyle(.segmented).disabled(state.isLoadingFundamentals)
            if let message = state.financials.refreshMessage {
                Text(message).font(.footnote).foregroundStyle(.secondary)
                Button("Try again") { onAction(.refreshFundamentals) }.tint(.blue)
            }
            ForEach(state.financials.sections, id: \.title) { section in
                CompanyFinancialsSectionView(section: section,
                    onRetry: { onAction(.refreshFundamentals) },
                    onInfo: { onAction(.openMetric($0)) })
            }
        case .valuation:
            CompanyDetailCard(title: "Price in context") {
                Text("Compare valuation with the company's history, industry, peers and growth. A single ratio cannot establish whether a stock is cheap or expensive.")
                Text("Historical comparisons use eligible fiscal-year observations.").font(.footnote)
            }
            ForEach(detail.valuation, id: \.id) { metric in
                CompanyDetailCard(title: metric.label) {
                    metricRow(metric)
                    Text("\(metric.historicalLabel): \(CompanyDetailPresenter.shared.number(value: metric.historicalAverage))")
                    if let range = metric.historicalRange { Text(range).font(.footnote) }
                    Text("Industry: \(CompanyDetailPresenter.shared.number(value: metric.industryAverage))")
                    if let context = CompanyDetailPresenter.shared.historicalContext(metric: metric) { Text(context) }
                }
            }
        case .news:
            CompanyDetailCard(title: "Company news") {
                if state.isLoadingNews { ProgressView("Loading company news…") }
                if let error = state.newsError { Text(error) }
                if !state.isLoadingNews && state.newsError == nil && state.companyNews.isEmpty { Text("No recent company news is available.") }
                Button("Refresh news") { onAction(.refreshNews) }
            }
            ForEach(state.companyNews, id: \.url) { article in NewsCard(article: article) }
        default: EmptyView()
        }
    }
    private func metricRow(_ metric: FinancialMetric) -> some View {
        Button { onAction(.openMetric(metric.id)) } label: {
            VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.small)) {
                Text("\(metric.label): \(CompanyDetailPresenter.shared.metricValue(metric: metric))").font(.headline)
                Text(metric.context ?? "Tap to learn what this means").font(.caption)
            }.frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, CGFloat(StockStepsTheme.spacing.small))
        }
    }
}

enum CompanyDetailLayout { static let readingWidth: CGFloat = 760 }
