import SwiftUI
import Charts
import Shared

/// Portfolio performance card (Phase 3): period pills, the dated account-value chart (cash included, missing observations are gaps —
/// never interpolated) or a compact empty state. History loads only when a period is chosen, so no pill looks selected until dated
/// values are loading or shown. Same rules as the Compose `PortfolioPerformanceCard` (`PortfolioChartRules`).
struct PortfolioHistoryChart: View {
    let state: PortfolioUiState
    let onHistory: (String) -> Void
    @State private var selected: String?
    @Environment(\.colorScheme) private var scheme
    private let type = StockStepsTheme.typography
    private let space = StockStepsTheme.spacing
    private var selection: PortfolioValuation? { state.history.first { $0.date == selected } ?? state.history.last }
    private struct Point: Identifiable {
        let id: String
        let value: Double?
        let segment: Int
    }
    private var points: [Point] {
        var segment = 0
        return state.history.map { point in
            if point.totalValue == nil { segment += 1 }
            return Point(id: point.date, value: point.totalValue.flatMap(Double.init), segment: segment)
        }
    }
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let active = !state.history.isEmpty || state.historyLoading
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            Text("Portfolio performance").font(StockStepsTheme.font(type.cardTitle)).foregroundStyle(colors.textTitle).accessibilityAddTraits(.isHeader)
            StockPillSelector(options: ["1D", "1W", "1M", "3M", "1Y", "ALL"], selected: active ? state.historyRange : "", label: { $0 }, onSelect: onHistory)
            if state.historyLoading { ProgressView().frame(maxWidth: .infinity).accessibilityLabel("Loading dated values") }
            if state.history.isEmpty {
                if !state.historyLoading {
                    HStack(spacing: CGFloat(space.md)) {
                        StockIconTile(systemName: "chart.line.uptrend.xyaxis", container: colors.surfaceSecondary, content: colors.iconSecondary)
                        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                            Text("No performance history shown").font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textTitle)
                            Text("Choose a period to load dated values.").font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
                        }
                    }
                    .accessibilityElement(children: .combine)
                }
            } else if PortfolioChartRules.shared.drawableHistory(history: state.history) {
                if let selection {
                    Text("\(selection.date) · \(state.currency) \(selection.totalValue.map { PortfolioFormat.shared.amount(value: $0) } ?? "Unavailable")")
                        .font(StockStepsTheme.font(type.numberLabel)).foregroundStyle(colors.textSupporting)
                }
                Chart {
                    ForEach(points) { point in
                        if let value = point.value {
                            LineMark(x: .value("Date", point.id), y: .value("Value", value), series: .value("Segment", point.segment))
                                .foregroundStyle(colors.primary)
                            PointMark(x: .value("Date", point.id), y: .value("Value", value)).symbolSize(8).foregroundStyle(colors.primary)
                        }
                    }
                }.frame(height: CGFloat(StockStepsTheme.dimensions.chartHeight))
                    .chartXSelection(value: $selected)
                    .accessibilityLabel("Portfolio value in \(state.currency), from \(state.history.first?.date ?? "") to \(state.history.last?.date ?? ""). Missing values are gaps.")
            } else {
                // Fewer than two dated values: a line chart would be an almost empty box, so state the one value instead.
                let latest = state.history.last { $0.totalValue != nil }
                Text(latest.map { "Only one dated value so far: \($0.date) · \($0.currency.name) \(PortfolioFormat.shared.amount(value: $0.totalValue))" } ?? "No dated values in this period.")
                    .font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSupporting)
            }
            Text(state.historyNotice).font(StockStepsTheme.font(type.caption)).foregroundStyle(colors.textMeta)
        }
        .stockCard()
    }
}
