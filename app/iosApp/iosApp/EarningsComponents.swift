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

/// 5D.4.1: the one "Show details" of a card (as Compose `EarningsShowDetails`). Visible: the first sentence of `explanation`. Collapsed by default:
/// the rest of the sentence and `content` (secondary lines, notes, lesson). No toggle when there is nothing to reveal.
struct EarningsShowDetails<Content: View>: View {
    let explanation: String?
    let hasMore: Bool
    /// 5D.4.2: an action sharing the toggle's row (e.g. "Learn more"); moves below it when they don't fit.
    var trailing: AnyView? = nil
    @ViewBuilder var content: () -> Content
    @State private var open = false
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    var body: some View {
        let _ = typeSize
        let colors = StockStepsTheme.colors(scheme)
        let parts = explanation.map { EarningsDisplay.shared.firstSentence(text: $0) }
        let first = parts?.first as? String
        let rest = parts?.second as? String
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            if let first {
                Text(open && rest != nil ? "\(first) \(rest!)" : first).stockFont(type.small, relativeTo: .subheadline).foregroundStyle(colors.textBody)
                    .fixedSize(horizontal: false, vertical: true)
            }
            let toggle = rest != nil || hasMore
            if toggle && open { content() }
            if toggle || trailing != nil {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: CGFloat(space.md)) { toggleButton(toggle, colors); trailing }
                    VStack(alignment: .leading, spacing: 0) { toggleButton(toggle, colors); trailing }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder private func toggleButton(_ shown: Bool, _ colors: StockColors) -> some View {
        if shown {
                Button { open.toggle() } label: {
                    HStack(spacing: CGFloat(space.xxs)) {
                        Text(open ? "Hide details" : "Show details")
                        Image(systemName: open ? "chevron.up" : "chevron.down").accessibilityHidden(true)
                    }
                    .font(StockStepsTheme.font(type.label, relativeTo: .subheadline)).foregroundStyle(colors.primaryText)
                    .frame(minHeight: CGFloat(StockStepsTheme.dimensions.touchTarget)).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityValue(open ? "Expanded" : "Collapsed")
        }
    }
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
