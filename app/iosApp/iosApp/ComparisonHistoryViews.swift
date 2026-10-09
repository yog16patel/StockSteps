import Charts
import Shared
import SwiftUI

/// Company Comparison Phase 3: historical financial comparison (mirrors Android's `HistoricalComparisonCard`).
/// Free = latest four fiscal quarters of revenue, net income and diluted EPS; 3Y/5Y and growth, margin, EPS
/// growth and the revenue index are StockSteps+, decided by the server. Locked items open a preview, never a
/// request. Values, periods, observations and the table come formatted from the shared Kotlin presenter.
struct HistoricalComparisonView: View {
    let state: ComparisonUiState
    let client: IosScreenerClient
    let onUpgrade: () -> Void
    let onSignIn: () -> Void
    @Environment(\.colorScheme) private var scheme
    @State private var why: (String, String)?
    @State private var allInsights = false

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: 8) {
            Text("Historical comparison").font(.headline).accessibilityAddTraits(.isHeader)
            Text("How each company's reported results changed over time. \(state.historyRange.description_); \(state.historyRange.granularity == .quarterly ? "quarterly" : "annual") figures, as reported.")
                .font(.caption).foregroundStyle(colors.textSecondary)
            HStack(spacing: 8) {
                ForEach(client.historyRanges(), id: \.label) { range in
                    let locked = state.historyLocked(range: range)
                    chip(range.label + (locked ? " · Plus" : ""), selected: state.historyRange == range, colors) { client.comparison.selectHistoryRange(range: range) }
                        .accessibilityLabel(range.description_ + (locked ? ", requires StockSteps+" : ""))
                }
            }
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 120), spacing: 8)], alignment: .leading, spacing: 6) {
                ForEach(client.historyMetrics(), id: \.name) { metric in
                    let locked = state.historyLocked(metric: metric)
                    chip(metric.label + (locked ? " · Plus" : ""), selected: state.historyMetric == metric, colors) { client.comparison.selectHistoryMetric(metric: metric) }
                        .accessibilityLabel(metric.label + (locked ? ", requires StockSteps+" : ""))
                }
            }
            if state.historyLoading { ProgressView().accessibilityLabel("Loading financial history") }
            if let error = state.historyError { StockSectionMessage(message: error, actionTitle: "Try again") { client.comparison.retryHistory() } }
            if let history = state.history, let view = state.historyView() {
                Text(view.title).font(.subheadline.weight(.semibold)).accessibilityAddTraits(.isHeader)
                Text(view.definition).font(.caption).foregroundStyle(colors.textSecondary)
                Text(view.unitNote).font(.caption2).foregroundStyle(colors.textTertiary)
                if view.empty { Text("No values to show for this metric and period.").font(.subheadline) } else { chart(view, colors) }
                table(view, colors)
                let insights = history.insights + view.insights
                if !insights.isEmpty {
                    Text("What does this mean?").font(.caption.weight(.semibold)).foregroundStyle(colors.textSecondary).accessibilityAddTraits(.isHeader)
                    ForEach(Array((allInsights ? insights : Array(insights.prefix(3))).enumerated()), id: \.offset) { _, text in Text("• \(text)").font(.subheadline) }
                    if insights.count > 3 {
                        Button(allInsights ? "Show fewer" : "Show all \(insights.count) observations") { allInsights.toggle() }
                            .font(.subheadline).frame(minHeight: 44).accessibilityValue(allInsights ? "Expanded" : "Collapsed")
                    }
                    Text("These describe reported figures. They don't explain why results changed and aren't predictions or advice.")
                        .font(.caption2).foregroundStyle(colors.textTertiary)
                }
                Text("Source: \(history.source)").font(.caption).foregroundStyle(colors.textSecondary)
                ForEach(Array(Set(view.notes + history.notes)).sorted(), id: \.self) { Text($0).font(.caption).foregroundStyle(colors.textSecondary) }
                if let message = history.access.message { Text(message).font(.caption).foregroundStyle(colors.textSecondary) }
            }
            if !state.historyPlus {
                Text("3Y and 5Y history, growth, margins, EPS growth and the revenue index are part of StockSteps+.").font(.caption2).foregroundStyle(colors.textTertiary)
            }
        }
        .stockCard()
        .alert(why?.0 ?? "", isPresented: Binding(get: { why != nil }, set: { if !$0 { why = nil } })) { Button("OK") { why = nil } } message: { Text(why?.1 ?? "") }
        .alert(state.historyUpsell?.title ?? "", isPresented: Binding(get: { state.historyUpsell != nil }, set: { if !$0 { client.comparison.dismissHistoryUpsell() } })) {
            if state.historyUpsell?.signIn == true {
                Button("Sign In") { client.comparison.dismissHistoryUpsell(); onSignIn() }
            } else {
                Button("See StockSteps+") { client.comparison.dismissHistoryUpsell(); onUpgrade() }
            }
            Button("Not now", role: .cancel) { client.comparison.dismissHistoryUpsell() }
        } message: {
            if let upsell = state.historyUpsell {
                Text(([upsell.body] + upsell.benefits.map { "• \($0)" } + [upsell.footnote]).joined(separator: "\n"))
            }
        }
    }

    private func chip(_ title: String, selected: Bool, _ colors: StockColors, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.subheadline).lineLimit(1).minimumScaleFactor(0.8)
                .padding(.horizontal, 12).frame(minHeight: 44)
                .foregroundStyle(selected ? Color.white : colors.textPrimary)
                .background(selected ? colors.primary : colors.surfaceSecondary, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private struct Point: Identifiable {
        let id: String
        let index: Int
        let value: Double
        let symbol: String
        let segment: String
    }

    /// One line per company; a missing period splits the line (gaps are never joined or filled).
    @ViewBuilder private func chart(_ view: HistoryView, _ colors: StockColors) -> some View {
        let points: [Point] = view.series.flatMap { series -> [Point] in
            var segment = 0
            var result: [Point] = []
            for (i, raw) in series.values.enumerated() {
                if let number = (raw as Any?) as? NSNumber {
                    result.append(Point(id: "\(series.symbol)-\(i)", index: i, value: number.doubleValue, symbol: series.label, segment: "\(series.symbol)#\(segment)"))
                } else { segment += 1 }
            }
            return result
        }
        Chart {
            if let reference = view.reference {
                RuleMark(y: .value(view.referenceLabel ?? "Reference", reference.doubleValue)).foregroundStyle(colors.textTertiary).lineStyle(StrokeStyle(lineWidth: 1, dash: [6, 4]))
            }
            ForEach(points) { point in
                LineMark(x: .value("Period", point.index), y: .value(view.title, point.value), series: .value("Segment", point.segment))
                    .foregroundStyle(by: .value("Company", point.symbol))
                    .lineStyle(by: .value("Company", point.symbol))
                PointMark(x: .value("Period", point.index), y: .value(view.title, point.value))
                    .foregroundStyle(by: .value("Company", point.symbol))
                    .symbol(by: .value("Company", point.symbol))
            }
        }
        .chartXAxis(.hidden)
        // Compact labels ("150B"): the default axis printed revenue as "1.5E11" (Phase 5C.1).
        .chartYAxis { AxisMarks { AxisGridLine(); AxisValueLabel(format: FloatingPointFormatStyle<Double>.number.notation(.compactName)) } }
        .frame(height: 180)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(view.description_)
        if let first = view.xLabels.first, let last = view.xLabels.last {
            HStack { Text(first).font(.caption2); Spacer(); Text(last).font(.caption2) }.foregroundStyle(colors.textTertiary)
        }
    }

    /// Newest first; each cell shows the company's own fiscal label and period end (equal-width columns).
    private func table(_ view: HistoryView, _ colors: StockColors) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) { ForEach(view.symbols, id: \.self) { Text($0).font(.caption.weight(.semibold)).foregroundStyle(colors.primaryText).frame(maxWidth: .infinity, alignment: .leading) } }
                .accessibilityAddTraits(.isHeader)
            ForEach(Array(view.rows.enumerated()), id: \.offset) { _, row in
                Divider()
                Text(row.title).font(.caption2).foregroundStyle(colors.textTertiary)
                HStack(alignment: .top, spacing: 8) {
                    ForEach(Array(zip(view.symbols, row.cells).enumerated()), id: \.offset) { _, pair in
                        let (symbol, cell) = pair
                        VStack(alignment: .leading, spacing: 1) {
                            Text(cell.text + (cell.explanation != nil ? " ⓘ" : "")).font(.subheadline.monospacedDigit())
                                .foregroundStyle(cell.explanation != nil ? colors.textTertiary : colors.textPrimary).lineLimit(1).minimumScaleFactor(0.8)
                            if let period = cell.period { Text(period).font(.caption2).foregroundStyle(colors.textSecondary).lineLimit(2) }
                        }
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .topLeading)
                        .contentShape(Rectangle())
                        .onTapGesture { if let e = cell.explanation { why = ("\(symbol) · \(cell.period ?? row.title)", e) } }
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(row.title + ": " + zip(view.symbols, row.cells).map { symbol, cell in
                    "\(symbol) " + (cell.explanation == nil ? "\(cell.text), \(cell.period ?? "")" : "not available, \(cell.explanation ?? "")") }.joined(separator: "; ") + (row.note.map { ". \($0)" } ?? ""))
                if let note = row.note { Text(note).font(.caption2).foregroundStyle(colors.cautionText) }
            }
        }
    }
}
