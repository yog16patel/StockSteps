import SwiftUI
import Charts
import Shared

/// Account value history inside the overview card (Portfolio Phase B), mirroring the Compose `PortfolioHistorySection`: heading + note, period
/// pills, then one `PortfolioOverviewDisplay.historyState` — "Show 1M" before any request (history stays on demand), loading, the chart (line
/// only, gaps for missing values, high/low axis labels from exact decimals, selection line above), a flat or single-value summary instead of a
/// misleading line, empty, or an error with Retry. It plots account value including cash and deposits, never investment performance.
struct PortfolioHistorySection: View {
    let state: PortfolioUiState
    let requested: String?
    let onPeriod: (String) -> Void
    let onRetry: () -> Void
    @State private var selected: String?
    @Environment(\.colorScheme) private var scheme
    // Read in body so a live Dynamic Type change re-renders the section (StockStepsTheme.font resolves the current size).
    @Environment(\.dynamicTypeSize) private var typeSize
    private let type = StockStepsTheme.typography
    private let space = StockStepsTheme.spacing
    private let display = PortfolioDisplay.shared
    private let overview = PortfolioOverviewDisplay.shared

    private struct Point: Identifiable {
        let id: String
        let value: Double?
        let segment: Int
    }
    /// Consecutive runs get their own series so a missing value stays a gap (never interpolated).
    private var points: [Point] {
        var segment = 0
        return state.history.map { point in
            if point.totalValue == nil { segment += 1 }
            return Point(id: point.date, value: point.totalValue.flatMap(Double.init), segment: segment)
        }
    }
    private var selection: PortfolioValuation? { state.history.first { $0.date == selected } ?? state.history.last }
    private var yDomain: ClosedRange<Double> {
        let values = points.compactMap(\.value)
        guard let low = values.min(), let high = values.max(), high > low else { return 0...1 }
        let pad = (high - low) * 0.05
        return (low - pad)...(high + pad)
    }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let history = overview.historyState(state: state, requested: requested)
        let _ = typeSize
        VStack(alignment: .leading, spacing: CGFloat(space.sm)) {
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                Text(display.CHART_TITLE).font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline)).foregroundStyle(colors.textTitle)
                    .accessibilityAddTraits(.isHeader)
                Text(display.CHART_NOTE).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
            }
            StockPillSelector(options: overview.PERIODS, selected: overview.selectedPeriod(state: state, requested: requested), label: { $0 }, onSelect: onPeriod)
            switch history {
            case .notRequested:
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                    Text(overview.NOT_REQUESTED_MESSAGE).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textSupporting)
                    Button(overview.SHOW_ACTION) { onPeriod(overview.SHOW_PERIOD) }.buttonStyle(.stockSecondary)
                }
            case .loading:
                ProgressView().frame(maxWidth: .infinity).accessibilityLabel(overview.LOADING_MESSAGE)
            case .loaded:
                if let selection {
                    Text(overview.pointDetail(value: selection)).font(StockStepsTheme.font(type.numberLabel, relativeTo: .footnote))
                        .foregroundStyle(colors.textPrimary).accessibilityHidden(true)
                }
                chart(colors)
                if overview.hasGaps(history: state.history) {
                    Text(overview.GAPS_NOTE).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textMeta)
                }
            case .flat:
                Text(overview.flatSummary(state: state) ?? "").font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textBody)
            case .insufficient:
                Text(overview.insufficientSummary(state: state)).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textSupporting)
            case .empty:
                Text(overview.EMPTY_MESSAGE).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.textSupporting)
            case .error:
                VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
                    Text(overview.ERROR_MESSAGE).font(StockStepsTheme.font(type.small, relativeTo: .subheadline)).foregroundStyle(colors.negativeText)
                    Button(overview.RETRY, action: onRetry).buttonStyle(.stockSecondary)
                }
            default:
                EmptyView()
            }
        }
    }

    /// Same spec as Compose `StockTrendChart`: one brand-blue line (colour never encodes gain/loss), no point marks, a rule on the selected day,
    /// first/last dates and the high/low values as axis labels.
    private func chart(_ colors: StockColors) -> some View {
        let yLabels = overview.yLabels(history: state.history)
        let values = points.compactMap(\.value)
        return Chart {
            ForEach(points) { point in
                if let value = point.value {
                    LineMark(x: .value("Date", point.id), y: .value("Value", value), series: .value("Segment", point.segment))
                        .foregroundStyle(colors.primary)
                        .lineStyle(StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                }
            }
            if let selected, let value = points.first(where: { $0.id == selected })?.value {
                RuleMark(x: .value("Date", selected)).foregroundStyle(colors.border)
                PointMark(x: .value("Date", selected), y: .value("Value", value)).symbolSize(40).foregroundStyle(colors.primary)
            }
        }
        .frame(height: CGFloat(StockStepsTheme.dimensions.chartHeight))
        .chartYScale(domain: yDomain)
        .chartYAxis {
            AxisMarks(position: .trailing, values: [values.max(), values.min()].compactMap { $0 }) { value in
                AxisValueLabel {
                    if let number = value.as(Double.self), let index = [values.max(), values.min()].firstIndex(of: number), index < yLabels.count {
                        Text(verbatim: yLabels[index]).font(StockStepsTheme.font(type.tiny, relativeTo: .footnote)).fixedSize()
                    }
                }
            }
        }
        // First and last dates only (as Compose): categorical date labels collide on long ranges.
        .chartXAxis {
            AxisMarks(values: [state.history.first?.date, state.history.last?.date].compactMap { $0 }) { value in
                AxisValueLabel {
                    if let date = value.as(String.self) {
                        Text(PortfolioDates.shared.shortDate(value: date)).font(StockStepsTheme.font(type.tiny, relativeTo: .footnote)).fixedSize()
                    }
                }
            }
        }
        .chartXSelection(value: $selected)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(overview.chartDescription(state: state))
    }
}
