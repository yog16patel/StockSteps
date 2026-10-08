import SwiftUI
import Charts
import Shared

struct PortfolioHistoryChart: View {
    let state: PortfolioUiState
    let onHistory: (String) -> Void
    @State private var selected: String?
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
        VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.sm)) {
            ScrollView(.horizontal) {
                HStack {
                    ForEach(["1D", "1W", "1M", "3M", "1Y", "ALL"], id: \.self) { range in
                        Button(range) { onHistory(range) }.buttonStyle(.bordered)
                            .tint(state.historyRange == range ? .blue : .secondary)
                    }
                }
            }
            if state.historyLoading { ProgressView("Loading dated values") }
            if state.history.isEmpty { Text("Choose a period to load history. Missing prices or investment history are never estimated.").font(.subheadline) }
            else {
                if let selection { Text("\(selection.date) · \(state.currency) \(selection.totalValue ?? "Unavailable")").monospacedDigit() }
                Chart {
                    ForEach(points) { point in
                        if let value = point.value {
                            LineMark(x: .value("Date", point.id), y: .value("Value", value), series: .value("Segment", point.segment))
                            PointMark(x: .value("Date", point.id), y: .value("Value", value)).symbolSize(8)
                        }
                    }
                }.frame(height: CGFloat(StockStepsTheme.dimensions.chartHeight))
                    .chartXSelection(value: $selected)
                    .accessibilityLabel("Portfolio value in \(state.currency), from \(state.history.first?.date ?? "") to \(state.history.last?.date ?? ""). Missing values are gaps.")
            }
            Text(state.historyNotice).font(.caption)
        }
    }
}
