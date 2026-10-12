import SwiftUI
import Shared

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography

/// Allocation ring (Portfolio Phase C), mirroring the Compose `StockAllocationDonut`: slices in legend order with colours from
/// `StockChartSeriesStyle` (never gain/loss colours), geometry from the shared `StockAllocationLayout`. Decorative for VoiceOver — the legend
/// rows carry every value. Callers draw it only for valid allocations (`allocationDonutAllowed`).
struct StockAllocationDonut: View {
    let fractions: [Float]
    let colorIndexes: [Int]
    let diameter: CGFloat
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let slices = StockAllocationLayout.shared.slices(fractions: fractions.map { KotlinFloat(value: $0) })
        Canvas { context, size in
            let ring = min(size.width, size.height) * CGFloat(StockAllocationLayout.shared.RING_FRACTION)
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let radius = (min(size.width, size.height) - ring) / 2
            var track = Path()
            track.addArc(center: center, radius: radius, startAngle: .degrees(0), endAngle: .degrees(360), clockwise: false)
            context.stroke(track, with: .color(colors.surfaceSecondary), lineWidth: ring)
            for (index, slice) in slices.enumerated() {
                let start = Double(truncating: slice.first ?? 0), sweep = Double(truncating: slice.second ?? 0)
                guard sweep > 0 else { continue }
                var arc = Path()
                arc.addArc(center: center, radius: radius, startAngle: .degrees(start), endAngle: .degrees(start + sweep), clockwise: false)
                let colorIndex = index < colorIndexes.count ? colorIndexes[index] : index
                context.stroke(arc, with: .color(StockStepsTheme.color(StockStepsTheme.chartLine(colorIndex, scheme).color)), style: StrokeStyle(lineWidth: ring, lineCap: .butt))
            }
        }
        .frame(width: diameter, height: diameter)
        .accessibilityHidden(true)
    }
}

/// Allocation legend line (mirrors Compose `StockAllocationLegendRow`): colour dot · label · percentage (strong) · amount (muted), one spoken
/// label. The label always names the category, so colour is never the only cue; it wraps on whole words at large sizes.
struct StockAllocationLegendRow: View {
    let colorIndex: Int
    let label: String
    let percent: String
    let amount: String?
    let spoken: String
    /// C.1: the group's decision (`StockAllocationLegendRow.groupStacked`) so all rows read the same way; nil = decide alone.
    var groupStacked: Bool? = nil
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    @State private var width: CGFloat = 0

    /// C.1: values under the label when the whole (short) label and the values can't share the line (shared `StockLayout.metricRowStacked`,
    /// as Compose); always at accessibility sizes. Text widths only, never this row's own height.
    /// True when any line (label, percent, amount) can't share one line at `width` — then every row of the group stacks (mirrors Compose).
    static func groupStacked(_ lines: [(String, String, String?)], width: CGFloat, typeSize: DynamicTypeSize) -> Bool {
        if typeSize.isAccessibilitySize { return true }
        guard width > 0 else { return false }
        return lines.contains { label, percent, amount in
            let l = (label as NSString).size(withAttributes: [.font: StockStepsTheme.uiFont(type.small, typeSize: typeSize)]).width
            let v = max((percent as NSString).size(withAttributes: [.font: StockStepsTheme.uiFont(type.numberLabelStrong, typeSize: typeSize)]).width,
                        ((amount ?? "") as NSString).size(withAttributes: [.font: StockStepsTheme.uiFont(type.caption, typeSize: typeSize)]).width)
            return StockLayout.shared.metricRowStacked(longestLabelWord: Float(l + 18), valueWidth: Float(v), availableWidth: Float(width), gap: Float(space.sm))
        }
    }

    private var stacked: Bool {
        if let groupStacked { return groupStacked }
        if typeSize.isAccessibilitySize { return true }
        guard width > 0 else { return false }
        let labelWidth = (label as NSString).size(withAttributes: [.font: StockStepsTheme.uiFont(type.small, typeSize: typeSize)]).width
        let values = max((percent as NSString).size(withAttributes: [.font: StockStepsTheme.uiFont(type.numberLabelStrong, typeSize: typeSize)]).width,
                         ((amount ?? "") as NSString).size(withAttributes: [.font: StockStepsTheme.uiFont(type.caption, typeSize: typeSize)]).width)
        return StockLayout.shared.metricRowStacked(longestLabelWord: Float(labelWidth + 18), valueWidth: Float(values), availableWidth: Float(width), gap: Float(space.sm))
    }

    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        let dot = Circle().fill(StockStepsTheme.color(StockStepsTheme.chartLine(colorIndex, scheme).color)).frame(width: 10, height: 10)
        let values = VStack(alignment: stacked ? .leading : .trailing, spacing: 0) {
            Text(percent).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .footnote)).foregroundStyle(colors.textValue)
            if let amount { Text(amount).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textSupporting) }
        }
        Group {
            if stacked {
                HStack(alignment: .top, spacing: CGFloat(space.sm)) {
                    dot.padding(.top, 6)
                    VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                        Text(label).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textBody)
                        values
                    }
                }
            } else {
                HStack(spacing: CGFloat(space.sm)) {
                    dot
                    Text(label).font(StockStepsTheme.font(type.small, relativeTo: .footnote)).foregroundStyle(colors.textBody)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    values
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(spoken)
    }
}
