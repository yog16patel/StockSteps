import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography

// Phase 5D.3 Earnings building blocks (SwiftUI), mirroring Compose `EarningsComponents.kt`. Display only: every value is already formatted.

/// The three key numbers as the shared metric grid (`StockLayout.metricColumns`: fewer columns, one at large text, never truncated).
struct EarningsKeyMetrics: View {
    let metrics: [MetricLine]
    var body: some View {
        if !metrics.isEmpty {
            StockMetricGrid(items: metrics.map { StockMetricItem(label: $0.label, value: $0.value, helper: $0.detail) }, maxColumns: 3)
        }
    }
}

/// The measured price change with the app's arrow and gain/loss style (the sign and arrow carry the meaning; colour is secondary).
struct EarningsReactionHeadline: View {
    let headline: String
    let direction: PriceDirection?
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        if let direction, direction != .unavailable {
            StockPriceChange(percentage: headline, direction: direction, style: type.numberMedium, lineLimit: nil)
        } else {
            Text(headline).font(StockStepsTheme.font(type.numberMedium, relativeTo: .footnote)).foregroundStyle(StockStepsTheme.colors(scheme).textPrimary)
        }
    }
}

/// A collapsed secondary section (methodology, sources, disclosures): a 48 pt button that announces its state; nothing inside is removed.
struct EarningsDisclosure<Content: View>: View {
    let title: String
    var systemImage = "info.circle"
    @ViewBuilder var content: () -> Content
    @State private var open = false
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    var body: some View {
        let _ = typeSize
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Button { open.toggle() } label: {
                HStack(spacing: CGFloat(space.xs)) {
                    Image(systemName: systemImage).accessibilityHidden(true)
                    Text(title).multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                    Image(systemName: open ? "chevron.down" : "chevron.right").accessibilityHidden(true)
                }
                .font(StockStepsTheme.font(type.label, relativeTo: .subheadline))
                .foregroundStyle(colors.primaryText)
                .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityValue(open ? "Expanded" : "Collapsed")
            if open { content() }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Caption lines inside a disclosure.
struct EarningsNotes: View {
    let lines: [String]
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        ForEach(lines, id: \.self) {
            Text($0).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(StockStepsTheme.colors(scheme).textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// Neutral SF Symbol per insight category (`EarningsDisplay.insightIcon`; never a direction cue).
func earningsInsightSymbol(_ category: String) -> String {
    switch EarningsDisplay.shared.insightIcon(category: category) {
    case "percent": "percent"
    case "chart": "chart.bar"
    case "layers": "square.stack.3d.up"
    case "info": "info.circle"
    default: "lightbulb"
    }
}
