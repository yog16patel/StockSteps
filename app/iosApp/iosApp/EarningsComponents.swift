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
            // 5D.4: aligned label/value rows when the three can't share a row (as Compose), instead of three tall stacked blocks.
            StockMetricGrid(items: metrics.enumerated().map { i, m in StockMetricItem(label: m.label, value: m.value, helper: m.detail, quiet: i > 0) }, maxColumns: 3, rowsWhenNarrow: true)
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

/// A collapsed secondary section; since 5D.5.1 the design-system `StockDisclosure` (same behaviour, kept under its Earnings name).
struct EarningsDisclosure<Content: View>: View {
    let title: String
    var systemImage = "info.circle"
    @ViewBuilder var content: () -> Content
    var body: some View { StockDisclosure(title: title, systemImage: systemImage, content: content) }
}

/// Caption lines inside a disclosure (`StockNotes`).
struct EarningsNotes: View {
    let lines: [String]
    var body: some View { StockNotes(lines: lines) }
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

/// 5D.4.1: the one "Show details" of a card; since 5D.5.1 the design-system `StockShowDetails` (same behaviour, kept under its Earnings name).
/// 5D.4.2: `trailing` shares the toggle's row (e.g. "Learn more").
struct EarningsShowDetails<Content: View>: View {
    let explanation: String?
    let hasMore: Bool
    var trailing: AnyView? = nil
    @ViewBuilder var content: () -> Content
    var body: some View { StockShowDetails(explanation: explanation, hasMore: hasMore, trailing: trailing, content: content) }
}

/// 5D.4.1 (option a): a compact insight row — small neutral icon, period, title; the server explanation behind "Show details", unchanged.
struct EarningsInsightRow: View {
    let category: String
    let period: String?
    let title: String
    let explanation: String
    let lessonTitle: String?
    let onLesson: (() -> Void)?
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    var body: some View {
        let _ = typeSize
        let colors = StockStepsTheme.colors(scheme)
        HStack(alignment: .top, spacing: CGFloat(space.sm)) {
            // 5D.4.2: the icon lines up with the title (below the period caption).
            Image(systemName: earningsInsightSymbol(category)).font(StockStepsTheme.font(type.body, relativeTo: .body)).foregroundStyle(colors.primaryText)
                .frame(width: CGFloat(StockStepsTheme.dimensions.icon)).padding(.top, period == nil ? 0 : CGFloat(space.lg + space.xxs)).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                if let period { Text(period).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary) }
                Text(title).font(StockStepsTheme.font(type.bodySemiBold, relativeTo: .subheadline)).foregroundStyle(colors.textTitle)
                    .fixedSize(horizontal: false, vertical: true).accessibilityAddTraits(.isHeader)
                EarningsShowDetails(explanation: nil, hasMore: true) {
                    Text(explanation).stockFont(type.small, relativeTo: .subheadline).foregroundStyle(colors.textBody).fixedSize(horizontal: false, vertical: true)
                    if let lessonTitle, let onLesson {
                        Button(action: onLesson) { Label(lessonTitle, systemImage: "lightbulb").multilineTextAlignment(.leading) }
                            .buttonStyle(.plain).font(StockStepsTheme.font(type.bodyMedium, relativeTo: .subheadline)).foregroundStyle(colors.primaryText)
                            .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget))
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, CGFloat(space.sm))
    }
}
