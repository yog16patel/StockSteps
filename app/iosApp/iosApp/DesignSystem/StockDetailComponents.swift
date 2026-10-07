import Shared
import SwiftUI

// SwiftUI counterparts of the Compose detail components (StockTag, StockBadge, StockIconTile,
// StockRangeBar, StockLineChart, StockAssessmentRow, StockPillSelector, StockInfoRow).

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

/// Container/content colors for a semantic tone (contrast-safe text on each container).
func toneColors(_ tone: FactTone, _ colors: StockColors) -> (Color, Color) {
    switch tone {
    case .positive: (colors.positiveContainer, colors.positiveText)
    case .negative: (colors.negativeContainer, colors.negativeText)
    case .caution: (colors.warningContainer, colors.cautionText)
    default: (colors.surfaceSecondary, colors.textSecondary)
    }
}

/// Non-interactive neutral label (sector, industry, size band).
struct StockTag: View {
    @Environment(\.colorScheme) private var scheme
    let text: String
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        Text(text)
            .font(StockStepsTheme.font(type.caption, relativeTo: .caption1))
            .foregroundStyle(colors.textSecondary)
            .lineLimit(1)
            .padding(.horizontal, CGFloat(space.sm))
            .padding(.vertical, CGFloat(space.xxs))
            .background(colors.surfaceSecondary, in: Capsule())
            .overlay(Capsule().stroke(colors.borderSubtle, lineWidth: CGFloat(dims.border)))
    }
}

/// Short status label tinted by tone; the text always states the status.
struct StockBadge: View {
    @Environment(\.colorScheme) private var scheme
    let text: String
    let tone: FactTone
    var body: some View {
        let (container, content) = toneColors(tone, StockStepsTheme.colors(scheme))
        Text(text)
            .font(StockStepsTheme.font(type.label, relativeTo: .footnote))
            .foregroundStyle(content)
            .lineLimit(1)
            .padding(.horizontal, CGFloat(space.sm))
            .padding(.vertical, CGFloat(space.xxs))
            .background(container, in: Capsule())
    }
}

/// Decorative SF Symbol on a tinted rounded square.
struct StockIconTile: View {
    let systemName: String
    let container: Color
    let content: Color
    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: CGFloat(dims.iconSmall) * 0.8, weight: .semibold))
            .foregroundStyle(content)
            .frame(width: CGFloat(dims.iconTile), height: CGFloat(dims.iconTile))
            .background(container, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.chip)))
            .accessibilityHidden(true)
    }
}

/// Where today's price sits between a low and a high (day range, 52-week range).
struct StockRangeBar: View {
    @Environment(\.colorScheme) private var scheme
    let title: String
    let range: RangeSummary
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.xs)) {
            Text(title).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary)
            GeometryReader { proxy in
                let width = proxy.size.width
                let x = width * CGFloat(range.position)
                let start = max(0, x - width * 0.15)
                ZStack(alignment: .leading) {
                    Capsule().fill(colors.borderSubtle).frame(height: CGFloat(dims.rangeBar))
                    Capsule().fill(colors.positive).frame(width: x - start, height: CGFloat(dims.rangeBar)).offset(x: start)
                    Circle().fill(colors.textPrimary)
                        .frame(width: CGFloat(dims.rangeMarker), height: CGFloat(dims.rangeMarker))
                        .offset(x: min(max(0, x - CGFloat(dims.rangeMarker) / 2), width - CGFloat(dims.rangeMarker)))
                }
                .frame(height: CGFloat(dims.rangeMarker))
            }
            .frame(height: CGFloat(dims.rangeMarker))
            HStack {
                Text(range.low); Spacer(); Text(range.high)
            }
            .font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textSecondary)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title): low \(range.low), high \(range.high)")
    }
}

/// Beginner price chart: line with soft area fill, right price labels, bottom time labels.
/// Dragging reveals the point under the finger through `onScrub`.
struct StockLineChart: View {
    @Environment(\.colorScheme) private var scheme
    let summary: ChartSummary
    var onScrub: (Int?) -> Void = { _ in }
    @State private var touched: Int?

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let closes = summary.closes.map(\.doubleValue)
        let line: Color = switch summary.direction {
        case .up: colors.positive
        case .down: colors.negative
        default: colors.primary
        }
        VStack(spacing: CGFloat(space.xs)) {
            HStack(spacing: CGFloat(space.sm)) {
                GeometryReader { proxy in
                    let size = proxy.size
                    let inset = CGFloat(dims.statusDot)
                    let low = closes.min() ?? 0
                    let span = max((closes.max() ?? 0) - low, 0.000001)
                    let step = size.width / CGFloat(max(closes.count - 1, 1))
                    let point: (Int) -> CGPoint = { index in
                        CGPoint(x: CGFloat(index) * step,
                                y: inset + (size.height - inset * 2) * CGFloat(1 - (closes[index] - low) / span))
                    }
                    let path = Path { p in
                        for index in closes.indices { index == 0 ? p.move(to: point(index)) : p.addLine(to: point(index)) }
                    }
                    let focus = touched ?? closes.count - 1
                    ZStack(alignment: .topLeading) {
                        Path { p in
                            p.addPath(path)
                            p.addLine(to: CGPoint(x: size.width, y: size.height))
                            p.addLine(to: CGPoint(x: 0, y: size.height))
                            p.closeSubpath()
                        }
                        .fill(LinearGradient(colors: [line.opacity(0.22), line.opacity(0)], startPoint: .top, endPoint: .bottom))
                        path.stroke(line, style: StrokeStyle(lineWidth: CGFloat(dims.sparklineStroke) * 1.5, lineCap: .round, lineJoin: .round))
                        if touched != nil {
                            Path { p in
                                p.move(to: CGPoint(x: point(focus).x, y: 0))
                                p.addLine(to: CGPoint(x: point(focus).x, y: size.height))
                            }.stroke(colors.border, lineWidth: 1)
                        }
                        if !closes.isEmpty {
                            Circle().fill(line).frame(width: inset, height: inset)
                                .overlay(Circle().stroke(colors.surface, lineWidth: 2))
                                .position(point(focus))
                        }
                    }
                    .contentShape(Rectangle())
                    .gesture(DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            let index = min(max(Int((value.location.x / max(size.width, 1)) * CGFloat(closes.count - 1)), 0), closes.count - 1)
                            touched = index
                            onScrub(index)
                        }
                        .onEnded { _ in touched = nil; onScrub(nil) })
                }
                VStack(alignment: .trailing) {
                    ForEach(Array(summary.yLabels.enumerated()), id: \.offset) { index, label in
                        if index > 0 { Spacer(minLength: 0) }
                        Text(label)
                    }
                }
                .font(StockStepsTheme.font(type.tiny, relativeTo: .caption2)).foregroundStyle(colors.textTertiary)
            }
            HStack {
                ForEach(Array(summary.xLabels.enumerated()), id: \.offset) { index, label in
                    if index > 0 { Spacer(minLength: 0) }
                    Text(label).lineLimit(1)
                }
            }
            .font(StockStepsTheme.font(type.tiny, relativeTo: .caption2)).foregroundStyle(colors.textTertiary)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(summary.description_)
    }
}

/// One fact-based assessment line: icon tile, title, optional badge, fact and plain-English note.
struct StockAssessmentRow: View {
    @Environment(\.colorScheme) private var scheme
    let systemName: String
    let iconContainer: Color
    let iconContent: Color
    let title: String
    let detail: String
    var badge: String?
    var tone: FactTone = .neutral
    var note: String?
    var action: (() -> Void)?

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let content = HStack(spacing: CGFloat(space.md)) {
            StockIconTile(systemName: systemName, container: iconContainer, content: iconContent)
            VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                HStack(spacing: CGFloat(space.sm)) {
                    Text(title).font(StockStepsTheme.font(type.bodySemiBold)).foregroundStyle(colors.textPrimary)
                    Spacer(minLength: 0)
                    if let badge { StockBadge(text: badge, tone: tone) }
                }
                Text(detail).font(StockStepsTheme.font(type.small)).foregroundStyle(colors.textBody)
                if let note {
                    Text(note).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
                }
            }
            if action != nil {
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(colors.iconSecondary).accessibilityHidden(true)
            }
        }
        .padding(.vertical, CGFloat(space.sm))
        .frame(minHeight: CGFloat(dims.touchTarget))
        .contentShape(Rectangle())
        if let action {
            Button(action: action) { content }.buttonStyle(.plain).accessibilityElement(children: .combine)
        } else {
            content.accessibilityElement(children: .combine)
        }
    }
}

/// Equal-width pill selector for short options (chart ranges).
struct StockPillSelector<Option: Hashable>: View {
    @Environment(\.colorScheme) private var scheme
    let options: [Option]
    let selected: Option
    let label: (Option) -> String
    let onSelect: (Option) -> Void
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        HStack(spacing: 0) {
            ForEach(options, id: \.self) { option in
                let isSelected = option == selected
                Button { onSelect(option) } label: {
                    Text(label(option))
                        .font(StockStepsTheme.font(type.label, relativeTo: .footnote))
                        .foregroundStyle(isSelected ? colors.onPrimary : colors.textSecondary)
                        .lineLimit(1)
                        .padding(.horizontal, CGFloat(space.md))
                        .padding(.vertical, CGFloat(space.xs))
                        .background(isSelected ? colors.primaryDark : .clear, in: Capsule())
                        .frame(maxWidth: .infinity, minHeight: CGFloat(dims.touchTarget))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? [.isSelected] : [])
            }
        }
    }
}

/// Compact label/value line, with an optional signed change column.
struct StockInfoLine: View {
    @Environment(\.colorScheme) private var scheme
    let label: String
    let value: String
    var helper: String?
    var change: String?
    var direction: PriceDirection?
    var compact = false
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            HStack(spacing: CGFloat(space.sm)) {
                Text(label).font(StockStepsTheme.font(compact ? type.small : type.body)).foregroundStyle(compact ? colors.textSecondary : colors.textBody)
                Spacer(minLength: 0)
                Text(value).font(StockStepsTheme.font(compact ? type.numberLabelStrong : type.numberMedium)).foregroundStyle(colors.textPrimary)
                if let change {
                    Group {
                        if let direction, direction != .unavailable {
                            StockPriceChange(percentage: change, direction: direction, style: type.numberLabel)
                        } else {
                            Text(change).font(StockStepsTheme.font(type.numberLabel)).foregroundStyle(colors.textSecondary)
                        }
                    }
                    .frame(minWidth: CGFloat(dims.changeColumn), alignment: .trailing)
                }
            }
            if let helper {
                Text(helper).font(StockStepsTheme.font(type.caption, relativeTo: .caption1)).foregroundStyle(colors.textSecondary)
            }
        }
        .padding(.vertical, CGFloat(compact ? space.xs : space.sm))
        .accessibilityElement(children: .combine)
    }
}
