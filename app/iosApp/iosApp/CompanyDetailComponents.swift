import Shared
import SwiftUI

struct CompanyDetailCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.medium)) {
            Text(title).font(.title3.bold())
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(CGFloat(StockStepsTheme.spacing.large))
        .background(.background, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.large)))
    }
}

struct CompanyHeaderView: View {
    @Environment(\.colorScheme) private var scheme
    let detail: CompanyDetailUiState
    let loading: Bool
    let error: String?
    let saved: Bool
    let enabled: Bool
    let onToggle: () -> Void
    let onRetry: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.small)) {
            if let url = detail.logoUrl, url.hasPrefix("https://"), let url = URL(string: url) {
                AsyncImage(url: url) { image in image.resizable().scaledToFit() } placeholder: { Image(systemName: "building.2") }
                    .frame(width: CGFloat(StockStepsTheme.spacing.extraLarge * 2), height: CGFloat(StockStepsTheme.spacing.extraLarge * 2))
                    .accessibilityLabel("\(detail.name) logo")
            }
            Text(detail.name).font(StockStepsTheme.font(StockStepsTheme.typography.headline, relativeTo: .title1))
            Text([detail.symbol, detail.listing, detail.sector].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")).foregroundStyle(.secondary)
            if loading { ProgressView("Loading quote…") }
            if let error { Text(error); Button("Retry quote", action: onRetry).stockStepsGlassButton() }
            Text(detail.price).font(.largeTitle.bold())
            Text(detail.priceChange).foregroundStyle(changeColor)
            Button(saved ? "Remove from WatchList" : "Add to WatchList", action: onToggle)
                .stockStepsGlassButton().disabled(!enabled)
        }
    }
    private var changeColor: Color {
        let palette = StockStepsTheme.palette(scheme)
        switch detail.direction {
        case .up: return StockStepsTheme.color(palette.positive)
        case .down: return StockStepsTheme.color(palette.negative)
        default: return StockStepsTheme.color(palette.textSecondary)
        }
    }
}

struct MetricInfoSheet: View {
    let id: String
    let metrics: [FinancialMetric]
    let onClose: () -> Void
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.large)) {
                    if id == "snapshot" {
                        Text("The snapshot displays verified financial facts and deterministic comparisons. Missing or unsuitable values remain unavailable. No investment scores or buy/sell recommendations are generated.")
                    } else {
                        let metric = metrics.first { $0.id == id } ?? MetricEducation.shared.metric(id: id)
                        Text(metric.explanation)
                        Text("Company: \(CompanyDetailPresenter.shared.metricValue(metric: metric))")
                        Text("Industry: \(CompanyDetailPresenter.shared.number(value: metric.industryAverage))")
                        Text("\(metric.historicalLabel): \(CompanyDetailPresenter.shared.number(value: metric.historicalAverage))")
                        if let context = CompanyDetailPresenter.shared.historicalContext(metric: metric) { Text(context) }
                    }
                }.padding(CGFloat(StockStepsTheme.spacing.extraLarge))
            }
            .stockStepsTopBar(.screen(id == "snapshot" ? "How we calculate this" : (metrics.first { $0.id == id } ?? MetricEducation.shared.metric(id: id)).label, backButton: .close), onBack: onClose)
        }
    }
}
